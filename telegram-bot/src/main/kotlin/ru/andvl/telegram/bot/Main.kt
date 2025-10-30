package ru.andvl.telegram.bot

import io.github.cdimascio.dotenv.dotenv
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import ru.andvl.telegram.bot.model.BotConfig
import ru.andvl.telegram.bot.service.AnalysisServerClient
import ru.andvl.telegram.bot.service.DailyReportScheduler
import ru.andvl.telegram.bot.service.TelegramBotService
import kotlin.system.exitProcess

/**
 * Главный класс Telegram бота для ежедневных отчетов
 */
class DailyReportBot(private val config: BotConfig) {
    private val logger = LoggerFactory.getLogger(DailyReportBot::class.java)

    private val analysisClient = AnalysisServerClient(config.analysisServerUrl)
    private val telegramBot = TelegramBotService(config)
    private val scheduler = DailyReportScheduler(config, analysisClient, telegramBot)

    /**
     * Запустить бота
     */
    fun start(testMode: Boolean = false) {
        logger.info("Starting Telegram Daily Report Bot")
        logger.info("Target repository: ${config.targetRepository}")
        logger.info("Analysis server: ${config.analysisServerUrl}")
        logger.info("Daily report time: ${config.dailyReportTime}")

        if (!telegramBot.validateConfig()) {
            logger.error("Invalid bot configuration")
            exitProcess(1)
        }

        // Проверяем подключение к Telegram API
        runBlocking {
            if (!telegramBot.checkTelegramConnection()) {
                logger.error("Cannot connect to Telegram API. Check your internet connection and try again.")
                logger.info("Possible solutions:")
                logger.info("1. Check internet connection")
                logger.info("2. Verify DNS settings")
                logger.info("3. Try using a VPN or proxy if Telegram is blocked")
                exitProcess(1)
            }
        }

        if (testMode) {
            logger.info("Running in test mode - sending immediate report")
            runBlocking {
                try {
                    scheduler.runImmediateReport()
                    logger.info("Test report completed")
                } catch (e: Exception) {
                    logger.error("Test report failed: ${e.message}", e)
                    exitProcess(1)
                }
            }
        } else {
            scheduler.start()

            // Регистрируем shutdown hook для корректного завершения
            Runtime.getRuntime().addShutdownHook(Thread {
                logger.info("Shutting down Telegram bot...")
                scheduler.stop()
                analysisClient.close()
                telegramBot.shutdown()
            })

            logger.info("Bot started successfully. Press Ctrl+C to stop.")

            // Ожидаем завершения программы
            try {
                Thread.currentThread().join()
            } catch (e: InterruptedException) {
                logger.info("Bot interrupted")
            }
        }
    }
}

/**
 * Точка входа в приложение
 */
fun main(args: Array<String>) {
    val logger = LoggerFactory.getLogger("Main")

    try {
        // Загружаем конфигурацию из .env файла
        val dotenv = dotenv {
            ignoreIfMalformed = true
            ignoreIfMissing = true
        }

        val config = BotConfig(
            telegramBotToken = dotenv["TELEGRAM_BOT_TOKEN"] ?: "",
            adminUserId = dotenv["TELEGRAM_ADMIN_USER_ID"] ?: "",
            analysisServerUrl = dotenv["ANALYSIS_SERVER_URL"] ?: "http://localhost:8081",
            targetRepository = dotenv["TARGET_REPOSITORY"] ?: "jetbrains/koog",
            dailyReportTime = dotenv["DAILY_REPORT_TIME"] ?: "09:00"
        )

        // Проверяем обязательные параметры
        if (config.telegramBotToken.isBlank()) {
            logger.error("TELEGRAM_BOT_TOKEN is required")
            exitProcess(1)
        }

        if (config.adminUserId.isBlank()) {
            logger.error("TELEGRAM_ADMIN_USER_ID is required")
            exitProcess(1)
        }

        // Обрабатываем аргументы командной строки
        val testMode = args.contains("--test")
        val showHelp = args.contains("--help") || args.contains("-h")

        if (showHelp) {
            printHelp()
            return
        }

        val bot = DailyReportBot(config)
        bot.start(testMode)

    } catch (e: Exception) {
        logger.error("Failed to start bot: ${e.message}", e)
        exitProcess(1)
    }
}

/**
 * Показать справку по использованию
 */
private fun printHelp() {
    println("""
        Telegram Daily Report Bot
        
        Usage: ./gradlew :telegram-bot:run [options]
        
        Options:
          --test       Run in test mode (send report immediately)
          --help, -h   Show this help message
        
        Environment variables:
          TELEGRAM_BOT_TOKEN       - Telegram bot token (required)
          TELEGRAM_ADMIN_USER_ID   - Admin user ID (required)
          ANALYSIS_SERVER_URL      - Analysis server URL (default: http://localhost:8080)
          TARGET_REPOSITORY        - Repository to analyze (default: jetbrains/koog)
          DAILY_REPORT_TIME        - Daily report time in HH:mm format (default: 09:00)
        
        Examples:
          ./gradlew :telegram-bot:run                    # Start bot normally
          ./gradlew :telegram-bot:run --args="--test"    # Test mode
          ./gradlew :telegram-bot:run --args="--help"    # Show help
    """.trimIndent())
}
