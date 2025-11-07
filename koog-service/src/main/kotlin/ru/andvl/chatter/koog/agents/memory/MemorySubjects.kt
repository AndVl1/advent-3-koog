package ru.andvl.chatter.koog.agents.memory

import ai.koog.agents.memory.model.MemorySubject

internal object MemorySubjects {

    data object DockerAvailability : MemorySubject() {
        override val name: String = "docker-availability"
        override val promptDescription: String = "Docker availability status"
        override val priorityLevel: Int = 1

    }

    data object HomeworkRequirements : MemorySubject() {
        override val name: String = "homework_requirements"
        override val promptDescription: String = "Requirements for homework assignments including general conditions, constraints, advantages, and attention points"
        override val priorityLevel: Int = 1
    }

    data object GithubRepositoryAnalysis : MemorySubject() {
        override val name: String = "github_repository_analysis"
        override val promptDescription: String = "GitHub repository analysis results including structure, dependencies, code quality, and review findings"
        override val priorityLevel: Int = 1
    }
}
