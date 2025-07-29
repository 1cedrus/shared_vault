# Share Vault - Real-time File Synchronization System

A robust Java-based file synchronization system with real-time collaboration capabilities, featuring a server component and a command-line client.

## 🚀 Features

- ✅ **Real-time synchronization** via WebSocket notifications
- ✅ **Conflict-free collaboration** - intelligent merge handling
- ✅ **File deduplication** - SHA-256 hash-based storage
- ✅ **Complete audit trail** - full change log history
- ✅ **Debounced file watching** - efficient batch processing
- ✅ **Cross-platform support** - Windows, macOS, Linux
- ✅ **Self-contained JARs** - no dependency installation required

## 📦 Quick Start

### Prerequisites
- Java 21 or higher
- Maven 3.8+ (for building from source)

### Download & Run

1. **Download pre-built JARs** (or build from source)
2. **Start the server:**
   ```bash
   java -jar Server.jar
   ```
   Server will start on `http://localhost:4289`

3. **Use the client:**
   ```bash
   # Create a new vault from current folder
   java -jar Client.jar monitor
   
   # Monitor existing vault
   java -jar Client.jar monitor -v <vault-name> -f <folder>
   ```

## 🏗️ Building from Source

```bash
# Clone the repository
git clone <repository-url>
cd share-vault

# Build both Client.jar and Server.jar
mvn clean package

# JARs will be created in target/ directory:
# - target/Client.jar (9.2MB - includes all dependencies)
# - target/Server.jar (9.2MB - includes all dependencies)
```

## 📖 Usage Guide

### Server Component

Start the Share Vault server:
```bash
java -jar Server.jar
```

The server provides:
- **HTTP API** for vault operations
- **WebSocket endpoint** for real-time notifications
- **File storage** with hash-based deduplication

### Client Component (`sv`)

The client is a CLI tool for creating and monitoring vaults:

#### Create a New Vault
```bash
# Create vault from current directory
java -jar Client.jar monitor

# Create vault from specific directory
java -jar Client.jar monitor -f /path/to/folder
```

This will:
1. Scan all files in the folder
2. Create a vault on the server
3. Return a vault name (share this with collaborators)
4. Start real-time monitoring

#### Monitor an Existing Vault
```bash
# Monitor vault in current directory
java -jar Client.jar monitor -v <vault-name>

# Monitor vault in specific directory
java -jar Client.jar monitor -v <vault-name> -f /path/to/folder
```

#### Command Options
```bash
Usage: sv [-hV] [COMMAND]
Share Vault - Folder synchronization tool

Commands:
  monitor  Create a vault or start monitoring an existing one

Monitor Command Options:
  -v, --vault <name>      Name of vault to monitor
  -f, --folder <path>     Local folder to sync (default: current directory)
  -d, --debounce <secs>   Debounce time before creating changelog (default: 5)
      --server <url>      Server URL (default: http://localhost:4289)
  -h, --help              Show help message
```

## 🎯 Example Workflow

### Scenario: Team Collaboration

**Team Lead (Alice):**
```bash
# Create shared vault from project folder
cd /projects/my-app
java -jar Client.jar monitor

# Output: Vault created successfully!
# Vault name: abc123xyz...
# Share this name with others to give them access
```

**Team Member (Bob):**
```bash
# Join the vault
mkdir /workspace/shared-project
cd /workspace/shared-project
java -jar Client.jar monitor -v abc123xyz...

# Files will sync automatically
# Any changes Bob makes will sync to Alice and vice versa
```

### Scenario: Personal Sync
```bash
# Computer 1: Create vault
java -jar Client.jar monitor -f ~/Documents

# Computer 2: Join same vault
java -jar Client.jar monitor -v <vault-name> -f ~/Documents
```

## 🏛️ Architecture

### System Components

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   Client A      │────▶│  Share Vault    │◀────│    Client B     │
│  (sv CLI)       │     │    Server       │     │   (sv CLI)      │
└─────────────────┘     └─────────────────┘     └─────────────────┘
```

### Server APIs

- `POST /vault` - Create new vault with initial files
- `POST /vault/:name/sync` - Sync changes (change logs + files)
- `GET /vault/:name/change_logs` - Get all change logs
- `GET /vault/:name/change_logs/since/:timestamp` - Get incremental changes
- `GET /vault/:name/files/:hash` - Get file by hash
- `GET /vault?name=:name` - Download entire vault as ZIP
- `WebSocket /v` - Real-time change notifications

### Client Directory Structure

Each monitored folder contains a `.sv/` directory:
```
.sv/
├── config.json          # Vault configuration
├── files/               # Files stored by hash
│   ├── abc123...
│   └── def456...
└── change_logs/         # Local change log history
    ├── 001704067200000.json
    └── 002704067400000.json
```

### Change Log Format

```json
{
  "timestamp": 1704067200000,
  "parent": 1704067100000,
  "changes": {
    "added": [{"path": "file1.txt", "hash": "abc123..."}],
    "modified": [{"path": "file2.txt", "hash": "def456..."}], 
    "deleted": ["file3.txt"]
  }
}
```

## 🔧 Advanced Configuration

### Custom Server URL
```bash
java -jar Client.jar monitor --server http://my-server:8080
```

### Custom Debounce Time
```bash
# Wait 10 seconds before processing file changes
java -jar Client.jar monitor -d 10
```

### Environment Variables
```bash
# Set default server URL
export SV_SERVER_URL=http://my-server:8080

# Set default debounce time
export SV_DEBOUNCE_SECONDS=10
```

## 🛠️ Development

### Project Structure
```
src/main/java/org/one_cedrus/
├── Client.java                    # CLI client entry point
├── Server.java                    # HTTP server entry point
├── communication/                 # Network layer
│   ├── ApiClient.java            # HTTP client for vault operations
│   ├── VWebSocket.java           # WebSocket server handler
│   └── VWebSocketClient.java     # WebSocket client
├── manager/                       # Core business logic
│   ├── VaultManager.java         # Main vault operations coordinator
│   ├── ChangeLogManager.java     # Change log persistence
│   └── SharedVaultDirManager.java # Local vault directory management
├── service/                       # Business services
│   └── DirectoryStateService.java # Directory state management
├── util/                          # Utilities
│   ├── HashCalculator.java       # SHA-256 hash calculations
│   ├── FileWatcher.java          # File system monitoring
│   ├── VaultConfig.java          # Configuration management
│   └── VaultUtils.java           # General utilities
├── shared/                        # Shared data models
│   ├── ChangeLog.java            # Change log data structure
│   ├── FileChange.java           # File change representation
│   └── MessageType.java          # WebSocket message types
└── exception/                     # Custom exception hierarchy
    ├── VaultException.java        # Base exception
    ├── SyncException.java         # Sync operation failures
    ├── ConfigurationException.java # Configuration errors
    ├── VaultNotInitializedException.java # Initialization errors
    └── HashCalculationException.java # Hash calculation failures
```

### Code Quality Features
- **Custom Exception Hierarchy** - Specific error types for different failure scenarios
- **Service Layer Architecture** - Clear separation of concerns
- **Hash-based Deduplication** - Efficient storage and conflict resolution
- **Comprehensive Logging** - Structured logging with [INFO], [ERROR], [DEBUG] prefixes
- **Debounced File Watching** - Efficient batch processing of file changes

### Testing
```bash
# Compile and test
mvn clean compile test

# Run integration tests
mvn verify
```

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🆘 Troubleshooting

### Common Issues

**"Connection refused" when starting client:**
- Ensure the server is running: `java -jar Server.jar`
- Check if port 4289 is available
- Verify server URL configuration

**"Vault not found" error:**
- Double-check the vault name
- Ensure you have network connectivity to the server
- Verify the server has the vault data

**File changes not syncing:**
- Check if the file watcher has permission to monitor the directory
- Verify WebSocket connection in logs
- Check for network connectivity issues

**Build fails:**
- Ensure Java 21+ is installed and configured
- Verify Maven 3.8+ is available
- Check internet connectivity for dependency downloads

### Debug Mode
```bash
# Enable verbose logging
java -Dlogging.level.org.one_cedrus=DEBUG -jar Client.jar monitor
```

### Support
For issues and questions:
- Check existing [Issues](../../issues)
- Create a new issue with detailed description
- Include system information and error logs 