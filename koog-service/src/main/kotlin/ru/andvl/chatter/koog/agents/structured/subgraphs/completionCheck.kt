package ru.andvl.chatter.koog.agents.structured.subgraphs

import ai.koog.agents.core.dsl.builder.AIAgentGraphStrategyBuilder
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.forwardTo
import ru.andvl.chatter.koog.agents.structured.Response
import ru.andvl.chatter.koog.model.structured.ChatRequest
import ru.andvl.chatter.koog.model.structured.CompletionStatus

internal fun AIAgentGraphStrategyBuilder<ChatRequest, Response>.completionCheckSubgraph(): AIAgentSubgraphDelegate<Pair<Response, ChatRequest>, Triple<CompletionStatus, ChatRequest, Response>> =
    subgraph<Pair<Response, ChatRequest>, Triple<CompletionStatus, ChatRequest, Response>>("completion-check") {
        val checkCompletion by node<Pair<Response, ChatRequest>, Triple<CompletionStatus, ChatRequest, Response>>("check-done") { response ->
            val allResolved = response.first.getOrNull()!!.structure.checkList.all { it.resolution != null }
            val hasChecklist = response.first.getOrNull()!!.structure.checkList.isNotEmpty()

            Triple(
                CompletionStatus(
                    isComplete = !hasChecklist || allResolved,
                    requiresMoreInfo = hasChecklist && !allResolved,
                    readyForFinalAnswer = allResolved && hasChecklist
                ), response.second, response.first
            )
        }
        edge(nodeStart forwardTo checkCompletion)
        edge(checkCompletion forwardTo nodeFinish)
    }
