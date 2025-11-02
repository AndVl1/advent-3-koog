package ru.andvl.chatter.koog.agents.mcp.subgraphs

import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.dsl.builder.*
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.executeTool
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.structure.StructureFixingParser
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.agents.mcp.toolCallsKey
import ru.andvl.chatter.koog.agents.utils.FIXING_MAX_CONTEXT_LENGTH
import ru.andvl.chatter.koog.model.common.TokenUsage
import ru.andvl.chatter.koog.model.docker.*
import ru.andvl.chatter.koog.model.tool.*
import java.io.File

private val originalRequestKey = createStorageKey<InitialPromptAnalysisModel.SuccessAnalysisModel>("original-request")
private val logger = LoggerFactory.getLogger("mcp")

internal fun AIAgentGraphStrategyBuilder<GithubChatRequest, ToolChatResponse>.subgraphGithubAnalyze():
        AIAgentSubgraphDelegate<InitialPromptAnalysisModel.SuccessAnalysisModel, ToolChatResponse> =
    subgraph("github-analysis") {
        val nodeGithubRequest by nodeGithubRequest()
        val nodeExecuteTool by nodeExecuteTool()
        val nodeSendToolResult by nodeLLMSendToolResult("send-tool")
        val nodeProcessResult by nodeProcessResult()

        edge(nodeStart forwardTo nodeGithubRequest)
        edge(nodeGithubRequest forwardTo nodeExecuteTool onToolCall { true })
        edge(nodeGithubRequest forwardTo nodeProcessResult onAssistantMessage { true })
        edge(nodeExecuteTool forwardTo nodeSendToolResult)
        edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
        edge(nodeSendToolResult forwardTo nodeProcessResult onAssistantMessage { true })
        edge(nodeProcessResult forwardTo nodeFinish)

    }

private fun AIAgentSubgraphBuilderBase<InitialPromptAnalysisModel.SuccessAnalysisModel, ToolChatResponse>.nodeGithubRequest() =
    node<InitialPromptAnalysisModel.SuccessAnalysisModel, Message.Response>("process-user-request") { request ->
        // Store original request for later use
        storage.set(originalRequestKey, request)

        llm.writeSession {
            appendPrompt {
                val requirementsText = request.requirements?.let { req ->
                    """
      
                                        **STRUCTURED REQUIREMENTS TO ANALYZE:**
                                        
                                        General Conditions: ${req.generalConditions}

                                        Important Constraints (pay special attention):
                                        ${req.importantConstraints.joinToString("\n") { "- $it" }}
                                        
                                        Additional Advantages (look for positive aspects):
                                        ${req.additionalAdvantages.joinToString("\n") { "- $it" }}
                                        
                                        Attention Points (requires careful review):
                                        ${req.attentionPoints.joinToString("\n") { "- $it" }}
                                        """.trimIndent()
                } ?: ""

                system(
                    """
                                        You are a GitHub repository analysis expert with access to GitHub API tools.
                                        
                                        Your task is to thoroughly analyze the requested GitHub repository and gather comprehensive information to answer the user's specific questions, with special focus on structured requirements provided.
                                        
                                        **IMPORTANT LANGUAGE REQUIREMENT:**
                                        - Detect the language of the original user request and requirements
                                        - Gather information systematically but prepare for final response in the SAME language as the original request
                                        - If the user request was in Russian, the final analysis should be in Russian
                                        - If the user request was in English, the final analysis should be in English
                                        - Preserve technical terms and maintain professional language style
                                        
                                        **Available Tools:**
                                        Use the GitHub MCP tools to collect information about:
                                        - Repository metadata (name, description, stars, forks, topics, license)
                                        - README and documentation files
                                        - File structure and directory contents
                                        - Dependencies (package.json, requirements.txt, pom.xml, etc.)
                                        - Recent commits and activity
                                        - Issues and pull requests (if relevant)
                                        - Code samples from key files
                                        
                                        **Analysis Strategy:**
                                        1. Start with basic repository information
                                        2. Examine the README for project overview
                                        3. Analyze the file structure to understand project organization
                                        4. Check dependencies and build configuration
                                        5. Look at recent activity and development status
                                        6. **CRITICAL**: For each requirement category, actively look for evidence in the codebase:
                                           - Check how general conditions are met
                                           - Verify compliance with important constraints
                                           - Identify additional advantages present in the code
                                           - Gather data for attention points that need human review
                                        7. Focus on specific aspects mentioned in the user's request
                                        
                                        **Important:**
                                        - Use multiple tool calls to gather comprehensive information
                                        - Be systematic in your approach
                                        - Collect relevant code snippets when analyzing technical aspects
                                        - Pay special attention to structured requirements provided below
                                        - For each requirement point, try to find specific file references and line numbers
                                        - Document any problems, advantages, or OK status for each requirement
                                        - Try to use no more then 15 tool calls (increased for thorough requirements analysis)
                                        
                                        ${requirementsText}
                                        
                                        Proceed with gathering information about the repository systematically, with particular focus on addressing the structured requirements.
                                    """.trimIndent()
                )

                user(
                    """
                                        Please analyze the GitHub repository: ${request.githubRepo}
                                        
                                        User's specific request: ${request.userRequest}
                                        
                                        **LANGUAGE NOTE:** The final analysis report should be written in the same language as the user's original request above.
                                        
                                        Use the available GitHub tools to collect comprehensive information and focus on answering the user's specific questions.
                                    """.trimIndent()
                )

                model = model.copy(
//                    id = "qwen/qwen3-coder", // "openai/gpt-5-nano", // "qwen/qwen3-coder"
                    capabilities = listOf(
                        LLMCapability.Temperature,
                        LLMCapability.Completion,
                        LLMCapability.Tools,
//                        LLMCapability.ToolChoice,
                    )
                )
            }

            requestLLM()
        }
    }

private fun AIAgentSubgraphBuilderBase<InitialPromptAnalysisModel.SuccessAnalysisModel, ToolChatResponse>.nodeProcessResult() =
    node<String, ToolChatResponse>("process-llm-result") { rawAnalysisData ->
        val originalRequest = storage.get(originalRequestKey)
        llm.writeSession {
            appendPrompt {
                model = model.copy(
                    id = "z-ai/glm-4.6"
                )

                val requirementsSection = originalRequest?.requirements?.let { req ->
                    """
                                        
                                        **STRUCTURED REQUIREMENTS REVIEW:**
                                        You MUST provide a structured review based on the requirements:
                                        
                                        1. General Conditions Review:
                                           - Requirement: ${req.generalConditions}
                                           - Provide ONE RequirementReviewComment evaluating how well the repository meets this condition
                                        
                                        2. Important Constraints Review:
                                           ${req.importantConstraints.mapIndexed { index, constraint -> 
                                               "- Constraint ${index + 1}: $constraint"
                                           }.joinToString("\n                                           ")}
                                           - Provide RequirementReviewComment for EACH constraint
                                        
                                        3. Additional Advantages Review:
                                           ${req.additionalAdvantages.mapIndexed { index, advantage -> 
                                               "- Advantage ${index + 1}: $advantage"
                                           }.joinToString("\n                                           ")}
                                           - Provide RequirementReviewComment for EACH advantage
                                        
                                        4. Attention Points Review:
                                           ${req.attentionPoints.mapIndexed { index, point -> 
                                               "- Point ${index + 1}: $point"
                                           }.joinToString("\n                                           ")}
                                           - Provide RequirementReviewComment for EACH attention point
                                        
                                        For each RequirementReviewComment provide:
                                        - comment_type: EXACTLY one of "PROBLEM", "ADVANTAGE", or "OK"
                                        - comment: Detailed analysis of the requirement
                                        - file_reference: ACTUAL file path and line number (e.g., "src/main.js:42") - ONLY if found in analysis
                                        - code_quote: ACTUAL code snippet - ONLY if found in analysis
                                        """.trimIndent()
                } ?: ""

                val dockerDetectionSection = """

                **DOCKER ENVIRONMENT ANALYSIS:**
                Analyze if this project can be containerized with Docker:

                1. Check for existing Docker configuration:
                   - Dockerfile, docker-compose.yml, .dockerignore

                2. Identify project type and technology:
                   - Node.js: package.json with scripts
                   - Python: requirements.txt, setup.py
                   - Java: pom.xml (Maven), build.gradle (Gradle)
                   - Go: go.mod
                   - Ruby: Gemfile
                   - PHP: composer.json

                3. If suitable for Docker, provide `docker_env`:

                   **Node.js projects:**
                   - base_image: "node:18-alpine" or "node:20-alpine"
                   - build_command: "npm install" or "yarn install"
                   - run_command: "npm start" or "node index.js"
                   - port: 3000 (or from package.json)

                   **Python projects:**
                   - base_image: "python:3.9-slim" or "python:3.11-slim"
                   - build_command: "pip install -r requirements.txt"
                   - run_command: "python app.py" or "gunicorn app:app --bind 0.0.0.0:5000"
                   - port: 5000 or 8000

                   **Java/Maven projects:**
                   - base_image: "maven:3.8-openjdk-11"
                   - build_command: "mvn clean package"
                   - run_command: "java -jar target/*.jar"
                   - port: 8080

                   **Java/Gradle projects:**
                   - base_image: "gradle:7-jdk11"
                   - build_command: "./gradlew build"
                   - run_command: "java -jar build/libs/*.jar"
                   - port: 8080

                4. Set `docker_env` to **null** if:
                   - Pure library/SDK (no runnable application)
                   - CLI tool without server component
                   - Requires specific hardware/OS features
                   - No clear build/run commands

                **Important:** Be conservative - only suggest Docker if clearly applicable.
                """.trimIndent()

                val jsonExample = if (originalRequest?.requirements != null) {
                    """

                    **REQUIRED JSON OUTPUT STRUCTURE:**
                    ```json
                    {
                      "free_form_github_analysis": "Comprehensive analysis text with repository overview, technical details, architecture, dependencies, etc.",
                      "tldr": "Brief summary of key findings",
                      "repository_review": {
                        "general_conditions_review": {
                          "comment_type": "OK|PROBLEM|ADVANTAGE",
                          "comment": "Detailed evaluation of general conditions",
                          "file_reference": "path/to/file.ext:123",
                          "code_quote": "actual code snippet"
                        },
                        "constraints_review": [
                          {
                            "comment_type": "OK|PROBLEM|ADVANTAGE",
                            "comment": "Evaluation of first constraint",
                            "file_reference": "path/to/file.ext:456",
                            "code_quote": "relevant code"
                          }
                        ],
                        "advantages_review": [
                          {
                            "comment_type": "OK|PROBLEM|ADVANTAGE",
                            "comment": "Evaluation of first advantage",
                            "file_reference": "path/to/file.ext:789",
                            "code_quote": "supporting code"
                          }
                        ],
                        "attention_points_review": [
                          {
                            "comment_type": "OK|PROBLEM|ADVANTAGE",
                            "comment": "Analysis of attention point",
                            "file_reference": "path/to/file.ext:012",
                            "code_quote": "related code"
                          }
                        ]
                      },
                      "docker_env": {
                        "base_image": "node:18-alpine",
                        "build_command": "npm install",
                        "run_command": "npm start",
                        "port": 3000,
                        "additional_notes": "Optional notes"
                      }
                    }
                    ```

                    Set `docker_env` to null if Docker is not applicable.
                    """
                } else {
                    """

                    **REQUIRED JSON OUTPUT STRUCTURE:**
                    ```json
                    {
                      "free_form_github_analysis": "Comprehensive analysis text with repository overview, technical details, etc.",
                      "tldr": "Brief summary of key findings",
                      "repository_review": null,
                      "docker_env": {
                        "base_image": "node:18-alpine",
                        "build_command": "npm install",
                        "run_command": "npm start",
                        "port": 3000,
                        "additional_notes": "Optional notes"
                      }
                    }
                    ```

                    Set `docker_env` to null if Docker is not applicable.
                    """
                }

                system(
                    """
                                        You are an expert at synthesizing GitHub repository analysis results into structured JSON reports.

                                        Your task: Process the raw analysis data and create a comprehensive response following the EXACT JSON structure provided.

                                        **CRITICAL LANGUAGE REQUIREMENT:**
                                        - The entire response (free_form_github_analysis, tldr, and all comments) MUST be written in the SAME language as the original user request
                                        - If the original user request was in Russian, write the analysis in Russian
                                        - If the original user request was in English, write the analysis in English
                                        - Preserve technical terms but adapt the language style to match the original request
                                        - Maintain professional and technical tone in the target language

                                        **CRITICAL REQUIREMENTS:**
                                        1. Output MUST be valid JSON matching the provided structure
                                        2. Field names MUST match exactly: "free_form_github_analysis", "tldr", "repository_review", "docker_env"
                                        3. Use ONLY actual file references found in the analysis data - DO NOT invent file paths
                                        4. Comment types MUST be exactly: "PROBLEM", "ADVANTAGE", or "OK"
                                        5. If no requirements provided, set repository_review to null
                                        6. STRICTLY DO NOT USE MARKDOWN TAGS LIKE ```json TO WRAP CONTENT

                                        ${requirementsSection}
                                        ${dockerDetectionSection}
                                        ${jsonExample}

                                        **Content Guidelines:**
                                        - free_form_github_analysis: Comprehensive, structured analysis (include overview, architecture, dependencies, code quality, etc.)
                                        - tldr: Concise 1-2 sentence summary of key findings
                                        - repository_review: ONLY if requirements were provided, evaluate each requirement systematically
                                        - docker_env: Analyze if project can be containerized, provide Docker configuration or null
                                        - Use professional, technical language in the same language as original request
                                        - Include specific details and examples where relevant
                                        - Base all file references on actual repository analysis - DO NOT fabricate
                                    """.trimIndent()
                )

                user(
                    """
                                        Based on the GitHub repository analysis below, create a structured JSON report following the exact structure provided above.
                                        
                                        **IMPORTANT:** Remember to write the entire response in the SAME language as the original user request. Detect the language from the original request and maintain it throughout the analysis.
                                        
                                        **Original User Request:** ${originalRequest?.userRequest}
                                        
                                        **Analysis Data:**
                                        $rawAnalysisData
                                        
                                        **Instructions:**
                                        1. Extract comprehensive repository information for free_form_github_analysis
                                        2. Create concise summary for tldr
                                        3. If requirements were provided, evaluate each requirement systematically in repository_review
                                        4. Use ONLY file references and code snippets that were actually found in the analysis
                                        5. Output valid JSON matching the structure exactly
                                        6. Write ALL content in the same language as the original user request
                                    """.trimIndent()
                )
            }
            val totalToolCalls = storage.get(toolCallsKey).orEmpty()

            val response = requestLLMStructured<GithubRepositoryAnalysisModel>(
                examples = listOf(
                    GithubRepositoryAnalysisModel.SuccessAnalysisModel(
                        freeFormAnswer = "The repository userName/repoName is about something",
                        shortSummary = "Short info",
                        repositoryReview = if (originalRequest?.requirements != null) {
                            RepositoryReviewModel(
                                generalConditionsReview = RequirementReviewComment(
                                    commentType = "OK",
                                    comment = "General conditions are met",
                                    fileReference = "src/main.js:15",
                                    codeQuote = "function main() { ... }"
                                ),
                                constraintsReview = listOf(
                                    RequirementReviewComment(
                                        commentType = "PROBLEM",
                                        comment = "Security constraint violated",
                                        fileReference = "config/auth.js:23",
                                        codeQuote = "const secret = 'hardcoded-secret'"
                                    )
                                ),
                                advantagesReview = listOf(
                                    RequirementReviewComment(
                                        commentType = "ADVANTAGE",
                                        comment = "Excellent error handling",
                                        fileReference = "src/error-handler.js:10",
                                        codeQuote = "try { ... } catch (err) { logger.error(err); }"
                                    )
                                ),
                                attentionPointsReview = listOf(
                                    RequirementReviewComment(
                                        commentType = "OK",
                                        comment = "Performance metrics implemented",
                                        fileReference = "src/metrics.js:5",
                                        codeQuote = "const performanceTimer = new Timer();"
                                    )
                                )
                            )
                        } else null
                    ),
                    GithubRepositoryAnalysisModel.FailedAnalysisModel("Request was failed because of ...")
                ),
                fixingParser = StructureFixingParser(
                    fixingModel = LLModel(
                        provider = LLMProvider.OpenRouter,
                        id = "z-ai/glm-4.6", // z-ai/glm-4.6 mistralai/mistral-7b-instruct google/gemma-3n-e4b-it
                        capabilities = listOf(
                            LLMCapability.Temperature,
                            LLMCapability.Completion,
                        ),
                        contextLength = FIXING_MAX_CONTEXT_LENGTH,
                    ),
                    retries = 3
                )
            )
            if (response.isSuccess) {
                val resp = response.getOrThrow().also {
                    logger.info("Final response: $it")
                }

                // Run Docker build if dockerEnv is present
                val dockerInfo = when (val structure = resp.structure) {
                    is GithubRepositoryAnalysisModel.SuccessAnalysisModel -> {
                        structure.dockerEnv?.let { dockerEnv ->
                            logger.info("Docker environment detected, starting build...")
                            val buildResult = runDockerBuild(dockerEnv, structure.freeFormAnswer)
                            DockerInfoModel(
                                dockerEnv = dockerEnv,
                                buildResult = buildResult,
                                dockerfileGenerated = buildResult.buildStatus != "NOT_ATTEMPTED"
                            )
                        }
                    }
                    else -> null
                }

                ToolChatResponse(
                    response = when (val structure = resp.structure) {
                        is GithubRepositoryAnalysisModel.FailedAnalysisModel -> structure.reason
                        is GithubRepositoryAnalysisModel.SuccessAnalysisModel ->
                            structure.freeFormAnswer + if (dockerInfo != null) formatDockerResults(dockerInfo) else ""
                    },
                    shortSummary = when (val structure = resp.structure) {
                        is GithubRepositoryAnalysisModel.FailedAnalysisModel -> "Request failed"
                        is GithubRepositoryAnalysisModel.SuccessAnalysisModel -> structure.shortSummary
                    },
                    toolCalls = totalToolCalls,
                    originalMessage = resp.message,
                    tokenUsage = TokenUsage(
                        promptTokens = resp.message.metaInfo.inputTokensCount ?: 0,
                        completionTokens = resp.message.metaInfo.outputTokensCount ?: 0,
                        totalTokens = resp.message.metaInfo.totalTokensCount ?: 0
                    ),
                    repositoryReview = when (val structure = resp.structure) {
                        is GithubRepositoryAnalysisModel.FailedAnalysisModel -> null
                        is GithubRepositoryAnalysisModel.SuccessAnalysisModel -> structure.repositoryReview
                    },
                    requirements = originalRequest?.requirements,
                    dockerInfo = dockerInfo
                )
            } else {
                ToolChatResponse(
                    response = "Request finished with error: ${response.exceptionOrNull()?.message}",
                    shortSummary = "Request failed",
                    toolCalls = totalToolCalls,
                    originalMessage = null,
                    tokenUsage = TokenUsage(0, 0, 0),
                    repositoryReview = null,
                    requirements = originalRequest?.requirements,
                    dockerInfo = null
                )
            }
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

// Docker build helper functions
private fun checkDockerAvailability(): Boolean {
    return try {
        val process = ProcessBuilder("docker", "--version").start()
        val exitCode = process.waitFor()
        logger.info("Docker version check exit code: $exitCode")
        exitCode == 0
    } catch (e: Exception) {
        logger.warn("Docker not available: ${e.message}")
        false
    }
}

private fun runDockerBuild(dockerEnv: DockerEnvModel, freeFormAnalysis: String): DockerBuildResult {
    val dockerAvailable = checkDockerAvailability()

    if (!dockerAvailable) {
        return DockerBuildResult(
            buildStatus = "NOT_ATTEMPTED",
            errorMessage = "Docker not available on system"
        )
    }

    logger.info("Starting Docker build with base image: ${dockerEnv.baseImage}")

    var tempDir: File? = null
    try {
        // 1. Create temp directory
        tempDir = createTempDir("docker-build-")
        logger.info("Created temp directory: ${tempDir.absolutePath}")

        // 2. Extract repo URL from analysis
        val repoUrl = extractRepoUrl(freeFormAnalysis)
        if (repoUrl == null) {
            logger.error("Could not extract repository URL from analysis")
            return DockerBuildResult(
                buildStatus = "FAILED",
                errorMessage = "Could not extract repository URL from analysis"
            )
        }

        logger.info("Cloning repository: $repoUrl")

        // 3. Clone repository
        val cloneProcess = ProcessBuilder("git", "clone", "--depth", "1", repoUrl, tempDir.absolutePath)
            .redirectErrorStream(true)
            .start()

        val cloneOutput = cloneProcess.inputStream.bufferedReader().readText()
        val cloneExitCode = cloneProcess.waitFor()

        if (cloneExitCode != 0) {
            logger.error("Failed to clone repository. Exit code: $cloneExitCode")
            return DockerBuildResult(
                buildStatus = "FAILED",
                errorMessage = "Failed to clone repository",
                buildLogs = cloneOutput.lines().takeLast(5)
            )
        }

        logger.info("Repository cloned successfully")

        // 4. Create Dockerfile if missing
        val dockerFile = tempDir.resolve("Dockerfile")
        val dockerfileGenerated = !dockerFile.exists()

        if (dockerfileGenerated) {
            val dockerfileContent = generateDockerfile(dockerEnv)
            dockerFile.writeText(dockerfileContent)
            logger.info("Generated Dockerfile")
        } else {
            logger.info("Using existing Dockerfile")
        }

        // 5. Run Docker build
        val startTime = System.currentTimeMillis()
        val imageName = "chatter-test-${System.currentTimeMillis()}"

        logger.info("Starting Docker build with image name: $imageName")

        val buildProcess = ProcessBuilder(
            "docker", "build",
            "-t", imageName,
            "--no-cache",
            "."
        )
            .directory(tempDir)
            .redirectErrorStream(true)
            .start()

        val buildLogs = mutableListOf<String>()
        buildProcess.inputStream.bufferedReader().use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.let {
                    buildLogs.add(it)
                    if (buildLogs.size % 10 == 0) {
                        logger.info("Docker build progress: ${buildLogs.size} lines")
                    }
                }
            }
        }

        val buildExitCode = buildProcess.waitFor()
        val duration = ((System.currentTimeMillis() - startTime) / 1000).toInt()

        logger.info("Docker build completed with exit code: $buildExitCode, duration: ${duration}s")

        // 6. Get image size if successful
        val imageSize = if (buildExitCode == 0) {
            getImageSize(imageName)
        } else null

        // 7. Cleanup image
        if (buildExitCode == 0) {
            try {
                ProcessBuilder("docker", "rmi", imageName).start().waitFor()
                logger.info("Cleaned up Docker image: $imageName")
            } catch (e: Exception) {
                logger.warn("Failed to cleanup Docker image: ${e.message}")
            }
        }

        // 8. Return result
        return DockerBuildResult(
            buildStatus = if (buildExitCode == 0) "SUCCESS" else "FAILED",
            buildLogs = buildLogs.takeLast(20),
            imageSize = imageSize,
            buildDurationSeconds = duration,
            errorMessage = if (buildExitCode != 0) {
                "Docker build failed with exit code $buildExitCode. Check logs for details."
            } else null
        )

    } catch (e: Exception) {
        logger.error("Docker build error", e)
        return DockerBuildResult(
            buildStatus = "FAILED",
            errorMessage = "Exception during Docker build: ${e.message}",
            buildLogs = listOf(e.stackTraceToString().lines().take(10)).flatten()
        )
    } finally {
        // Cleanup temp directory
        tempDir?.let {
            try {
                it.deleteRecursively()
                logger.info("Cleaned up temp directory")
            } catch (e: Exception) {
                logger.warn("Failed to cleanup temp directory: ${e.message}")
            }
        }
    }
}

private fun generateDockerfile(dockerEnv: DockerEnvModel): String {
    return """
FROM ${dockerEnv.baseImage}

WORKDIR /app

# Copy project files
COPY . .

# Build command
RUN ${dockerEnv.buildCommand}

# Expose port if specified
${if (dockerEnv.port != null) "EXPOSE ${dockerEnv.port}" else "# No port specified"}

# Run command
CMD ${dockerEnv.runCommand}
    """.trimIndent()
}

private fun extractRepoUrl(analysis: String): String? {
    val pattern = "https://github\\.com/[\\w.-]+/[\\w.-]+".toRegex()
    return pattern.find(analysis)?.value
}

private fun getImageSize(imageName: String): String? {
    return try {
        val process = ProcessBuilder("docker", "images", "--format", "{{.Size}}", imageName).start()
        if (process.waitFor() == 0) {
            process.inputStream.bufferedReader().readText().trim()
        } else null
    } catch (e: Exception) {
        logger.warn("Failed to get image size: ${e.message}")
        null
    }
}

private fun formatDockerResults(dockerInfo: DockerInfoModel): String {
    val result = dockerInfo.buildResult
    return buildString {
        appendLine("\n\n## 🐳 Docker Build Results\n")
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
