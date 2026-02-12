"""
Whisper Service for audio transcription.

Uses OpenAI's Whisper model (facebook/wav2vec2-base) to transcribe audio chunks.
"""

import io
import logging
from typing import Optional
import numpy as np
import torch
import torchaudio
from transformers import Wav2Vec2ForCTC, Wav2Vec2Processor

logger = logging.getLogger(__name__)


class WhisperService:
    """
    Service for transcribing audio to text using Whisper (Wav2Vec2).
    
    Loads the model on first use and keeps it in memory for subsequent requests.
    Handles audio buffering and transcription.
    """
    
    def __init__(self, model_name: str = "facebook/wav2vec2-base"):
        """
        Initialize the Whisper service.
        
        Args:
            model_name: Hugging Face model name (default: facebook/wav2vec2-base)
        """
        self.model_name = model_name
        self.model = None
        self.processor = None
        self.device = None
        self._load_model()
    
    def _load_model(self):
        """Load the Whisper model and processor."""
        try:
            logger.info(f"Loading Whisper model: {self.model_name}")
            
            # Determine device (MPS for Apple Silicon, CUDA if available, else CPU)
            if torch.backends.mps.is_available():
                self.device = torch.device("mps")
                logger.info("Using MPS (Apple Silicon) device")
            elif torch.cuda.is_available():
                self.device = torch.device("cuda")
                logger.info("Using CUDA device")
            else:
                self.device = torch.device("cpu")
                logger.info("Using CPU device")
            
            # Load model and processor
            self.processor = Wav2Vec2Processor.from_pretrained(self.model_name)
            self.model = Wav2Vec2ForCTC.from_pretrained(self.model_name)
            self.model.to(self.device)
            self.model.eval()
            
            logger.info(f"Whisper model loaded successfully on {self.device}")
            
        except Exception as e:
            logger.error(f"Failed to load Whisper model: {str(e)}")
            raise
    
    def transcribe(self, audio_data: bytes, sample_rate: int = 16000) -> str:
        """
        Transcribe audio data to text.
        
        Args:
            audio_data: Raw audio bytes (should be mono 16kHz)
            sample_rate: Sample rate of the audio (default: 16000)
        
        Returns:
            Transcribed text string
        
        Raises:
            ValueError: If audio data is empty or invalid
            RuntimeError: If transcription fails
        """
        if not audio_data:
            raise ValueError("Audio data is empty")
        
        try:
            # Convert bytes to audio tensor
            audio_tensor = self._bytes_to_audio_tensor(audio_data, sample_rate)
            
            # Ensure mono channel
            if audio_tensor.shape[0] > 1:
                audio_tensor = audio_tensor.mean(dim=0, keepdim=True)
            
            # Process audio through model
            with torch.no_grad():
                input_values = self.processor(
                    audio_tensor.squeeze(),
                    sampling_rate=sample_rate,
                    return_tensors="pt"
                ).input_values.to(self.device)
                
                logits = self.model(input_values).logits
                predicted_ids = torch.argmax(logits, dim=-1)
                
                transcription = self.processor.batch_decode(predicted_ids)[0]
            
            logger.info(f"Transcribed {len(audio_data)} bytes to: {transcription[:100]}...")
            return transcription
            
        except Exception as e:
            logger.error(f"Transcription failed: {str(e)}")
            raise RuntimeError(f"Transcription failed: {str(e)}")
    
    def _bytes_to_audio_tensor(self, audio_data: bytes, sample_rate: int) -> torch.Tensor:
        """
        Convert audio bytes to a PyTorch tensor.
        
        Args:
            audio_data: Raw audio bytes
            sample_rate: Target sample rate
        
        Returns:
            Audio tensor
        """
        try:
            # Try loading with torchaudio first
            audio_io = io.BytesIO(audio_data)
            waveform, original_sr = torchaudio.load(audio_io)
            
            # Resample if necessary
            if original_sr != sample_rate:
                resampler = torchaudio.transforms.Resample(orig_sr=original_sr, new_sr=sample_rate)
                waveform = resampler(waveform)
            
            return waveform
            
        except Exception as e:
            logger.warning(f"torchaudio load failed: {str(e)}, trying numpy fallback")
            
            # Fallback: simple conversion assuming 16-bit PCM
            try:
                audio_array = np.frombuffer(audio_data, dtype=np.int16)
                audio_tensor = torch.from_numpy(audio_array).float() / 32768.0
                return audio_tensor.unsqueeze(0)
            except Exception as e2:
                logger.error(f"Failed to convert audio: {str(e2)}")
                raise ValueError(f"Failed to convert audio data: {str(e2)}")
    
    def transcribe_buffer(self, audio_buffer: bytes) -> Optional[str]:
        """
        Transcribe audio from a buffer, returning None if unclear.
        
        Args:
            audio_buffer: Audio bytes from buffer
        
        Returns:
            Transcribed text or None if transcription is unclear
        """
        try:
            text = self.transcribe(audio_buffer)
            
            # Filter out very short or unclear transcriptions
            if len(text.strip()) < 2:
                logger.debug("Skipping very short transcription")
                return None
            
            return text
            
        except Exception as e:
            logger.warning(f"Buffer transcription failed: {str(e)}")
            return None
