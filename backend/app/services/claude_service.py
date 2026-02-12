"""
Claude Service for conversational AI.

Uses Anthropic's Claude API to generate responses to transcribed text.
"""

import logging
from typing import Optional
from anthropic import Anthropic
from anthropic.types import Message

logger = logging.getLogger(__name__)


class ClaudeService:
    """
    Service for interacting with Anthropic's Claude API.
    
    Handles message creation, response generation, and error handling.
    Maintains conversation context for natural dialogue.
    """
    
    def __init__(self, api_key: str, model: str = "claude-opus-4-5"):
        """
        Initialize the Claude service.
        
        Args:
            api_key: Anthropic API key
            model: Claude model to use (default: claude-opus-4-5)
        """
        self.api_key = api_key
        self.model = model
        self.client = Anthropic(api_key=api_key)
        self.conversation_history = []
        logger.info(f"Claude service initialized with model: {model}")
    
    def add_to_history(self, role: str, content: str):
        """
        Add a message to the conversation history.
        
        Args:
            role: Message role ("user" or "assistant")
            content: Message content
        """
        self.conversation_history.append({
            "role": role,
            "content": content
        })
        
        # Keep only last 20 messages to avoid token limits
        if len(self.conversation_history) > 20:
            self.conversation_history = self.conversation_history[-20:]
    
    def clear_history(self):
        """Clear the conversation history."""
        self.conversation_history = []
        logger.info("Conversation history cleared")
    
    def get_response(self, user_message: str, max_tokens: int = 1024) -> str:
        """
        Get a response from Claude.
        
        Args:
            user_message: The user's message text
            max_tokens: Maximum tokens in response (default: 1024)
        
        Returns:
            Claude's response text
        
        Raises:
            ValueError: If user_message is empty
            RuntimeError: If API call fails
        """
        if not user_message or not user_message.strip():
            raise ValueError("User message is empty")
        
        try:
            # Add user message to history
            self.add_to_history("user", user_message)
            
            logger.info(f"Sending message to Claude: {user_message[:100]}...")
            
            # Create message with history
            response: Message = self.client.messages.create(
                model=self.model,
                max_tokens=max_tokens,
                messages=self.conversation_history
            )
            
            # Extract response text
            response_text = response.content[0].text
            
            # Add assistant response to history
            self.add_to_history("assistant", response_text)
            
            logger.info(f"Claude response: {response_text[:100]}...")
            return response_text
            
        except Exception as e:
            logger.error(f"Claude API error: {str(e)}")
            
            # Try to extract error details
            if hasattr(e, 'body'):
                logger.error(f"Claude API error details: {e.body}")
            
            raise RuntimeError(f"Claude API call failed: {str(e)}")
    
    async def get_response_async(self, user_message: str, max_tokens: int = 1024) -> str:
        """
        Async version of get_response.
        
        Args:
            user_message: The user's message text
            max_tokens: Maximum tokens in response
        
        Returns:
            Claude's response text
        """
        # Claude SDK doesn't have native async, but this wrapper is here
        # for future async implementations
        return self.get_response(user_message, max_tokens)
    
    def format_prompt(self, transcribed_text: str, context: Optional[str] = None) -> str:
        """
        Format the transcribed text into a proper prompt.
        
        Args:
            transcribed_text: Raw transcribed text from Whisper
            context: Optional context to prepend
        
        Returns:
            Formatted prompt string
        """
        prompt = transcribed_text.strip()
        
        if context:
            prompt = f"{context}\n\nUser: {prompt}"
        
        return prompt
    
    def get_conversation_stats(self) -> dict:
        """
        Get statistics about the current conversation.
        
        Returns:
            Dictionary with conversation stats
        """
        return {
            "message_count": len(self.conversation_history),
            "model": self.model,
            "has_history": len(self.conversation_history) > 0
        }
