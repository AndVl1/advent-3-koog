package ru.andvl.chatter.koog.agents.mcp.subgraphs

import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.*
import ai.koog.agents.core.dsl.extension.nodeLLMCompressHistory
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.executeTool
import ai.koog.agents.memory.config.MemoryScopeType
import ai.koog.agents.memory.feature.withMemory
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.message.Message
import ai.koog.prompt.structure.StructureFixingParser
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.agents.mcp.toolCallsKey
import ru.andvl.chatter.koog.agents.memory.*
import ru.andvl.chatter.koog.agents.utils.FixedWholeHistoryCompressionStrategy
import ru.andvl.chatter.koog.agents.utils.isHistoryTooLong
import ru.andvl.chatter.koog.mcp.McpProvider
import ru.andvl.chatter.koog.model.docker.DockerEnvModel
import ru.andvl.chatter.koog.model.tool.*

private val originalRequestKey = createStorageKey<InitialPromptAnalysisModel.SuccessAnalysisModel>("original-request")
internal val requirementsKey = createStorageKey<RequirementsAnalysisModel>("requirements")
private val logger = LoggerFactory.getLogger("mcp")



internal suspend fun AIAgentGraphStrategyBuilder<GithubChatRequest, ToolChatResponse>.subgraphGithubAnalyze(
    fixingModel: ai.koog.prompt.llm.LLModel
): AIAgentSubgraphDelegate<InitialPromptAnalysisModel.SuccessAnalysisModel, GithubRepositoryAnalysisModel.SuccessAnalysisModel> =
    subgraph(
        name = "github-analysis",
        tools = McpProvider.getGithubToolsRegistry().tools
//        toolSelectionStrategy = ai.koog.agents.core.agent.entity.ToolSelectionStrategy.Tools(
//            McpProvider.getGithubToolsDescriptors() + McpProvider.getUtilsToolsDescriptors()
//        )
    ) {
        val nodeLoadMemory by nodeLoadGithubMemory()
        val nodeGithubRequest by nodeGithubRequest()
        val nodeExecuteTool by nodeExecuteTool("github-execute-tool")
        val nodeSendToolResult by nodeLLMSendToolResult("github-subgraph-send-tool")
        val nodeCompressHistory by nodeLLMCompressHistory<ReceivedToolResult>(
            name = "github-compress-history",
            strategy = FixedWholeHistoryCompressionStrategy()
        )
        val nodeProcessResult by nodeProcessResult(fixingModel)
        val nodeSaveMemory by nodeSaveGithubMemory()

        // Load previous analysis from memory at the start
        edge(nodeStart forwardTo /*nodeLoadMemory)
        edge(nodeLoadMemory forwardTo*/ nodeGithubRequest)

        edge(nodeGithubRequest forwardTo nodeExecuteTool onToolCall { true })
        edge(nodeGithubRequest forwardTo nodeProcessResult onAssistantMessage { true })

        edge(nodeExecuteTool forwardTo nodeCompressHistory onCondition { isHistoryTooLong() })
        edge(nodeCompressHistory forwardTo nodeSendToolResult)

        edge(nodeExecuteTool forwardTo nodeSendToolResult onCondition { !isHistoryTooLong() })
        edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
        edge(nodeSendToolResult forwardTo nodeProcessResult onAssistantMessage { true })

        // Save analysis to memory before finish (but don't force finish)
        edge(nodeProcessResult forwardTo nodeSaveMemory)
        edge(nodeSaveMemory forwardTo nodeFinish)
    }

private fun AIAgentSubgraphBuilderBase<InitialPromptAnalysisModel.SuccessAnalysisModel, GithubRepositoryAnalysisModel.SuccessAnalysisModel>.nodeGithubRequest() =
    node<InitialPromptAnalysisModel.SuccessAnalysisModel, Message.Response>("github-process-user-request") { request ->
        // Store original request for later use
        storage.set(originalRequestKey, request)
        request.requirements?.let { storage.set(requirementsKey, it) }

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
                        LLMCapability.OpenAIEndpoint.Completions
//                        LLMCapability.ToolChoice,
                    )
                )
            }

            requestLLM()
        }
    }

private fun AIAgentSubgraphBuilderBase<InitialPromptAnalysisModel.SuccessAnalysisModel, GithubRepositoryAnalysisModel.SuccessAnalysisModel>.nodeProcessResult(
    fixingModel: ai.koog.prompt.llm.LLModel
) =
    node<String, GithubRepositoryAnalysisModel.SuccessAnalysisModel>("github-process-llm-result") { rawAnalysisData ->
        val originalRequest = storage.get(originalRequestKey)
        llm.writeSession {
            changeLLMParams(
                newParams = prompt.params.copy(
                    temperature = 0.0,
                )
            )
            appendPrompt {

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
                   - Android: app/build.gradle.kts, AndroidManifest.xml
                   - Go: go.mod, main.go
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

                   **Android projects:**
                   - base_image: "mingc/android-build-box:latest" or "gradle:7-jdk11" or any suitable for project
                   - build_command: "./gradlew assembleDebug" or "./gradlew build"
                   - run_command: "echo 'APK built successfully at app/build/outputs/apk/debug/app-debug.apk'"
                   - port: null (mobile app, no server port)
                   - additional_notes: "This is an Android mobile application. Docker is used for building APK, not running a server."

                   **Go projects:**
                   - base_image: "golang:1.21-alpine" or "golang:1.22-alpine"
                   - build_command: "go mod download && go build -o app ."
                   - run_command: "./app"
                   - port: 8080 (or check main.go for actual port)

                4. Set `docker_env` to **null** if:
                   - Pure library/SDK (no runnable application)
                   - CLI tool without server component
                   - Requires specific hardware/OS features (e.g., iOS apps, desktop GUI apps)
                   - No clear build/run commands
                   - Simple Android UI app without any build verification requirements

                **Important:**
                - Be conservative - only suggest Docker if clearly applicable
                - For Android apps: Suggest Docker if build verification or CI/CD is beneficial
                - For Android apps: Set to null if it's a simple UI-only educational project
                """.trimIndent()

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
                                        2. Field names MUST match exactly: "free_form_github_analysis", "tldr", "repository_review", "user_request_analysis", "docker_env"
                                        3. Use ONLY actual file references found in the analysis data - DO NOT invent file paths
                                        4. Comment types MUST be exactly: "PROBLEM", "ADVANTAGE", or "OK"
                                        5. If no requirements provided, set repository_review to null
                                        6. STRICTLY DO NOT USE MARKDOWN TAGS LIKE ```json TO WRAP CONTENT

                                        ${requirementsSection}
                                        ${dockerDetectionSection}
                                        

                                        **Content Guidelines:**
                                        - free_form_github_analysis: Comprehensive, structured analysis (include overview, architecture, dependencies, code quality, etc.)
                                        - tldr: Concise 1-2 sentence summary of key findings
                                        - repository_review: ONLY if requirements were provided, evaluate each requirement systematically
                                        - user_request_analysis: CRITICAL - Use ONLY for user's specific questions NOT covered by requirements (general conditions, constraints, advantages, attention points). Must be full detailed answer, not summary. Set to null if user's request is fully covered by requirements or no specific questions asked.
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

                                        **IMPORTANT - OUTPUT FORMATTING:**
                                        If user explicitly requested:
                                        - Tree structure (древовидную структуру)
                                        - File listings
                                        - Directory contents
                                        - Code examples
                                        - ANY SPECIFIC FORMAT

                                        Then INCLUDE THE EXACT OUTPUT in user_request_analysis field, NOT a description or summary!
                                        Do not transform or summarize unless explicitly asked.

                                        **Instructions:**
                                        1. Extract comprehensive repository information for free_form_github_analysis
                                        2. **CRITICAL for user_request_analysis**: Check if user's request contains specific questions BEYOND the requirements (general conditions, constraints, advantages, attention points)
                                        3. If user asked specific questions NOT covered by requirements, provide FULL DETAILED answer (not summary) in user_request_analysis field
                                        4. **IMPORTANT**: If user explicitly asks for tree structure, file listings, directory contents, or any specific output format - INCLUDE IT EXACTLY as requested in user_request_analysis
                                        5. If user's request is fully covered by requirements OR no specific questions asked, set user_request_analysis to null
                                        6. Create concise summary for tldr
                                        7. If requirements were provided, evaluate each requirement systematically in repository_review
                                        8. Use ONLY file references and code snippets that were actually found in the analysis
                                        9. Output valid JSON matching the structure exactly
                                        10. Write ALL content in the same language as the original user request
                                        11. **IMPORTANT**: Do NOT skip user_request_analysis if user asks specific technical questions about the repository that aren't covered by requirements
                                    """.trimIndent()
                )
            }

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
                        } else null,
                        userRequestAnalysis = "User asked about specific feature X which is implemented in module Y",
                        dockerEnv = DockerEnvModel(
                            baseImage = "mingc/android-build-box:latest or any suitable",
                            buildCommand = "./gradlew assembleDebug",
                            runCommand = "echo 'APK built successfully at app/build/outputs/apk/debug/app-debug.apk'",
                            port = null,
                            additionalNotes = "Android mobile application - Docker used for building APK"
                        )
                    ),
                    GithubRepositoryAnalysisModel.FailedAnalysisModel("Request was failed because of ...")
                ),
                fixingParser = StructureFixingParser(
                    fixingModel = fixingModel,
                    retries = 3
                )
            )
            if (response.isSuccess) {
                val resp = response.getOrThrow().also {
                    logger.info("Final response: $it")
                }

                when (val structure = resp.structure) {
                    is GithubRepositoryAnalysisModel.FailedAnalysisModel -> {
                        throw IllegalStateException("GitHub analysis failed: ${structure.reason}")
                    }
                    is GithubRepositoryAnalysisModel.SuccessAnalysisModel -> structure
                }
            } else {
                throw IllegalStateException("Request finished with error: ${response.exceptionOrNull()?.message}")
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

// Load GitHub analysis from memory
@OptIn(InternalAgentsApi::class)
private fun AIAgentSubgraphBuilderBase<InitialPromptAnalysisModel.SuccessAnalysisModel, GithubRepositoryAnalysisModel.SuccessAnalysisModel>.nodeLoadGithubMemory() =
    node<InitialPromptAnalysisModel.SuccessAnalysisModel, InitialPromptAnalysisModel.SuccessAnalysisModel>("load-github-memory") { request ->
        withMemory {
            val repoUrl = request.githubRepo
            logger.info("📚 Loading GitHub analysis from memory for: $repoUrl")

            val concepts = listOf(
                getRepositoryUrlConcept(repoUrl),
                getRepositoryStructureConcept(repoUrl),
                getRepositoryDependenciesConcept(repoUrl),
                getRepositoryKeyFindingsConcept(repoUrl),
                getRepositoryAnalysisSummaryConcept(repoUrl)
            )

            concepts.forEach { concept ->
                loadFactsToAgent(
                    llm,
                    concept,
                    listOf(MemoryScopeType.AGENT),
                    subjects = listOf(MemorySubjects.GithubRepositoryAnalysis)
                )
            }

            logger.info("✅ Loaded GitHub analysis facts for: $repoUrl")
        }

        request
    }

// Save GitHub analysis to memory
@OptIn(InternalAgentsApi::class)
private fun AIAgentSubgraphBuilderBase<InitialPromptAnalysisModel.SuccessAnalysisModel, GithubRepositoryAnalysisModel.SuccessAnalysisModel>.nodeSaveGithubMemory() =
    node<GithubRepositoryAnalysisModel.SuccessAnalysisModel, GithubRepositoryAnalysisModel.SuccessAnalysisModel>("save-github-memory") { analysisResult ->
        val originalRequest = storage.get(originalRequestKey)
        val repoUrl = originalRequest?.githubRepo ?: ""

        if (repoUrl.isNotBlank()) {
            logger.info("💾 Saving GitHub analysis to memory for: $repoUrl")

            withMemory {
                val memoryScope = scopesProfile.getScope(MemoryScopeType.AGENT)
                if (memoryScope != null) {
                    saveGithubAnalysisFromModel(
                        llm = llm,
                        subject = MemorySubjects.GithubRepositoryAnalysis,
                        scope = memoryScope,
                        model = analysisResult,
                        repoUrl = repoUrl
                    )
                }
            }

            logger.info("✅ Saved GitHub analysis for: $repoUrl")
        } else {
            logger.warn("⚠️ No repository URL found, skipping memory save")
        }

        analysisResult
    }
