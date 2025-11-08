package ru.andvl.chatter.koog.agents.mcp

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMCompressHistory
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIChatParams
import ru.andvl.chatter.koog.agents.mcp.subgraphs.subgraphDocker
import ru.andvl.chatter.koog.agents.mcp.subgraphs.subgraphGithubAnalyze
import ru.andvl.chatter.koog.agents.mcp.subgraphs.subgraphGithubLLMRequest
import ru.andvl.chatter.koog.agents.mcp.subgraphs.subgraphGoogleSheets
import ru.andvl.chatter.koog.model.structured.ChatRequest
import ru.andvl.chatter.koog.model.tool.GithubChatRequest
import ru.andvl.chatter.koog.model.tool.GithubRepositoryAnalysisModel
import ru.andvl.chatter.koog.model.tool.ToolChatResponse

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
    fixingModel: ai.koog.prompt.llm.LLModel
): AIAgentGraphStrategy<GithubChatRequest, ToolChatResponse> =
    strategy("github-analysis-agent") {
        val initialRequestNode by subgraphGithubLLMRequest(fixingModel)
        val githubAnalysisSubgraph by subgraphGithubAnalyze(fixingModel)
        val nodeCompressHistory by nodeLLMCompressHistory<GithubRepositoryAnalysisModel.SuccessAnalysisModel>("github-strategy-intermediate-compress")
        val dockerSubgraph by subgraphDocker(fixingModel)
        val googleSheetsSubgraph by subgraphGoogleSheets()

        nodeStart then initialRequestNode then githubAnalysisSubgraph then nodeCompressHistory then dockerSubgraph then googleSheetsSubgraph then nodeFinish
    }
