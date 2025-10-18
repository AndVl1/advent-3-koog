package ru.andvl.chatter.backend.koog.utils

import ai.koog.ktor.KoogAgentsConfig
import ai.koog.ktor.KoogAgentsConfig.TimeoutConfiguration
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.llm.LLModel
import io.ktor.client.*

fun KoogAgentsConfig.LLMConfig.zAI(apiKey: String, configure: ZAIConfig.() -> Unit = {}) {}

public class ZAIConfig {
    /**
     * Specifies the base URL for the Z-AI API used in client requests.
     *
     * This URL serves as the root endpoint for all API interactions with Anthropic services.
     * It can be customized to point to different server environments (e.g., production, staging, or testing).
     * By default, it is set to [AnthropicClientSettings.baseUrl].
     */
    public var baseUrl: String? = null

    /**
     * Maps a specific `LLModel` to its corresponding version string. This configuration is primarily
     * used to associate particular model identifiers with their appropriate versions, allowing the
     * system to select or adjust model behaviors based on these mappings.
     *
     * By default, this property is initialized with a predefined map ([AnthropicClientSettings.modelVersionsMap]),
     * but can be customized to support other mappings depending on the requirements.
     *
     * This property is typically utilized in the configuration of interaction with Anthropic LLM clients
     * to ensure appropriate versioned models are used during LLM execution.
     */
    public var modelVersionsMap: Map<LLModel, String>? = null

    /**
     * Specifies the API version used for requests to the Anthropic API.
     *
     * This variable determines the version of the API that the client interacts with and ensures compatibility
     * with the desired API features and endpoints. It plays a key role in configuring Anthropic API requests
     * and is initialized to the default API version provided by the system.
     *
     * The value can be updated to specify a different version if required for a specific use case.
     */
    public var apiVersion: String? = null

    /**
     * Configures the timeout settings for API requests, connection establishment, and
     * socket operations when interacting with the Anthropic API.
     * This property is used to customize timeout behavior to handle use cases
     * requiring different default durations for network-related operations.
     */
    public var timeoutConfig: ConnectionTimeoutConfig? = null

    /**
     * Represents the HTTP client that is used to perform network operations
     * such as API requests within the AnthropicConfig configuration.
     *
     * This variable serves as the base client for executing HTTP calls, including
     * request preparation, timeout handling, and connection management, utilizing
     * settings specified in the configuration.
     *
     * It can be customized or replaced if an alternative HTTP client
     * is required for specific use cases or integrations.
     */
    public var httpClient: HttpClient = HttpClient()

    /**
     * Configures the timeout values for network requests, connection establishment,
     * and socket operations by applying the provided configuration block.
     *
     * @param configure A lambda function to customize the timeout configuration
     *                  using the provided TimeoutConfiguration instance.
     */
    public fun timeouts(configure: TimeoutConfiguration.() -> Unit) {
        timeoutConfig = with(TimeoutConfiguration()) {
            configure()
            ConnectionTimeoutConfig(
                requestTimeout.inWholeMilliseconds,
                connectTimeout.inWholeMilliseconds,
                socketTimeout.inWholeMilliseconds
            )
        }
    }
}

/**
 * Represents the settings for configuring an Anthropic client, including model mapping, base URL, and API version.
 *
 * @property modelVersionsMap Maps specific `LLModel` instances to their corresponding model version strings.
 * This determines which Anthropic model versions are used for operations.
 * @property baseUrl The base URL for accessing the Anthropic API. Defaults to "https://api.anthropic.com".
 * @property apiVersion The version of the Anthropic API to be used. Defaults to "2023-06-01".
 */
public class AnthropicClientSettings(
    public val modelVersionsMap: Map<LLModel, String> = emptyMap(),
    public val baseUrl: String = "https://api.anthropic.com",
    public val apiVersion: String = "2023-06-01",
    public val timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig()
)