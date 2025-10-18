- Use koog documentation to find any information about koog framework (https://docs.koog.ai/)
- Use ktor documentation to find any information about ktor framework (https://ktor.io/)
- Always use only last available versions of libraries and frameworks, use web search to determine them
- Use libs.versions.toml for version management. Never downgrade libraries versions
- Try to find documentation in /tools/docs. If not, download them with /tools/scripts/download-docs.sh

## Test Scripts Management
- Test scripts are located in `/tests/scripts/` directory (gitignored)
- Create temporary test scripts for feature validation, then delete when no longer needed
- Keep only scripts that test current, active functionality
- Document script purpose and requirements in `/tests/README.md`
- Clean up outdated scripts after features are validated and working