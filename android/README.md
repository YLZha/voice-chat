# Voice Chat Android App

A production-ready Android voice chat application built with Kotlin, Jetpack Compose, and Material Design 3.

## Features

- **Google Sign-In Integration**: Secure authentication using Google's OAuth 2.0
- **JWT Token Management**: Secure token storage with auto-refresh using EncryptedSharedPreferences
- **WebSocket Audio Streaming**: Real-time bidirectional audio communication
- **Voice Recording & Playback**: High-quality 16kHz PCM audio capture and playback
- **Modern UI**: Material Design 3 with Jetpack Compose
- **Clean Architecture**: MVVM with repository pattern and Hilt dependency injection

## Architecture

```
com.voicechat.android
├── data/
│   ├── local/           # Token storage
│   ├── remote/          # API & WebSocket services
│   └── repository/      # Repository implementations
├── domain/
│   ├── model/           # Domain models
│   ├── repository/      # Repository interfaces
│   └── usecase/         # Audio recording/playback
├── presentation/
│   ├── auth/           # Authentication UI
│   ├── chat/           # Chat UI
│   ├── components/     # Reusable UI components
│   ├── navigation/    # Navigation setup
│   └── theme/          # Material theme
└── di/                 # Hilt modules
```

## Requirements

- Android Studio Hedgehog (2023.1.1) or later
- Kotlin 1.9.22
- Gradle 8.4
- Android SDK 34 (min SDK 26)
- Java 17

## Setup Instructions

### 1. Prerequisites

Ensure you have the following installed:
- Android Studio
- JDK 17
- Android SDK
- Gradle (optional, wrapper included)

### 2. Clone and Setup

```bash
cd ~/projects/voice-chat/android
chmod +x setup.gradle.sh
./setup.gradle.sh
```

### 3. Configure Google Sign-In

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select existing
3. Enable the Google Sign-In API
4. Create OAuth 2.0 credentials:
   - Application type: Web application
   - Add Android app package: `com.voicechat.android`
   - Get the WEB_CLIENT_ID
5. Update `app/src/main/res/values/strings.xml`:
   ```xml
   <string name="web_client_id">YOUR_WEB_CLIENT_ID_HERE</string>
   ```

### 4. Configure Backend

Update the base URLs in `di/AppModule.kt`:
```kotlin
private const val BASE_URL = "https://voice.morningwoodstudio.com/"
private const val WS_BASE_URL = "wss://voice.morningwoodstudio.com/ws/voice-chat"
```

### 5. Build and Run

```bash
# Debug build
./gradlew assembleDebug

# Run on device/emulator
./gradlew installDebug

# Run tests
./gradlew test

# Run lint checks
./gradlew lint
```

## Dependencies

### Core
- AndroidX Core KTX
- Jetpack Compose
- Hilt (Dependency Injection)

### Authentication
- Google Play Services Auth

### Networking
- Retrofit 2.9
- OkHttp 4.12
- Scarlet 0.1.14 (WebSocket)

### Security
- AndroidX Security Crypto (EncryptedSharedPreferences)

### UI
- Material Design 3
- Accompanist (Permissions)

### Testing
- JUnit 4
- Mockito
- Coroutines Test
- Turbine

## Key Components

### TokenManager
Secure token storage using EncryptedSharedPreferences:
- Access token
- Refresh token
- Token expiration timestamp
- Auto-refresh on 401

### AudioRecorder
16kHz PCM audio recording:
- Configurable sample rate: 16000 Hz
- Mono channel
- 16-bit encoding
- Real-time chunk streaming

### AudioPlayer
Audio playback with AudioTrack:
- 16kHz PCM playback
- Smooth streaming
- Play/Pause/Stop controls

### WebSocket Client
Real-time communication:
- JWT authentication on connect
- Audio chunk streaming
- TTS response handling
- Auto-reconnection

## API Endpoints

### Authentication
- `POST /auth/google` - Authenticate with Google ID token
- `POST /auth/refresh` - Refresh access token
- `GET /auth/me` - Get current user

### WebSocket Protocol
- `auth` - Send JWT token
- `audio` - Send audio chunks (base64)
- `message` - Send text message
- `tts` - Receive TTS audio response
- `transcription` - Receive transcription

## Configuration Files

- `build.gradle.kts` - Project configuration
- `app/build.gradle.kts` - App module configuration
- `gradle.properties` - Gradle settings
- `local.properties` - SDK location
- `proguard-rules.pro` - ProGuard configuration

## Production Deployment

1. Update `build.gradle.kts`:
   - Set `minifyEnabled = true` for release builds
   - Configure signing credentials
   - Update version code/name

2. Test thoroughly:
   - Different network conditions
   - Token expiration scenarios
   - Audio recording/playback
   - Permission handling

3. Security considerations:
   - Use Android Keystore for encryption
   - Implement certificate pinning
   - Add ProGuard rules

## Troubleshooting

### Google Sign-In Issues
- Verify WEB_CLIENT_ID in strings.xml
- Check SHA-1 fingerprint in Google Cloud Console
- Ensure Google Play Services is installed

### Audio Issues
- Check microphone permissions
- Verify audio format matches server requirements
- Test with different sample rates

### WebSocket Issues
- Verify server endpoint is accessible
- Check firewall settings
- Monitor connection state

## License

This project is proprietary software.

## Contributing

Contact the development team for contribution guidelines.
