from fastapi import FastAPI, Header, HTTPException, WebSocket, status, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
import asyncio
import base64
import io
import logging
import time
import wave
from typing import Optional, TYPE_CHECKING

if TYPE_CHECKING:
    from app.services import WhisperService, ClaudeService, TTSService

from app.models import (
    GoogleLoginRequest,
    AuthResponse,
    RefreshRequest,
    UserResponse,
    WebSocketAuthMessage,
)
from app.auth import token_manager, google_auth_manager
from app.config import config
# Lazy imports to avoid loading heavy ML models on startup
# from app.services import WhisperService, ClaudeService, TTSService

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Create FastAPI app
app = FastAPI(
    title="Voice Chat Backend",
    description="Real-time voice conversation API with JWT auth",
    version="0.1.0"
)

# Enable CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # In production, restrict this
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# Health check endpoint
@app.get("/health")
async def health_check():
    """Health check endpoint"""
    return {"status": "ok", "service": "voice-chat-backend"}


# ============================================================================
# AUTH ENDPOINTS
# ============================================================================

@app.post("/auth/google", response_model=AuthResponse)
async def google_login(body: GoogleLoginRequest):
    """
    Google OAuth login endpoint.
    
    Request: {id_token: "..."}
    
    1. Verify Google token
    2. Extract email
    3. Check allowlist
    4. Generate JWT access + refresh tokens
    
    Returns: {access_token, refresh_token, expires_in}
    """
    try:
        # Verify Google token and get user info
        user_info = google_auth_manager.verify_google_token(body.id_token)
        email = user_info["email"]
        logger.info(f"Google token verified for email: {email}")

        # Check allowlist
        if not google_auth_manager.check_allowlist(email):
            logger.warning(f"Email not in allowlist: {email}")
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Email not authorized"
            )

        logger.info(f"Email in allowlist: {email}")

        # Generate tokens
        access_token = token_manager.create_access_token(
            email, name=user_info.get("name", ""), picture=user_info.get("picture")
        )
        refresh_token = token_manager.create_refresh_token(email)
        
        logger.info(f"Tokens generated for: {email}")
        
        return AuthResponse(
            access_token=access_token,
            refresh_token=refresh_token,
            token_type="bearer",
            expires_in=config.access_token_expire_hours * 3600
        )
    
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error in google_login: {str(e)}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Internal server error"
        )


@app.post("/auth/refresh", response_model=AuthResponse)
async def refresh_access_token(body: RefreshRequest):
    """
    Refresh token endpoint.
    
    Request: {refresh_token: "..."}
    
    1. Verify refresh token
    2. Generate new access token
    
    Returns: {access_token, expires_in}
    """
    try:
        # Verify refresh token and get email
        email = token_manager.verify_refresh_token(body.refresh_token)
        logger.info(f"Refresh token verified for email: {email}")
        
        # Generate new access token
        new_access_token = token_manager.create_access_token(email)
        
        logger.info(f"New access token generated for: {email}")
        
        return AuthResponse(
            access_token=new_access_token,
            token_type="bearer",
            expires_in=config.access_token_expire_hours * 3600
        )
    
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error in refresh_access_token: {str(e)}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Internal server error"
        )


@app.get("/auth/me", response_model=UserResponse)
async def get_current_user(authorization: str = Header(...)):
    """
    Get current user info from JWT token.

    Requires Authorization header: "Bearer <access_token>"
    """
    try:
        # Extract token from "Bearer <token>"
        if not authorization.startswith("Bearer "):
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid authorization header"
            )
        token = authorization[7:]

        # Verify and decode JWT
        import jwt as pyjwt
        claims = pyjwt.decode(token, config.jwt_secret, algorithms=["HS256"])
        if claims.get("type") != "access":
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid token type"
            )

        email = claims.get("sub", "")
        return UserResponse(
            id=email,
            email=email,
            name=claims.get("name", ""),
            picture=claims.get("picture")
        )

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error in get_current_user: {str(e)}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Internal server error"
        )


# ============================================================================
# SERVICES INITIALIZATION
# ============================================================================

# Lazy initialization for services (load on first request to avoid startup delays)
_whisper_service = None
_claude_service = None
_tts_service = None


def get_whisper_service():
    """Get or create Whisper service instance."""
    global _whisper_service
    if _whisper_service is None:
        logger.info("Initializing Whisper service...")
        from app.services import WhisperService
        _whisper_service = WhisperService()
    return _whisper_service


def get_claude_service():
    """Get or create Claude service instance."""
    global _claude_service
    if _claude_service is None:
        logger.info("Initializing Claude service...")
        from app.services import ClaudeService
        _claude_service = ClaudeService(
            api_key=config.claude_api_key,
            model=config.claude_model
        )
    return _claude_service


def get_tts_service():
    """Get or create TTS service instance."""
    global _tts_service
    if _tts_service is None:
        logger.info("Initializing TTS service...")
        from app.services import TTSService
        _tts_service = TTSService()
    return _tts_service


# ============================================================================
# AUDIO BUFFERING
# ============================================================================

class AudioBuffer:
    """
    Sliding window audio buffer for accumulating 1-second chunks.
    
    Buffers audio until 5-second window is complete, then processes.
    """
    
    def __init__(self, window_seconds: float = 5.0, sample_rate: int = 16000):
        self.window_seconds = window_seconds
        self.sample_rate = sample_rate
        self.audio_chunks = []
        self.buffer_duration = 0.0
        self.chunk_duration = 1.0  # Assume 1-second chunks from client
        
    def add_chunk(self, audio_data: bytes) -> bool:
        """
        Add a new audio chunk to the buffer.
        
        Args:
            audio_data: Raw audio bytes
        
        Returns:
            True if buffer is ready (5-second window complete)
        """
        self.audio_chunks.append(audio_data)
        self.buffer_duration += self.chunk_duration
        return self.buffer_duration >= self.window_seconds
    
    def is_ready(self) -> bool:
        """Check if buffer has reached the window threshold."""
        return self.buffer_duration >= self.window_seconds
    
    def get_audio(self) -> bytes:
        """
        Get concatenated audio from buffer.
        
        Returns:
            Concatenated audio bytes
        """
        return b''.join(self.audio_chunks)
    
    def clear(self):
        """Clear the buffer."""
        self.audio_chunks = []
        self.buffer_duration = 0.0
    
    def get_partial_audio(self) -> bytes:
        """
        Get any accumulated audio (for early processing if needed).
        
        Returns:
            Concatenated audio bytes (may be less than full window)
        """
        return b''.join(self.audio_chunks)


# ============================================================================
# WEBSOCKET ENDPOINT
# ============================================================================

@app.websocket("/ws/voice-chat")
async def websocket_voice_chat(websocket: WebSocket):
    """
    WebSocket endpoint for voice chat with full AI pipeline.
    
    Pipeline:
    1. Await auth message with JWT token
    2. Verify token
    3. Receive 1-second audio chunks from client
    4. Buffer to 5-second window
    5. Transcribe with Whisper
    6. Send text to Claude
    7. Convert response to speech with TTS
    8. Stream audio response back to client
    
    Auth handshake:
    - Client sends: {token: "JWT_ACCESS_TOKEN"}
    - Server validates token and accepts connection
    
    Audio streaming:
    - Client sends: binary audio chunks (1-second chunks)
    - Server processes through AI pipeline
    - Server sends back: binary WAV audio response
    """
    await websocket.accept()
    logger.info("WebSocket connection accepted, waiting for auth...")
    
    # Services for this connection
    audio_buffer = AudioBuffer(window_seconds=5.0)
    whisper = None
    claude = None
    tts = None
    email = None
    
    try:
        # Await auth message
        auth_data = await websocket.receive_json()
        token = auth_data.get("token")
        
        if not token:
            logger.warning("No token provided in auth message")
            await websocket.close(code=status.WS_1008_POLICY_VIOLATION, reason="No token")
            return
        
        # Verify token
        try:
            email = token_manager.verify_access_token(token)
            logger.info(f"WebSocket authenticated for: {email}")
        except HTTPException as e:
            logger.warning(f"Token verification failed: {e.detail}")
            await websocket.close(code=status.WS_1008_POLICY_VIOLATION, reason="Invalid token")
            return
        
        # Initialize services for this connection
        try:
            whisper = get_whisper_service()
            claude = get_claude_service()
            tts = get_tts_service()
            logger.info(f"Services initialized for {email}")
        except Exception as e:
            logger.error(f"Failed to initialize services: {str(e)}")
            await websocket.close(
                code=status.WS_1011_SERVER_ERROR,
                reason="Service initialization failed"
            )
            return
        
        # Voice chat loop
        logger.info(f"Starting voice chat for {email}")
        
        while True:
            try:
                # Receive audio chunk from client
                start_time = time.time()
                data = await websocket.receive_bytes()
                
                if not data:
                    logger.info(f"Empty audio chunk received from {email}")
                    continue
                
                audio_size = len(data)
                logger.info(f"Received {audio_size} bytes from {email}")
                
                # Add to buffer
                audio_buffer.add_chunk(data)
                
                # Process if buffer is ready (5-second window)
                if audio_buffer.is_ready():
                    logger.info(f"Processing 5-second buffer for {email}")
                    
                    # Get buffered audio
                    buffered_audio = audio_buffer.get_audio()
                    
                    # Transcribe with Whisper
                    try:
                        transcription_start = time.time()
                        transcription = whisper.transcribe(buffered_audio)
                        transcription_time = time.time() - transcription_start
                        logger.info(
                            f"Transcription complete ({transcription_time:.2f}s): "
                            f"{transcription[:100]}..."
                        )
                    except Exception as e:
                        logger.error(f"Transcription failed: {str(e)}")
                        audio_buffer.clear()
                        continue
                    
                    # Get response from Claude
                    try:
                        claude_start = time.time()
                        response = claude.get_response(transcription)
                        claude_time = time.time() - claude_start
                        logger.info(
                            f"Claude response ({claude_time:.2f}s): "
                            f"{response[:100]}..."
                        )
                    except Exception as e:
                        logger.error(f"Claude API failed: {str(e)}")
                        # Send error audio
                        error_audio = tts.text_to_speech("I'm having trouble connecting to my brain. Please try again.")
                        await websocket.send_bytes(error_audio)
                        audio_buffer.clear()
                        continue
                    
                    # Convert to speech with TTS
                    try:
                        tts_start = time.time()
                        audio_response = tts.text_to_speech(response)
                        tts_time = time.time() - tts_start
                        logger.info(f"TTS complete ({tts_time:.2f}s): {len(audio_response)} bytes")
                    except Exception as e:
                        logger.error(f"TTS failed: {str(e)}")
                        # Try to send error message
                        try:
                            error_audio = tts.text_to_speech("I understand, but I'm having trouble speaking. Let me text instead.")
                            await websocket.send_bytes(error_audio)
                        except:
                            pass
                        audio_buffer.clear()
                        continue
                    
                    # Send audio response back to client
                    total_processing_time = time.time() - start_time
                    logger.info(
                        f"Total pipeline time: {total_processing_time:.2f}s "
                        f"for {audio_size} bytes → {len(audio_response)} bytes"
                    )
                    
                    await websocket.send_bytes(audio_response)
                    logger.info(f"Sent audio response to {email}")
                    
                    # Clear buffer after processing
                    audio_buffer.clear()
                else:
                    # Not ready yet, acknowledge receipt
                    await websocket.send_json({
                        "status": "buffering",
                        "buffered_seconds": audio_buffer.buffer_duration,
                        "target_seconds": audio_buffer.window_seconds
                    })
                    logger.info(
                        f"Buffering: {audio_buffer.buffer_duration:.1f}/"
                        f"{audio_buffer.window_seconds}s"
                    )
            
            except WebSocketDisconnect:
                logger.info(f"WebSocket disconnected for {email}")
                break
            except Exception as e:
                logger.error(f"Error processing audio for {email}: {str(e)}")
                await websocket.close(
                    code=status.WS_1011_SERVER_ERROR,
                    reason="Processing error"
                )
                break
    
    except WebSocketDisconnect:
        logger.info(f"WebSocket disconnected for {email}")
    except Exception as e:
        logger.error(f"WebSocket error: {str(e)}")
        await websocket.close(code=status.WS_1011_SERVER_ERROR, reason="Internal error")
    finally:
        # Cleanup
        if email:
            logger.info(f"Cleaning up voice chat for {email}")


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "app.main:app",
        host=config.server_host,
        port=config.server_port,
        reload=config.debug
    )
