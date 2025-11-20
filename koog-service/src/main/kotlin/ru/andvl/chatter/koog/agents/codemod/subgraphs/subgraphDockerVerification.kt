package ru.andvl.chatter.koog.agents.codemod.subgraphs

import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.dsl.builder.*
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.executeTool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.tools
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.structure.StructureFixingParser
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.model.codemod.ModificationResult
import ru.andvl.chatter.koog.model.codemod.VerificationResult
import ru.andvl.chatter.koog.model.docker.DockerBuildResult
import ru.andvl.chatter.koog.model.docker.DockerEnvModel
import ru.andvl.chatter.koog.tools.DockerToolSet

private val logger = LoggerFactory.getLogger("codemod-verification")

// Storage key for docker environment
internal val dockerEnvKey = createStorageKey<DockerEnvModel>("docker-env")

/**
 * Subgraph 4: Docker Verification
 *
 * Flow:
 * 1. Request Docker verification (LLM uses Docker tools)
 * 2. Execute tool calls (build image, run tests)
 * 3. Send tool results back to LLM
 * 4. Parse verification results with structured parsing
 */
internal suspend fun AIAgentGraphStrategyBuilder<*, *>.subgraphDockerVerification(
    model: LLModel,
    fixingModel: LLModel
): AIAgentSubgraphDelegate<ModificationResult, VerificationResult> =
    subgraph(
        name = "docker-verification",
        tools = ToolRegistry {
            tools(DockerToolSet())
        }.tools
    ) {
        val nodeDockerRequest by nodeDockerRequest()
        val nodeExecuteTool by nodeExecuteTool()
        val nodeSendToolResult by nodeLLMSendToolResult()
        val nodeProcessResult by nodeProcessResult(fixingModel)

        // Start -> Docker request
        edge(nodeStart forwardTo nodeDockerRequest)

        // Docker request -> Execute tool (when LLM calls tools)
        edge(nodeDockerRequest forwardTo nodeExecuteTool onToolCall { true })

        // Docker request -> Process result (when LLM responds with text)
        edge(nodeDockerRequest forwardTo nodeProcessResult onAssistantMessage { true })

        // Execute tool -> Send result back to LLM
        edge(nodeExecuteTool forwardTo nodeSendToolResult)

        // Send result -> Execute more tools (if LLM calls more tools)
        edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })

        // Send result -> Process result (when LLM finishes with text)
        edge(nodeSendToolResult forwardTo nodeProcessResult onAssistantMessage { true })

        // Process result -> Finish
        edge(nodeProcessResult forwardTo nodeFinish)
    }

/**
 * Node: Request Docker verification with tools
 */
private fun AIAgentSubgraphBuilderBase<ModificationResult, VerificationResult>.nodeDockerRequest() =
    node<ModificationResult, Message.Response>("docker-verification-request") { modificationResult ->
        val repositoryPath = storage.get(repositoryPathKey) ?: throw IllegalStateException("Repository path not found")
        val dockerEnv = storage.get(dockerEnvKey)

        logger.info("Starting Docker verification for repository: $repositoryPath")

        if (dockerEnv == null) {
            logger.warn("No Docker environment detected - this should not happen after code analysis")
            throw IllegalStateException("Docker environment not found in storage")
        }

        logger.info("Docker environment: ${dockerEnv.baseImage}")
        logger.info("Build command: ${dockerEnv.buildCommand}")
        logger.info("Run command: ${dockerEnv.runCommand}")

        llm.writeSession {
            appendPrompt {
                system(
                    """
You are a Docker verification automation expert. Your task is to verify code changes by running tests in a Docker container.

**CRITICAL: YOU MUST USE TOOL CALLS**
- DO NOT write tool calls in text format
- DO NOT describe what tools you would use
- ACTUALLY CALL THE TOOLS using the function calling mechanism
- Each step requires calling a specific tool

**Repository Information:**
- Repository Path: $repositoryPath
- Files Modified: ${modificationResult.filesModified.joinToString(", ")}
- Files Created: ${modificationResult.filesCreated.joinToString(", ")}
- Patches Applied: ${modificationResult.patchesApplied}

**Docker Configuration:**
- Base Image: ${dockerEnv.baseImage}
- Build Command: ${dockerEnv.buildCommand}
- Run Command: ${dockerEnv.runCommand}
${if (dockerEnv.port != null) "- Port: ${dockerEnv.port}" else ""}
${if (dockerEnv.additionalNotes != null) "- Notes: ${dockerEnv.additionalNotes}" else ""}

**Available Docker Tools (YOU MUST CALL THESE):**
1. check-docker-availability() - Check if Docker is available on the system
2. generate-dockerfile(directoryPath, baseImage, buildCommand, runCommand, port?) - Generate Dockerfile in the directory
3. build-docker-image(directoryPath, imageName?) - Build Docker image from the directory
4. run-docker-container(imageName, command?) - Run verification command in the container
5. get-image-size(imageName) - Get built image size
6. cleanup-image(imageName) - Remove Docker image after testing
7. cleanup-directory(directoryPath) - Clean up temporary directory if needed

**Verification Process (EXECUTE EACH STEP BY CALLING TOOLS):**

**Step 1:** Call check-docker-availability() first
- If Docker is not available, stop and explain why

**Step 2:** If Docker is available:
- Call generate-dockerfile(directoryPath="$repositoryPath", baseImage="${dockerEnv.baseImage}", buildCommand="${dockerEnv.buildCommand}", runCommand="${dockerEnv.runCommand}"${if (dockerEnv.port != null) ", port=${dockerEnv.port}" else ""})

**Step 3:** Call build-docker-image(directoryPath="$repositoryPath")
- Use repository name as image name

**Step 4:** Call run-docker-container(imageName=<built-image-name>, command="${dockerEnv.runCommand}")
- This runs the verification tests
- Capture output and exit code

**Step 5:** If build succeeded, call get-image-size(imageName=<built-image-name>)

**Step 6:** Call cleanup-image(imageName=<built-image-name>)

**Step 7:** After all tools have been executed, provide a summary in natural language

**IMPORTANT RULES:**
- START IMMEDIATELY by calling check-docker-availability() tool
- DO NOT write "I will call tool X" - JUST CALL IT
- Wait for each tool result before calling the next tool
- Handle errors gracefully but continue the verification process
- Always clean up resources (image) at the end
- Your first response MUST be a tool call, not text
""".trimIndent()
                )

                user("Begin Docker verification now by calling check-docker-availability() tool.")
            }

            requestLLM()
        }
    }

/**
 * Node: Process verification results with structured parsing
 */
private fun AIAgentSubgraphBuilderBase<ModificationResult, VerificationResult>.nodeProcessResult(
    fixingModel: LLModel
) =
    node<String, VerificationResult>("process-verification-result") { rawVerificationData ->
        logger.info("Processing Docker verification results")

        // Parse Docker verification results from LLM response using structured parser
        llm.writeSession {
            appendPrompt {
                system(
                    """
You are an expert at parsing Docker verification results and creating structured reports.

Your task: Extract Docker verification information from the tool execution results and create a structured response.

**Required Output Structure:**
{
  "success": true or false,
  "command_executed": "the actual command that was executed",
  "exit_code": 0,
  "logs": ["last 20-30 lines of output"],
  "error_message": "error details" or null
}

**Instructions:**
- Extract success from the tool results (true if exit code is 0, false otherwise)
- Extract command_executed from run-docker-container tool results
- Extract exit_code from run-docker-container tool results
- Extract logs from run-docker-container tool results (take last 20-30 lines)
- Extract error_message if verification failed
- STRICTLY DO NOT USE MARKDOWN TAGS LIKE ```json TO WRAP CONTENT

**Important:**
- success should be true only if exit_code is 0
- If Docker was not available, set success to false with appropriate error_message
- Include relevant output in logs array
""".trimIndent()
                )

                user(
                    """
Docker verification process results:

$rawVerificationData

Please extract and structure the Docker verification results according to the format above.
""".trimIndent()
                )
            }

            val response = requestLLMStructured<DockerBuildResult>(
                examples = listOf(
                    DockerBuildResult(
                        buildStatus = "SUCCESS",
                        buildLogs = listOf(
                            "Step 1/5 : FROM gradle:8.5-jdk17-alpine",
                            "Successfully built abc123",
                            "BUILD SUCCESSFUL in 12s"
                        ),
                        imageSize = "156MB",
                        buildDurationSeconds = 12,
                        errorMessage = null
                    ),
                    DockerBuildResult(
                        buildStatus = "FAILED",
                        buildLogs = listOf(
                            "Task :test FAILED",
                            "UserServiceTest > testGetUser() FAILED",
                            "Expected: <User(id=1)> but was: <null>"
                        ),
                        imageSize = null,
                        buildDurationSeconds = 8,
                        errorMessage = "Tests failed: 1 test failed out of 5"
                    )
                ),
                fixingParser = StructureFixingParser(
                    fixingModel = fixingModel,
                    retries = 3
                )
            )

            val dockerBuildResult = if (response.isSuccess) {
                response.getOrThrow().structure
            } else {
                DockerBuildResult(
                    buildStatus = "FAILED",
                    buildLogs = listOf(rawVerificationData),
                    errorMessage = "Failed to parse Docker verification results: ${response.exceptionOrNull()?.message}"
                )
            }

            // Convert DockerBuildResult to VerificationResult
            val verificationResult = VerificationResult(
                success = dockerBuildResult.buildStatus == "SUCCESS",
                commandExecuted = "Docker verification",
                exitCode = if (dockerBuildResult.buildStatus == "SUCCESS") 0 else 1,
                logs = dockerBuildResult.buildLogs,
                errorMessage = dockerBuildResult.errorMessage
            )

            logger.info("Verification result: ${if (verificationResult.success) "SUCCESS" else "FAILED"}")
            logger.info("Command executed: ${verificationResult.commandExecuted}")
            logger.info("Exit code: ${verificationResult.exitCode}")
            if (!verificationResult.success) {
                logger.warn("Error message: ${verificationResult.errorMessage}")
            }

            verificationResult
        }
    }

/**
 * Node: Execute tool calls from LLM
 */
private fun AIAgentSubgraphBuilderBase<*, *>.nodeExecuteTool():
        AIAgentNodeDelegate<Message.Tool.Call, ReceivedToolResult> =
    node("execute-docker-tool") { toolCall ->
        logger.info("Executing Docker tool: ${toolCall.tool}")
        environment.executeTool(toolCall)
    }
