# Grid - Android Server File Browser

A modern Android application for browsing and managing files on remote servers through FTP, SFTP, and SMB protocols. Built with Clean Architecture principles and the latest Android development technologies.

## Current Status

### ✅ Working Features
- **SMB/CIFS**: Successfully connects to Windows shares and SMB servers with full file browsing
- **File Browser**: Complete file browser with list and grid view modes
- **File Upload**: Functional file upload with Material 3 expressive wavy progress indicators
- **File Opening**: Open text and image files with built-in viewers (text editor, image viewer)
- **File Downloads**: Download selected files to public Downloads folder with batch support
- **Multi-Select**: File selection mode with multi-select capabilities and batch operations
- **File Operations**: Delete, rename, and create directory functionality with proper SMB directory handling
- **File Sorting**: Sort files by name, type, or last modified date with session persistence
- **Long Press**: Enter selection mode with haptic feedback (works in both list and grid views)
- **Path Display**: Windows-style path formatting for SMB connections (\SHARENAME\PATH)
- **Navigation**: Proper back button handling with directory-level navigation (no more jumping to root)
- **Progress Animation**: Smooth animated progress bars for file transfer operations
- **Pull-to-Refresh**: Material 3 pull-to-refresh functionality for refreshing file listings
- **Biometric Authentication**: Secure biometric unlock for connections with auto-authentication
- **Connection Reordering**: Move up/down buttons in connection menu for custom ordering
- **Settings System**: Persistent settings with theme switching and view mode preferences  
- **UI Framework**: Material 3 interface with expressive floating toolbars and navigation
- **Architecture**: Clean Architecture implementation with Hilt DI
- **Secure Storage**: Android Keystore encryption for credentials
- **Connection Management**: Add, edit, test, and manage server connections
- **About Section**: Developer information with clickable website link and version info

### ⚠️ Known Issues
- **SFTP**: Connection failures with cryptography provider issues - not currently functional
- **FTP**: Connection timeout and stability issues - not currently functional
- **Directory Downloads**: Directory downloads are skipped (files only for now)

### 🚧 In Development
- SFTP and FTP protocol stability improvements
- Directory download support with recursive file extraction
- Additional file type viewers (PDF, video, audio)
- File search and filtering capabilities

## Features

### Protocol Support
- **SMB** - Server Message Block for Windows file sharing (✅ Fully functional)
- **FTP** - File Transfer Protocol with secure authentication (❌ Currently broken)
- **SFTP** - SSH File Transfer Protocol with key-based authentication (❌ Currently broken)

### Security & Storage
- **Encrypted Credentials** - Android Keystore integration for secure credential storage
- **Biometric Authentication** - Optional biometric unlock for stored credentials
- **Session Management** - Secure connection handling with automatic cleanup

### Modern Android Stack
- **Material 3 Design** - Expressive UI following Google's latest design system
- **Jetpack Compose** - Modern declarative UI framework
- **Clean Architecture** - Separation of concerns with data/domain/presentation layers
- **Dependency Injection** - Hilt for scalable dependency management

## Technical Architecture

### Project Structure
```
app/src/main/java/com/grid/app/
├── data/
│   ├── local/          # Local storage and encryption
│   ├── remote/         # Network clients (FTP, SFTP, SMB)
│   └── repository/     # Repository implementations
├── domain/
│   ├── model/          # Domain entities
│   ├── repository/     # Repository interfaces
│   └── usecase/        # Business logic use cases
├── presentation/
│   ├── theme/          # Material 3 theming
│   └── ui/             # Compose UI components
└── di/                 # Dependency injection modules
```

### Key Technologies
- **Kotlin** 1.9.25 with Coroutines for asynchronous programming
- **Android Gradle Plugin** 8.13.0 with Gradle 8.13
- **Jetpack Compose** with Material 3 components
- **Hilt** 2.53 for dependency injection
- **DataStore** for preferences storage
- **Android Keystore** for credential encryption

### Network Libraries
- **Apache Commons Net** - FTP client implementation
- **Apache MINA SSHD** - SFTP client with SSH support
- **SMBJ** - SMB/CIFS client for Windows file sharing

## Getting Started

### Prerequisites
- **Android Studio** Arctic Fox (2020.3.1) or later
- **Android SDK** API 24 (Android 7.0) minimum
- **JDK** 11 or later

### Installation
1. Clone the repository:
   ```bash
   git clone <repository-url>
   cd grid
   ```

2. Open the project in Android Studio

3. Sync the project and install dependencies:
   ```bash
   ./gradlew build
   ```

4. Run the application:
   - Connect an Android device or start an emulator
   - Click "Run" in Android Studio or use:
   ```bash
   ./gradlew installDebug
   ```

### Building
```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run tests
./gradlew test

# Run lint checks
./gradlew lint
```

## Configuration

### Network Permissions
The app requires network permissions to connect to remote servers. These are declared in the AndroidManifest.xml:
- `android.permission.INTERNET`
- `android.permission.ACCESS_NETWORK_STATE`

### Biometric Permissions
For biometric authentication (optional):
- `android.permission.USE_BIOMETRIC`
- `android.permission.USE_FINGERPRINT`

## Usage

### Adding a Server Connection
1. Open the Grid application
2. Tap "Add Connection"
3. Select protocol (FTP, SFTP, or SMB)
4. Enter server details:
   - Hostname/IP address
   - Port (optional, uses protocol defaults)
   - Username and password/SSH key
5. Test the connection to verify settings
6. Save the connection

**Note**: SMB connection testing works reliably and accurately reflects connection status.

### Managing Credentials
- Credentials are automatically encrypted using Android Keystore
- Enable biometric authentication in settings for additional security
- Credentials are stored locally and never transmitted unencrypted

### File Operations
- Browse remote directories with pull-to-refresh functionality
- Download files to local storage (accessible Downloads folder)
- Upload files from device with real-time progress indicators
- Create, rename, and delete files/folders with confirmation dialogs
- Multi-select mode for batch operations
- Open text and image files with built-in viewers
- Sort files by name, type, or modification date

## Development

### Code Style
- Follow Kotlin coding conventions
- Use ktlint for code formatting
- Maintain test coverage for business logic

### Architecture Guidelines
- Keep domain layer pure Kotlin (no Android dependencies)
- Use Repository pattern for data access
- Implement use cases for business logic
- Follow MVVM pattern in presentation layer

### Adding New Protocols
1. Create a new client class implementing `NetworkClient` interface
2. Add protocol enum value in `Protocol.kt`
3. Update `NetworkClientFactory` to handle the new protocol
4. Add corresponding UI elements for protocol-specific settings

## Security Considerations

### Credential Storage
- All credentials are encrypted using Android Keystore
- Encryption keys are hardware-backed when available
- Credentials are automatically wiped on app uninstall

### Network Security
- Connections use protocol-native security (SSH for SFTP, etc.)
- Certificate validation is enforced for secure connections
- No credentials are logged or transmitted in plain text

### Permissions
- Minimal permission model - only required permissions are requested
- Biometric authentication is optional and can be disabled
- Network access is restricted to user-configured servers

## Contributing

### Development Setup
1. Fork the repository
2. Create a feature branch: `git checkout -b feature/new-feature`
3. Make your changes following the coding standards
4. Add tests for new functionality
5. Run the full test suite: `./gradlew test`
6. Submit a pull request

### Pull Request Guidelines
- Include a clear description of changes
- Add tests for new features
- Update documentation as needed
- Ensure all existing tests pass
- Follow the existing code style

## Acknowledgments

- **Apache Commons Net** for FTP client implementation
- **Apache MINA SSHD** for SFTP connectivity
- **SMBJ** for SMB/CIFS protocol support
- **Android Jetpack** for modern Android development components
- **Material Design** for UI/UX guidelines

## Support

For issues, feature requests, or questions:
- Open an issue on the project repository
- Follow the issue template for bug reports
- Include device information and logs for troubleshooting

---

**Grid** - Bringing remote file management to Android with security and modern design.