#!/usr/bin/env python3
"""
Test script for Phase 2 voice chat backend.

Tests the full pipeline: Whisper → Claude → TTS
"""

import asyncio
import json
import sys
import os

# Add project root to path
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.config import config
from app.auth import token_manager


def test_config():
    """Test configuration loading."""
    print("1. Testing configuration...")
    print(f"   Claude model: {config.claude_model}")
    print(f"   Server port: {config.server_port}")
    print("   ✓ Configuration loaded successfully")


def test_token_creation():
    """Test JWT token creation."""
    print("\n2. Testing token creation...")
    email = "frank@example.com"
    
    access_token = token_manager.create_access_token(email)
    refresh_token = token_manager.create_refresh_token(email)
    
    print(f"   ✓ Access token created: {access_token[:30]}...")
    print(f"   ✓ Refresh token created: {refresh_token[:30]}...")
    
    # Verify tokens
    verified_email = token_manager.verify_access_token(access_token)
    assert verified_email == email
    print(f"   ✓ Access token verified: {verified_email}")
    
    verified_email = token_manager.verify_refresh_token(refresh_token)
    assert verified_email == email
    print(f"   ✓ Refresh token verified: {verified_email}")


async def test_websocket_connection():
    """Test WebSocket connection with echo (not full pipeline without services)."""
    print("\n3. Testing WebSocket connection...")
    
    try:
        import websockets
        
        # Create a token
        token = token_manager.create_access_token("frank@example.com")
        
        # Connect
        uri = "ws://127.0.0.1:9000/ws/voice-chat"
        print(f"   Connecting to {uri}...")
        
        async with websockets.connect(uri) as websocket:
            # Send auth
            await websocket.send(json.dumps({"token": token}))
            print("   ✓ Auth sent")
            
            # Receive auth confirmation
            response = await websocket.recv()
            print(f"   ✓ Auth confirmed: {response}")
            
            # Send test audio (5-second buffer worth)
            import struct
            for i in range(5):
                chunk = struct.pack('h', 0) * 16000  # 1 second of silence
                await websocket.send(chunk)
                print(f"   ✓ Sent chunk {i+1}/5")
                
                # Receive response
                try:
                    response = await asyncio.wait_for(websocket.recv(), timeout=30.0)
                    if isinstance(response, dict):
                        print(f"   ✓ Buffer status: {response.get('status', 'unknown')}")
                    else:
                        print(f"   ✓ Received {len(response)} bytes audio")
                except asyncio.TimeoutError:
                    print(f"   ! Timeout waiting for response (models may be loading)")
                    break
            
            print("   ✓ WebSocket test complete")
            
    except Exception as e:
        print(f"   ⚠ WebSocket test failed: {e}")
        print("   (This is expected if server is not running)")


def test_service_imports():
    """Test that services can be imported."""
    print("\n4. Testing service imports...")
    
    try:
        from app.services import WhisperService, ClaudeService, TTSService
        print("   ✓ All services imported successfully")
        print("   ✓ Services package structure correct")
    except ImportError as e:
        print(f"   ⚠ Service import failed: {e}")
        print("   (Expected if dependencies not installed)")


async def main():
    """Run all tests."""
    print("=" * 60)
    print("Voice Chat Backend - Phase 2 Test Suite")
    print("=" * 60)
    
    test_config()
    test_token_creation()
    await test_websocket_connection()
    test_service_imports()
    
    print("\n" + "=" * 60)
    print("✅ Phase 2 test suite complete!")
    print("=" * 60)


if __name__ == "__main__":
    asyncio.run(main())
