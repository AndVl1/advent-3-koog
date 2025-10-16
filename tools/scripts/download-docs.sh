#!/bin/bash

# Documentation Downloader Script
# Downloads documentation from websites and saves locally

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default values
DOCS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../docs" && pwd)"
TEMP_DIR="/tmp/docs-downloader-$$"
USER_AGENT="Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36"

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to cleanup temp directory
cleanup() {
    if [ -d "$TEMP_DIR" ]; then
        rm -rf "$TEMP_DIR"
    fi
}

# Trap to cleanup on exit
trap cleanup EXIT

# Function to check dependencies
check_dependencies() {
    local deps=("curl" "sed" "grep")
    
    for dep in "${deps[@]}"; do
        if ! command -v "$dep" &> /dev/null; then
            print_error "Required dependency '$dep' is not installed"
            print_status "Please install: brew install $dep"
            exit 1
        fi
    done
}

# Function to download single page
download_page() {
    local url="$1"
    local output_file="$2"
    local base_url="$3"
    
    print_status "Downloading: $url"
    
    # Create directory if it doesn't exist
    mkdir -p "$(dirname "$output_file")"
    
    # Download the page
    curl -s -L \
        -H "User-Agent: $USER_AGENT" \
        -H "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8" \
        "$url" | \
        sed -e "s|href=\"\\([^\"]*\\)\"|href=\"$(echo "$base_url" | sed 's/\/$//')\"|g" \
        > "$output_file"
    
    if [ $? -eq 0 ]; then
        print_success "Saved to: $output_file"
    else
        print_error "Failed to download: $url"
    fi
}

# Function to download sitemap-based documentation
download_from_sitemap() {
    local base_url="$1"
    local sitemap_url="$2"
    local output_dir="$3"
    
    print_status "Discovering pages from sitemap..."
    
    # Get sitemap
    local sitemap_xml=$(curl -s -H "User-Agent: $USER_AGENT" "$sitemap_url")
    
    if [ -z "$sitemap_xml" ]; then
        print_error "Failed to fetch sitemap from: $sitemap_url"
        return 1
    fi
    
    # Extract URLs from sitemap
    local urls=$(echo "$sitemap_xml" | grep -oE '<loc>[^<]+</loc>' | sed -e 's/<loc>//g' -e 's/<\/loc>//g')
    
    if [ -z "$urls" ]; then
        print_warning "No URLs found in sitemap"
        return 1
    fi
    
    print_status "Found $(echo "$urls" | wc -l | tr -d ' ') pages to download"
    
    # Download each page
    while IFS= read -r url; do
        if [ -n "$url" ]; then
            # Convert URL to filename
            local relative_path=$(echo "$url" | sed "s|$base_url/||")
            if [ -z "$relative_path" ] || [ "$relative_path" = "$base_url" ]; then
                relative_path="index.html"
            else
                relative_path="${relative_path}.html"
            fi
            
            local output_file="$output_dir/$relative_path"
            download_page "$url" "$output_file" "$base_url"
        fi
    done <<< "$urls"
}

# Function to download with site map pattern
download_site_structure() {
    local base_url="$1"
    local output_dir="$2"
    local pages=(
        "/"
        "/getting-started"
        "/guides"
        "/api-reference"
        "/examples"
        "/configuration"
        "/troubleshooting"
    )
    
    print_status "Downloading documentation site structure..."
    
    for page in "${pages[@]}"; do
        local url="${base_url}${page}"
        local relative_path="${page}.html"
        
        # Handle root page
        if [ "$page" = "/" ]; then
            relative_path="index.html"
        fi
        
        # Remove leading slash from path
        relative_path=$(echo "$relative_path" | sed 's/^\///')
        
        local output_file="$output_dir/$relative_path"
        download_page "$url" "$output_file" "$base_url"
        
        # Small delay to be respectful
        sleep 0.5
    done
}

# Function to show usage
show_usage() {
    cat << EOF
Documentation Downloader

Usage: $0 [OPTIONS] URL

OPTIONS:
    -o, --output DIR       Output directory (default: $DOCS_DIR)
    -t, --type TYPE        Type of download:
                          - sitemap: Try to download from sitemap.xml
                          - structure: Download common documentation pages
                          - single: Download single page only
                          (default: structure)
    -h, --help            Show this help message

EXAMPLES:
    # Download Koog documentation
    $0 https://docs.koog.ai

    # Download to custom directory
    $0 -o /tmp/my-docs https://docs.koog.ai

    # Download using sitemap
    $0 -t sitemap https://docs.koog.ai

    # Download single page
    $0 -t single https://docs.koog.ai/getting-started

EOF
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -o|--output)
            DOCS_DIR="$2"
            shift 2
            ;;
        -t|--type)
            DOWNLOAD_TYPE="$2"
            shift 2
            ;;
        -h|--help)
            show_usage
            exit 0
            ;;
        -*)
            print_error "Unknown option: $1"
            show_usage
            exit 1
            ;;
        *)
            URL="$1"
            shift
            ;;
    esac
done

# Check if URL provided
if [ -z "$URL" ]; then
    print_error "Please provide a URL to download documentation from"
    show_usage
    exit 1
fi

# Set default download type
DOWNLOAD_TYPE=${DOWNLOAD_TYPE:-"structure"}

# Normalize URL (add trailing slash if missing)
if [[ ! "$URL" =~ /$ ]]; then
    URL="${URL}/"
fi

# Extract site name for output directory
SITE_NAME=$(echo "$URL" | sed -e 's|https\?://||' -e 's|/$||' -e 's|\.|_|g')
OUTPUT_DIR="${DOCS_DIR}/${SITE_NAME}"

# Main execution
main() {
    print_status "Documentation Downloader"
    print_status "========================"
    print_status "URL: $URL"
    print_status "Type: $DOWNLOAD_TYPE"
    print_status "Output: $OUTPUT_DIR"
    echo
    
    # Check dependencies
    check_dependencies
    
    # Create output directory
    mkdir -p "$OUTPUT_DIR"
    
    # Create temp directory
    mkdir -p "$TEMP_DIR"
    
    # Download based on type
    case "$DOWNLOAD_TYPE" in
        "sitemap")
            download_from_sitemap "$URL" "${URL}sitemap.xml" "$OUTPUT_DIR"
            ;;
        "structure")
            download_site_structure "$URL" "$OUTPUT_DIR"
            ;;
        "single")
            download_page "$URL" "$OUTPUT_DIR/index.html" "$URL"
            ;;
        *)
            print_error "Unknown download type: $DOWNLOAD_TYPE"
            exit 1
            ;;
    esac
    
    # Create index file listing all downloaded files
    cat > "$OUTPUT_DIR/_index.txt" << EOF
Documentation Download Report
=============================
Source: $URL
Download Date: $(date)
Download Type: $DOWNLOAD_TYPE

Files:
$(find "$OUTPUT_DIR" -name "*.html" -type f | sort | sed "s|$OUTPUT_DIR/||")

EOF
    
    print_success "Documentation downloaded successfully!"
    print_status "Files saved in: $OUTPUT_DIR"
    print_status "Index file: $OUTPUT_DIR/_index.txt"
    echo
    
    # Show summary
    local file_count=$(find "$OUTPUT_DIR" -name "*.html" -type f | wc -l | tr -d ' ')
    local dir_size=$(du -sh "$OUTPUT_DIR" | cut -f1)
    print_status "Downloaded $file_count files (${dir_size})"
    
    # Open in finder if on macOS
    if command -v open &> /dev/null && [[ "$OSTYPE" == "darwin"* ]]; then
        print_status "Opening documentation folder..."
        open "$OUTPUT_DIR"
    fi
}

# Run main function
main "$@"