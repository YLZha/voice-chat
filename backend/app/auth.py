import jwt
import json
from datetime import datetime, timedelta, timezone
from typing import Optional
from fastapi import HTTPException, status
from google.auth.transport import requests
from google.oauth2 import id_token
from app.config import config


class TokenManager:
    """Handle JWT token creation and validation.

    Supports graceful secret rotation: new tokens are always signed with
    the current secret, but verification tries the current secret first
    and falls back to jwt_secret_previous (if configured).
    """

    @staticmethod
    def _decode(token: str) -> dict:
        """Decode a JWT, trying the current secret then the previous one."""
        secrets = [config.jwt_secret]
        prev = config.jwt_secret_previous
        if prev:
            secrets.append(prev)

        last_exc: Exception = None
        for secret in secrets:
            try:
                return jwt.decode(token, secret, algorithms=["HS256"])
            except jwt.ExpiredSignatureError:
                raise  # expiry is not a key issue — don't try next secret
            except jwt.InvalidTokenError as e:
                last_exc = e
                continue

        # None of the secrets worked
        raise last_exc  # type: ignore[misc]

    @staticmethod
    def create_access_token(email: str, name: str = "", picture: str = None, expires_delta: Optional[timedelta] = None) -> str:
        """Create a JWT access token (always signed with the current secret)."""
        if expires_delta is None:
            expires_delta = timedelta(hours=config.access_token_expire_hours)

        expire = datetime.now(timezone.utc) + expires_delta
        to_encode = {
            "sub": email,
            "name": name,
            "picture": picture,
            "exp": expire,
            "type": "access"
        }

        return jwt.encode(to_encode, config.jwt_secret, algorithm="HS256")

    @staticmethod
    def create_refresh_token(email: str, expires_delta: Optional[timedelta] = None) -> str:
        """Create a JWT refresh token (always signed with the current secret)."""
        if expires_delta is None:
            expires_delta = timedelta(days=config.refresh_token_expire_days)

        expire = datetime.now(timezone.utc) + expires_delta
        to_encode = {
            "sub": email,
            "exp": expire,
            "type": "refresh"
        }

        return jwt.encode(to_encode, config.jwt_secret, algorithm="HS256")

    @staticmethod
    def verify_access_token(token: str) -> str:
        """Verify access token and return email."""
        payload = TokenManager._verify_token(token, expected_type="access")
        return payload["sub"]

    @staticmethod
    def verify_access_token_full(token: str) -> dict:
        """Verify access token and return full claims (sub, name, picture, etc.)."""
        return TokenManager._verify_token(token, expected_type="access")

    @staticmethod
    def verify_refresh_token(token: str) -> str:
        """Verify refresh token and return email."""
        payload = TokenManager._verify_token(token, expected_type="refresh")
        return payload["sub"]

    @staticmethod
    def _verify_token(token: str, expected_type: str) -> dict:
        """Shared verification logic for access and refresh tokens."""
        try:
            payload = TokenManager._decode(token)

            if payload.get("type") != expected_type:
                raise HTTPException(
                    status_code=status.HTTP_401_UNAUTHORIZED,
                    detail="Invalid token type"
                )

            if not payload.get("sub"):
                raise HTTPException(
                    status_code=status.HTTP_401_UNAUTHORIZED,
                    detail="Token missing subject"
                )

            return payload

        except jwt.ExpiredSignatureError:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Token expired"
            )
        except jwt.InvalidTokenError:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid token"
            )


class GoogleAuthManager:
    """Handle Google OAuth verification"""
    
    @staticmethod
    def verify_google_token(token: str) -> dict:
        """
        Verify Google ID token and extract user info.
        Returns dict with email, name, picture if valid, raises HTTPException otherwise.
        """
        try:
            idinfo = id_token.verify_oauth2_token(
                token,
                requests.Request(),
                config.google_client_id
            )

            # Token is valid
            email = idinfo.get("email")
            if not email:
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail="Token does not contain email"
                )

            return {
                "email": email,
                "name": idinfo.get("name", ""),
                "picture": idinfo.get("picture"),
            }
        
        except ValueError as e:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail=f"Invalid Google token: {str(e)}"
            )
    
    @staticmethod
    def check_allowlist(email: str) -> bool:
        """Check if email is in allowlist"""
        return email in config.allowlist


# Export for use in main.py
token_manager = TokenManager()
google_auth_manager = GoogleAuthManager()
