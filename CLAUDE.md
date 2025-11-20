- Use koog documentation to find any information about koog framework (https://docs.koog.ai/)
- Use ktor documentation to find any information about ktor framework (https://ktor.io/)
- Always use only last available versions of libraries and frameworks, use web search to determine them
- Use libs.versions.toml for version management. Never downgrade libraries versions
- Try to find documentation in /tools/docs. If not, download them with /tools/scripts/download-docs.sh

## Koog Framework Development
- **CRITICAL**: When writing Koog agents, follow `.claude/prompts/koog-agent-system-prompt.md`
- Quick reference: `.claude/prompts/koog-quick-reference.md`
- **Most common mistakes**:
  1. Edge conditions must check node outputs, NOT storage values
  2. Explicitly prevent LLM from calling tools in text-only nodes
  3. Set reasonable retry limits (2-3 max, not 5+)
- See examples in `koog-service/src/main/kotlin/ru/andvl/chatter/koog/agents/`

## Test Scripts Management
- Test scripts are located in `/tests/scripts/` directory (gitignored)
- Create temporary test scripts for feature validation, then delete when no longer needed
- Keep only scripts that test current, active functionality
- Document script purpose and requirements in `/tests/README.md`
- Clean up outdated scripts after features are validated and working

## Keep in mind
- Everything in `/thirdparty/` dir is read-only, and you should not modify code there. 
These are sources of used libraries
- Use code in `/thirdparty/` to learn sources of downloaded there libraries
- Use proper privacy modifiers. If code is not use outside module – do not forget to set private modifier
