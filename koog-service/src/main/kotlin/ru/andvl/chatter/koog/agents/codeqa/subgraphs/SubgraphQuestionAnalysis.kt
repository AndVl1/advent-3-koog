package ru.andvl.chatter.koog.agents.codeqa.subgraphs

import ai.koog.agents.core.dsl.builder.AIAgentGraphStrategyBuilder
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.prompt.llm.LLModel
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.agents.codeqa.*
import ru.andvl.chatter.koog.model.codeqa.QuestionAnalysisResult
import ru.andvl.chatter.koog.model.codeqa.SessionValidationResult

private val logger = LoggerFactory.getLogger("codeqa-question-analysis")
private val json = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Subgraph: Question Analysis
 *
 * Purpose: Analyze user's question to extract intent and search parameters
 *
 * Flow:
 * 1. Analyze question intent using LLM (no tools, JSON response only)
 *
 * Input: SessionValidationResult
 * Output: QuestionAnalysisResult (contains intent, search query, keywords)
 */
internal suspend fun AIAgentGraphStrategyBuilder<*, *>.subgraphQuestionAnalysis(
    model: LLModel
): AIAgentSubgraphDelegate<SessionValidationResult, QuestionAnalysisResult> =
    subgraph(name = "question-analysis") {
        val nodeAnalyzeQuestionIntent by nodeAnalyzeQuestionIntent(model)

        edge(nodeStart forwardTo nodeAnalyzeQuestionIntent)
        edge(nodeAnalyzeQuestionIntent forwardTo nodeFinish)
    }

/**
 * Node: Analyze Question Intent
 *
 * Uses LLM to analyze the user's question and extract:
 * - Intent type (CODE_LOOKUP, EXPLANATION, MODIFICATION, GENERAL)
 * - Search query for code search
 * - Keywords for better search results
 * - Whether code search is required
 *
 * This is a text-only LLM node that returns JSON (no tool calls).
 */
private fun AIAgentSubgraphBuilderBase<SessionValidationResult, QuestionAnalysisResult>.nodeAnalyzeQuestionIntent(
    model: LLModel
) = node<SessionValidationResult, QuestionAnalysisResult>("analyze-question-intent") { validationResult ->
    val question = storage.get(questionKey)!!
    val repositoryName = validationResult.repositoryName ?: "Unknown"
    val conversationHistory = storage.get(conversationHistoryKey) ?: emptyList()

    logger.info("Analyzing question intent for: $question")

    // Build conversation context from history
    val historyContext = if (conversationHistory.isNotEmpty()) {
        "\n**Previous Conversation**:\n" + conversationHistory.takeLast(5).joinToString("\n") { msg ->
            "${msg.role.name}: ${msg.content}"
        }
    } else {
        ""
    }

    val responseContent = llm.writeSession {
        appendPrompt {
            system(
                """
You are a code question analysis expert. Your task is to analyze a user's question about a code repository and extract structured information.

**Repository**: $repositoryName
**User Question**: $question
$historyContext

**Your Task**:
Analyze the question and provide a structured response with:
1. **intent**: The type of question (CODE_LOOKUP, EXPLANATION, MODIFICATION, or GENERAL)
   - CODE_LOOKUP: User wants to find specific code or files
   - EXPLANATION: User wants to understand how something works
   - MODIFICATION: User wants to modify code (suggest using code modification agent)
   - GENERAL: General question about the repository

2. **search_query**: An optimized search query to find relevant code (remove filler words, focus on technical terms)

3. **keywords**: List of 3-5 important keywords from the question (e.g., function names, class names, file types)

4. **requires_code_search**: Boolean - whether we need to search the codebase

5. **suggested_files**: List of file patterns that might be relevant (e.g., ["*.kt", "build.gradle.kts"])

6. **analysis_explanation**: Brief explanation of your analysis

**Important**:
- DO NOT call any tools, just provide the JSON response
- Respond with ONLY a JSON object, nothing else
- Use the exact field names shown below

**Output Format**:
```json
{
  "intent": "CODE_LOOKUP",
  "search_query": "authentication login function",
  "keywords": ["auth", "login", "user", "session"],
  "requires_code_search": true,
  "suggested_files": ["*.kt", "*.java"],
  "analysis_explanation": "User wants to find authentication code"
}
```
                """.trimIndent()
            )
        }

        val response = requestLLM()
        response.content
    }

    logger.debug("LLM response: $responseContent")

    // Parse JSON response with error handling
    val analysisResult = try {
        json.decodeFromString<QuestionAnalysisResult>(responseContent)
    } catch (e: Exception) {
        logger.warn("Failed to parse JSON directly, attempting to extract from markdown block")

        // Try to extract JSON from markdown code block
        val jsonMatch = Regex("""```json\s*([\s\S]*?)```""").find(responseContent)
        if (jsonMatch != null) {
            val jsonContent = jsonMatch.groupValues[1].trim()
            logger.debug("Extracted JSON: $jsonContent")
            json.decodeFromString<QuestionAnalysisResult>(jsonContent)
        } else {
            logger.error("Failed to parse LLM response as JSON: $responseContent")

            // Fallback: create a basic analysis result
            QuestionAnalysisResult(
                intent = ru.andvl.chatter.koog.model.codeqa.QuestionIntent.GENERAL,
                searchQuery = question,
                keywords = question.split(" ").take(5),
                requiresCodeSearch = true,
                suggestedFiles = emptyList(),
                analysisExplanation = "Failed to parse LLM response, using fallback analysis"
            )
        }
    }

    logger.info("Question analysis completed - Intent: ${analysisResult.intent}, Requires search: ${analysisResult.requiresCodeSearch}")
    logger.debug("Search query: ${analysisResult.searchQuery}")
    logger.debug("Keywords: ${analysisResult.keywords}")

    // Store analysis result
    storage.set(questionAnalysisResultKey, analysisResult)
    storage.set(questionIntentKey, analysisResult.intent)
    storage.set(searchQueryKey, analysisResult.searchQuery)
    storage.set(keywordsKey, analysisResult.keywords)
    storage.set(requiresCodeSearchKey, analysisResult.requiresCodeSearch)

    analysisResult
}
