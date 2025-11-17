package ru.andvl.chatter.desktop.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.andvl.chatter.shared.models.github.AnalysisEvent

/**
 * Display analysis progress with current event and progress bar
 */
@Composable
fun AnalysisProgressView(
    currentEvent: AnalysisEvent?,
    progress: Int,
    recentEvents: List<AnalysisEvent>,
    modifier: Modifier = Modifier,
    currentStep: Int = 0,
    totalSteps: Int = 6,
    currentStepName: String = ""
) {
    // Анимация для плавного изменения прогресса
    val animatedProgress by animateFloatAsState(
        targetValue = progress / 100f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "progress"
    )

    // Анимация для номера шага
    val animatedStep by animateIntAsState(
        targetValue = currentStep,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "step"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Progress bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            // Отображение шага
            if (currentStep > 0 && currentStepName.isNotEmpty()) {
                Text(
                    text = "Шаг $animatedStep из $totalSteps: $currentStepName",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (currentStep == 0) {
                Text(
                    text = "Инициализация...",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Current event with animation
            AnimatedVisibility(
                visible = currentEvent != null,
                enter = fadeIn(animationSpec = tween(300)) +
                        slideInVertically(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(300)) +
                       slideOutVertically(animationSpec = tween(300))
            ) {
                currentEvent?.let { event ->
                    CurrentEventDisplay(event)
                }
            }

            // Recent events history with animation
            AnimatedVisibility(
                visible = recentEvents.isNotEmpty(),
                enter = fadeIn(animationSpec = tween(400)) + expandVertically(),
                exit = fadeOut(animationSpec = tween(400)) + shrinkVertically()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider()
                    Text(
                        text = "Недавние действия:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // LazyColumn с анимацией и авто-скроллом
                    val listState = rememberLazyListState()
                    val eventsToShow = recentEvents.takeLast(5).reversed()

                    // Авто-скролл к последнему добавленному элементу
                    LaunchedEffect(recentEvents.size) {
                        if (eventsToShow.isNotEmpty()) {
                            listState.animateScrollToItem(0)
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.heightIn(max = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(
                            items = eventsToShow,
                            key = { event -> event.id }
                        ) { event ->
                            AnimatedEventItemLazy(event = event)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Display the current event being processed
 */
@Composable
private fun CurrentEventDisplay(event: AnalysisEvent) {
    // Пульсирующая анимация для иконки
    val infiniteTransition = rememberInfiniteTransition(label = "iconPulse")
    val iconAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconAlpha"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
    ) {
        Icon(
            imageVector = getEventIcon(event),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = iconAlpha),
            modifier = Modifier.size(24.dp)
        )

        Text(
            text = getEventDisplayText(event),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Event item in LazyColumn with animateItem() modifier
 */
@Composable
private fun LazyItemScope.AnimatedEventItemLazy(
    event: AnalysisEvent
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .animateItem(
                fadeInSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                fadeOutSpec = tween(durationMillis = 200),
                placementSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
    ) {
        Icon(
            imageVector = getEventIcon(event),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )

        Text(
            text = getEventDisplayText(event),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Get icon for event type
 */
private fun getEventIcon(event: AnalysisEvent): ImageVector {
    return when (event) {
        is AnalysisEvent.Started -> Icons.Default.PlayArrow
        is AnalysisEvent.StageUpdate -> Icons.Default.Schedule
        is AnalysisEvent.ToolExecution -> Icons.Default.Build
        is AnalysisEvent.NodeStarted -> Icons.Default.PlayArrow
        is AnalysisEvent.NodeCompleted -> Icons.Default.CheckCircle
        is AnalysisEvent.RAGIndexing -> Icons.Default.Storage
        is AnalysisEvent.LLMStreamChunk -> Icons.AutoMirrored.Filled.Chat
        is AnalysisEvent.Error -> Icons.Default.Error
        is AnalysisEvent.Completed -> Icons.Default.Done
        is AnalysisEvent.Progress -> Icons.AutoMirrored.Filled.TrendingUp
    }
}

/**
 * Get display text for event
 */
private fun getEventDisplayText(event: AnalysisEvent): String {
    return when (event) {
        is AnalysisEvent.Started -> event.message
        is AnalysisEvent.StageUpdate -> event.description
        is AnalysisEvent.ToolExecution -> "Инструмент: ${event.description}"
        is AnalysisEvent.NodeStarted -> {
            // Показываем описание, если есть, иначе сокращенное имя узла
            event.description ?: formatNodeName(event.nodeName)
        }
        is AnalysisEvent.NodeCompleted -> {
            // Для завершенных узлов показываем только если есть длительность
            if (event.durationMs != null) {
                "✓ ${formatNodeName(event.nodeName)} (${event.durationMs}ms)"
            } else {
                "✓ ${formatNodeName(event.nodeName)}"
            }
        }
        is AnalysisEvent.RAGIndexing -> {
            if (event.isComplete) {
                "RAG: Индексировано ${event.filesIndexed} файлов, ${event.totalChunks} чанков"
            } else {
                "RAG: ${event.filesIndexed} файлов, ${event.totalChunks} чанков..."
            }
        }
        is AnalysisEvent.LLMStreamChunk -> {
            val preview = event.content.take(50)
            if (event.isComplete) "LLM: $preview" else "LLM: $preview..."
        }
        is AnalysisEvent.Error -> "Ошибка: ${event.message}"
        is AnalysisEvent.Completed -> event.message
        is AnalysisEvent.Progress -> {
            if (event.currentStep > 0) {
                "Шаг ${event.currentStep}/${event.totalSteps}: ${event.stepName}"
            } else {
                event.stepName
            }
        }
    }
}

/**
 * Format node name for display (shorten technical names)
 */
private fun formatNodeName(nodeName: String): String {
    return when {
        // Убираем технические префиксы и делаем более читаемыми
        nodeName.startsWith("github-") -> nodeName.removePrefix("github-").replace("-", " ").capitalize()
        nodeName.startsWith("docker-") -> nodeName.removePrefix("docker-").replace("-", " ").capitalize()
        nodeName.contains("-") -> nodeName.split("-").joinToString(" ") { it.capitalize() }
        else -> nodeName.capitalize()
    }
}

private fun String.capitalize(): String {
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
