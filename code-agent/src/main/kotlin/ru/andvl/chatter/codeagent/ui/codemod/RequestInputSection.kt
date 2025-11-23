package ru.andvl.chatter.codeagent.ui.codemod

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Request input section for Code Modification
 *
 * Allows user to enter modification request and select file scope.
 *
 * @param modificationRequest Current modification request text
 * @param fileScope Selected files for scope (null = all files)
 * @param canGeneratePlan Whether generate plan button is enabled
 * @param isLoadingPlan Whether plan is being generated
 * @param onRequestChange Callback when request text changes
 * @param onFileScopeChange Callback when file scope changes
 * @param onGeneratePlan Callback when generate plan is clicked
 */
@Composable
fun RequestInputSection(
    modificationRequest: String,
    fileScope: List<String>?,
    canGeneratePlan: Boolean,
    isLoadingPlan: Boolean,
    onRequestChange: (String) -> Unit,
    onFileScopeChange: (List<String>?) -> Unit,
    onGeneratePlan: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Section title
            Text(
                text = "Modification Request",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Request input
            OutlinedTextField(
                value = modificationRequest,
                onValueChange = onRequestChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                label = { Text("Describe the code changes you want to make") },
                placeholder = { Text("Example: Refactor the authentication module to use JWT tokens instead of session-based auth") },
                supportingText = { Text("Be as specific as possible. You can reference files, functions, or specific requirements.") },
                minLines = 5,
                maxLines = 10,
                enabled = !isLoadingPlan
            )

            // File scope selector
            ScopeSelector(
                fileScope = fileScope,
                onFileScopeChange = onFileScopeChange,
                enabled = !isLoadingPlan
            )

            // Generate plan button
            Button(
                onClick = onGeneratePlan,
                modifier = Modifier.fillMaxWidth(),
                enabled = canGeneratePlan
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isLoadingPlan) "Generating Plan..." else "Generate Modification Plan",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
