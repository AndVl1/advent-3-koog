package ru.andvl.chatter.koog.mcp

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.tools
import ai.koog.agents.mcp.McpToolRegistryProvider
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import ru.andvl.chatter.koog.tools.CurrentTimeToolSet
import ru.andvl.chatter.koog.tools.DockerToolSet
import ru.andvl.chatter.koog.tools.RagToolSet
import java.io.File
import kotlin.system.exitProcess

object McpProvider {

    private var googleDocsClient: Client? = null
    private val googleDocsMutex = Mutex()

    suspend fun getGoogleDocsClient(): Client {
        return googleDocsClient ?: googleDocsMutex.withLock {
            googleDocsClient ?: createGoogleDocsClient().also {
                googleDocsClient = it
            }
        }
    }

    //////////////////////////////////

    private var githubClient: Client? = null
    private val githubMutex = Mutex()

    suspend fun getGithubToolsRegistry(): ToolRegistry {
        return McpToolRegistryProvider.fromClient(getGithubClient())
    }

    suspend fun getGithubToolsDescriptors(): List<ToolDescriptor> {
        return getGithubToolsRegistry()
            .tools
            .map { it.descriptor }
    }

    suspend fun getGithubClient(): Client {
        return githubClient ?: githubMutex.withLock {
            if (githubClient != null) {
                githubClient!!
            } else {
                createGithubClient()
                    .also { githubClient = it }
            }
        }
    }

    suspend fun getGoogleDocsToolsRegistry(): ToolRegistry {
        return McpToolRegistryProvider.fromClient(getGoogleDocsClient())
    }

    suspend fun getGoogleDocsToolsDescriptors(): List<ToolDescriptor> {
        return getGoogleDocsToolsRegistry()
            .tools
            .map { it.descriptor }
    }

    fun getDockerToolsRegistry(): ToolRegistry {
        return ToolRegistry {
            tools(DockerToolSet())
        }
    }

    fun getDockerToolsDescriptors(): List<ToolDescriptor> {
        return getDockerToolsRegistry()
            .tools
            .map { it.descriptor }
    }

    fun getUtilsToolsRegistry(): ToolRegistry {
        return ToolRegistry {
            tools(CurrentTimeToolSet())
        }
    }

    fun getUtilsToolsDescriptors(): List<ToolDescriptor> {
        return getUtilsToolsRegistry()
            .tools
            .map { it.descriptor }
    }

    internal fun getRagToolsRegistry(): ToolRegistry {
        return ToolRegistry {
            tools(RagToolSet())
        }
    }

    internal fun getRagToolsDescriptors(): List<ToolDescriptor> {
        return getRagToolsRegistry()
            .tools
            .map { it.descriptor }
    }

    private suspend fun createGoogleDocsClient(): Client {
        val jarPath = "mcp/googledocs/build/libs/googledocs-0.1.0.jar"
        val jarFile = File(jarPath)
        if (!jarFile.exists()) {
            println("❌ JAR файл не найден: $jarPath")
            println("Абсолютный путь: ${jarFile.absolutePath}")
            println("Текущая директория: ${File(".").absolutePath}")
            println("💡 Запустите: ./gradlew :mcp:github:build")
            exitProcess(1)
        }

        println("✅ JAR файл найден: $jarPath")

        println("🚀 Запуск Kotlin MCP сервера...")
        val process = ProcessBuilder("java", "-jar", jarFile.absolutePath)
            .redirectErrorStream(false) // Разделяем stdout и stderr
            .start()

        // Даем серверу время на запуск
        println("⏳ Ожидание запуска сервера...")
        delay(2000)

        // Проверяем, что процесс еще работает
        if (!process.isAlive) {
            println("❌ Процесс сервера завершился с кодом: ${process.exitValue()}")
            val errorOutput = process.errorStream.bufferedReader().readText()
            val stdOutput = process.inputStream.bufferedReader().readText()
            println("Stderr: $errorOutput")
            println("Stdout: $stdOutput")
            exitProcess(1)
        } else {
            // Читаем stderr для диагностики (неблокирующе)
            if (process.errorStream.available() > 0) {
                val errorOutput = process.errorStream.bufferedReader().readLine()
                println("📋 Server log: $errorOutput")
            }
        }

        println("📦 Подключение к MCP серверу...")
        val transport = StdioClientTransport(
            input = process.inputStream.asSource().buffered(),
            output = process.outputStream.asSink().buffered()
        )

        val client = Client(
            clientInfo = Implementation(name = "kotlin-googledocs-test-client", version = "1.0.0"),
        )

        try {
            println("🔌 Подключение к серверу...")
            client.connect(transport)
            println("✅ Подключено успешно!")

            // Give a moment for initialization
            delay(1000)
        } catch (e: Exception) {
            println("❌ Ошибка подключения: ${e.message}")
            e.printStackTrace()
            process.destroy()
            exitProcess(1)
        }

        return client
    }

    private suspend fun createGithubClient(): Client {
        val jarPath = "mcp/github/build/libs/github-0.1.0.jar"

        // Проверяем существование JAR файла
        val jarFile = File(jarPath)
        if (!jarFile.exists()) {
            println("❌ JAR файл не найден: $jarPath")
            println("Абсолютный путь: ${jarFile.absolutePath}")
            println("Текущая директория: ${File(".").absolutePath}")
            println("💡 Запустите: ./gradlew :mcp:github:build")
            exitProcess(1)
        }

        println("✅ JAR файл найден: $jarPath")

        println("🚀 Запуск Kotlin MCP сервера...")
        val process = ProcessBuilder("java", "-jar", jarFile.absolutePath)
            .redirectErrorStream(false) // Разделяем stdout и stderr
            .start()

        // Даем серверу время на запуск
        println("⏳ Ожидание запуска сервера...")
        delay(2000)

        // Проверяем, что процесс еще работает
        if (!process.isAlive) {
            println("❌ Процесс сервера завершился с кодом: ${process.exitValue()}")
            val errorOutput = process.errorStream.bufferedReader().readText()
            val stdOutput = process.inputStream.bufferedReader().readText()
            println("Stderr: $errorOutput")
            println("Stdout: $stdOutput")
            exitProcess(1)
        } else {
            // Читаем stderr для диагностики (неблокирующе)
            if (process.errorStream.available() > 0) {
                val errorOutput = process.errorStream.bufferedReader().readLine()
                println("📋 Server log: $errorOutput")
            }
        }

        println("📦 Подключение к MCP серверу...")
        val transport = StdioClientTransport(
            input = process.inputStream.asSource().buffered(),
            output = process.outputStream.asSink().buffered()
        )

        val client = Client(
            clientInfo = Implementation(name = "kotlin-github-test-client", version = "1.0.0"),
        )

        client.connect(transport)

        return client
    }
}
