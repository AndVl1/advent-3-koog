package ru.andvl.chatter.codeagent.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * File tree view component for displaying repository structure
 *
 * This component parses the structure tree string (multi-line tree format)
 * and displays it as an interactive tree with expand/collapse functionality.
 *
 * @param structureTree Multi-line tree structure string
 * @param expansionState Map of file paths to their expansion state
 * @param onToggleNode Callback when tree node is toggled
 */
@Composable
internal fun FileTreeView(
    structureTree: String,
    expansionState: Map<String, Boolean>,
    onToggleNode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val treeNodes = remember(structureTree) {
        parseTreeStructure(structureTree)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "File Structure",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                treeNodes.forEach { node ->
                    // Only show node if all its parents are expanded
                    if (shouldShowNode(node, treeNodes, expansionState)) {
                        TreeNodeItem(
                            node = node,
                            expansionState = expansionState,
                            onToggleNode = onToggleNode
                        )
                    }
                }
            }
        }
    }
}

/**
 * Data class representing a tree node
 */
private data class TreeNode(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val level: Int,
    val children: List<TreeNode> = emptyList()
)

/**
 * Parse tree structure string into TreeNode hierarchy
 *
 * Expected format (similar to `tree` command output):
 * ```
 * repository/
 * ├── src/
 * │   ├── main/
 * │   │   └── kotlin/
 * │   └── test/
 * ├── build.gradle.kts
 * └── README.md
 * ```
 */
private fun parseTreeStructure(structureTree: String): List<TreeNode> {
    if (structureTree.isBlank()) return emptyList()

    val lines = structureTree.lines().filter { it.isNotBlank() }
    val nodes = mutableListOf<TreeNode>()
    val stack = mutableListOf<Pair<TreeNode, Int>>() // (node, indentLevel)

    lines.forEachIndexed { index, line ->
        // Calculate indentation level by counting tree prefixes
        // Each level adds "│   " (4 chars) or "    " (4 spaces)
        var level = 0
        var remainingLine = line

        // Count how many "│   " or "    " prefixes exist
        while (remainingLine.startsWith("│   ") || remainingLine.startsWith("    ")) {
            level++
            remainingLine = remainingLine.substring(4)
        }

        // Remove tree drawing characters (├──, └──, etc.)
        val name = remainingLine
            .removePrefix("├── ")
            .removePrefix("└── ")
            .trim()

        if (name.isEmpty()) return@forEachIndexed

        val isDirectory = name.endsWith("/")
        val cleanName = name.removeSuffix("/")

        // Build path by traversing up the stack
        val path = buildPath(stack, level, cleanName)

        val node = TreeNode(
            name = cleanName,
            path = path,
            isDirectory = isDirectory,
            level = level
        )

        // Manage stack: pop nodes at deeper or equal levels
        while (stack.isNotEmpty() && stack.last().second >= level) {
            stack.removeLast()
        }

        // Add current node to stack
        stack.add(node to level)

        // Add to result list (we'll display flat list with indentation)
        nodes.add(node)
    }

    return nodes
}

/**
 * Build full path for a node based on stack
 */
private fun buildPath(
    stack: List<Pair<TreeNode, Int>>,
    currentLevel: Int,
    currentName: String
): String {
    val parentNodes = stack.filter { it.second < currentLevel }
    val pathParts = parentNodes.map { it.first.name } + currentName
    return pathParts.joinToString("/")
}

/**
 * Check if a node should be shown based on its parents' expansion state
 */
private fun shouldShowNode(
    node: TreeNode,
    allNodes: List<TreeNode>,
    expansionState: Map<String, Boolean>
): Boolean {
    // Root level nodes are always visible
    if (node.level == 0) return true

    // For nested nodes, check if all parent directories are expanded
    // Build list of parent paths by splitting the node path
    val pathParts = node.path.split("/")

    // Check each parent level
    for (i in 0 until pathParts.size - 1) {
        val parentPath = pathParts.subList(0, i + 1).joinToString("/")

        // Find the parent node
        val parentNode = allNodes.find { it.path == parentPath }

        // If parent is a directory and not expanded, hide this node
        if (parentNode?.isDirectory == true) {
            val isExpanded = expansionState[parentPath] ?: false
            if (!isExpanded) return false
        }
    }

    return true
}

/**
 * Individual tree node item
 */
@Composable
private fun TreeNodeItem(
    node: TreeNode,
    expansionState: Map<String, Boolean>,
    onToggleNode: (String) -> Unit
) {
    val isExpanded = expansionState[node.path] ?: false
    val hasToggle = node.isDirectory

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = hasToggle) {
                if (hasToggle) {
                    onToggleNode(node.path)
                }
            }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Indentation
        Spacer(modifier = Modifier.width((node.level * 20).dp))

        // Expand/collapse icon (only for directories)
        if (hasToggle) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Spacer(modifier = Modifier.width(16.dp))
        }

        // File/folder icon
        Icon(
            imageVector = getFileIcon(node.name, node.isDirectory),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (node.isDirectory) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )

        // File/folder name
        Text(
            text = node.name,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (node.isDirectory) FontWeight.Medium else FontWeight.Normal
        )
    }
}

/**
 * Get icon for file based on extension or directory
 */
private fun getFileIcon(name: String, isDirectory: Boolean): ImageVector {
    return when {
        isDirectory -> Icons.Default.Folder
        name.endsWith(".kt") || name.endsWith(".kts") -> Icons.Default.Code
        name.endsWith(".java") -> Icons.Default.Code
        name.endsWith(".xml") -> Icons.Default.Code
        name.endsWith(".json") -> Icons.Default.DataObject
        name.endsWith(".md") -> Icons.Default.Description
        name.endsWith(".gradle") || name.endsWith(".kts") -> Icons.Default.Build
        name.endsWith(".properties") -> Icons.Default.Settings
        name.endsWith(".txt") -> Icons.Default.Description
        name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".svg") -> Icons.Default.Image
        else -> Icons.Default.Description
    }
}
