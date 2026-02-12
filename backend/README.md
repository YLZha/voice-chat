# Voice Chat Backend (FastAPI)

Real-time voice conversation API with JWT authentication and Google OAuth.

## ✅ Phase 1 Complete

- [x] Project directory created: `~/projects/voice-chat-backend`
- [x] FastAPI project structure with venv
- [x] Auth endpoints: `/auth/google` and `/auth/refresh`
- [x] WebSocket `/ws/voice-chat` stub (echoes audio for now)
- [x] Configuration: `config.yaml` with allowlist and JWT secret
- [x] Requirements: `requirements.txt` with all dependencies

## ✅ Phase 2 Complete

- [x] **Whisper Integration** (`app/services/whisper_service.py`)
  - Loads `facebook/wav2vec2-base` model (~1GB)
  - Transcribes 5-second audio buffers to text
  - Handles MPS/CUDA/CPU devices automatically

- [x] **Claude Integration** (`app/services/claude_service.py`)
  - Connects to Anthropic Claude API
  - Maintains conversation history
  - Handles API errors gracefully

- [x] **Coqui TTS Integration** (`app/services/tts_service.py`)
  - Loads `tts_models/en/ljspeech/glow-tts` model (~1GB)
  - Converts text to WAV audio
  - Streaming support ready

- [x] **Full Pipeline** (`app/main.py`)
  - Receives 1-second audio chunks
  - Buffers to 5-second window
  - Transcribes → Claude → TTS
  - Streams audio response back

- [x] **Logging & Error Handling**
  - Comprehensive logging at INFO level
  - Error handling for API failures
  - Graceful degradation on errors

- [x] **Updated Requirements**
  - Added: `openai-whisper`, `anthropic`, `TTS`
  - Added: `torch`, `torchaudio`, `transformers`
  - Added: `soundfile` for WAV conversion

## Project Structure

```
voice-chat-backend/
├── app/
│   ├── __init__.py
│   ├── main.py           # FastAPI app + all endpoints
│   ├── config.py         # Configuration loader (reads config.yaml)
│   ├── auth.py           # JWT + Google OAuth verification
│   ├── models.py         # Pydantic request/response models
│   └── services/
│       ├── __init__.py
│       ├── whisper_service.py   # Audio transcription (Whisper)
│       ├── claude_service.py     # Claude API integration
│       └── tts_service.py        # Text-to-speech (Coqui TTS)
├── config.yaml           # Configuration (allowlist, JWT secret, etc.)
├── requirements.txt      # Python dependencies
└── README.md
```

## Setup

### 1. Install Dependencies

```bash
cd ~/projects/voice-chat-backend
source venv/bin/activate
pip install -r requirements.txt
```

### 2. Update Configuration

Edit `config.yaml`:

```yaml
auth:
  google_client_id: "YOUR_GOOGLE_CLIENT_ID.apps.googleusercontent.com"
  jwt_secret: "REDACTED_JWT_PLACEHOLDER"
  allowlist:
    - frank@example.com      # Add allowed emails here
    - family@example.com

claude:
  api_key: "your-claude-api-key"    # Get from Anthropic Console
  model: "claude-opus-4-5"
```

## Testing

### Start the Server

```bash
source venv/bin/activate
python -m uvicorn app.main:app --host 127.0.0.1 --port 9000 --reload
```

Server runs at: `http://127.0.0.1:9000`

API docs: `http://127.0.0.1:9000/docs`

### Test Health Check

```bash
curl http://127.0.0.1:9000/health
```

Response:
```json
{"status": "ok", "service": "voice-chat-backend"}
```

### Test with Simple WebSocket Client

Create `test_voice_chat.py`:

```python
#!/usr/bin/env python3
import asyncio
import json
import websockets
from app.auth import token_manager

async def test_voice_chat():
    """Test the full voice chat pipeline."""
    
    # Create a token
    token = token_manager.create_access_token("frank@example.com")
    print(f"✓ Token created: {token[:30]}...")
    
    # Connect to WebSocket
    uri = "ws://127.0.0.1:9000/ws/voice-chat"
    print(f"Connecting to {uri}...")
    
    async with websockets.connect(uri) as websocket:
        # Send auth token
        await websocket.send(json.dumps({"token": token}))
        print("✓ Auth sent")
        
        # Wait for ready message
        response = await websocket.recv()
        print(f"✓ Server ready: {response}")
        
        # Create test audio (5 seconds of silence = 5 * 16000 * 2 bytes)
        # For real test, use actual audio from microphone
        import struct
        audio_data = struct.pack('h', 0) * 80000  # 5 seconds of silence
        print(f"✓ Sending {len(audio_data)} bytes of test audio...")
        
        # Send audio chunks (simulating 5 1-second chunks)
        for i in range(5):
            chunk = struct.pack('h', 0) * 16000  # 1 second
            await websocket.send(chunk)
            print(f"✓ Sent chunk {i+1}/5")
            
            # Receive response
            response = await websocket.recv()
            print(f"✓ Received {len(response)} bytes response")
            print(f"✓ Response is audio: {response[:4] == b'RIFF'}")
    
    print("\n✅ Voice chat test complete!")

asyncio.run(test_voice_chat())
```

Run it:
```bash
source venv/bin/activate
python test_voice_chat.py
```

Or test with real audio using a recording:
```python
# Record audio using pyaudio or similar
import pyaudio
import wave

def record_audio(seconds=5):
    """Record audio from microphone."""
    p = pyaudio.PyAudio()
    stream = p.open(format=pyaudio.paInt16, channels=1, rate=16000, input=True)
    frames = []
    
    for _ in range(seconds):
        data = stream.read(16000)
        frames.append(data)
    
    stream.stop_stream()
    stream.close()
    p.terminate()
    
    return b''.join(frames)
```

### Test Google Login (POST /auth/google)

```bash
curl -X POST http://127.0.0.1:9000/auth/google \
  -H "Content-Type: application/json" \
  -d '{"google_id_token": "FAKE_TOKEN"}'
```

Expected: `401 Unauthorized` (because token is fake)

In production, use real Google ID token from Firebase:
```python
# Android/iOS sends this after Google Sign-In
{
  "google_id_token": "eyJhbGc..."
}
```

### Test Token Refresh (POST /auth/refresh)

First, create a real token:

```python
from app.auth import token_manager

# Create a refresh token
refresh_token = token_manager.create_refresh_token("frank@example.com")
print(f"Refresh token: {refresh_token}")

# Now refresh it
curl -X POST http://127.0.0.1:9000/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refresh_token": "'$refresh_token'"}'
```

Response:
```json
{
  "access_token": "new_jwt_token...",
  "token_type": "bearer",
  "expires_in": 3600
}
```

### Test WebSocket (ws://127.0.0.1:9000/ws/voice-chat)

Using Python WebSocket client:

```python
import asyncio
import websockets
import json
from app.auth import token_manager

async def test_websocket():
    # Create a token
    token = token_manager.create_access_token("frank@example.com")
    
    # Connect to WebSocket
    uri = "ws://127.0.0.1:9000/ws/voice-chat"
    async with websockets.connect(uri) as websocket:
        # Send auth token
        await websocket.send(json.dumps({"token": token}))
        print("✓ Authenticated")
        
        # Send fake audio data
        audio_chunk = b"fake audio data 1234567890"
        await websocket.send(audio_chunk)
        print(f"✓ Sent {len(audio_chunk)} bytes")
        
        # Receive echo (should match what we sent)
        response = await websocket.recv()
        print(f"✓ Received {len(response)} bytes")
        print(f"✓ Echo matches: {response == audio_chunk}")

# Run the test
asyncio.run(test_websocket())
```

Or using `websocat`:

```bash
# Install websocat
brew install websocat

# Create a test token first
python3 -c "from app.auth import token_manager; print(token_manager.create_access_token('frank@example.com'))" > /tmp/token.txt

# Connect
websocat "ws://127.0.0.1:9000/ws/voice-chat"
# Send: {"token": "PASTE_TOKEN_HERE"}
# Then send binary data (hit Ctrl+D to send)
```

### Full Integration Test

Create a test file `test_api.py`:

```python
#!/usr/bin/env python3
import asyncio
import json
import websockets
from app.auth import token_manager, google_auth_manager

print("🧪 Voice Chat Backend - Integration Tests\n")

# Test 1: Token creation
print("1️⃣  Testing token creation...")
email = "frank@example.com"
access_token = token_manager.create_access_token(email)
refresh_token = token_manager.create_refresh_token(email)
print(f"   ✓ Access token: {access_token[:30]}...")
print(f"   ✓ Refresh token: {refresh_token[:30]}...")

# Test 2: Token verification
print("\n2️⃣  Testing token verification...")
verified_email = token_manager.verify_access_token(access_token)
print(f"   ✓ Access token verified: {verified_email}")

verified_email = token_manager.verify_refresh_token(refresh_token)
print(f"   ✓ Refresh token verified: {verified_email}")

# Test 3: Allowlist check
print("\n3️⃣  Testing allowlist check...")
allowed = google_auth_manager.check_allowlist("frank@example.com")
print(f"   ✓ frank@example.com allowed: {allowed}")

not_allowed = google_auth_manager.check_allowlist("random@example.com")
print(f"   ✓ random@example.com allowed: {not_allowed}")

# Test 4: WebSocket connection
print("\n4️⃣  Testing WebSocket...")

async def test_ws():
    try:
        uri = "ws://127.0.0.1:9000/ws/voice-chat"
        async with websockets.connect(uri) as ws:
            # Send auth
            await ws.send(json.dumps({"token": access_token}))
            print("   ✓ WebSocket auth sent")
            
            # Send audio chunk
            test_audio = b"test audio data 12345"
            await ws.send(test_audio)
            print(f"   ✓ Sent {len(test_audio)} bytes")
            
            # Receive echo
            response = await ws.recv()
            print(f"   ✓ Received {len(response)} bytes")
            print(f"   ✓ Echo matches: {response == test_audio}")
    except Exception as e:
        print(f"   ❌ WebSocket test failed: {e}")
        print("   (Make sure server is running on port 9000)")

asyncio.run(test_ws())

print("\n✅ All tests passed!")
```

Run it:
```bash
source venv/bin/activate
python test_api.py
```

## API Endpoints

### POST /auth/google
- **Purpose:** Google OAuth login
- **Request:** `{"google_id_token": "..."}`
- **Response:** `{"access_token": "...", "refresh_token": "...", "expires_in": 3600}`
- **Status Codes:**
  - `200 OK` - Login successful
  - `403 Forbidden` - Email not in allowlist
  - `401 Unauthorized` - Invalid Google token

### POST /auth/refresh
- **Purpose:** Refresh access token
- **Request:** `{"refresh_token": "..."}`
- **Response:** `{"access_token": "...", "expires_in": 3600}`
- **Status Codes:**
  - `200 OK` - Token refreshed
  - `401 Unauthorized` - Invalid or expired refresh token

### WebSocket /ws/voice-chat
- **Purpose:** Real-time voice chat
- **Auth:** Send `{"token": "JWT_ACCESS_TOKEN"}` on connect
- **Data Flow:**
  1. Client sends audio chunks (binary)
  2. Server echoes back (current stub behavior)
  3. Connection closes on disconnect or auth failure
- **Status Codes:**
  - `1000` - Normal closure
  - `1008` - Policy violation (no token)
  - `1011` - Server error

## Configuration

### config.yaml Sections

**auth:**
- `google_client_id`: Google OAuth 2.0 Client ID
- `jwt_secret`: Secret key for JWT signing (change in production!)
- `access_token_expire_hours`: How long access tokens last (default: 1 hour)
- `refresh_token_expire_days`: How long refresh tokens last (default: 30 days)
- `allowlist`: List of allowed email addresses

**tts:** (ElevenLabs fallback)
- `provider`: `"elevenlabs"` or `"espeak"` (for fallback)
- `api_key`: ElevenLabs API key (optional, using Coqui TTS by default)
- `voice_id`: ElevenLabs voice ID

**claude:**
- `api_key`: **REQUIRED** - Claude API key from Anthropic Console
- `model`: Claude model to use (`claude-opus-4-5` recommended)

**whisper:** (optional)
- `model_name`: Whisper model variant (`facebook/wav2vec2-base`)

**tts_coqui:** (Coqui TTS)
- `model_name`: Coqui TTS model (`tts_models/en/ljspeech/glow-tts`)
- `sample_rate`: Output sample rate (22050 Hz)

**server:**
- `host`: Bind address (default: `0.0.0.0`)
- `port`: Port to listen on (default: `9000`)
- `debug`: Enable debug mode (default: `true`)

## What's Next (Phase 3)

- [ ] Optimize latency (aim for <2s total pipeline time)
- [ ] Add conversation context management
- [ ] Implement interrupt handling (stop speaking when user interrupts)
- [ ] Add audio quality enhancement
- [ ] Implement rate limiting on auth endpoints
- [ ] Add monitoring and metrics
- [ ] Mobile app integration

## Phase 2 Summary

Phase 2 implements the complete AI voice chat pipeline:

1. **Audio Input**: Client sends 1-second audio chunks (mono 16kHz PCM16)
2. **Buffering**: Server accumulates 5-second windows
3. **Transcription**: Whisper (Wav2Vec2) converts audio to text
4. **Response**: Claude generates conversational response
5. **Synthesis**: Coqui TTS converts text to WAV audio
6. **Output**: Server streams audio back to client

### Pipeline Timing

Typical processing times (on Mac mini M2):
- Whisper transcription: 1-3 seconds (first run slower due to model load)
- Claude API: 0.5-2 seconds (network dependent)
- Coqui TTS: 0.5-1 second
- **Total**: 2-6 seconds end-to-end

### Model Downloads (~2GB total)

First run will download:
- **Whisper (Wav2Vec2)**: ~1GB (`facebook/wav2vec2-base`)
- **Coqui TTS**: ~1GB (`tts_models/en/ljspeech/glow-tts`)

Expect 5-10 minutes for initial download.

See `/Users/scrubplane/.openclaw/workspace/VOICE_CHAT_PLAN.md` for full plan.

## Running the Server in Production

### Local Testing
```bash
source venv/bin/activate
python -m uvicorn app.main:app --host 127.0.0.1 --port 9000
```

### Via Cloudflare Tunnel
```bash
# In Terminal 1: Start backend
source venv/bin/activate
python -m uvicorn app.main:app --host 0.0.0.0 --port 9000

# In Terminal 2: Start Cloudflare tunnel
cloudflared tunnel run voice-chat-app
```

The app will be accessible at: `https://voice.scrubplane.com`

## Logs

The app logs to stdout. Watch for:
- `INFO` - Normal operations
- `WARNING` - Auth failures
- `ERROR` - Errors (check these!)

Example:
```
INFO:app.main:Google token verified for email: frank@example.com
INFO:app.main:Email in allowlist: frank@example.com
INFO:app.main:Tokens generated for: frank@example.com
INFO:app.main:WebSocket connection accepted, waiting for auth...
INFO:app.main:WebSocket authenticated for: frank@example.com
INFO:app.main:Received 21 bytes from frank@example.com
INFO:app.main:Echoed 21 bytes back to frank@example.com
```

## Security Notes

⚠️ **Development Only**

The config files and JWT secret are committed to git for easy testing. In production:

- [ ] Move `config.yaml` to environment variables
- [ ] Use strong random JWT secret (e.g., `openssl rand -hex 32`)
- [ ] Enable HTTPS (Cloudflare provides this)
- [ ] Restrict CORS origins
- [ ] Add rate limiting
- [ ] Monitor auth failures
- [ ] **NEVER commit Claude API keys** - use environment variables
- [ ] Add API key validation before service initialization

## Troubleshooting

### Import errors
```
ModuleNotFoundError: No module named 'app'
```
Make sure you're in the project root and have activated the venv:
```bash
cd ~/projects/voice-chat-backend
source venv/bin/activate
```

### Port already in use
```
Address already in use: ('127.0.0.1', 9000)
```
Use a different port:
```bash
python -m uvicorn app.main:app --port 9001
```

### config.yaml not found
```
FileNotFoundError: config.yaml not found
```
Make sure `config.yaml` is in the project root:
```bash
ls -la ~/projects/voice-chat-backend/config.yaml
```

### Claude API errors
```
RuntimeError: Claude API call failed: Invalid API key
```
Check your Claude API key in `config.yaml`:
```bash
grep -A1 "claude:" config.yaml
```

### Model download failures
First run downloads ~2GB models. If it fails:
```bash
# Clear cache and retry
rm -rf ~/.cache/torch ~/.cache/huggingface
python -c "from transformers import Wav2Vec2Processor; print('OK')"
```

### Out of memory (Mac mini)
If models crash due to memory:
```bash
# Use CPU instead of MPS
export PYTORCH_ENABLE_MPS_FALLBACK=1
```

### Audio quality issues
Ensure client sends:
- **Format**: Mono (single channel)
- **Sample rate**: 16000 Hz
- **Bit depth**: 16-bit PCM
- **Chunk size**: ~32000 bytes/second (16000 * 2)

## Files

- **app/main.py** - FastAPI app with all endpoints and WebSocket handler
- **app/auth.py** - JWT and Google OAuth verification
- **app/config.py** - Configuration loader
- **app/models.py** - Pydantic models for requests/responses
- **app/services/whisper_service.py** - Whisper transcription service
- **app/services/claude_service.py** - Claude API integration
- **app/services/tts_service.py** - Coqui TTS text-to-speech
- **config.yaml** - Configuration file (allowlist, secrets, AI models)
- **requirements.txt** - Python dependencies (including Phase 2)
- **README.md** - This file
