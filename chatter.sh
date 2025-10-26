#!/bin/bash

# Chatter CLI Runner Script with Auto-rebuild

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLI_BIN="$SCRIPT_DIR/cli/build/install/cli/bin/cli"
CLI_SRC_DIR="$SCRIPT_DIR/cli/src"
CLI_BUILD_DIR="$SCRIPT_DIR/cli/build"
GRADLE_BUILD="$SCRIPT_DIR/cli/build.gradle.kts"

# Function to check if rebuild is needed
needs_rebuild() {
    # If binary doesn't exist, need to build
    if [ ! -f "$CLI_BIN" ]; then
        return 0
    fi

    # Check if any source file is newer than the binary
    if [ -d "$CLI_SRC_DIR" ]; then
        # Find the newest source file
        NEWEST_SRC=$(find "$CLI_SRC_DIR" -name "*.kt" -exec stat -f "%m %N" {} \; 2>/dev/null | sort -nr | head -1 | cut -d' ' -f2-)
        if [ -n "$NEWEST_SRC" ] && [ "$NEWEST_SRC" -nt "$CLI_BIN" ]; then
            return 0
        fi
    fi

    # Check if build.gradle.kts is newer than binary
    if [ "$GRADLE_BUILD" -nt "$CLI_BIN" ]; then
        return 0
    fi

    # Check if libs.versions.toml is newer than binary
    VERSIONS_TOML="$SCRIPT_DIR/gradle/libs.versions.toml"
    if [ -f "$VERSIONS_TOML" ] && [ "$VERSIONS_TOML" -nt "$CLI_BIN" ]; then
        return 0
    fi

    return 1
}

# Function to build CLI
build_cli() {
    echo "🔨 Building CLI..."
    cd "$SCRIPT_DIR"
    ./gradlew :cli:installDist -q
    if [ $? -eq 0 ]; then
        echo "✅ CLI built successfully"
        return 0
    else
        echo "❌ Failed to build CLI"
        return 1
    fi
}

# Main logic
if needs_rebuild; then
    if [ -f "$CLI_BIN" ]; then
        echo "🔄 Source changes detected, rebuilding..."
    else
        echo "📦 CLI binary not found, building..."
    fi

    if ! build_cli; then
        exit 1
    fi
fi

# Run the CLI
if [ -f "$CLI_BIN" ]; then
    "$CLI_BIN" "$@"
else
    echo "❌ CLI binary still not found after build attempt"
    exit 1
fi
