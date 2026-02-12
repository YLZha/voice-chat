import jwt
import json
from datetime import datetime, timedelta, timezone
from typing import Optional
from fastapi import HTTPException, status
from google.auth.transport import requests
from google.oauth2 import id_token
from app.config import config


class TokenManager:
    """Handle JWT token creation and validation"""
    
    @staticmethod
    def create_access_token(email: str, name: str = "", picture: str = None, expires_delta: Optional[timedelta] = None) -> str:
        """Create a JWT access token"""
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

        encoded_jwt = jwt.encode(to_encode, config.jwt_secret, algorithm="HS256")
        return encoded_jwt
    
    @staticmethod
    def create_refresh_token(email: str, expires_delta: Optional[timedelta] = None) -> str:
        """Create a JWT refresh token"""
        if expires_delta is None:
            expires_delta = timedelta(days=config.refresh_token_expire_days)
        
        expire = datetime.now(timezone.utc) + expires_delta
        to_encode = {
            "sub": email,
            "exp": expire,
            "type": "refresh"
        }
        
        encoded_jwt = jwt.encode(to_encode, config.jwt_secret, algorithm="HS256")
        return encoded_jwt
    
    @staticmethod
    def verify_access_token(token: str) -> str:
        """Verify access token and return email"""
        try:
            payload = jwt.decode(token, config.jwt_secret, algorithms=["HS256"])
            
            # Check token type
            if payload.get("type") != "access":
                raise HTTPException(
                    status_code=status.HTTP_401_UNAUTHORIZED,
                    detail="Invalid token type"
                )
            
            email: str = payload.get("sub")
            if email is None:
                raise HTTPException(
                    status_code=status.HTTP_401_UNAUTHORIZED,
                    detail="Token missing subject"
                )
            
            return email
        
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
    
    @staticmethod
    def verify_refresh_token(token: str) -> str:
        """Verify refresh token and return email"""
        try:
            payload = jwt.decode(token, config.jwt_secret, algorithms=["HS256"])
            
            # Check token type
            if payload.get("type") != "refresh":
                raise HTTPException(
                    status_code=status.HTTP_401_UNAUTHORIZED,
                    detail="Invalid token type"
                )
            
            email: str = payload.get("sub")
            if email is None:
                raise HTTPException(
                    status_code=status.HTTP_401_UNAUTHORIZED,
                    detail="Token missing subject"
                )
            
            return email
        
        except jwt.ExpiredSignatureError:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Refresh token expired"
            )
        except jwt.InvalidTokenError:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid refresh token"
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
