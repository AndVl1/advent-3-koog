package ru.andvl.chatter.koog.agents.mcp

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMCompressHistory
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIChatParams
import ru.andvl.chatter.koog.agents.mcp.subgraphs.*
import ru.andvl.chatter.koog.embeddings.model.EmbeddingConfig
import ru.andvl.chatter.koog.model.structured.ChatRequest
import ru.andvl.chatter.koog.model.tool.GithubChatRequest
import ru.andvl.chatter.koog.model.tool.GithubRepositoryAnalysisModel
import ru.andvl.chatter.koog.model.tool.ToolChatResponse
import java.io.File

/**
 * Константы имен сабграфов и нод для GitHub анализа
 */
object GithubAnalysisNodes {
    // === Subgraphs ===
    object Subgraphs {
        const val INITIAL_ANALYSIS = "initial-analysis"
        const val RAG_INDEXING = "rag-indexing"
        const val GITHUB_ANALYSIS = "github-analysis"
        const val DOCKER_BUILD = "docker-build"
        const val GOOGLE_SHEETS = "google-sheets-population"
    }

    // === Nodes in initial-analysis subgraph ===
    object InitialAnalysis {
        const val SAVE_REQUIREMENTS = "save-requirements"
        const val LOAD_MEMORY = "load-memory"
        const val INITIAL_ANALYSIS = "initial-analysis"
        const val REQUIREMENTS_COLLECTION = "requirements-collection"
        const val PROCESS_FINAL_REQUIREMENTS = "process-final-requirements"
    }

    // === Nodes in rag-indexing subgraph ===
    object RAGIndexing {
        const val INITIALIZE_RAG = "initialize-rag"
        const val CLONE_AND_INDEX = "clone-and-index"
    }

    // === Nodes in github-analysis subgraph ===
    object GithubAnalysis {
        const val LOAD_GITHUB_MEMORY = "load-github-memory"
        const val GITHUB_PROCESS_USER_REQUEST = "github-process-user-request"
        const val GITHUB_EXECUTE_TOOL = "github-execute-tool"
        const val GITHUB_SEND_TOOL_RESULT = "github-subgraph-send-tool"
        const val GITHUB_COMPRESS_HISTORY = "github-compress-history"
        const val GITHUB_PROCESS_LLM_RESULT = "github-process-llm-result"
        const val SAVE_GITHUB_MEMORY = "save-github-memory"
    }

    // === Nodes in docker-build subgraph ===
    object DockerBuild {
        const val DOCKER_REQUEST = "docker-request"
        const val DOCKER_EXECUTE_TOOL = "docker-execute-tool"
        const val DOCKER_SEND_TOOL_RESULT = "docker-send-tool-result"
        const val PROCESS_DOCKER_RESULT = "process-docker-result"
    }

    // === Nodes in google-sheets subgraph ===
    object GoogleSheets {
        const val CHECK_GOOGLE_SHEETS = "check-google-sheets"
        const val ANALYZE_SHEET_STRUCTURE = "analyze-sheet-structure"
        const val POPULATE_SHEET_DATA = "populate-sheet-data"
        const val FINALIZE_GOOGLE_SHEETS = "finalize-google-sheets-response"
    }

    // === Top-level strategy nodes ===
    object Strategy {
        const val GITHUB_STRATEGY_INTERMEDIATE_COMPRESS = "github-strategy-intermediate-compress"
    }
}

internal val toolCallsKey = createStorageKey<List<String>>("tool-calls")

internal fun getToolAgentPrompt(
    systemPrompt: String,
    request: ChatRequest,
    temperature: Double? = null,
): Prompt {
    return prompt(
        existing = Prompt(
            messages = emptyList(),
            id = "tool-agent-prompt",
            params = OpenAIChatParams(
                temperature = temperature
            )
        )
    ) {
        system {
            text(systemPrompt)
        }
        messages(request.history)
    }
}

internal suspend fun getGithubAnalysisStrategy(
    fixingModel: ai.koog.prompt.llm.LLModel,
    embeddingConfig: EmbeddingConfig = EmbeddingConfig(),
    embeddingStorageDir: File = File("./rag/embeddings")
): AIAgentGraphStrategy<GithubChatRequest, ToolChatResponse> =
    strategy("github-analysis-agent") {
        val initialRequestNode by subgraphGithubLLMRequest(fixingModel)
        val ragIndexingSubgraph by subgraphRAGIndexing(embeddingConfig, embeddingStorageDir.toPath())

        val githubAnalysisSubgraph by subgraphGithubAnalyze(fixingModel)

        val nodeCompressHistory by nodeLLMCompressHistory<GithubRepositoryAnalysisModel.SuccessAnalysisModel>(
            GithubAnalysisNodes.Strategy.GITHUB_STRATEGY_INTERMEDIATE_COMPRESS
        )
        val dockerSubgraph by subgraphDocker(fixingModel)
        val googleSheetsSubgraph by subgraphGoogleSheets()

        nodeStart then initialRequestNode then ragIndexingSubgraph then githubAnalysisSubgraph then nodeCompressHistory then dockerSubgraph then googleSheetsSubgraph then nodeFinish
    }
