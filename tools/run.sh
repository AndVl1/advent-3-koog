#!/bin/bash

# Tools launcher script

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOLS_DIR="$SCRIPT_DIR"

# List available tools
list_tools() {
    echo "Available tools:"
    echo "==============="
    find "$TOOLS_DIR/scripts" -name "*.sh" -type f | while read -r script; do
        name=$(basename "$script" .sh)
        echo "  - $name: $(grep -m1 "# " "$script" | sed 's/# //')"
    done
}

# Show usage
show_usage() {
    cat << EOF
Tools Launcher

Usage: $0 [TOOL] [ARGS...]

Available tools:
EOF
    list_tools
    echo
    echo "Examples:"
    echo "  $0 download-docs https://docs.koog.ai"
    echo "  $0 download-docs --help"
    echo
}

# Check if tool specified
if [ $# -eq 0 ]; then
    show_usage
    exit 0
fi

TOOL="$1"
shift

# Find and run the tool
TOOL_SCRIPT="$TOOLS_DIR/scripts/${TOOL}.sh"

if [ -f "$TOOL_SCRIPT" ]; then
    exec "$TOOL_SCRIPT" "$@"
else
    echo "Error: Tool '$TOOL' not found"
    echo
    list_tools
    exit 1
fi