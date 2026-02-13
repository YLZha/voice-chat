import os
import yaml
from pathlib import Path
from typing import List, Optional


class Config:
    """
    Load configuration from config.yaml with environment variable overrides.

    Environment variables take precedence over config.yaml values.
    """

    def __init__(self):
        config_path = Path(__file__).parent.parent / "config.yaml"

        if config_path.exists():
            with open(config_path, "r") as f:
                self.data = yaml.safe_load(f) or {}
        else:
            self.data = {}

    def _get(self, *keys, env_var: str = None, default=None):
        """Get a config value: env var > config.yaml > default."""
        if env_var:
            env_val = os.environ.get(env_var)
            if env_val is not None:
                return env_val

        obj = self.data
        for key in keys:
            if isinstance(obj, dict):
                obj = obj.get(key)
            else:
                return default
        return obj if obj is not None else default

    # Auth config
    @property
    def google_client_id(self) -> str:
        return self._get("auth", "google_client_id", env_var="GOOGLE_CLIENT_ID")

    @property
    def jwt_secret(self) -> str:
        secret = self._get("auth", "jwt_secret", env_var="JWT_SECRET")
        if not secret or secret in ("CHANGE_ME", "REDACTED_JWT_PLACEHOLDER"):
            raise ValueError(
                "JWT_SECRET is not configured. Set the JWT_SECRET environment variable "
                "or update jwt_secret in config.yaml. "
                "Generate one with: python3 -c \"import secrets; print(secrets.token_urlsafe(32))\""
            )
        return secret

    @property
    def access_token_expire_hours(self) -> int:
        return int(self._get("auth", "access_token_expire_hours", default=1))

    @property
    def refresh_token_expire_days(self) -> int:
        return int(self._get("auth", "refresh_token_expire_days", default=30))

    @property
    def allowlist(self) -> List[str]:
        env_val = os.environ.get("AUTH_ALLOWLIST")
        if env_val:
            return [e.strip() for e in env_val.split(",") if e.strip()]
        return self._get("auth", "allowlist", default=[])

    # TTS config
    @property
    def tts_provider(self) -> str:
        return self._get("tts", "provider", default="elevenlabs")

    @property
    def tts_api_key(self) -> str:
        return self._get("tts", "api_key", env_var="TTS_API_KEY")

    @property
    def tts_voice_id(self) -> str:
        return self._get("tts", "voice_id", default="EXAVITQu4vr4xnSDxMaL")

    # Claude config
    @property
    def claude_api_key(self) -> str:
        return self._get("claude", "api_key", env_var="CLAUDE_API_KEY")

    @property
    def claude_model(self) -> str:
        return self._get("claude", "model", default="claude-opus-4-5")

    # Server config
    @property
    def server_host(self) -> str:
        return self._get("server", "host", default="0.0.0.0")

    @property
    def server_port(self) -> int:
        return int(self._get("server", "port", default=9000))

    @property
    def debug(self) -> bool:
        env_val = os.environ.get("DEBUG")
        if env_val is not None:
            return env_val.lower() in ("true", "1", "yes")
        return bool(self._get("server", "debug", default=False))


# Global config instance
config = Config()
