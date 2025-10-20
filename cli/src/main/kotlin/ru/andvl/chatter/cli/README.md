# Chatter CLI Structure

The CLI application is now organized into separate packages for better maintainability:

## Package Structure

- **`ru.andvl.chatter.cli`** - Main package
  - `CliApp.kt` - Application entry point and command-line argument parsing
  
- **`ru.andvl.chatter.cli.models`** - Data models
  - `Models.kt` - All serializable data classes (StructuredResponse, CheckListItem, etc.)
  
- **`ru.andvl.chatter.cli.ui`** - User interface utilities
  - `ColorPrinter.kt` - Handles all colored output and formatting
  
- **`ru.andvl.chatter.cli.history`** - Chat history management
  - `ChatHistory.kt` - Manages conversation history with size limits
  
- **`ru.andvl.chatter.cli.api`** - API communication
  - `ChatApiClient.kt` - Handles HTTP requests to the server
  
- **`ru.andvl.chatter.cli.interactive`** - Interactive mode
  - `InteractiveMode.kt` - Manages the interactive chat interface

## Key Features

1. **Modular Design**: Each component has a single responsibility
2. **Color Output**: Consistent colored output with ANSI codes
3. **History Management**: Automatic history tracking with size limits
4. **Error Handling**: Proper error messages and connection error handling
5. **Flexible Parsing**: Supports both JSON and plain text responses