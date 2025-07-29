# Shared Vault - Real-time File Synchronization System

> ⚠️ Disclaimer:
> This project is intended for academic and educational purposes only.
> It is not fully tested and not recommended for production use.
> Use it at your own risk

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

## 🚀 Development

- The project is still in heavy development. But the core features are available now.
- Also, the project is heavly inspired by Git.
