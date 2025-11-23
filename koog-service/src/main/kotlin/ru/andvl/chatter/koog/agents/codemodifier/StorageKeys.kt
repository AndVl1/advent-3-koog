package ru.andvl.chatter.koog.agents.codemodifier

import ai.koog.agents.core.agent.entity.createStorageKey
import ru.andvl.chatter.koog.model.codemodifier.*

/**
 * Storage keys for Code Modifier Agent
 *
 * These keys are used to share data between nodes and subgraphs
 * during the code modification workflow.
 */

// Request validation keys
internal val sessionIdKey = createStorageKey<String>("session-id")
internal val sessionPathKey = createStorageKey<String>("session-path")
internal val instructionsKey = createStorageKey<String>("instructions")
internal val normalizedFileScopeKey = createStorageKey<List<String>>("normalized-file-scope")
internal val enableValidationKey = createStorageKey<Boolean>("enable-validation")
internal val maxChangesKey = createStorageKey<Int>("max-changes")
internal val validationResultKey = createStorageKey<ValidationResult>("validation-result")

// Code analysis keys
internal val relevantFilesKey = createStorageKey<List<String>>("relevant-files")
internal val fileContextsKey = createStorageKey<List<FileContext>>("file-contexts")
internal val detectedPatternsKey = createStorageKey<CodePatterns>("detected-patterns")
internal val codeAnalysisResultKey = createStorageKey<CodeAnalysisResult>("code-analysis-result")

// Modification planning keys
internal val modificationPlanKey = createStorageKey<ModificationPlan>("modification-plan")
internal val proposedChangesKey = createStorageKey<List<ProposedChange>>("proposed-changes")
internal val planningResultKey = createStorageKey<PlanningResult>("planning-result")

// Validation keys
internal val syntaxValidKey = createStorageKey<Boolean>("syntax-valid")
internal val breakingChangesKey = createStorageKey<List<String>>("breaking-changes")
internal val validationCheckResultKey = createStorageKey<ValidationCheckResult>("validation-check-result")
internal val validationRetryCountKey = createStorageKey<Int>("validation-retry-count")

// Response building keys
internal val finalResultKey = createStorageKey<CodeModificationResult>("final-result")

// Docker validation keys
internal val dockerValidationEnabledKey = createStorageKey<Boolean>("docker-validation-enabled")
internal val dockerAvailableKey = createStorageKey<Boolean>("docker-available")
internal val dockerValidationResultKey = createStorageKey<DockerValidationResult>("docker-validation-result")
internal val validationImageNameKey = createStorageKey<String>("validation-image-name")
internal val validationDirectoryKey = createStorageKey<String>("validation-directory")
internal val detectedProjectTypeKey = createStorageKey<ProjectType>("detected-project-type")
