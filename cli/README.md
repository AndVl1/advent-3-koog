# Chatter CLI

Command-line interface for interacting with Chatter AI server.

## Building

```bash
./gradlew :cli:build
```

## Usage

### Using the runner script (recommended):

```bash
# Send a single message
./chatter.sh -m "Hello AI!"

# Interactive mode
./chatter.sh -i

# Custom server
./chatter.sh -H localhost -p 8081 -m "Hello"

# Show help
./chatter.sh --help
```

### Direct execution:

```bash
# After running ./gradlew :cli:installDist
./cli/build/install/cli/bin/chatter-cli -m "Hello AI!"
```

## Options

- `-H, --host`: Server host (default: localhost)
- `-p, --port`: Server port (default: 8081)
- `-m, --message`: Message to send to AI
- `-i, --interactive`: Run in interactive mode
- `-h, --help`: Show help message

## Interactive Mode Commands

- `help` - Show available commands
- `exit` or `quit` - Exit the program
- Type any message to chat with AI