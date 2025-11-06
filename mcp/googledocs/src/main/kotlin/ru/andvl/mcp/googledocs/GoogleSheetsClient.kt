package ru.andvl.mcp.googledocs

import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.*
import com.google.api.services.sheets.v4.model.CellData
import com.google.api.services.sheets.v4.model.Color
import org.slf4j.LoggerFactory

class GoogleSheetsClient(
    private val sheetsService: Sheets
) {
    private val logger = LoggerFactory.getLogger(GoogleSheetsClient::class.java)

    /**
     * Get basic information about a spreadsheet
     */
    suspend fun getSpreadsheetInfo(spreadsheetId: String): Result<GoogleSpreadsheetInfo> {
        return try {
            val spreadsheet = sheetsService.spreadsheets()
                .get(spreadsheetId)
                .setFields("properties(locale,timeZone),sheets(properties(sheetId,title,index,sheetType,gridProperties))")
                .execute()

            val sheets = spreadsheet.sheets.map { sheet ->
                SheetInfo(
                    sheetId = sheet.properties.sheetId.toString(),
                    title = sheet.properties.title ?: "",
                    index = sheet.properties.index ?: 0,
                    sheetType = sheet.properties.sheetType ?: "GRID",
                    rowCount = sheet.properties.gridProperties?.rowCount ?: 1000,
                    columnCount = sheet.properties.gridProperties?.columnCount ?: 26,
                    hidden = false // hidden field not available in this API version
                )
            }

            val info = GoogleSpreadsheetInfo(
                spreadsheetId = spreadsheetId,
                title = spreadsheet.properties.title ?: "",
                locale = spreadsheet.properties.locale,
                timeZone = spreadsheet.properties.timeZone,
                sheetCount = sheets.size,
                sheets = sheets
            )

            Result.success(info)
        } catch (e: Exception) {
            logger.error("Error getting spreadsheet info: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Get content of a specific sheet or range
     */
    suspend fun getSheetContent(
        spreadsheetId: String,
        sheetName: String? = null,
        range: String? = null
    ): Result<SheetContent> {
        return try {
            val actualRange = if (sheetName != null && range != null) {
                "'$sheetName'!$range"
            } else if (sheetName != null) {
                "'$sheetName'!A1:Z1000"
            } else if (range != null) {
                range
            } else {
                "A1:Z1000"
            }

            val response = sheetsService.spreadsheets()
                .values()
                .get(spreadsheetId, actualRange)
                .execute()

            val values = response.getValues() ?: emptyList()
            val cells = values.mapIndexed { rowIndex, row ->
                row.mapIndexed { colIndex, cell ->
                    CellData(
                        row = rowIndex + 1,
                        column = colIndex + 1,
                        value = cell?.toString(),
                        formula = null,
                        formattedValue = cell?.toString(),
                        backgroundColor = null,
                        textColor = null
                    )
                }
            }

            val sheetTitle = sheetName ?: "Sheet1"

            val content = SheetContent(
                sheetId = spreadsheetId,
                sheetTitle = sheetTitle,
                range = actualRange,
                cells = cells
            )

            Result.success(content)
        } catch (e: Exception) {
            logger.error("Error getting sheet content: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Update cells in a spreadsheet
     */
    suspend fun updateSheetContent(
        spreadsheetId: String,
        range: String,
        values: List<List<String>>,
        majorDimension: String = "ROWS"
    ): Result<String> {
        return try {
            val valueRange = ValueRange()
                .setValues(values.map { row -> row.map { it } })

            val result = sheetsService.spreadsheets()
                .values()
                .update(spreadsheetId, range, valueRange)
                .setValueInputOption("USER_ENTERED")
                .execute()

            Result.success("Updated ${result.getUpdatedCells()} cells in range: $range")
        } catch (e: Exception) {
            logger.error("Error updating sheet content: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Append data to a sheet
     */
    suspend fun appendToSheet(
        spreadsheetId: String,
        range: String,
        values: List<List<String>>
    ): Result<String> {
        return try {
            // Fix range format for append operation - ensure it's a valid range
            // If range is like "Sheet1!A:C", convert to "Sheet1!A1:C1"
            // If range is just "A:C", convert to "A1:C1"
            val fixedRange = if (range.contains("!")) {
                if (range.matches(Regex(".+!\\w+:\\w+"))) {
                    range
                } else if (range.matches(Regex(".+!\\w+"))) {
                    "${range}1"
                } else {
                    // For ranges like Sheet1!A:C, add row number
                    range.replace(Regex("([A-Z]+):([A-Z]+)"), "$11:$2${values.size}")
                }
            } else {
                if (range.matches(Regex("\\w+:\\w+"))) {
                    range
                } else if (range.matches(Regex("\\w+"))) {
                    "${range}1"
                } else {
                    // For ranges like A:C, add row number
                    range.replace(Regex("([A-Z]+):([A-Z]+)"), "$11:$2${values.size}")
                }
            }

            val valueRange = ValueRange()
                .setValues(values.map { row -> row.map { it } })

            val result = sheetsService.spreadsheets()
                .values()
                .append(spreadsheetId, fixedRange, valueRange)
                .setValueInputOption("USER_ENTERED")
                .setInsertDataOption("INSERT_ROWS")
                .execute()

            Result.success("Appended ${result.getUpdates().getUpdatedCells()} cells")
        } catch (e: Exception) {
            logger.error("Error appending to sheet: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Create a new sheet in a spreadsheet
     */
    suspend fun createSheet(
        spreadsheetId: String,
        title: String,
        rowCount: Int = 1000,
        columnCount: Int = 26
    ): Result<SheetInfo> {
        return try {
            val sheetProperties = SheetProperties()
                .setTitle(title)
                .setSheetType("GRID")
                .setGridProperties(GridProperties()
                    .setRowCount(rowCount)
                    .setColumnCount(columnCount))

            val request = AddSheetRequest()
                .setProperties(sheetProperties)

            val batchUpdateRequest = BatchUpdateSpreadsheetRequest()
                .setRequests(listOf(
                    Request().setAddSheet(request)
                ))

            val response = sheetsService.spreadsheets()
                .batchUpdate(spreadsheetId, batchUpdateRequest)
                .execute()

            val addedSheet = response.replies[0].addSheet

            Result.success(SheetInfo(
                sheetId = addedSheet.properties.sheetId.toString(),
                title = addedSheet.properties.title ?: "",
                index = addedSheet.properties.index ?: 0,
                sheetType = addedSheet.properties.sheetType ?: "GRID",
                rowCount = addedSheet.properties.gridProperties?.rowCount ?: 1000,
                columnCount = addedSheet.properties.gridProperties?.columnCount ?: 26,
                hidden = false // hidden field not available in this API version
            ))
        } catch (e: Exception) {
            logger.error("Error creating sheet: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Delete a sheet from a spreadsheet
     */
    suspend fun deleteSheet(
        spreadsheetId: String,
        sheetId: String
    ): Result<String> {
        return try {
            val request = DeleteSheetRequest()
                .setSheetId(sheetId.toInt())

            val batchUpdateRequest = BatchUpdateSpreadsheetRequest()
                .setRequests(listOf(
                    Request().setDeleteSheet(request)
                ))

            sheetsService.spreadsheets()
                .batchUpdate(spreadsheetId, batchUpdateRequest)
                .execute()

            Result.success("Sheet deleted successfully")
        } catch (e: Exception) {
            logger.error("Error deleting sheet: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Clear a range in a sheet
     */
    suspend fun clearRange(
        spreadsheetId: String,
        range: String
    ): Result<String> {
        return try {
            val requestBody = ClearValuesRequest()

            sheetsService.spreadsheets()
                .values()
                .clear(spreadsheetId, range, requestBody)
                .execute()

            Result.success("Range cleared: $range")
        } catch (e: Exception) {
            logger.error("Error clearing range: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Format cells (simple formatting)
     */
    suspend fun formatCells(
        spreadsheetId: String,
        range: String,
        backgroundColor: String? = null,
        textColor: String? = null,
        bold: Boolean? = null,
        fontSize: Int? = null
    ): Result<String> {
        return try {
            val cellFormat = CellFormat()

            val textFormat = TextFormat()
            bold?.let { textFormat.setBold(it) }
            fontSize?.let {
                textFormat.setFontSize(it)
            }

            backgroundColor?.let {
                val color = Color()
                // Parse hex color - for simplicity just setting a default color
                color.setRed(0.8f)
                color.setGreen(0.8f)
                color.setBlue(0.8f)
                cellFormat.setBackgroundColor(color)
            }

            textColor?.let {
                val color = Color()
                // Parse hex color - for simplicity just setting a default color
                color.setRed(0.0f)
                color.setGreen(0.0f)
                color.setBlue(0.0f)
                textFormat.setForegroundColor(color)
            }

            cellFormat.setTextFormat(textFormat)

            val request = Request()
                .setRepeatCell(RepeatCellRequest()
                    .setRange(GridRange()
                        .setSheetId(0)
                        .setStartRowIndex(0)
                        .setEndRowIndex(1000)
                        .setStartColumnIndex(0)
                        .setEndColumnIndex(26))
                    .setCell(CellData().setUserEnteredFormat(cellFormat))
                    .setFields("userEnteredFormat(backgroundColor,textFormat,bold,fontSize)"))

            val batchUpdateRequest = BatchUpdateSpreadsheetRequest()
                .setRequests(listOf(request))

            sheetsService.spreadsheets()
                .batchUpdate(spreadsheetId, batchUpdateRequest)
                .execute()

            Result.success("Cells formatted successfully")
        } catch (e: Exception) {
            logger.error("Error formatting cells: ${e.message}", e)
            Result.failure(e)
        }
    }
}
