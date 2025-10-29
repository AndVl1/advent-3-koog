package ru.andvl.chatter.koog.agents.mcp

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.params.LLMParams
import ru.andvl.chatter.koog.agents.mcp.subgraphs.subgraphGithubAnalyze
import ru.andvl.chatter.koog.agents.mcp.subgraphs.subgraphGithubLLMRequest
import ru.andvl.chatter.koog.model.common.TokenUsage
import ru.andvl.chatter.koog.model.structured.ChatRequest
import ru.andvl.chatter.koog.model.tool.GithubChatRequest
import ru.andvl.chatter.koog.model.tool.InitialPromptAnalysisModel
import ru.andvl.chatter.koog.model.tool.ToolChatResponse

internal fun getToolAgentPrompt(
    systemPrompt: String,
    request: ChatRequest,
    temperature: Double? = null,
): Prompt {
    return prompt(
        existing = Prompt(
            messages = emptyList(),
            id = "tool-agent-prompt",
            params = LLMParams(
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

internal fun getGithubAnalysisStrategy(): AIAgentGraphStrategy<GithubChatRequest, ToolChatResponse> =
    strategy("github-analysis-agent") {
        val initialRequestNode by subgraphGithubLLMRequest()
        val githubAnalysisSubgraph by subgraphGithubAnalyze()

        edge(nodeStart forwardTo initialRequestNode)
        // instant finish on initial request failure
        edge(initialRequestNode forwardTo nodeFinish onCondition {
            it.first is InitialPromptAnalysisModel.FailedAnalysisModel
        } transformed {
            ToolChatResponse(
                response = (it.first as InitialPromptAnalysisModel.FailedAnalysisModel).reason,
                toolCalls = emptyList(),
                originalMessage = it.second,
                tokenUsage = TokenUsage(0, 0, 0)
            )
        })

        edge(initialRequestNode forwardTo githubAnalysisSubgraph onCondition {
            it.first is InitialPromptAnalysisModel.SuccessAnalysisModel
        } transformed { it.first as InitialPromptAnalysisModel.SuccessAnalysisModel })
        edge(githubAnalysisSubgraph forwardTo nodeFinish)
    }
