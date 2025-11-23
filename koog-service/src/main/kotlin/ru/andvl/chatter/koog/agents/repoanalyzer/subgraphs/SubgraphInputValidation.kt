package ru.andvl.chatter.koog.agents.repoanalyzer.subgraphs

import ai.koog.agents.core.dsl.builder.AIAgentGraphStrategyBuilder
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.forwardTo
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.agents.repoanalyzer.*
import ru.andvl.chatter.koog.model.repoanalyzer.ValidatedRequest
import ru.andvl.chatter.shared.models.codeagent.RepositoryAnalysisRequest

private val logger = LoggerFactory.getLogger("repoanalyzer-input-validation")

/**
 * GitHub URL regex pattern
 *
 * Matches URLs like:
 * - https://github.com/owner/repo
 * - https://github.com/owner/repo.git
 * - http://github.com/owner/repo
 * - github.com/owner/repo
 */
private val GITHUB_URL_REGEX = Regex(
    """^(?:https?://)?(?:www\.)?github\.com/([^/]+)/([^/]+?)(?:\.git)?/?$""",
    RegexOption.IGNORE_CASE
)

/**
 * Subgraph: Input Validation
 *
 * Flow:
 * 1. Validate GitHub URL format
 * 2. Extract owner and repository name from URL
 *
 * Input: RepositoryAnalysisRequest
 * Output: ValidatedRequest (contains owner, repo, url, enableEmbeddings)
 */
internal suspend fun AIAgentGraphStrategyBuilder<RepositoryAnalysisRequest, *>.subgraphInputValidation():
        AIAgentSubgraphDelegate<RepositoryAnalysisRequest, ValidatedRequest> =
    subgraph(name = "input-validation") {
        val nodeValidateGithubUrl by nodeValidateGithubUrl()
        val nodeExtractMetadata by nodeExtractMetadata()

        edge(nodeStart forwardTo nodeValidateGithubUrl)
        edge(nodeValidateGithubUrl forwardTo nodeExtractMetadata)
        edge(nodeExtractMetadata forwardTo nodeFinish)
    }

/**
 * Node: Validate GitHub URL format
 *
 * Validates that the provided URL is a valid GitHub repository URL.
 * Throws IllegalArgumentException if URL is invalid.
 */
private fun AIAgentSubgraphBuilderBase<RepositoryAnalysisRequest, ValidatedRequest>.nodeValidateGithubUrl() =
    node<RepositoryAnalysisRequest, RepositoryAnalysisRequest>("validate-github-url") { request ->
        logger.info("Validating GitHub URL: ${request.githubUrl}")

        val url = request.githubUrl.trim()

        // Check if URL matches GitHub pattern
        if (!GITHUB_URL_REGEX.matches(url)) {
            logger.error("Invalid GitHub URL format: $url")
            throw IllegalArgumentException(
                "Invalid GitHub URL format. Expected format: https://github.com/owner/repo"
            )
        }

        logger.info("GitHub URL validated successfully")

        // Store original URL and request metadata
        storage.set(githubUrlKey, url)
        storage.set(enableEmbeddingsKey, request.enableEmbeddings)
        storage.set(analysisTypeKey, request.analysisType)

        request
    }

/**
 * Node: Extract metadata from GitHub URL
 *
 * Extracts owner and repository name from validated URL.
 */
private fun AIAgentSubgraphBuilderBase<RepositoryAnalysisRequest, ValidatedRequest>.nodeExtractMetadata() =
    node<RepositoryAnalysisRequest, ValidatedRequest>("extract-metadata") { request ->
        val url = storage.get(githubUrlKey)!!
        val matchResult = GITHUB_URL_REGEX.find(url)!!

        val owner = matchResult.groupValues[1]
        val repo = matchResult.groupValues[2]

        logger.info("Extracted metadata - Owner: $owner, Repo: $repo")

        // Store extracted values
        storage.set(ownerKey, owner)
        storage.set(repoKey, repo)

        val validatedRequest = ValidatedRequest(
            githubUrl = url,
            owner = owner,
            repo = repo,
            enableEmbeddings = request.enableEmbeddings
        )

        // Store validated request for later use
        storage.set(validatedRequestKey, validatedRequest)

        logger.info("Input validation completed successfully")

        validatedRequest
    }
