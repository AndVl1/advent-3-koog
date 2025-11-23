package ru.andvl.chatter.koog.agents.repoanalyzer.subgraphs

import ai.koog.agents.core.dsl.builder.AIAgentGraphStrategyBuilder
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.forwardTo
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.agents.repoanalyzer.*
import ru.andvl.chatter.koog.model.repoanalyzer.DependencyInfo
import ru.andvl.chatter.koog.model.repoanalyzer.DependencyResult
import ru.andvl.chatter.koog.model.repoanalyzer.StructureResult
import java.io.File

private val logger = LoggerFactory.getLogger("repoanalyzer-dependencies")

/**
 * Build tool file patterns
 *
 * Maps build tools to their configuration file patterns
 */
private val BUILD_TOOL_FILES = mapOf(
    "Gradle" to listOf("build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts"),
    "Maven" to listOf("pom.xml"),
    "npm" to listOf("package.json"),
    "yarn" to listOf("yarn.lock"),
    "pip" to listOf("requirements.txt", "setup.py", "Pipfile"),
    "cargo" to listOf("Cargo.toml"),
    "go" to listOf("go.mod"),
    "composer" to listOf("composer.json"),
    "bundler" to listOf("Gemfile")
)

/**
 * Framework patterns for detection
 *
 * Maps framework names to their typical indicators
 */
private val FRAMEWORK_PATTERNS = mapOf(
    "Spring Boot" to listOf("spring-boot-starter", "SpringBootApplication"),
    "Spring" to listOf("springframework"),
    "Ktor" to listOf("io.ktor"),
    "React" to listOf("\"react\":", "import React"),
    "Vue" to listOf("\"vue\":", "import Vue"),
    "Angular" to listOf("\"@angular/core\":", "@Component"),
    "Express" to listOf("\"express\":"),
    "Django" to listOf("django", "DJANGO_SETTINGS_MODULE"),
    "Flask" to listOf("from flask import", "Flask(__name__)"),
    "FastAPI" to listOf("from fastapi import", "FastAPI()"),
    "Rails" to listOf("gem 'rails'"),
    "Laravel" to listOf("laravel/framework"),
    "ASP.NET" to listOf("Microsoft.AspNetCore")
)

/**
 * Subgraph: Dependency Analysis
 *
 * Flow:
 * 1. Detect build tool (Gradle, Maven, npm, pip, etc.)
 * 2. Extract dependencies from build files
 * 3. Detect frameworks from dependencies
 *
 * Input: StructureResult
 * Output: DependencyResult (contains buildTools, dependencies, frameworks)
 */
internal suspend fun AIAgentGraphStrategyBuilder<*, *>.subgraphDependencyAnalysis():
        AIAgentSubgraphDelegate<StructureResult, DependencyResult> =
    subgraph(name = "dependency-analysis") {
        val nodeDetectBuildTool by nodeDetectBuildTool()
        val nodeExtractDependencies by nodeExtractDependencies()

        edge(nodeStart forwardTo nodeDetectBuildTool)
        edge(nodeDetectBuildTool forwardTo nodeExtractDependencies)
        edge(nodeExtractDependencies forwardTo nodeFinish)
    }

/**
 * Node: Detect build tool
 *
 * Searches for build tool configuration files in the repository.
 */
private fun AIAgentSubgraphBuilderBase<StructureResult, DependencyResult>.nodeDetectBuildTool() =
    node<StructureResult, StructureResult>("detect-build-tool") { structureResult ->
        logger.info("Detecting build tools in repository")

        val repositoryPath = storage.get(repositoryPathKey)
            ?: throw IllegalStateException("Repository path not found in storage")

        val repoDir = File(repositoryPath)
        val detectedTools = mutableListOf<String>()

        // Search for build tool files
        BUILD_TOOL_FILES.forEach { (tool, filePatterns) ->
            val found = filePatterns.any { pattern ->
                findFileRecursive(repoDir, pattern) != null
            }
            if (found) {
                detectedTools.add(tool)
                logger.info("Detected build tool: $tool")
            }
        }

        if (detectedTools.isEmpty()) {
            logger.info("No build tools detected")
        }

        storage.set(buildToolsKey, detectedTools)

        structureResult
    }

/**
 * Node: Extract dependencies
 *
 * Parses build files to extract dependency information.
 */
private fun AIAgentSubgraphBuilderBase<StructureResult, DependencyResult>.nodeExtractDependencies() =
    node<StructureResult, DependencyResult>("extract-dependencies") { structureResult ->
        logger.info("Extracting dependencies from build files")

        val repositoryPath = storage.get(repositoryPathKey)
            ?: throw IllegalStateException("Repository path not found in storage")

        val buildTools = storage.get(buildToolsKey) ?: emptyList()
        val repoDir = File(repositoryPath)

        val allDependencies = mutableListOf<DependencyInfo>()
        val detectedFrameworks = mutableSetOf<String>()

        // Extract dependencies based on detected build tools
        buildTools.forEach { tool ->
            when (tool) {
                "Gradle" -> {
                    val gradleDeps = extractGradleDependencies(repoDir)
                    allDependencies.addAll(gradleDeps)
                }
                "Maven" -> {
                    val mavenDeps = extractMavenDependencies(repoDir)
                    allDependencies.addAll(mavenDeps)
                }
                "npm", "yarn" -> {
                    val npmDeps = extractNpmDependencies(repoDir)
                    allDependencies.addAll(npmDeps)
                }
                "pip" -> {
                    val pipDeps = extractPipDependencies(repoDir)
                    allDependencies.addAll(pipDeps)
                }
                "cargo" -> {
                    val cargoDeps = extractCargoDependencies(repoDir)
                    allDependencies.addAll(cargoDeps)
                }
                "go" -> {
                    val goDeps = extractGoDependencies(repoDir)
                    allDependencies.addAll(goDeps)
                }
            }
        }

        // Detect frameworks from dependencies and file contents
        detectedFrameworks.addAll(detectFrameworks(repoDir, allDependencies))

        logger.info("Dependencies extracted: ${allDependencies.size}")
        logger.info("Frameworks detected: ${detectedFrameworks.joinToString(", ")}")

        val dependencyResult = DependencyResult(
            buildTools = buildTools,
            dependencies = allDependencies,
            frameworks = detectedFrameworks.toList()
        )

        storage.set(dependencyResultKey, dependencyResult)
        storage.set(dependenciesKey, allDependencies)
        storage.set(frameworksKey, detectedFrameworks.toList())

        dependencyResult
    }

/**
 * Find file recursively in directory (limited depth)
 */
private fun findFileRecursive(directory: File, fileName: String, maxDepth: Int = 5, currentDepth: Int = 0): File? {
    if (currentDepth >= maxDepth) return null

    directory.listFiles()?.forEach { file ->
        if (file.name == fileName) {
            return file
        }
        if (file.isDirectory && !file.name.startsWith(".") && file.name != "node_modules" && file.name != "build") {
            val found = findFileRecursive(file, fileName, maxDepth, currentDepth + 1)
            if (found != null) return found
        }
    }

    return null
}

/**
 * Extract dependencies from Gradle build files
 */
private fun extractGradleDependencies(repoDir: File): List<DependencyInfo> {
    val dependencies = mutableListOf<DependencyInfo>()

    // Find Gradle build files
    val buildFiles = listOf(
        findFileRecursive(repoDir, "build.gradle.kts"),
        findFileRecursive(repoDir, "build.gradle")
    ).filterNotNull()

    buildFiles.forEach { buildFile ->
        try {
            val content = buildFile.readText()

            // Pattern for Kotlin DSL: implementation("group:artifact:version")
            val kotlinDslPattern = Regex("""(implementation|api|compileOnly|runtimeOnly)\s*\(\s*"([^"]+)"\s*\)""")
            kotlinDslPattern.findAll(content).forEach { match ->
                val depString = match.groupValues[2]
                val parts = depString.split(":")
                if (parts.size >= 2) {
                    dependencies.add(
                        DependencyInfo(
                            name = "${parts[0]}:${parts[1]}",
                            version = parts.getOrNull(2),
                            type = "gradle"
                        )
                    )
                }
            }

            // Pattern for Groovy DSL: implementation 'group:artifact:version'
            val groovyDslPattern = Regex("""(implementation|api|compileOnly|runtimeOnly)\s+['"]([\w\.\-]+):([\w\.\-]+):([^'"]+)['"]""")
            groovyDslPattern.findAll(content).forEach { match ->
                dependencies.add(
                    DependencyInfo(
                        name = "${match.groupValues[2]}:${match.groupValues[3]}",
                        version = match.groupValues[4],
                        type = "gradle"
                    )
                )
            }
        } catch (e: Exception) {
            logger.debug("Failed to parse Gradle file ${buildFile.name}: ${e.message}")
        }
    }

    return dependencies
}

/**
 * Extract dependencies from Maven pom.xml
 */
private fun extractMavenDependencies(repoDir: File): List<DependencyInfo> {
    val dependencies = mutableListOf<DependencyInfo>()
    val pomFile = findFileRecursive(repoDir, "pom.xml") ?: return dependencies

    try {
        val content = pomFile.readText()

        // Simple regex-based parsing (not perfect but works for basic cases)
        val depPattern = Regex("""<dependency>.*?<groupId>(.*?)</groupId>.*?<artifactId>(.*?)</artifactId>(?:.*?<version>(.*?)</version>)?.*?</dependency>""", RegexOption.DOT_MATCHES_ALL)

        depPattern.findAll(content).forEach { match ->
            val groupId = match.groupValues[1].trim()
            val artifactId = match.groupValues[2].trim()
            val version = match.groupValues.getOrNull(3)?.trim()

            dependencies.add(
                DependencyInfo(
                    name = "$groupId:$artifactId",
                    version = version,
                    type = "maven"
                )
            )
        }
    } catch (e: Exception) {
        logger.debug("Failed to parse Maven pom.xml: ${e.message}")
    }

    return dependencies
}

/**
 * Extract dependencies from package.json
 */
private fun extractNpmDependencies(repoDir: File): List<DependencyInfo> {
    val dependencies = mutableListOf<DependencyInfo>()
    val packageJson = findFileRecursive(repoDir, "package.json") ?: return dependencies

    try {
        val content = packageJson.readText()

        // Simple regex-based parsing for dependencies
        val depPattern = Regex(""""([\w\-@/]+)":\s*"([^"]+)"""")

        // Look for dependencies section
        val depsSection = Regex(""""dependencies":\s*\{([^}]+)\}""", RegexOption.DOT_MATCHES_ALL)
        val devDepsSection = Regex(""""devDependencies":\s*\{([^}]+)\}""", RegexOption.DOT_MATCHES_ALL)

        depsSection.find(content)?.let { match ->
            depPattern.findAll(match.groupValues[1]).forEach { depMatch ->
                dependencies.add(
                    DependencyInfo(
                        name = depMatch.groupValues[1],
                        version = depMatch.groupValues[2].removePrefix("^").removePrefix("~"),
                        type = "npm"
                    )
                )
            }
        }

        devDepsSection.find(content)?.let { match ->
            depPattern.findAll(match.groupValues[1]).forEach { depMatch ->
                dependencies.add(
                    DependencyInfo(
                        name = depMatch.groupValues[1],
                        version = depMatch.groupValues[2].removePrefix("^").removePrefix("~"),
                        type = "npm-dev"
                    )
                )
            }
        }
    } catch (e: Exception) {
        logger.debug("Failed to parse package.json: ${e.message}")
    }

    return dependencies
}

/**
 * Extract dependencies from requirements.txt
 */
private fun extractPipDependencies(repoDir: File): List<DependencyInfo> {
    val dependencies = mutableListOf<DependencyInfo>()
    val reqFile = findFileRecursive(repoDir, "requirements.txt") ?: return dependencies

    try {
        reqFile.readLines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                // Parse package==version or package>=version
                val parts = trimmed.split(Regex("[=><]+"))
                dependencies.add(
                    DependencyInfo(
                        name = parts[0].trim(),
                        version = parts.getOrNull(1)?.trim(),
                        type = "pip"
                    )
                )
            }
        }
    } catch (e: Exception) {
        logger.debug("Failed to parse requirements.txt: ${e.message}")
    }

    return dependencies
}

/**
 * Extract dependencies from Cargo.toml
 */
private fun extractCargoDependencies(repoDir: File): List<DependencyInfo> {
    val dependencies = mutableListOf<DependencyInfo>()
    val cargoFile = findFileRecursive(repoDir, "Cargo.toml") ?: return dependencies

    try {
        val content = cargoFile.readText()
        val depPattern = Regex("""(\w+)\s*=\s*"([^"]+)"""")

        // Find dependencies section
        val depsSection = Regex("""\[dependencies\](.*?)(?:\[|$)""", RegexOption.DOT_MATCHES_ALL)
        depsSection.find(content)?.let { match ->
            depPattern.findAll(match.groupValues[1]).forEach { depMatch ->
                dependencies.add(
                    DependencyInfo(
                        name = depMatch.groupValues[1],
                        version = depMatch.groupValues[2],
                        type = "cargo"
                    )
                )
            }
        }
    } catch (e: Exception) {
        logger.debug("Failed to parse Cargo.toml: ${e.message}")
    }

    return dependencies
}

/**
 * Extract dependencies from go.mod
 */
private fun extractGoDependencies(repoDir: File): List<DependencyInfo> {
    val dependencies = mutableListOf<DependencyInfo>()
    val goModFile = findFileRecursive(repoDir, "go.mod") ?: return dependencies

    try {
        goModFile.readLines().forEach { line ->
            val trimmed = line.trim()
            // Parse lines like: github.com/user/repo v1.2.3
            if (!trimmed.startsWith("module") && !trimmed.startsWith("go ") && trimmed.contains("/")) {
                val parts = trimmed.split(Regex("\\s+"))
                if (parts.size >= 2) {
                    dependencies.add(
                        DependencyInfo(
                            name = parts[0],
                            version = parts.getOrNull(1),
                            type = "go"
                        )
                    )
                }
            }
        }
    } catch (e: Exception) {
        logger.debug("Failed to parse go.mod: ${e.message}")
    }

    return dependencies
}

/**
 * Detect frameworks from dependencies and file contents
 */
private fun detectFrameworks(repoDir: File, dependencies: List<DependencyInfo>): Set<String> {
    val frameworks = mutableSetOf<String>()

    // Check dependencies
    dependencies.forEach { dep ->
        FRAMEWORK_PATTERNS.forEach { (framework, patterns) ->
            if (patterns.any { pattern -> dep.name.contains(pattern, ignoreCase = true) }) {
                frameworks.add(framework)
            }
        }
    }

    // Check file contents for framework patterns (limited search)
    try {
        repoDir.walkTopDown()
            .filter { it.isFile && it.extension in listOf("kt", "java", "js", "ts", "py", "rb") }
            .take(20) // Limit to first 20 source files for performance
            .forEach { file ->
                try {
                    val content = file.readText()
                    FRAMEWORK_PATTERNS.forEach { (framework, patterns) ->
                        if (patterns.any { pattern -> content.contains(pattern) }) {
                            frameworks.add(framework)
                        }
                    }
                } catch (e: Exception) {
                    // Ignore files that can't be read
                }
            }
    } catch (e: Exception) {
        logger.debug("Error during framework detection: ${e.message}")
    }

    return frameworks
}
