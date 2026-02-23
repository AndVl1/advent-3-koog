package ru.andvl.chatter.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.andvl.chatter.shared.models.github.RequirementReviewCommentDto

@Composable
internal fun ReviewCommentCard(comment: RequirementReviewCommentDto) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when (comment.commentType) {
                "PROBLEM" -> MaterialTheme.colorScheme.errorContainer
                "ADVANTAGE" -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = comment.commentType,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (comment.commentType) {
                        "PROBLEM" -> MaterialTheme.colorScheme.onErrorContainer
                        "ADVANTAGE" -> MaterialTheme.colorScheme.onTertiaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                comment.fileReference?.let {
                    Text(text = it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Text(text = comment.comment, style = MaterialTheme.typography.bodySmall)
            comment.codeQuote?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
                }
            }
        }
    }
}
