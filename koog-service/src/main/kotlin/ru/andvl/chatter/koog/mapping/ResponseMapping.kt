package ru.andvl.chatter.koog.mapping

import ru.andvl.chatter.koog.model.structured.CheckListItem
import ru.andvl.chatter.koog.model.structured.StructuredResponse
import ru.andvl.chatter.shared.models.SharedCheckListItem
import ru.andvl.chatter.shared.models.SharedStructuredResponse

/**
 * Mapper functions for converting between shared and internal StructuredResponse models
 */

/**
 * Convert internal StructuredResponse to shared SharedStructuredResponse
 */
fun StructuredResponse.toSharedResponse(): SharedStructuredResponse {
    return SharedStructuredResponse(
        title = title,
        message = message,
        checkList = checkList.map { it.toSharedCheckListItem() }
    )
}

/**
 * Convert SharedCheckListItem to internal CheckListItem
 */
fun CheckListItem.toSharedCheckListItem(): SharedCheckListItem {
    return SharedCheckListItem(
        point = point,
        resolution = resolution
    )
}

/**
 * Convert SharedCheckListItem to internal CheckListItem
 */
fun SharedCheckListItem.toInternalCheckListItem(): CheckListItem {
    return CheckListItem(
        point = point,
        resolution = resolution
    )
}

/**
 * Convert SharedStructuredResponse to internal StructuredResponse
 */
fun SharedStructuredResponse.toInternalResponse(): StructuredResponse {
    return StructuredResponse(
        title = title,
        message = message,
        checkList = checkList.map { it.toInternalCheckListItem() }
    )
}
