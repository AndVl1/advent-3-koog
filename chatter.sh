#!/bin/bash

# Chatter CLI Runner Script

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLI_BIN="$SCRIPT_DIR/cli/build/install/cli/bin/cli"

if [ -f "$CLI_BIN" ]; then
    "$CLI_BIN" "$@"
else
    echo "CLI binary not found. Building..."
    ./gradlew :cli:installDist -q
    if [ -f "$CLI_BIN" ]; then
        "$CLI_BIN" "$@"
    else
        echo "Failed to build CLI"
        exit 1
    fi
fi