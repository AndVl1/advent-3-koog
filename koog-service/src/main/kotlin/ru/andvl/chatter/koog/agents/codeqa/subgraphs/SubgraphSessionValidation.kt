package ru.andvl.chatter.koog.agents.codeqa.subgraphs

import ai.koog.agents.core.dsl.builder.AIAgentGraphStrategyBuilder
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.forwardTo
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.agents.codeqa.*
import ru.andvl.chatter.koog.model.codeqa.SessionValidationResult
import ru.andvl.chatter.shared.models.codeagent.CodeQARequest
import java.io.File

private val logger = LoggerFactory.getLogger("codeqa-session-validation")

/**
 * Subgraph: Session Validation
 *
 * Purpose: Validate session existence and check RAG availability
 *
 * Flow:
 * 1. Validate session ID exists
 * 2. Check if repository is indexed (RAG available)
 * 3. Load repository metadata from previous analysis
 *
 * Input: CodeQARequest
 * Output: SessionValidationResult (contains session info and RAG availability)
 */
internal suspend fun AIAgentGraphStrategyBuilder<CodeQARequest, *>.subgraphSessionValidation():
        AIAgentSubgraphDelegate<CodeQARequest, SessionValidationResult> =
    subgraph(name = "session-validation") {
        val nodeValidateSessionId by nodeValidateSessionId()
        val nodeCheckRepositoryIndexed by nodeCheckRepositoryIndexed()
        val nodeLoadRepositoryMetadata by nodeLoadRepositoryMetadata()

        edge(nodeStart forwardTo nodeValidateSessionId)
        edge(nodeValidateSessionId forwardTo nodeCheckRepositoryIndexed)
        edge(nodeCheckRepositoryIndexed forwardTo nodeLoadRepositoryMetadata)
        edge(nodeLoadRepositoryMetadata forwardTo nodeFinish)
    }

/**
 * Node: Validate Session ID
 *
 * Validates that the session ID maps to a valid repository.
 * In this implementation, sessionId is the repository path.
 *
 * Throws IllegalArgumentException if session does not exist.
 */
private fun AIAgentSubgraphBuilderBase<CodeQARequest, SessionValidationResult>.nodeValidateSessionId() =
    node<CodeQARequest, CodeQARequest>("validate-session-id") { request ->
        logger.info("Validating session ID: ${request.sessionId}")

        // Store request components
        storage.set(inputRequestKey, request)
        storage.set(sessionIdKey, request.sessionId)
        storage.set(questionKey, request.question)
        storage.set(conversationHistoryKey, request.history)
        storage.set(maxHistoryLengthKey, request.maxHistoryLength)

        // SessionId is the repository path
        val repositoryPath = request.sessionId
        val repoDir = File(repositoryPath)

        // Validate repository exists
        if (!repoDir.exists() || !repoDir.isDirectory) {
            val errorMessage = "Session not found: Repository does not exist at path: $repositoryPath"
            logger.error(errorMessage)
            throw IllegalArgumentException(errorMessage)
        }

        // Check if it's a git repository
        val gitDir = File(repoDir, ".git")
        if (!gitDir.exists()) {
            val errorMessage = "Session invalid: Not a git repository at path: $repositoryPath"
            logger.error(errorMessage)
            throw IllegalArgumentException(errorMessage)
        }

        logger.info("Session ID validated successfully")
        storage.set(repositoryPathKey, repositoryPath)

        request
    }

/**
 * Node: Check Repository Indexed
 *
 * Checks if the repository has a RAG index available.
 * For now, we assume RAG is available if embeddings directory exists.
 */
private fun AIAgentSubgraphBuilderBase<CodeQARequest, SessionValidationResult>.nodeCheckRepositoryIndexed() =
    node<CodeQARequest, Boolean>("check-repository-indexed") { request ->
        val repositoryPath = storage.get(repositoryPathKey)!!
        logger.info("Checking RAG availability for repository: $repositoryPath")

        // Check if RAG embeddings exist for this repository
        // This is a simplified check - in production, we'd check the RAG service
        val embeddingsDir = File(repositoryPath, ".embeddings")
        val ragAvailable = embeddingsDir.exists() && embeddingsDir.isDirectory

        logger.info("RAG available: $ragAvailable")
        storage.set(ragAvailableKey, ragAvailable)

        ragAvailable
    }

/**
 * Node: Load Repository Metadata
 *
 * Loads repository metadata from previous analysis if available.
 * This includes repository name, structure, dependencies, etc.
 */
private fun AIAgentSubgraphBuilderBase<CodeQARequest, SessionValidationResult>.nodeLoadRepositoryMetadata() =
    node<Boolean, SessionValidationResult>("load-repository-metadata") { ragAvailable ->
        val sessionId = storage.get(sessionIdKey)!!
        val repositoryPath = storage.get(repositoryPathKey)!!

        logger.info("Loading repository metadata for: $repositoryPath")

        // Extract repository name from path
        val repositoryName = File(repositoryPath).name

        // In a production system, we'd load the actual AnalysisResult from storage
        // For now, we create a minimal result
        // The analysis result would have been saved by the Repository Analyzer Agent
        logger.info("Repository name: $repositoryName")
        storage.set(repositoryNameKey, repositoryName)

        val validationResult = SessionValidationResult(
            sessionId = sessionId,
            sessionExists = true,
            repositoryPath = repositoryPath,
            repositoryName = repositoryName,
            ragAvailable = ragAvailable,
            analysisResult = null // Would be loaded from storage in production
        )

        logger.info("Session validation completed successfully")
        storage.set(sessionValidationResultKey, validationResult)

        validationResult
    }
