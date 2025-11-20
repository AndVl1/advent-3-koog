package ru.andvl.chatter.koog.agents.codemod.subgraphs

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
import ru.andvl.chatter.koog.model.codemod.AnalysisResult
import ru.andvl.chatter.koog.model.codemod.SetupResult
import ru.andvl.chatter.koog.model.docker.DockerEnvModel
import ru.andvl.chatter.koog.tools.FileOperationsToolSet

private val logger = LoggerFactory.getLogger("codemod-analysis")

/**
 * Subgraph 2: Code Analysis
 *
 * Flow:
 * 1. Analyze codebase using LLM + FileOperations tools
 * 2. Generate modification plan
 */
internal suspend fun AIAgentGraphStrategyBuilder<*, *>.subgraphCodeAnalysis(
    model: LLModel,
    fixingModel: LLModel
): AIAgentSubgraphDelegate<SetupResult, AnalysisResult> =
    subgraph(
        name = "code-analysis",
        tools = ToolRegistry {
            tools(FileOperationsToolSet())
        }.tools
    ) {
        val nodeAnalyzeCodebase by nodeAnalyzeCodebase(model)
        val nodeExecuteTool by nodeExecuteTool("code-analysis-execute-tool")
        val nodeSendToolResult by nodeLLMSendToolResult("code-analysis-send-tool-result")
        val nodeGeneratePlan by nodeGeneratePlan(fixingModel)

        edge(nodeStart forwardTo nodeAnalyzeCodebase)
        edge(nodeAnalyzeCodebase forwardTo nodeExecuteTool onToolCall { true })
        edge(nodeAnalyzeCodebase forwardTo nodeGeneratePlan onAssistantMessage { true })

        edge(nodeExecuteTool forwardTo nodeSendToolResult)
        edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
        edge(nodeSendToolResult forwardTo nodeGeneratePlan onAssistantMessage { true })

        edge(nodeGeneratePlan forwardTo nodeFinish)
    }

/**
 * Node: Analyze codebase with LLM using FileOperations tools
 */
private fun AIAgentSubgraphBuilderBase<SetupResult, AnalysisResult>.nodeAnalyzeCodebase(model: LLModel) =
    node<SetupResult, Message.Response>("analyze-codebase") { setupResult ->
        val userRequest = storage.get(userRequestKey) ?: "Modify the code as requested"
        val repositoryPath = setupResult.repositoryPath

        logger.info("Analyzing codebase at: $repositoryPath")
        logger.info("User request: $userRequest")

        llm.writeSession {
            appendPrompt {
                system("""
You are an expert software engineer analyzing a codebase to make specific changes.

**User Request**: $userRequest

**Repository Path**: $repositoryPath
**Repository**: ${setupResult.owner}/${setupResult.repo}
**Feature Branch**: ${setupResult.featureBranch}

**Available Tools**:
- get-file-tree: Get directory structure (with depth, hidden files, exclusions)
- read-file-content: Read file contents with optional line ranges
- search-in-files: Search for patterns in code with context

**Your Task**:
1. Understand the current codebase structure
2. Find relevant files that need modification
3. Identify dependencies and impacts of the changes
4. Analyze Docker environment capabilities
5. Create a detailed modification plan

**Analysis Guidelines**:
- Start by getting the file tree to understand the structure
- Search for relevant code patterns related to the user's request
- Read files that need to be modified
- Consider dependencies, imports, and related code
- Think about edge cases and potential issues
- Check for Docker configuration files and build tools

**DOCKER ENVIRONMENT ANALYSIS:**
Analyze if this project can be containerized with Docker for verification:

1. **Check for existing Docker configuration**:
   - Look for: Dockerfile, docker-compose.yml, .dockerignore
   - If found, analyze the configuration

2. **Identify project type and technology**:
   - **Node.js**: package.json with scripts (npm/yarn/pnpm)
   - **Python**: requirements.txt, setup.py, pyproject.toml
   - **Java**: pom.xml (Maven), build.gradle/build.gradle.kts (Gradle)
   - **Kotlin**: build.gradle.kts, settings.gradle.kts
   - **Android**: app/build.gradle.kts, AndroidManifest.xml
   - **Go**: go.mod, main.go
   - **Ruby**: Gemfile
   - **PHP**: composer.json
   - **Rust**: Cargo.toml

3. **If suitable for Docker, provide docker_env**:

   **Node.js** (with package.json):
   ```json
   "docker_env": {
     "base_image": "node:18-alpine",
     "build_command": "npm install",
     "run_command": "npm test",
     "port": null,
     "additional_notes": "Node.js project with npm"
   }
   ```

   **Python** (with requirements.txt):
   ```json
   "docker_env": {
     "base_image": "python:3.11-slim",
     "build_command": "pip install -r requirements.txt",
     "run_command": "pytest",
     "port": null,
     "additional_notes": "Python project with pytest"
   }
   ```

   **Gradle/Kotlin/Java**:
   ```json
   "docker_env": {
     "base_image": "gradle:8.5-jdk17-alpine or any suitable or any other lighter and also suitable",
     "build_command": "./gradlew build -x test",
     "run_command": "./gradlew test",
     "port": null,
     "additional_notes": "Gradle-based project with JUnit tests"
   }
   ```

   **Maven/Java**:
   ```json
   "docker_env": {
     "base_image": "maven:3.9-eclipse-temurin-17-alpine",
     "build_command": "mvn clean install -DskipTests",
     "run_command": "mvn test",
     "port": null,
     "additional_notes": "Maven-based Java project"
   }
   ```

   **Go**:
   ```json
   "docker_env": {
     "base_image": "golang:1.21-alpine",
     "build_command": "go build",
     "run_command": "go test ./...",
     "port": null,
     "additional_notes": "Go project with go test"
   }
   ```

4. **Set docker_env to null if**:
   - Pure library/SDK (no runnable application or tests)
   - CLI tool without server component or tests
   - Requires specific hardware/OS features (e.g., iOS apps, desktop GUI apps)
   - No clear build/run/test commands
   - Simple Android UI app without verification requirements

**Plan Format** (you will generate this):
```json
{
  "modification_plan": "Detailed description of what needs to be changed and how",
  "files_to_modify": ["path/to/file1.kt", "path/to/file2.kt"],
  "dependencies_identified": ["dependency1", "dependency2"],
  "docker_env": {
    "base_image": "gradle:8.5-jdk17-alpine or any suitable",
    "build_command": "./gradlew build -x test",
    "run_command": "./gradlew test",
    "port": null,
    "additional_notes": "Project type description"
  }
}
```

**IMPORTANT**:
- If Docker environment is suitable, include the `docker_env` object
- If not suitable, set `docker_env` to `null`
- The docker_env will be used for verification after code modifications

Begin your analysis by exploring the codebase.
""".trimIndent())
            }

            requestLLM()
        }
    }

/**
 * Node: Generate modification plan from LLM analysis with structured parsing
 */
private fun AIAgentSubgraphBuilderBase<SetupResult, AnalysisResult>.nodeGeneratePlan(fixingModel: LLModel) =
    node<String, AnalysisResult>("generate-modification-plan") { analysisText ->
        logger.info("Generating modification plan from analysis")
        logger.debug("Analysis text: $analysisText")

        val dockerDetectionSection = """

**DOCKER ENVIRONMENT ANALYSIS:**
Analyze if this project can be containerized with Docker for verification:

1. **Check for existing Docker configuration**:
   - Look for: Dockerfile, docker-compose.yml, .dockerignore
   - If found, analyze the configuration

2. **Identify project type and technology**:
   - **Node.js**: package.json with scripts (npm/yarn/pnpm)
   - **Python**: requirements.txt, setup.py, pyproject.toml
   - **Java**: pom.xml (Maven), build.gradle/build.gradle.kts (Gradle)
   - **Kotlin**: build.gradle.kts, settings.gradle.kts
   - **Android**: app/build.gradle.kts, AndroidManifest.xml
   - **Go**: go.mod, main.go
   - **Ruby**: Gemfile
   - **PHP**: composer.json
   - **Rust**: Cargo.toml

3. **If suitable for Docker, provide docker_env**:

   **Node.js projects:**
   - base_image: "node:18-alpine" or "node:20-alpine"
   - build_command: "npm install" or "yarn install"
   - run_command: "npm test" or "npm start"
   - port: 3000 (or from package.json)
   - additional_notes: "Node.js project with npm/yarn"

   **Python projects:**
   - base_image: "python:3.9-slim" or "python:3.11-slim"
   - build_command: "pip install -r requirements.txt"
   - run_command: "pytest" or "python -m unittest"
   - port: 5000 or 8000
   - additional_notes: "Python project with pytest/unittest"

   **Java/Maven projects:**
   - base_image: "maven:3.9-eclipse-temurin-17-alpine"
   - build_command: "mvn clean install -DskipTests"
   - run_command: "mvn test"
   - port: 8080
   - additional_notes: "Maven-based Java project"

   **Java/Gradle/Kotlin projects:**
   - base_image: "gradle:8.5-jdk17-alpine or any suitable"
   - build_command: "./gradlew build -x test"
   - run_command: "./gradlew test"
   - port: 8080 (or null for test-only projects)
   - additional_notes: "Gradle-based Kotlin/Java project with JUnit tests"

   **Android projects:**
   - base_image: "mingc/android-build-box:latest" or "gradle:8.5-jdk17-alpine"
   - build_command: "./gradlew assembleDebug" or "./gradlew build"
   - run_command: "echo 'APK built successfully at app/build/outputs/apk/debug/app-debug.apk'"
   - port: null (mobile app, no server port)
   - additional_notes: "Android mobile application - Docker used for building APK"

   **Go projects:**
   - base_image: "golang:1.21-alpine" or "golang:1.22-alpine"
   - build_command: "go mod download && go build -o app ."
   - run_command: "go test ./..."
   - port: 8080 (or check main.go for actual port)
   - additional_notes: "Go project with go test"

4. **Set docker_env to null if**:
   - Pure library/SDK (no runnable application or tests)
   - CLI tool without server component or tests
   - Requires specific hardware/OS features (e.g., iOS apps, desktop GUI apps)
   - No clear build/run/test commands
   - Simple Android UI app without verification requirements

**Important:**
- Be conservative - only suggest Docker if clearly applicable
- For test projects: prioritize test execution (run_command should run tests)
- For server projects: prioritize running the application
""".trimIndent()

        // Parse analysis with structured LLM parser for guaranteed JSON correctness
        val parsed = llm.writeSession {
            appendPrompt {
                system(
                    """
You are an expert at synthesizing code analysis results into structured JSON format.

Your task: Process the raw analysis data and create a comprehensive response following the EXACT JSON structure provided.

**CRITICAL REQUIREMENTS:**
1. Output MUST be valid JSON matching the provided structure
2. Field names MUST match exactly: "modification_plan", "files_to_modify", "dependencies_identified", "docker_env"
3. STRICTLY DO NOT USE MARKDOWN TAGS LIKE ```json TO WRAP CONTENT
4. Base all information on actual analysis data provided

${dockerDetectionSection}

**Output Structure**:
{
  "modification_plan": "Detailed description of what needs to be changed and how",
  "files_to_modify": ["path/to/file1.kt", "path/to/file2.kt"],
  "dependencies_identified": ["dependency1", "dependency2"],
  "docker_env": {
    "base_image": "gradle:8.5-jdk17-alpine or any suitable",
    "build_command": "./gradlew build -x test",
    "run_command": "./gradlew test",
    "port": null,
    "additional_notes": "Project type description"
  }
}

**Important**:
- docker_env: Analyze if project can be containerized, provide Docker configuration or null
- Include all files that need modification from the analysis
- List all dependencies that were identified
- Provide a clear, actionable modification plan
- Extract and structure the information from the analysis provided by the user
""".trimIndent()
                )
                user(
                    """
Based on the code analysis below, create a structured JSON report following the exact structure provided above.

**Analysis Results:**

$analysisText

**Instructions:**
1. Extract modification plan from the analysis
2. List all files that need to be modified
3. Identify all dependencies mentioned
4. **CRITICAL**: Analyze the project type and determine if Docker environment is suitable for verification
5. If Docker is suitable, provide complete docker_env configuration
6. If Docker is not suitable, set docker_env to null
7. Output valid JSON matching the structure exactly
8. DO NOT wrap the output in markdown code blocks

Please provide the structured JSON response.
""".trimIndent()
                )
            }

            requestLLMStructured<AnalysisResult>(
                examples = listOf(
                    AnalysisResult(
                        modificationPlan = "Add unit tests using JUnit 5 for all service classes. Create test files in src/test/kotlin matching the structure of src/main/kotlin.",
                        filesToModify = listOf("src/main/kotlin/com/example/UserService.kt", "src/main/kotlin/com/example/OrderService.kt"),
                        dependenciesIdentified = listOf("JUnit 5", "Mockito", "Kotlin Test"),
                        dockerEnv = DockerEnvModel(
                            baseImage = "gradle:8.5-jdk17-alpine or any suitable",
                            buildCommand = "./gradlew build -x test",
                            runCommand = "./gradlew test",
                            port = null,
                            additionalNotes = "Gradle-based Kotlin project with JUnit tests"
                        )
                    )
                ),
                fixingParser = StructureFixingParser(
                    fixingModel = fixingModel,
                    retries = 3
                )
            )
        }.getOrThrow().structure

        logger.info("Modification plan generated:")
        logger.info("  Files to modify: ${parsed.filesToModify.joinToString(", ")}")
        logger.info("  Dependencies: ${parsed.dependenciesIdentified.joinToString(", ")}")

        // Save Docker environment to storage if detected
        if (parsed.dockerEnv != null) {
            storage.set(dockerEnvKey, parsed.dockerEnv)
            logger.info("  Docker environment detected: ${parsed.dockerEnv.baseImage}")
            logger.info("    Build command: ${parsed.dockerEnv.buildCommand}")
            logger.info("    Run command: ${parsed.dockerEnv.runCommand}")
        } else {
            logger.info("  Docker environment: Not detected or not suitable")
        }

        parsed
    }

/**
 * Node: Execute tool calls
 */
private fun AIAgentSubgraphBuilderBase<*, *>.nodeExecuteTool(
    name: String? = null
): AIAgentNodeDelegate<ai.koog.prompt.message.Message.Tool.Call, ReceivedToolResult> =
    node(name) { toolCall ->
        environment.executeTool(toolCall)
    }
