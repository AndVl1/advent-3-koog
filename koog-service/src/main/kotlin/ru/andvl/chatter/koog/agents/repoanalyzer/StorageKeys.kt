package ru.andvl.chatter.koog.agents.repoanalyzer

import ai.koog.agents.core.agent.entity.createStorageKey
import ru.andvl.chatter.koog.model.repoanalyzer.*
import ru.andvl.chatter.shared.models.codeagent.AnalysisType

/**
 * Storage keys for Repository Analyzer Agent
 *
 * These keys are used to share data between nodes and subgraphs
 * during the repository analysis workflow.
 */

// Input validation keys
internal val validatedRequestKey = createStorageKey<ValidatedRequest>("validated-request")
internal val ownerKey = createStorageKey<String>("owner")
internal val repoKey = createStorageKey<String>("repo")
internal val githubUrlKey = createStorageKey<String>("github-url")
internal val enableEmbeddingsKey = createStorageKey<Boolean>("enable-embeddings")
internal val analysisTypeKey = createStorageKey<AnalysisType>("analysis-type")

// Repository setup keys
internal val setupResultKey = createStorageKey<SetupResult>("setup-result")
internal val repositoryPathKey = createStorageKey<String>("repository-path")
internal val defaultBranchKey = createStorageKey<String>("default-branch")
internal val alreadyExistedKey = createStorageKey<Boolean>("already-existed")

// Structure analysis keys
internal val structureResultKey = createStorageKey<StructureResult>("structure-result")
internal val fileTreeKey = createStorageKey<String>("file-tree")
internal val languagesKey = createStorageKey<Map<String, Int>>("languages")
internal val totalFilesKey = createStorageKey<Int>("total-files")
internal val totalLinesKey = createStorageKey<Int>("total-lines")

// Dependency analysis keys
internal val dependencyResultKey = createStorageKey<DependencyResult>("dependency-result")
internal val buildToolsKey = createStorageKey<List<String>>("build-tools")
internal val dependenciesKey = createStorageKey<List<DependencyInfo>>("dependencies")
internal val frameworksKey = createStorageKey<List<String>>("frameworks")

// Summary generation keys
internal val summaryResultKey = createStorageKey<SummaryResult>("summary-result")
internal val summaryKey = createStorageKey<String>("summary")

// Embeddings generation keys
internal val embeddingsResultKey = createStorageKey<EmbeddingsResult>("embeddings-result")
internal val totalChunksKey = createStorageKey<Int>("total-chunks")
internal val filesProcessedKey = createStorageKey<Int>("files-processed")
