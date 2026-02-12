"""
Text-to-Speech Service using Coqui TTS.

Converts text to audio using the Coqui TTS library.
"""

import io
import logging
from typing import Optional
from TTS.api import TTS

logger = logging.getLogger(__name__)


class TTSService:
    """
    Service for converting text to speech using Coqui TTS.
    
    Uses the tts_models/en/ljspeech/glow-tts model by default.
    Handles model loading, text synthesis, and audio streaming.
    """
    
    def __init__(self, model_name: str = "tts_models/en/ljspeech/glow-tts"):
        """
        Initialize the TTS service.
        
        Args:
            model_name: Coqui TTS model name (default: tts_models/en/ljspeech/glow-tts)
        """
        self.model_name = model_name
        self.tts = None
        self._load_model()
    
    def _load_model(self):
        """Load the Coqui TTS model."""
        try:
            logger.info(f"Loading Coqui TTS model: {self.model_name}")
            
            # Initialize TTS model
            self.tts = TTS(model_name=self.model_name)
            
            logger.info(f"Coqui TTS model loaded successfully")
            
        except Exception as e:
            logger.error(f"Failed to load Coqui TTS model: {str(e)}")
            raise
    
    def text_to_speech(self, text: str) -> bytes:
        """
        Convert text to speech audio.
        
        Args:
            text: Text to convert to speech
        
        Returns:
            WAV audio bytes
        
        Raises:
            ValueError: If text is empty or invalid
            RuntimeError: If TTS synthesis fails
        """
        if not text or not text.strip():
            raise ValueError("Text is empty")
        
        try:
            # Clean and truncate text
            text = text.strip()
            
            # Limit text length to avoid memory issues (max ~1000 characters)
            if len(text) > 1000:
                text = text[:1000]
                logger.warning("Text truncated to 1000 characters")
            
            logger.info(f"Generating TTS for: {text[:100]}...")
            
            # Generate speech
            # Coqui TTS returns a numpy array
            audio_output = self.tts.tts(text)
            
            # Convert to WAV bytes
            audio_bytes = self._audio_to_wav(audio_output)
            
            logger.info(f"Generated {len(audio_bytes)} bytes of audio")
            return audio_bytes
            
        except Exception as e:
            logger.error(f"TTS synthesis failed: {str(e)}")
            raise RuntimeError(f"TTS synthesis failed: {str(e)}")
    
    def _audio_to_wav(self, audio_data) -> bytes:
        """
        Convert audio data to WAV format bytes.
        
        Args:
            audio_data: Audio array (numpy or torch tensor)
        
        Returns:
            WAV bytes
        """
        try:
            import soundfile as sf
            
            # Convert to numpy if needed
            if hasattr(audio_data, 'cpu'):
                audio_data = audio_data.cpu().numpy()
            elif hasattr(audio_data, 'numpy'):
                audio_data = audio_data.numpy()
            
            # Ensure it's a 1D array
            if len(audio_data.shape) > 1:
                audio_data = audio_data.flatten()
            
            # Create WAV in memory
            wav_io = io.BytesIO()
            sf.write(wav_io, audio_data, 22050, format='WAV')
            wav_bytes = wav_io.getvalue()
            
            return wav_bytes
            
        except ImportError:
            # Fallback: simple WAV header creation
            logger.warning("soundfile not available, using simple WAV conversion")
            return self._simple_wav_header(audio_data)
        except Exception as e:
            logger.error(f"Audio conversion failed: {str(e)}")
            raise
    
    def _simple_wav_header(self, audio_data) -> bytes:
        """
        Create a simple WAV header for audio data.
        
        Args:
            audio_data: Audio array
        
        Returns:
            WAV bytes with minimal header
        """
        import struct
        import numpy as np
        
        # Convert to int16
        if audio_data.dtype != np.int16:
            audio_data = (audio_data * 32767).astype(np.int16)
        
        # WAV header parameters
        sample_rate = 22050
        num_channels = 1
        bits_per_sample = 16
        byte_rate = sample_rate * num_channels * (bits_per_sample // 8)
        block_align = num_channels * (bits_per_sample // 8)
        
        data_size = len(audio_data) * 2
        file_size = 36 + data_size
        
        # Create header
        header = struct.pack(
            '<4sI4s',  # WAV format markers
            b'RIFF',
            file_size,
            b'WAVE'
        )
        
        # fmt chunk
        header += struct.pack(
            '<4sIHHIIHH',
            b'fmt ',
            16,           # Subchunk1Size (16 for PCM)
            1,            # AudioFormat (1 = PCM)
            num_channels,
            sample_rate,
            byte_rate,
            block_align,
            bits_per_sample
        )
        
        # data chunk
        header += struct.pack(
            '<4sI',
            b'data',
            data_size
        )
        
        # Combine header with audio data
        audio_bytes = audio_data.tobytes()
        return header + audio_bytes
    
    def text_to_speech_streaming(self, text: str, chunk_size: int = 1024):
        """
        Generator for streaming TTS audio in chunks.
        
        Args:
            text: Text to convert
            chunk_size: Size of audio chunks to yield
        
        Yields:
            WAV audio chunks as bytes
        """
        # For now, return full audio (Coqui doesn't support true streaming)
        # This method is here for future optimization
        audio = self.text_to_speech(text)
        yield audio
    
    def get_model_info(self) -> dict:
        """
        Get information about the loaded TTS model.
        
        Returns:
            Dictionary with model info
        """
        return {
            "model_name": self.model_name,
            "loaded": self.tts is not None,
            "sample_rate": getattr(self.tts, 'sample_rate', None) if self.tts else None
        }
