package ru.andvl.chatter.koog.agents.codeqa.subgraphs

import ai.koog.agents.core.dsl.builder.AIAgentGraphStrategyBuilder
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.prompt.llm.LLModel
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.agents.codeqa.*
import ru.andvl.chatter.koog.model.codeqa.AnswerGenerationResult
import ru.andvl.chatter.koog.model.codeqa.CodeSearchResult

private val logger = LoggerFactory.getLogger("codeqa-answer-generation")
private val json = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Subgraph: Answer Generation
 *
 * Purpose: Generate comprehensive answer based on code search results
 *
 * Flow:
 * 1. Generate answer using LLM with code references (no tools, JSON response)
 *
 * Input: CodeSearchResult
 * Output: AnswerGenerationResult (contains answer, code references with explanations, confidence)
 */
internal suspend fun AIAgentGraphStrategyBuilder<*, *>.subgraphAnswerGeneration(
    model: LLModel
): AIAgentSubgraphDelegate<CodeSearchResult, AnswerGenerationResult> =
    subgraph(name = "answer-generation") {
        val nodeGenerateAnswer by nodeGenerateAnswer(model)

        edge(nodeStart forwardTo nodeGenerateAnswer)
        edge(nodeGenerateAnswer forwardTo nodeFinish)
    }

/**
 * Node: Generate Answer
 *
 * Uses LLM to generate a comprehensive answer based on:
 * - User's question
 * - Conversation history
 * - Code search results
 *
 * Returns structured JSON with answer, code references, and confidence score.
 * This is a text-only LLM node (no tool calls).
 */
private fun AIAgentSubgraphBuilderBase<CodeSearchResult, AnswerGenerationResult>.nodeGenerateAnswer(
    model: LLModel
) = node<CodeSearchResult, AnswerGenerationResult>("generate-answer") { searchResult ->
    val question = storage.get(questionKey)!!
    val repositoryName = storage.get(repositoryNameKey) ?: "Unknown"
    val conversationHistory = storage.get(conversationHistoryKey) ?: emptyList()
    val questionAnalysis = storage.get(questionAnalysisResultKey)!!

    logger.info("Generating answer for question: $question")
    logger.info("Using ${searchResult.references.size} code references")

    // Build code context
    val codeContext = if (searchResult.references.isNotEmpty()) {
        "\n**Code References Found**:\n" + searchResult.references.joinToString("\n\n") { ref ->
            """
File: ${ref.filePath} (lines ${ref.lineStart}-${ref.lineEnd})
Source: ${ref.source}
Relevance: ${ref.relevanceScore}

```
${ref.codeSnippet.take(500)}${if (ref.codeSnippet.length > 500) "\n... (truncated)" else ""}
```
            """.trimIndent()
        }
    } else {
        "\n**No code references found** - provide a general answer based on the question."
    }

    // Build conversation context
    val historyContext = if (conversationHistory.isNotEmpty()) {
        "\n**Previous Conversation**:\n" + conversationHistory.takeLast(3).joinToString("\n") { msg ->
            "${msg.role.name}: ${msg.content.take(200)}${if (msg.content.length > 200) "..." else ""}"
        }
    } else {
        ""
    }

    val responseContent = llm.writeSession {
        appendPrompt {
            system(
                """
You are an expert code assistant helping users understand a codebase.

**Repository**: $repositoryName
**User Question**: $question
**Question Intent**: ${questionAnalysis.intent}
**Search Method**: ${searchResult.searchMethod}
$historyContext
$codeContext

**Your Task**:
Generate a comprehensive answer to the user's question based on the code references provided (if any).

**Guidelines**:
1. **Answer the question directly** - be clear and concise
2. **Reference specific code** - cite file names and line numbers when relevant
3. **Explain the code** - help the user understand what the code does
4. **Provide context** - explain how different parts relate to each other
5. **Suggest follow-ups** - offer related questions the user might want to ask
6. **Calculate confidence**:
   - High (0.8-1.0): Strong code references directly answer the question
   - Medium (0.5-0.79): Some relevant code found but answer is partial
   - Low (0.0-0.49): No code references or uncertain answer

7. **Detect modification intent**: If user seems to want code changes, set "suggests_code_modification" to true

**Important**:
- DO NOT call any tools, just provide the JSON response
- Respond with ONLY a JSON object, nothing else
- If no code was found, still provide a helpful answer explaining this
- Use the exact field names shown below

**Output Format**:
```json
{
  "answer": "Detailed answer to the question...",
  "code_references_with_explanations": [
    {
      "reference": {
        "file_path": "path/to/file.kt",
        "line_start": 10,
        "line_end": 25,
        "code_snippet": "code snippet here",
        "relevance_score": 0.95,
        "source": "RAG"
      },
      "explanation": "This code handles...",
      "relevance_to_question": "This is relevant because..."
    }
  ],
  "confidence": 0.85,
  "follow_up_suggestions": [
    "How does this integrate with the database?",
    "What error handling is implemented?"
  ],
  "suggests_code_modification": false
}
```
                """.trimIndent()
            )
        }

        val response = requestLLM()
        response.content
    }

    logger.debug("LLM response: ${responseContent.take(500)}...")

    // Parse JSON response with error handling
    val answerResult = try {
        json.decodeFromString<AnswerGenerationResult>(responseContent)
    } catch (e: Exception) {
        logger.warn("Failed to parse JSON directly, attempting to extract from markdown block")

        // Try to extract JSON from markdown code block
        val jsonMatch = Regex("""```json\s*([\s\S]*?)```""").find(responseContent)
        if (jsonMatch != null) {
            val jsonContent = jsonMatch.groupValues[1].trim()
            logger.debug("Extracted JSON: ${jsonContent.take(200)}...")
            json.decodeFromString<AnswerGenerationResult>(jsonContent)
        } else {
            logger.error("Failed to parse LLM response as JSON")

            // Fallback: create a basic answer
            AnswerGenerationResult(
                answer = if (searchResult.references.isEmpty()) {
                    "I couldn't find specific code related to your question. The repository may not have been indexed yet, or the question might need to be rephrased. Could you provide more details?"
                } else {
                    "Based on the code I found:\n\n$codeContext\n\n${responseContent.take(500)}"
                },
                codeReferencesWithExplanations = emptyList(),
                confidence = 0.3f,
                followUpSuggestions = emptyList(),
                suggestsCodeModification = false
            )
        }
    }

    logger.info("Answer generation completed")
    logger.info("Confidence: ${answerResult.confidence}")
    logger.info("Code references: ${answerResult.codeReferencesWithExplanations.size}")
    logger.info("Suggests modification: ${answerResult.suggestsCodeModification}")

    // Store answer result
    storage.set(answerGenerationResultKey, answerResult)
    storage.set(finalAnswerKey, answerResult.answer)
    storage.set(confidenceScoreKey, answerResult.confidence)
    storage.set(followUpSuggestionsKey, answerResult.followUpSuggestions)

    answerResult
}
