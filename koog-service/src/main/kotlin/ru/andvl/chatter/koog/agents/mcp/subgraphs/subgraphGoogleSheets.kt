package ru.andvl.chatter.koog.agents.mcp.subgraphs

import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.dsl.builder.*
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.executeTool
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.message.Message
import org.slf4j.LoggerFactory
import ru.andvl.chatter.koog.agents.mcp.GithubAnalysisNodes
import ru.andvl.chatter.koog.agents.mcp.toolCallsKey
import ru.andvl.chatter.koog.mcp.McpProvider
import ru.andvl.chatter.koog.model.tool.GithubChatRequest
import ru.andvl.chatter.koog.model.tool.ToolChatResponse

/**
 * Information about Google Sheets structure and where to insert data
 */
private data class SheetStructureInfo(
    val spreadsheetId: String,
    val sheetName: String,
    val headers: List<String>,
    val nextRowNumber: Int,
    val columnMapping: Map<String, String> // field name -> column letter (e.g., "Repository" -> "A")
)

private val googleSheetsUrlKey = createStorageKey<String>("google-sheets-url")
private val googleSheetsAnalysisKey = createStorageKey<ToolChatResponse>("google-sheets-analysis")
private val sheetStructureKey = createStorageKey<SheetStructureInfo>("sheet-structure")
private val logger = LoggerFactory.getLogger("google-sheets-subgraph")

internal suspend fun AIAgentGraphStrategyBuilder<GithubChatRequest, ToolChatResponse>.subgraphGoogleSheets():
        AIAgentSubgraphDelegate<ToolChatResponse, ToolChatResponse> =
    subgraph(
        GithubAnalysisNodes.Subgraphs.GOOGLE_SHEETS,
        tools = McpProvider.getGoogleDocsToolsRegistry().tools
    ) {
        val nodeCheckGoogleSheets by nodeCheckGoogleSheets()
        val nodeAnalyzeSheetStructure by nodeAnalyzeSheetStructure()
        val nodeAnalyzeExecuteTool by nodeExecuteTool("analyze-execute-tool")
        val nodeAnalyzeSendToolResult by nodeLLMSendToolResult("analyze-send-tool")
        val nodePopulateSheetData by nodePopulateSheetData()
        val nodePopulateExecuteTool by nodeExecuteTool("populate-execute-tool")
        val nodePopulateSendToolResult by nodeLLMSendToolResult("populate-send-tool")
        val nodeFinalizeResponse by nodeFinalizeResponse()

        // If Google Sheets URL is present, proceed with analysis
        edge(nodeStart forwardTo nodeCheckGoogleSheets)

        // If URL is present, analyze sheet structure
        edge(nodeCheckGoogleSheets forwardTo nodeAnalyzeSheetStructure onCondition {
            storage.get(googleSheetsUrlKey) != null
        })

        // If URL is not present, skip to finish
        edge(nodeCheckGoogleSheets forwardTo nodeFinish onCondition {
            storage.get(googleSheetsUrlKey) == null
        })

        // Phase 1: Sheet structure analysis (cyclic)
        edge(nodeAnalyzeSheetStructure forwardTo nodeAnalyzeExecuteTool onToolCall { true })
        edge(nodeAnalyzeSheetStructure forwardTo nodePopulateSheetData onAssistantMessage { true })
        edge(nodeAnalyzeExecuteTool forwardTo nodeAnalyzeSendToolResult)
        edge(nodeAnalyzeSendToolResult forwardTo nodeAnalyzeExecuteTool onToolCall { true })
        edge(nodeAnalyzeSendToolResult forwardTo nodePopulateSheetData onAssistantMessage { true })

        // Phase 2: Data population (cyclic)
        edge(nodePopulateSheetData forwardTo nodePopulateExecuteTool onToolCall { true })
        edge(nodePopulateSheetData forwardTo nodeFinalizeResponse onAssistantMessage { true })
        edge(nodePopulateExecuteTool forwardTo nodePopulateSendToolResult)
        edge(nodePopulateSendToolResult forwardTo nodePopulateExecuteTool onToolCall { true })
        edge(nodePopulateSendToolResult forwardTo nodeFinalizeResponse onAssistantMessage { true })

        edge(nodeFinalizeResponse forwardTo nodeFinish)
    }

private fun AIAgentSubgraphBuilderBase<ToolChatResponse, ToolChatResponse>.nodeCheckGoogleSheets() =
    node<ToolChatResponse, ToolChatResponse>(GithubAnalysisNodes.GoogleSheets.CHECK_GOOGLE_SHEETS) { analysisResult ->
        // Store analysis result for later use
        storage.set(googleSheetsAnalysisKey, analysisResult)

        // Get Google Sheets URL from the original request stored in storage
        val githubRequest = storage.get(initialGithubRequestKey)
        val googleSheetsUrl = githubRequest?.googleSheetsUrl

        if (!googleSheetsUrl.isNullOrBlank()) {
            logger.info("📊 Google Sheets URL detected: $googleSheetsUrl")
            storage.set(googleSheetsUrlKey, googleSheetsUrl)
        } else {
            logger.info("ℹ️ No Google Sheets URL provided, skipping Google Sheets population")
        }

        analysisResult
    }

private fun AIAgentSubgraphBuilderBase<ToolChatResponse, ToolChatResponse>.nodeAnalyzeSheetStructure() =
    node<ToolChatResponse, Message.Response>(GithubAnalysisNodes.GoogleSheets.ANALYZE_SHEET_STRUCTURE) { analysisResult ->
        val googleSheetsUrl = storage.get(googleSheetsUrlKey) ?: ""

        // Extract spreadsheet ID from URL
        val spreadsheetId = extractSpreadsheetId(googleSheetsUrl)

        if (spreadsheetId == null) {
            logger.error("❌ Invalid Google Sheets URL: $googleSheetsUrl")
            // Return immediately without using LLM
            return@node llm.writeSession {
                appendPrompt {
                    system("Invalid Google Sheets URL provided.")
                    user("Skip Google Sheets analysis.")
                }
                requestLLM()
            }
        }

        logger.info("📋 Phase 1: Analyzing Google Sheets structure with ID: $spreadsheetId")

        llm.writeSession {
            appendPrompt {
                system("""
                    You are a Google Sheets structure analysis expert. Your task is to analyze a Google Sheets spreadsheet and determine what fields need to be filled.

                    **Spreadsheet ID:** $spreadsheetId

                    **Available Google Sheets Tools:**
                    1. get-spreadsheet-info - Get spreadsheet metadata and sheet list
                    2. get-sheet-content - Read values from a range

                    **Your Task (Phase 1 - Structure Analysis):**
                    1. Get spreadsheet info to find available sheets
                    2. Choose the first/main sheet to work with
                    3. Read the header row (typically row 1: "SheetName!A1:Z1") to get column names
                    4. Read a few data rows to understand the data format
                    5. Find the next empty row number (count existing rows + 1)
                    6. Analyze which columns are relevant for repository analysis data:
                       - Repository name/URL
                       - Status/Result
                       - Comments/Description
                       - Date/Timestamp
                       - Score/Grade
                       - Any other relevant fields based on headers

                    **Important:**
                    - Use tools systematically to gather information
                    - Identify all column headers
                    - Determine the next row where data should be inserted
                    - Report your findings clearly

                    After analysis, provide a summary of:
                    - Sheet name
                    - List of column headers found
                    - Next row number to fill
                    - Which columns are relevant for our repository analysis data
                """.trimIndent())

                user("Please analyze the Google Sheets structure and determine what fields we need to fill.")

                model = model.copy(
                    capabilities = listOf(
                        LLMCapability.Temperature,
                        LLMCapability.Completion,
                        LLMCapability.Tools,
                        LLMCapability.OpenAIEndpoint.Completions
                    )
                )
            }

            requestLLM()
        }
    }

private fun AIAgentSubgraphBuilderBase<ToolChatResponse, ToolChatResponse>.nodePopulateSheetData() =
    node<String, Message.Response>(GithubAnalysisNodes.GoogleSheets.POPULATE_SHEET_DATA) { structureAnalysisResponse ->
        val googleSheetsUrl = storage.get(googleSheetsUrlKey) ?: ""
        val spreadsheetId = extractSpreadsheetId(googleSheetsUrl) ?: ""
        val analysisResult = storage.get(googleSheetsAnalysisKey)
            ?: throw IllegalStateException("Analysis result not found")

        logger.info("📋 Phase 2: Populating Google Sheets with analysis data")

        // Prepare analysis summary for the LLM
        val analysisSummary = buildString {
            appendLine("**Repository Analysis Results:**")
            appendLine()
            appendLine("Full review info: ${analysisResult.response}")
            appendLine()
            appendLine("TL;DR: ${analysisResult.shortSummary}")
            appendLine()

            analysisResult.repositoryReview?.let { review ->
                appendLine("**Repository Review:**")
                appendLine("- General Conditions: ${review.generalConditionsReview.commentType}")
                appendLine("  ${review.generalConditionsReview.comment}")

                if (review.constraintsReview.isNotEmpty()) {
                    appendLine()
                    appendLine("**Constraints:**")
                    review.constraintsReview.forEach { constraint ->
                        appendLine("- [${constraint.commentType}] ${constraint.comment}")
                    }
                }

                if (review.advantagesReview.isNotEmpty()) {
                    appendLine()
                    appendLine("**Advantages:**")
                    review.advantagesReview.forEach { advantage ->
                        appendLine("- [${advantage.commentType}] ${advantage.comment}")
                    }
                }

                if (review.attentionPointsReview.isNotEmpty()) {
                    appendLine()
                    appendLine("**Attention Points:**")
                    review.attentionPointsReview.forEach { point ->
                        appendLine("- [${point.commentType}] ${point.comment}")
                    }
                }
            }

            analysisResult.dockerInfo?.let { dockerInfo ->
                appendLine()
                appendLine("**Docker Build:**")
                appendLine("- Status: ${dockerInfo.buildResult.buildStatus}")
                dockerInfo.buildResult.imageSize?.let { appendLine("- Image Size: $it") }
            }

            analysisResult.requirements?.let { req ->
                appendLine()
                appendLine("**Requirements:**")
                appendLine("- General: ${req.generalConditions}")
                if (req.importantConstraints.isNotEmpty()) {
                    appendLine("- Constraints: ${req.importantConstraints.size}")
                }
            }
        }

        llm.writeSession {
            appendPrompt {
                system("""
                    You are a Google Sheets data population expert. Based on the sheet structure analysis from Phase 1, your task is to fill the spreadsheet with repository analysis data.

                    **Spreadsheet ID:** $spreadsheetId

                    **Structure Analysis from Phase 1:**
                    $structureAnalysisResponse

                    **Analysis Data Available:**
                    $analysisSummary

                    **Available Google Sheets Tools:**
                    1. update-sheet-cells(spreadsheetId, range, values, majorDimension) - Update cell values
                       - range: e.g., "Sheet1!A2:E2" for row 2, columns A to E
                       - values: 2D array, e.g., [["value1", "value2", "value3"]]
                       - majorDimension: "ROWS" or "COLUMNS"

                    **Your Task (Phase 2 - Data Population):**
                    1. Based on the structure analysis, map the analysis data to appropriate columns
                    2. Extract relevant information from the analysis summary:
                       - Repository name/URL (if available)
                       - Overall status (OK/PROBLEM/MIXED based on review)
                       - Brief summary (1-2 sentences from TL;DR)
                       - Current timestamp (use current date/time)
                       - Grade/score (if applicable based on requirements)
                       - Any other fields identified in Phase 1
                    3. Use update-sheet-cells to insert a new row with the data
                    4. **PROTECTED CELLS HANDLING:** If update fails with permission/protection error:
                       - The row may have protected cells that cannot be edited
                       - Try the NEXT row (current row + 1)
                       - Continue trying subsequent rows until you find an editable one
                       - Update your target row number accordingly
                       - Maximum 5 attempts to find an available row
                    5. **VALIDATION REQUIRED:** After updating cells, use get-sheet-content to read back the inserted row and verify that:
                       - The data was successfully written
                       - All expected fields are present
                       - Values match what you intended to write
                    6. Keep values concise (1-2 sentences max per cell)
                    7. Match the language (Russian/English) based on the analysis

                    **Important:**
                    - Only fill columns that were identified in Phase 1 structure analysis
                    - Use the correct row number from Phase 1 as starting point
                    - If cells are protected, automatically try next available rows
                    - After writing data, ALWAYS validate it was written correctly
                    - Provide a summary of what was added, which row was used, and validation status after completion

                    Begin populating the Google Sheets now.
                """.trimIndent())

                user("Please populate the Google Sheets with the repository analysis data based on the structure analysis.")

                model = model.copy(
                    capabilities = listOf(
                        LLMCapability.Temperature,
                        LLMCapability.Completion,
                        LLMCapability.Tools,
                        LLMCapability.OpenAIEndpoint.Completions
                    )
                )
            }

            requestLLM()
        }
    }

private fun AIAgentSubgraphBuilderBase<ToolChatResponse, ToolChatResponse>.nodeFinalizeResponse() =
    node<String, ToolChatResponse>(GithubAnalysisNodes.GoogleSheets.FINALIZE_GOOGLE_SHEETS) { llmResponse ->
        val toolCalls = storage.get(toolCallsKey).orEmpty()
        logger.info("✅ Google Sheets population completed")
        logger.info("LLM Response: ${llmResponse.take(200)}...")

        // Return the original analysis result unchanged
        // The Google Sheets population is a side effect
        val originalAnalysis = storage.get(googleSheetsAnalysisKey)
            ?: throw IllegalStateException("Google Sheets analysis result not found in storage")

        originalAnalysis.copy(
            toolCalls = toolCalls,
        )
    }

private fun AIAgentSubgraphBuilderBase<*, *>.nodeExecuteTool(
    name: String? = null
): AIAgentNodeDelegate<Message.Tool.Call, ReceivedToolResult> =
    node(name ?: "google-sheets-execute-tool") { toolCall ->
        val currentCalls = storage.get(toolCallsKey) ?: emptyList()
        storage.set(toolCallsKey, currentCalls + "${toolCall.tool} ${toolCall.content}")

        logger.info("🔧 Executing Google Sheets tool: ${toolCall.tool}")
        environment.executeTool(toolCall)
    }

private fun extractSpreadsheetId(url: String): String? {
    // Extract spreadsheet ID from Google Sheets URL
    val patterns = listOf(
        Regex("""/spreadsheets/d/([a-zA-Z0-9-_]+)"""),
        Regex("""id=([a-zA-Z0-9-_]+)""")
    )

    return patterns.firstNotNullOfOrNull { pattern ->
        pattern.find(url)?.groupValues?.get(1)
    }
}
