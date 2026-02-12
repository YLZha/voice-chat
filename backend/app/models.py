from pydantic import BaseModel
from typing import Optional


class GoogleLoginRequest(BaseModel):
    """Request body for POST /auth/google"""
    id_token: str


class AuthResponse(BaseModel):
    """Response from /auth/google and /auth/refresh"""
    access_token: str
    refresh_token: Optional[str] = None  # Only returned on initial login
    token_type: str = "bearer"
    expires_in: int


class RefreshRequest(BaseModel):
    """Request body for POST /auth/refresh"""
    refresh_token: str


class WebSocketAuthMessage(BaseModel):
    """Auth message sent on WebSocket connection"""
    token: str


class UserResponse(BaseModel):
    """Response from GET /auth/me"""
    id: str
    email: str
    name: str
    picture: Optional[str] = None


class ErrorResponse(BaseModel):
    """Standard error response"""
    error: str
    detail: Optional[str] = None
