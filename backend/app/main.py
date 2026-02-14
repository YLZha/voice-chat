from fastapi import FastAPI, Header, HTTPException, Request, WebSocket, status, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from slowapi import Limiter
from slowapi.util import get_remote_address
from slowapi.errors import RateLimitExceeded
from starlette.middleware.base import BaseHTTPMiddleware
import asyncio
import base64
import io
import json
import logging
import os
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
_cors_origins = config.cors_origins

if not _cors_origins:
    if config.debug:
        logger.warning(
            "CORS_ORIGINS not configured — allowing all origins (debug mode). "
            "Set CORS_ORIGINS or server.cors_origins before deploying to production."
        )
        _cors_origins = ["*"]
    else:
        raise RuntimeError(
            "CORS_ORIGINS must be configured in production. "
            "Set the CORS_ORIGINS env var (comma-separated) or server.cors_origins in config.yaml."
        )

app.add_middleware(
    CORSMiddleware,
    allow_origins=_cors_origins,
    allow_credentials="*" not in _cors_origins,
    allow_methods=["GET", "POST"],
    allow_headers=["authorization", "content-type"],
)

# Rate limiting
limiter = Limiter(key_func=get_remote_address)
app.state.limiter = limiter


@app.exception_handler(RateLimitExceeded)
async def rate_limit_handler(request: Request, exc: RateLimitExceeded):
    return JSONResponse(
        status_code=429,
        content={"detail": "Rate limit exceeded. Try again later."},
    )


# Security headers middleware
class SecurityHeadersMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        response = await call_next(request)
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["X-Frame-Options"] = "DENY"
        response.headers["Referrer-Policy"] = "strict-origin-when-cross-origin"
        response.headers["Permissions-Policy"] = "microphone=(self)"
        if not config.debug:
            response.headers["Strict-Transport-Security"] = "max-age=31536000; includeSubDomains"
        return response


app.add_middleware(SecurityHeadersMiddleware)


# Health check endpoint
@app.get("/health")
async def health_check():
    """Health check endpoint"""
    return {"status": "ok", "service": "voice-chat-backend"}


# ============================================================================
# AUTH ENDPOINTS
# ============================================================================

@app.post("/auth/google", response_model=AuthResponse)
@limiter.limit("10/minute")
async def google_login(request: Request, body: GoogleLoginRequest):
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
@limiter.limit("20/minute")
async def refresh_access_token(request: Request, body: RefreshRequest):
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
@limiter.limit("30/minute")
async def get_current_user(request: Request, authorization: str = Header(...)):
    """
    Get current user info from JWT token.

    Requires Authorization header: "Bearer <access_token>"
    """
    if not authorization.startswith("Bearer "):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid authorization header"
        )
    token = authorization[7:]

    claims = token_manager.verify_access_token_full(token)
    email = claims.get("sub", "")
    return UserResponse(
        id=email,
        email=email,
        name=claims.get("name", ""),
        picture=claims.get("picture")
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
    Audio buffer that accumulates PCM chunks until a time threshold is met.

    Duration is calculated from the actual byte count (16-bit mono PCM).
    """

    def __init__(self, window_seconds: float = 5.0, sample_rate: int = 16000):
        self.window_seconds = window_seconds
        self.sample_rate = sample_rate
        self.bytes_per_second = sample_rate * 2  # 16-bit (2 bytes per sample)
        self.audio_chunks: list[bytes] = []
        self.total_bytes = 0

    @property
    def buffer_duration(self) -> float:
        return self.total_bytes / self.bytes_per_second if self.bytes_per_second else 0.0

    def add_chunk(self, audio_data: bytes) -> bool:
        self.audio_chunks.append(audio_data)
        self.total_bytes += len(audio_data)
        return self.buffer_duration >= self.window_seconds

    def is_ready(self) -> bool:
        return self.buffer_duration >= self.window_seconds

    def get_audio(self) -> bytes:
        return b''.join(self.audio_chunks)

    def clear(self):
        self.audio_chunks = []
        self.total_bytes = 0


# ============================================================================
# AUDIO PROCESSING HELPER
# ============================================================================

async def _process_audio_buffer(websocket, audio_buffer, whisper, claude, tts, email):
    """Process accumulated audio through the Whisper → Claude → TTS pipeline."""
    start_time = time.time()
    buffered_audio = audio_buffer.get_audio()
    audio_buffer.clear()

    logger.info(f"Processing audio buffer for {email}")

    # 1. Transcribe
    try:
        transcription = whisper.transcribe(buffered_audio)
        logger.info(f"Transcription: {transcription[:100]}...")
    except Exception as e:
        logger.error(f"Transcription failed: {str(e)}")
        await websocket.send_json({
            "type": "error", "code": "transcription_failed",
            "message": "Failed to transcribe audio"
        })
        return

    # Send transcription to client so they can see what they said
    await websocket.send_json({
        "type": "transcription", "text": transcription
    })

    # 2. Get Claude response
    try:
        response_text = claude.get_response(transcription)
        logger.info(f"Claude response: {response_text[:100]}...")
    except Exception as e:
        logger.error(f"Claude API failed: {str(e)}")
        await websocket.send_json({
            "type": "error", "code": "claude_failed",
            "message": "AI response failed"
        })
        return

    # 3. Convert to speech
    try:
        audio_response = tts.text_to_speech(response_text)
        audio_base64 = base64.b64encode(audio_response).decode("utf-8")
        logger.info(f"TTS complete: {len(audio_response)} bytes")
    except Exception as e:
        logger.error(f"TTS failed: {str(e)}")
        # Send text-only response (no audio)
        await websocket.send_json({
            "type": "response", "text": response_text, "audio": None
        })
        return

    total_time = time.time() - start_time
    await websocket.send_json({
        "type": "response",
        "text": response_text,
        "audio": audio_base64,
        "processing_time": round(total_time, 2)
    })
    logger.info(f"Sent response to {email} (total: {total_time:.2f}s)")


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
    # Validate WebSocket origin against allowed CORS origins
    allowed = config.cors_origins
    if allowed:
        origin = websocket.headers.get("origin", "")
        if origin not in allowed:
            logger.warning(f"WebSocket rejected: origin '{origin}' not in allowed origins")
            await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
            return

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
        
        # Voice chat loop — handles both binary audio and text control messages
        logger.info(f"Starting voice chat for {email}")

        while True:
            try:
                message = await websocket.receive()

                if message.get("type") == "websocket.disconnect":
                    logger.info(f"WebSocket disconnecting for {email}")
                    break

                # Binary frame = raw audio data
                if "bytes" in message and message["bytes"]:
                    data = message["bytes"]
                    logger.info(f"Received {len(data)} audio bytes from {email}")

                    audio_buffer.add_chunk(data)

                    if audio_buffer.is_ready():
                        await _process_audio_buffer(
                            websocket, audio_buffer, whisper, claude, tts, email
                        )
                    else:
                        await websocket.send_json({
                            "type": "buffering",
                            "buffered_seconds": audio_buffer.buffer_duration,
                            "target_seconds": audio_buffer.window_seconds
                        })

                # Text frame = JSON control message
                elif "text" in message and message["text"]:
                    try:
                        text_data = json.loads(message["text"])
                        msg_type = text_data.get("type", "")

                        if msg_type == "end_audio":
                            # Process whatever is in the buffer
                            if audio_buffer.buffer_duration > 0:
                                await _process_audio_buffer(
                                    websocket, audio_buffer,
                                    whisper, claude, tts, email
                                )
                            else:
                                await websocket.send_json({
                                    "type": "info",
                                    "message": "No audio to process"
                                })
                        elif msg_type == "ping":
                            await websocket.send_json({"type": "pong"})
                        else:
                            logger.warning(f"Unknown text message type: {msg_type}")
                    except json.JSONDecodeError:
                        logger.warning(f"Invalid JSON from {email}")

            except WebSocketDisconnect:
                logger.info(f"WebSocket disconnected for {email}")
                break
            except Exception as e:
                logger.error(f"Error processing message for {email}: {str(e)}")
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
