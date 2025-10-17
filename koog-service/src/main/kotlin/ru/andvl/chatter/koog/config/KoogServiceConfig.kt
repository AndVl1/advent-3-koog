package ru.andvl.chatter.koog.config

import io.ktor.server.application.*

/**
 * Simple configuration for Koog service
 * Koog handles model routing internally
 */
object KoogConfig {
    
    /**
     * Setup Koog configuration with API keys from environment
     */
    fun setupKoogWithEnv(application: Application) {
        // Koog is configured in Application.module through install(Koog)
        // API keys are loaded from environment variables
        application.log.info("Koog configured with environment variables")
    }
}