package ru.andvl.chatter.koog.agents.mcp.subgraphs

import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.dsl.builder.*
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.executeTool
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.structure.StructureFixingParser
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.agents.mcp.GithubAnalysisNodes
import ru.andvl.chatter.koog.agents.mcp.toolCallsKey
import ru.andvl.chatter.koog.agents.utils.getLatestTotalTokenUsage
import ru.andvl.chatter.koog.model.common.TokenUsage
import ru.andvl.chatter.koog.model.docker.DockerBuildResult
import ru.andvl.chatter.koog.model.docker.DockerInfoModel
import ru.andvl.chatter.koog.model.tool.GithubChatRequest
import ru.andvl.chatter.koog.model.tool.GithubRepositoryAnalysisModel
import ru.andvl.chatter.koog.model.tool.ToolChatResponse

private val dockerAnalysisKey = createStorageKey<GithubRepositoryAnalysisModel.SuccessAnalysisModel>("docker-analysis")
private val logger = LoggerFactory.getLogger("docker-subgraph")

internal fun AIAgentGraphStrategyBuilder<GithubChatRequest, ToolChatResponse>.subgraphDocker(
    fixingModel: LLModel
): AIAgentSubgraphDelegate<GithubRepositoryAnalysisModel.SuccessAnalysisModel, ToolChatResponse> =
    subgraph(GithubAnalysisNodes.Subgraphs.DOCKER_BUILD) {
        val nodeDockerRequest by nodeDockerRequest()
        val nodeExecuteTool by nodeExecuteTool(GithubAnalysisNodes.DockerBuild.DOCKER_EXECUTE_TOOL)
        val nodeSendToolResult by nodeLLMSendToolResult(GithubAnalysisNodes.DockerBuild.DOCKER_SEND_TOOL_RESULT)
        val nodeProcessResult by nodeProcessResult(fixingModel)

        // Check forceSkipDocker flag - if true, skip Docker build entirely
        edge(nodeStart forwardTo nodeFinish onCondition {
            val githubRequest = storage.get(initialGithubRequestKey)
            githubRequest?.forceSkipDocker == true
        } transformed { analysisResult ->
            val toolCalls = storage.get(toolCallsKey).orEmpty()
            val requirements = storage.get(requirementsKey)

            logger.info("⏭️ Docker build skipped (forceSkipDocker = true)")

            ToolChatResponse(
                response = analysisResult.freeFormAnswer,
                shortSummary = analysisResult.shortSummary,
                toolCalls = toolCalls,
                originalMessage = null,
                tokenUsage = null,
                repositoryReview = analysisResult.repositoryReview,
                requirements = requirements,
                userRequestAnalysis = analysisResult.userRequestAnalysis,
                dockerInfo = null
            )
        } )

        edge(nodeStart forwardTo nodeDockerRequest onCondition {
            val githubRequest = storage.get(initialGithubRequestKey)
            githubRequest?.forceSkipDocker == false && it.dockerEnv != null
        })
        edge(nodeStart forwardTo nodeFinish onCondition {
            val githubRequest = storage.get(initialGithubRequestKey)
            githubRequest?.forceSkipDocker == false && it.dockerEnv == null
        } transformed { analysisResult ->
            val toolCalls = storage.get(toolCallsKey).orEmpty()
            val requirements = storage.get(requirementsKey)

            ToolChatResponse(
                response = analysisResult.freeFormAnswer,
                shortSummary = analysisResult.shortSummary,
                toolCalls = toolCalls,
                originalMessage = null,
                tokenUsage = null,
                repositoryReview = analysisResult.repositoryReview,
                requirements = requirements,
                userRequestAnalysis = analysisResult.userRequestAnalysis,
                dockerInfo = null
            )
        } )

        edge(nodeDockerRequest forwardTo nodeExecuteTool onToolCall { true })
        edge(nodeDockerRequest forwardTo nodeProcessResult onAssistantMessage { true })
        edge(nodeExecuteTool forwardTo nodeSendToolResult)
        edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
        edge(nodeSendToolResult forwardTo nodeProcessResult onAssistantMessage { true })
        edge(nodeProcessResult forwardTo nodeFinish)
    }

private fun AIAgentSubgraphBuilderBase<GithubRepositoryAnalysisModel.SuccessAnalysisModel, ToolChatResponse>.nodeDockerRequest() =
    node<GithubRepositoryAnalysisModel.SuccessAnalysisModel, Message.Response>(GithubAnalysisNodes.DockerBuild.DOCKER_REQUEST) { analysisResult ->
        storage.set(dockerAnalysisKey, analysisResult)

        if (analysisResult.dockerEnv == null) {
            // No Docker environment detected, skip Docker build
            logger.info("No Docker environment detected, skipping Docker build")
            llm.writeSession {
                appendPrompt {
                    system("Docker build is not applicable for this project.")
                    user("Skip Docker build.")
                }
                requestLLM()
            }
        } else {
            // Docker environment detected, proceed with build
            val dockerEnv = analysisResult.dockerEnv
            logger.info("Docker environment detected: $dockerEnv")

            llm.writeSession {
                appendPrompt {
                    system("""
                        You are a Docker build automation expert. Your task is to build a Docker image for the analyzed GitHub repository.

                        **Repository Information:**
                        Repository: ${analysisResult.freeFormAnswer.let { analysis ->
                            val pattern = "https://github\\.com/[\\w.-]+/[\\w.-]+".toRegex()
                            pattern.find(analysis)?.value ?: "unknown"
                        }}

                        **Docker Configuration:**
                        - Base Image: ${dockerEnv.baseImage}
                        - Build Command: ${dockerEnv.buildCommand}
                        - Run Command: ${dockerEnv.runCommand}
                        ${if (dockerEnv.port != null) "- Port: ${dockerEnv.port}" else ""}
                        ${if (dockerEnv.additionalNotes != null) "- Notes: ${dockerEnv.additionalNotes}" else ""}

                        **Available Docker Tools:**
                        1. check-docker-availability - Check if Docker is available
                        2. clone-repository(repositoryUrl) - Clone the repository
                        3. generate-dockerfile(directoryPath, baseImage, buildCommand, runCommand, port?) - Generate Dockerfile
                        4. build-docker-image(directoryPath, imageName?) - Build Docker image
                        5. get-image-size(imageName) - Get image size
                        6. cleanup-image(imageName) - Remove Docker image
                        7. cleanup-directory(directoryPath) - Remove temporary directory

                        **Build Process:**
                        1. Check Docker availability first
                        2. If Docker is not available, explain why build cannot proceed
                        3. If Docker is available:
                           - Clone the repository
                           - Generate Dockerfile if needed
                           - Build the Docker image
                           - Get image size if build succeeds
                           - Clean up the image
                           - Clean up the temporary directory
                        4. After completing all steps, provide a summary in natural language

                        **Important:**
                        - Use the tools systematically
                        - Handle errors gracefully
                        - Always clean up resources (image and directory) at the end
                        - Provide clear error messages if any step fails

                        Begin the Docker build process now.
                    """.trimIndent())

                    user("Please build a Docker image for this repository using the provided Docker configuration.")

                    model = model.copy(
                        capabilities = listOf(
                            LLMCapability.Temperature,
                            LLMCapability.Completion,
                            LLMCapability.Tools,
                            LLMCapability.OpenAIEndpoint.Completions
                        )
                    )
                }

                requestLLM()
            }
        }
    }

private fun AIAgentSubgraphBuilderBase<GithubRepositoryAnalysisModel.SuccessAnalysisModel, ToolChatResponse>.nodeProcessResult(
    fixingModel: LLModel
) =
    node<String, ToolChatResponse>(GithubAnalysisNodes.DockerBuild.PROCESS_DOCKER_RESULT) { rawDockerData ->
        val analysisResult = storage.get(dockerAnalysisKey)!!
        val requirements = storage.get(requirementsKey)
        val toolCalls = storage.get(toolCallsKey).orEmpty()

        if (analysisResult.dockerEnv == null) {
            // No Docker build was attempted
            return@node ToolChatResponse(
                response = analysisResult.freeFormAnswer,
                shortSummary = analysisResult.shortSummary,
                toolCalls = toolCalls,
                originalMessage = null,
                tokenUsage = getLatestTotalTokenUsage(),
                repositoryReview = analysisResult.repositoryReview,
                requirements = requirements,
                userRequestAnalysis = analysisResult.userRequestAnalysis,
                dockerInfo = null
            )
        }

        // Parse Docker build results from LLM response
        llm.writeSession {
            appendPrompt {
//                model = model.copy(
//                    id = "z-ai/glm-4.6"
//                )

                system("""
                    You are an expert at parsing Docker build results and creating structured reports.

                    Your task: Extract Docker build information from the tool execution results and create a structured response.

                    **Required Output Structure:**
                    ```json
                    {
                      "build_status": "SUCCESS" | "FAILED" | "NOT_ATTEMPTED",
                      "build_logs": ["last 20 lines of build output"],
                      "image_size": "123MB" or null,
                      "build_duration_seconds": 45 or null,
                      "error_message": "error details" or null
                    }
                    ```

                    **Instructions:**
                    - Extract build_status from the tool results (SUCCESS if build-docker-image succeeded, FAILED if it failed, NOT_ATTEMPTED if Docker was not available)
                    - Extract build_logs from build-docker-image tool results (take last 20 lines)
                    - Extract image_size from get-image-size tool results
                    - Calculate build_duration_seconds if available in the logs
                    - Extract error_message if build failed
                    - STRICTLY DO NOT USE MARKDOWN TAGS LIKE ```json TO WRAP CONTENT
                """.trimIndent())

                user("""
                    Docker build process results:

                    $rawDockerData

                    Please extract and structure the Docker build results according to the format above.
                """.trimIndent())
            }

            val response = requestLLMStructured<DockerBuildResult>(
                examples = listOf(
                    DockerBuildResult(
                        buildStatus = "SUCCESS",
                        buildLogs = listOf("Step 1/5 : FROM node:18-alpine", "Successfully built abc123"),
                        imageSize = "156MB",
                        buildDurationSeconds = 45,
                        errorMessage = null
                    ),
                    DockerBuildResult(
                        buildStatus = "FAILED",
                        buildLogs = listOf("Error: Cannot find module 'express'"),
                        imageSize = null,
                        buildDurationSeconds = 12,
                        errorMessage = "Build failed: missing dependencies"
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
                    errorMessage = "Failed to parse Docker build results: ${response.exceptionOrNull()?.message}"
                )
            }

            val dockerInfo = DockerInfoModel(
                dockerEnv = analysisResult.dockerEnv!!,
                buildResult = dockerBuildResult,
                dockerfileGenerated = dockerBuildResult.buildStatus != "NOT_ATTEMPTED"
            )

            ToolChatResponse(
                response = analysisResult.freeFormAnswer + "\n\n${formatDockerResults(dockerInfo)}",
                shortSummary = analysisResult.shortSummary,
                toolCalls = toolCalls,
                originalMessage = null,
                tokenUsage = response.getOrNull()?.message?.metaInfo?.let {
                    TokenUsage(
                        promptTokens = it.inputTokensCount ?: 0,
                        completionTokens = it.outputTokensCount ?: 0,
                        totalTokens = it.totalTokensCount ?: 0,
                    )
                },
                repositoryReview = analysisResult.repositoryReview,
                requirements = requirements,
                userRequestAnalysis = analysisResult.userRequestAnalysis,
                dockerInfo = dockerInfo
            )
        }
    }

private fun AIAgentSubgraphBuilderBase<*, *>.nodeExecuteTool(
    name: String? = null
): AIAgentNodeDelegate<Message.Tool.Call, ReceivedToolResult> =
    node(name) { toolCall ->
        val currentCalls = storage.get(toolCallsKey) ?: emptyList()
        storage.set(toolCallsKey, currentCalls + "${toolCall.tool} ${toolCall.content}")

        environment.executeTool(toolCall)
    }

private fun formatDockerResults(dockerInfo: DockerInfoModel): String {
    val result = dockerInfo.buildResult
    return buildString {
        appendLine("## 🐳 Docker Build Results")
        appendLine()
        appendLine("**Docker Environment:**")
        appendLine("- Base Image: `${dockerInfo.dockerEnv.baseImage}`")
        appendLine("- Build Command: `${dockerInfo.dockerEnv.buildCommand}`")
        appendLine("- Run Command: `${dockerInfo.dockerEnv.runCommand}`")
        if (dockerInfo.dockerEnv.port != null) {
            appendLine("- Port: `${dockerInfo.dockerEnv.port}`")
        }
        if (dockerInfo.dockerEnv.additionalNotes != null) {
            appendLine("- Notes: ${dockerInfo.dockerEnv.additionalNotes}")
        }
        appendLine()

        appendLine("**Build Status:** ${result.buildStatus}")
        if (result.buildDurationSeconds != null) {
            appendLine("**Duration:** ${result.buildDurationSeconds}s")
        }
        if (result.imageSize != null) {
            appendLine("**Image Size:** ${result.imageSize}")
        }
        if (dockerInfo.dockerfileGenerated) {
            appendLine("**Dockerfile:** Generated automatically")
        }

        if (result.errorMessage != null) {
            appendLine()
            appendLine("**Error:**")
            appendLine("```")
            appendLine(result.errorMessage)
            appendLine("```")
        }

        if (result.buildLogs.isNotEmpty()) {
            appendLine()
            appendLine("<details>")
            appendLine("<summary>Build Logs (last 20 lines)</summary>")
            appendLine()
            appendLine("```")
            result.buildLogs.forEach { log ->
                appendLine(log)
            }
            appendLine("```")
            appendLine("</details>")
        }
    }
}
