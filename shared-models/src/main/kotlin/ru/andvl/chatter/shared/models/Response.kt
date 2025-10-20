package ru.andvl.chatter.shared.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Shared StructuredResponse for external communication
 * This is the version that goes over the wire between client and server
 */
@Serializable
data class SharedStructuredResponse(
    val title: String,
    val message: String,
    @SerialName("checkList")
    val checkList: List<SharedCheckListItem> = emptyList()
)

@Serializable
@SerialName("ChecklistItem")
data class SharedCheckListItem(
    @SerialName("point")
    val point: String,
    @SerialName("resolution")
    val resolution: String?,
)