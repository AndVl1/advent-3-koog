# Tools

This directory contains utility scripts and tools for the Chatter project.

## Scripts

### download-docs.sh

A flexible script to download documentation from websites and save it locally for offline reference.

#### Features

- Download single pages or entire documentation sites
- Support for sitemap-based discovery
- Automatic directory structure creation
- Progress tracking and colored output
- Cross-platform support (macOS, Linux)

#### Usage

```bash
# Download documentation with default settings
./download-docs.sh https://docs.koog.ai

# Download to custom directory
./download-docs.sh -o /tmp/docs https://docs.koog.ai

# Download using sitemap (if available)
./download-docs.sh -t sitemap https://docs.koog.ai

# Download single page only
./download-docs.sh -t single https://docs.koog.ai/getting-started

# Show help
./download-docs.sh --help
```

#### Options

- `-o, --output DIR`: Specify output directory (default: `../docs`)
- `-t, --type TYPE`: Download type
  - `sitemap`: Discover pages from sitemap.xml
  - `structure`: Download common documentation pages
  - `single`: Download single page only
- `-h, --help`: Show help message

#### Examples

```bash
# Download Koog documentation
./download-docs.sh https://docs.koog.ai

# Download Ktor documentation
./download-docs.sh https://ktor.io/docs/

# Download to custom location
./download-docs.sh -o ~/Documents/docs https://docs.koog.ai
```

## Directory Structure

```
tools/
├── scripts/          # Utility scripts
│   └── download-docs.sh
├── docs/            # Downloaded documentation (gitignored)
│   └── [site_name]/
└── README.md        # This file
```

## Adding New Tools

1. Create a new script in the `scripts/` directory
2. Make it executable: `chmod +x scripts/your-script.sh`
3. Add documentation here in README.md
4. Update this README with usage instructions

## Dependencies

The scripts may require common tools:

- `curl` - For downloading files
- `sed` - For text processing
- `grep` - For pattern matching
- `jq` - For JSON processing (optional)

Install on macOS:
```bash
brew install curl grep gnu-sed jq
```

Install on Linux:
```bash
sudo apt-get install curl sed grep jq
```