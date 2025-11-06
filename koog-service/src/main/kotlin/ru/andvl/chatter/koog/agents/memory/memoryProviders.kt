package ru.andvl.chatter.koog.agents.memory

import ai.koog.agents.memory.providers.AgentMemoryProvider
import ai.koog.agents.memory.providers.LocalFileMemoryProvider
import ai.koog.agents.memory.providers.LocalMemoryConfig
import ai.koog.agents.memory.storage.SimpleStorage
import ai.koog.rag.base.files.JVMFileSystemProvider
import kotlin.io.path.Path

internal fun getMemoryProvider(
    root: String = "./memory"
): AgentMemoryProvider {
    return LocalFileMemoryProvider(
        config = LocalMemoryConfig("analysis-collection-agent-memory"),
        storage = SimpleStorage(JVMFileSystemProvider.ReadWrite),
        fs = JVMFileSystemProvider.ReadWrite,
        root = Path(root)
    )
}

internal val githubMemoryProvider = LocalFileMemoryProvider(
    config = LocalMemoryConfig("analysis-collection-agent-memory"),
    storage = SimpleStorage(JVMFileSystemProvider.ReadWrite),
    fs = JVMFileSystemProvider.ReadWrite,
    root = Path("./memory/")
)
