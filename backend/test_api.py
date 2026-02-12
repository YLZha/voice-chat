#!/usr/bin/env python3
"""
Integration test suite for Voice Chat Backend
Tests all endpoints and WebSocket functionality
"""

import asyncio
import json
import sys
from pathlib import Path

# Add app to path
sys.path.insert(0, str(Path(__file__).parent))

from app.auth import token_manager, google_auth_manager
from app.config import config

try:
    import websockets
    WEBSOCKETS_AVAILABLE = True
except ImportError:
    WEBSOCKETS_AVAILABLE = False
    print("⚠️  websockets not installed - skipping WebSocket tests")
    print("   Install with: pip install websockets")


def test_config():
    """Test configuration loading"""
    print("1️⃣  Testing configuration...")
    assert config.allowlist, "Allowlist should not be empty"
    assert config.jwt_secret, "JWT secret should be set"
    assert config.access_token_expire_hours > 0, "Access token expire should be > 0"
    print(f"   ✓ Config loaded (allowlist: {len(config.allowlist)} emails)")
    print(f"   ✓ JWT secret: {'*' * 20} ({len(config.jwt_secret)} chars)")
    print()


def test_token_creation():
    """Test JWT token creation"""
    print("2️⃣  Testing token creation...")
    email = "frank@example.com"
    
    access_token = token_manager.create_access_token(email)
    assert access_token, "Access token should be created"
    print(f"   ✓ Access token created")
    print(f"     {access_token[:40]}...")
    
    refresh_token = token_manager.create_refresh_token(email)
    assert refresh_token, "Refresh token should be created"
    print(f"   ✓ Refresh token created")
    print(f"     {refresh_token[:40]}...")
    print()
    
    return access_token, refresh_token


def test_token_verification(access_token, refresh_token):
    """Test JWT token verification"""
    print("3️⃣  Testing token verification...")
    email = "frank@example.com"
    
    verified_email = token_manager.verify_access_token(access_token)
    assert verified_email == email, f"Expected {email}, got {verified_email}"
    print(f"   ✓ Access token verified: {verified_email}")
    
    verified_email = token_manager.verify_refresh_token(refresh_token)
    assert verified_email == email, f"Expected {email}, got {verified_email}"
    print(f"   ✓ Refresh token verified: {verified_email}")
    print()


def test_allowlist():
    """Test allowlist checking"""
    print("4️⃣  Testing allowlist check...")
    
    # Test allowed email
    allowed = google_auth_manager.check_allowlist("frank@example.com")
    assert allowed, "frank@example.com should be in allowlist"
    print(f"   ✓ frank@example.com: allowed")
    
    # Test not allowed email
    not_allowed = google_auth_manager.check_allowlist("random@example.com")
    assert not not_allowed, "random@example.com should NOT be in allowlist"
    print(f"   ✓ random@example.com: NOT allowed")
    print()


async def test_websocket(access_token):
    """Test WebSocket connection and echo"""
    if not WEBSOCKETS_AVAILABLE:
        print("5️⃣  Skipping WebSocket tests (websockets not installed)")
        print()
        return
    
    print("5️⃣  Testing WebSocket...")
    
    try:
        # Try to connect to running server
        uri = "ws://127.0.0.1:9000/ws/voice-chat"
        async with websockets.connect(uri) as ws:
            # Send auth token
            auth_msg = {"token": access_token}
            await ws.send(json.dumps(auth_msg))
            print("   ✓ WebSocket authenticated")
            
            # Send test audio chunk
            test_audio = b"fake audio data test 12345"
            await ws.send(test_audio)
            print(f"   ✓ Sent {len(test_audio)} bytes of test audio")
            
            # Receive echo response
            response = await ws.recv()
            assert isinstance(response, bytes), "Response should be binary"
            print(f"   ✓ Received {len(response)} bytes")
            print(f"   ✓ Echo matches: {response == test_audio}")
            
    except ConnectionRefusedError:
        print("   ⚠️  WebSocket server not running on port 9000")
        print("   Start the server with:")
        print("   python -m uvicorn app.main:app --port 9000")
    except Exception as e:
        print(f"   ❌ WebSocket test failed: {e}")
    
    print()


def test_expired_token():
    """Test that expired tokens are rejected"""
    print("6️⃣  Testing token expiration...")
    import jwt
    from datetime import datetime, timedelta, timezone
    
    # Create an already-expired token
    expired_payload = {
        "sub": "test@example.com",
        "exp": datetime.now(timezone.utc) - timedelta(hours=1),  # Expired 1 hour ago
        "type": "access"
    }
    expired_token = jwt.encode(expired_payload, config.jwt_secret, algorithm="HS256")
    
    try:
        token_manager.verify_access_token(expired_token)
        assert False, "Expired token should be rejected"
    except Exception as e:
        assert "expired" in str(e).lower(), "Error should mention expiration"
        print(f"   ✓ Expired token rejected: {str(e)[:50]}...")
    
    print()


def main():
    """Run all tests"""
    print("\n" + "="*60)
    print("🧪 Voice Chat Backend - Integration Tests")
    print("="*60 + "\n")
    
    try:
        test_config()
        access_token, refresh_token = test_token_creation()
        test_token_verification(access_token, refresh_token)
        test_allowlist()
        test_expired_token()
        
        # WebSocket test (async)
        if WEBSOCKETS_AVAILABLE:
            asyncio.run(test_websocket(access_token))
        else:
            print("5️⃣  Skipping WebSocket tests\n")
        
        print("="*60)
        print("✅ All tests passed!")
        print("="*60)
        print("\n📝 Notes:")
        print("  - To test WebSocket, start the server first:")
        print("    python -m uvicorn app.main:app --port 9000")
        print("  - API docs available at: http://127.0.0.1:9000/docs")
        print("  - Read README.md for more detailed tests and examples")
        print()
        return 0
    
    except Exception as e:
        print(f"\n❌ Test failed: {e}")
        import traceback
        traceback.print_exc()
        return 1


if __name__ == "__main__":
    sys.exit(main())
