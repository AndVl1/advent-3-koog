package ru.andvl.mcp.googledocs

import kotlinx.serialization.Serializable

@Serializable
data class GoogleSpreadsheetInfo(
    val spreadsheetId: String,
    val title: String,
    val locale: String?,
    val timeZone: String?,
    val sheetCount: Int,
    val sheets: List<SheetInfo>
)

@Serializable
data class SheetInfo(
    val sheetId: String,
    val title: String,
    val index: Int,
    val sheetType: String,
    val rowCount: Int,
    val columnCount: Int,
    val hidden: Boolean
)

@Serializable
data class CellData(
    val row: Int,
    val column: Int,
    val value: String?,
    val formula: String?,
    val formattedValue: String?,
    val backgroundColor: String?,
    val textColor: String?
)

@Serializable
data class SheetContent(
    val sheetId: String,
    val sheetTitle: String,
    val range: String,
    val cells: List<List<CellData>>
)

@Serializable
data class UpdateRequest(
    val sheetId: String,
    val range: String,
    val values: List<List<String>>,
    val majorDimension: String = "ROWS"
)