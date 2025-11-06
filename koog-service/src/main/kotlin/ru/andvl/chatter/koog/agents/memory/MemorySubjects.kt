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
}
