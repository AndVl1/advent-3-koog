package ru.andvl.chatter.koog.agents.codemod.subgraphs

import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.dsl.builder.*
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.environment.executeTool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.tools
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.structure.StructureFixingParser
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.model.codemod.AnalysisResult
import ru.andvl.chatter.koog.model.codemod.ModificationResult
import ru.andvl.chatter.koog.tools.FileOperationsToolSet

private val logger = LoggerFactory.getLogger("codemod-modification")

// Storage key for tracking modifications
internal val modificationsAppliedKey = createStorageKey<ModificationResult>("modifications-applied")

/**
 * Subgraph 3: Code Modification
 *
 * Flow:
 * 1. Apply code changes using FileOperations tools
 * 2. Verify changes were applied correctly
 */
internal suspend fun AIAgentGraphStrategyBuilder<*, *>.subgraphCodeModification(
    model: LLModel,
    fixingModel: LLModel
): AIAgentSubgraphDelegate<AnalysisResult, ModificationResult> =
    subgraph(
        name = "code-modification",
        tools = ToolRegistry {
            tools(FileOperationsToolSet())
        }.tools
    ) {
        val nodeApplyChanges by nodeApplyChanges(model)
        val nodeExecuteTool by nodeExecuteTool("code-modification-execute-tool")
        val nodeSendToolResult by nodeLLMSendToolResult("code-modification-send-tool-result")
        val nodeVerifyChanges by nodeVerifyChanges(fixingModel)

        edge(nodeStart forwardTo nodeApplyChanges)
        edge(nodeApplyChanges forwardTo nodeExecuteTool onToolCall { true })
        edge(nodeApplyChanges forwardTo nodeVerifyChanges onAssistantMessage { true })

        edge(nodeExecuteTool forwardTo nodeSendToolResult)
        edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
        edge(nodeSendToolResult forwardTo nodeVerifyChanges onAssistantMessage { true })

        edge(nodeVerifyChanges forwardTo nodeFinish)
    }

/**
 * Node: Apply code changes using LLM + FileOperations tools
 */
private fun AIAgentSubgraphBuilderBase<AnalysisResult, ModificationResult>.nodeApplyChanges(model: LLModel) =
    node<AnalysisResult, Message.Response>("apply-code-changes") { analysisResult ->
        val repositoryPath = storage.get(repositoryPathKey) ?: throw IllegalStateException("Repository path not found")
        val userRequest = storage.get(userRequestKey) ?: "Modify the code as requested"

        logger.info("Applying code changes based on modification plan")
        logger.info("Repository: $repositoryPath")
        logger.info("Files to modify: ${analysisResult.filesToModify.joinToString(", ")}")

        llm.writeSession {
            appendPrompt {
                system(
                    """
Apply the planned code modifications to the repository.

**User Request**: $userRequest

**Modification Plan**: ${analysisResult.modificationPlan}

**Repository Path**: $repositoryPath

**Files to Modify**: ${analysisResult.filesToModify.joinToString(", ")}
**Dependencies**: ${analysisResult.dependenciesIdentified.joinToString(", ")}

**Available Tools**:
- read-file-content: Read current file content with optional line ranges
- apply-patch: Apply a single patch to a file (delete lines startLine-endLine, replace with new content)
- apply-patches: Apply multiple patches to a file (automatically sorted from end to start)
- create-file: Create a new file with content
- delete-file: Delete a file

**Important Guidelines**:
- Use apply-patches when modifying multiple locations in the same file
- Always read file content before applying patches to ensure correct line numbers
- Provide clear replacement content with proper indentation
- Create files with proper formatting and language-specific conventions
- When using apply-patch or apply-patches:
  - startLine and endLine are 1-indexed and inclusive
  - Line numbers refer to the ORIGINAL file content
  - If applying multiple patches to the same file, use apply-patches (patches will be sorted automatically)

**Code Quality Guidelines**:
- Make minimal, focused changes that address the user request
- Preserve existing code style and conventions
- Add comments where necessary for clarity
- Ensure imports and dependencies are updated
- Follow the language's best practices and idioms

**Output Format**:
After applying all modifications, respond with a summary in this JSON format:
```json
{
  "files_modified": ["path/to/file1.kt", "path/to/file2.kt"],
  "patches_applied": 5,
  "files_created": ["path/to/newfile.kt"],
  "files_deleted": []
}
```

Begin applying the modifications systematically. Use the tools to make the changes.
""".trimIndent()
                )
            }

            requestLLM()
        }
    }

/**
 * Node: Verify changes were applied correctly with structured parsing
 */
private fun AIAgentSubgraphBuilderBase<AnalysisResult, ModificationResult>.nodeVerifyChanges(
    fixingModel: LLModel
) =
    node<String, ModificationResult>("verify-changes-applied") { summaryText ->
        logger.info("Verifying changes were applied correctly")
        logger.debug("Summary text: $summaryText")

        // Use structured parsing for guaranteed JSON correctness
        val result = llm.writeSession {
            appendPrompt {
                system(
                    """
You are an expert at extracting structured information from code modification summaries.

**CRITICAL REQUIREMENTS:**
1. Output MUST be valid JSON matching the provided structure
2. Field names MUST match exactly: "files_modified", "patches_applied", "files_created", "files_deleted"
3. STRICTLY DO NOT USE MARKDOWN TAGS LIKE ```json TO WRAP CONTENT
4. Extract information from the modification summary text provided

**Required Output Structure:**
{
  "files_modified": ["path/to/file1.kt", "path/to/file2.kt"],
  "patches_applied": 5,
  "files_created": ["path/to/newfile.kt"],
  "files_deleted": []
}

**Instructions:**
- Extract all file paths that were modified
- Count the total number of patches applied
- List all files that were newly created
- List all files that were deleted
- If information is missing, use empty arrays or 0
""".trimIndent()
                )

                user(
                    """
Modification summary from the LLM:

$summaryText

Please extract and structure the modification results according to the format above.
""".trimIndent()
                )
            }

            requestLLMStructured<ModificationResult>(
                examples = listOf(
                    ModificationResult(
                        filesModified = listOf(
                            "src/main/kotlin/com/example/UserService.kt",
                            "src/test/kotlin/com/example/UserServiceTest.kt"
                        ),
                        patchesApplied = 3,
                        filesCreated = listOf("src/main/kotlin/com/example/UserRepository.kt"),
                        filesDeleted = emptyList()
                    ),
                    ModificationResult(
                        filesModified = listOf("README.md"),
                        patchesApplied = 1,
                        filesCreated = emptyList(),
                        filesDeleted = listOf("deprecated/OldFile.kt")
                    )
                ),
                fixingParser = StructureFixingParser(
                    fixingModel = fixingModel,
                    retries = 3
                )
            )
        }.getOrThrow().structure

        // Store modifications for later use
        storage.set(modificationsAppliedKey, result)

        logger.info("Modifications verified:")
        logger.info("  Files modified: ${result.filesModified.size}")
        logger.info("  Patches applied: ${result.patchesApplied}")
        logger.info("  Files created: ${result.filesCreated.size}")
        logger.info("  Files deleted: ${result.filesDeleted.size}")

        result
    }

/**
 * Node: Execute tool calls
 */
private fun AIAgentSubgraphBuilderBase<*, *>.nodeExecuteTool(
    name: String? = null
): AIAgentNodeDelegate<ai.koog.prompt.message.Message.Tool.Call, ai.koog.agents.core.environment.ReceivedToolResult> =
    node(name) { toolCall ->
        environment.executeTool(toolCall)
    }
