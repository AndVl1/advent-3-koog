package ru.andvl.chatter.koog.agents.codeqa

import ai.koog.agents.core.agent.entity.createStorageKey
import ru.andvl.chatter.koog.model.codeqa.*
import ru.andvl.chatter.shared.models.codeagent.CodeQAMessage
import ru.andvl.chatter.shared.models.codeagent.CodeQARequest
import ru.andvl.chatter.shared.models.codeagent.RepositoryAnalysisResult

/**
 * Storage keys for Code QA Agent
 *
 * These keys are used to share data between nodes and subgraphs
 * during the code question answering workflow.
 */

// Input keys
internal val inputRequestKey = createStorageKey<CodeQARequest>("input-request")
internal val sessionIdKey = createStorageKey<String>("session-id")
internal val questionKey = createStorageKey<String>("question")
internal val conversationHistoryKey = createStorageKey<List<CodeQAMessage>>("conversation-history")
internal val maxHistoryLengthKey = createStorageKey<Int>("max-history-length")

// Session validation keys
internal val sessionValidationResultKey = createStorageKey<SessionValidationResult>("session-validation-result")
internal val repositoryPathKey = createStorageKey<String>("repository-path")
internal val repositoryNameKey = createStorageKey<String>("repository-name")
internal val ragAvailableKey = createStorageKey<Boolean>("rag-available")
internal val analysisResultKey = createStorageKey<RepositoryAnalysisResult>("analysis-result")

// Question analysis keys
internal val questionAnalysisResultKey = createStorageKey<QuestionAnalysisResult>("question-analysis-result")
internal val questionIntentKey = createStorageKey<QuestionIntent>("question-intent")
internal val searchQueryKey = createStorageKey<String>("search-query")
internal val keywordsKey = createStorageKey<List<String>>("keywords")
internal val requiresCodeSearchKey = createStorageKey<Boolean>("requires-code-search")

// Code search keys
internal val codeSearchResultKey = createStorageKey<CodeSearchResult>("code-search-result")
internal val ragResultsCountKey = createStorageKey<Int>("rag-results-count")
internal val mcpResultsCountKey = createStorageKey<Int>("mcp-results-count")
internal val codeReferencesInternalKey = createStorageKey<List<CodeReferenceInternal>>("code-references-internal")

// Answer generation keys
internal val answerGenerationResultKey = createStorageKey<AnswerGenerationResult>("answer-generation-result")
internal val finalAnswerKey = createStorageKey<String>("final-answer")
internal val confidenceScoreKey = createStorageKey<Float>("confidence-score")
internal val followUpSuggestionsKey = createStorageKey<List<String>>("follow-up-suggestions")
