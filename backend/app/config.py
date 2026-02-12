import os
import yaml
from pathlib import Path
from typing import List, Optional

class Config:
    """Load and manage configuration from config.yaml"""
    
    def __init__(self):
        config_path = Path(__file__).parent.parent / "config.yaml"
        
        if not config_path.exists():
            raise FileNotFoundError(f"config.yaml not found at {config_path}")
        
        with open(config_path, "r") as f:
            self.data = yaml.safe_load(f)
    
    # Auth config
    @property
    def google_client_id(self) -> str:
        return self.data["auth"]["google_client_id"]
    
    @property
    def jwt_secret(self) -> str:
        return self.data["auth"]["jwt_secret"]
    
    @property
    def access_token_expire_hours(self) -> int:
        return self.data["auth"]["access_token_expire_hours"]
    
    @property
    def refresh_token_expire_days(self) -> int:
        return self.data["auth"]["refresh_token_expire_days"]
    
    @property
    def allowlist(self) -> List[str]:
        return self.data["auth"]["allowlist"]
    
    # TTS config
    @property
    def tts_provider(self) -> str:
        return self.data["tts"]["provider"]
    
    @property
    def tts_api_key(self) -> str:
        return self.data["tts"]["api_key"]
    
    @property
    def tts_voice_id(self) -> str:
        return self.data["tts"]["voice_id"]
    
    # Claude config
    @property
    def claude_api_key(self) -> str:
        return self.data["claude"]["api_key"]
    
    @property
    def claude_model(self) -> str:
        return self.data["claude"]["model"]
    
    # Server config
    @property
    def server_host(self) -> str:
        return self.data["server"]["host"]
    
    @property
    def server_port(self) -> int:
        return self.data["server"]["port"]
    
    @property
    def debug(self) -> bool:
        return self.data["server"]["debug"]


# Global config instance
config = Config()
