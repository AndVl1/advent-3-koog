package ru.andvl.telegram.bot.service

import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import ru.andvl.telegram.bot.model.BotConfig
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Планировщик ежедневных отчетов
 */
class DailyReportScheduler(
    private val config: BotConfig,
    private val analysisClient: AnalysisServerClient,
    private val telegramBot: TelegramBotService
) {
    private val logger = LoggerFactory.getLogger(DailyReportScheduler::class.java)
    private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(1)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Запустить планировщик ежедневных отчетов
     */
    fun start() {
        val reportTime = parseTime(config.dailyReportTime)
        val delayUntilFirstRun = calculateDelayUntilNextRun(reportTime)
        
        logger.info("Scheduling daily reports at ${config.dailyReportTime}")
        logger.info("Next report will be sent in ${delayUntilFirstRun / 1000 / 60} minutes")
        
        scheduler.scheduleAtFixedRate(
            { runDailyReport() },
            delayUntilFirstRun,
            TimeUnit.DAYS.toMillis(1),
            TimeUnit.MILLISECONDS
        )
        
        scope.launch {
            telegramBot.sendStatusUpdate("🚀 Бот запущен. Ежедневные отчеты будут отправляться в ${config.dailyReportTime}")
        }
    }

    /**
     * Выполнить отчет немедленно (для тестирования)
     */
    fun runImmediateReport() {
        logger.info("Running immediate report for testing")
        runDailyReport()
    }

    /**
     * Остановить планировщик
     */
    fun stop() {
        logger.info("Stopping daily report scheduler")
        scheduler.shutdown()
        scope.cancel()
        
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow()
            }
        } catch (e: InterruptedException) {
            scheduler.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }

    /**
     * Выполнить ежедневный отчет
     */
    private fun runDailyReport() {
        scope.launch {
            try {
                logger.info("Starting daily report generation for repository: ${config.targetRepository}")
                
                // Проверяем доступность сервера
                if (!analysisClient.checkServerHealth()) {
                    logger.error("Analysis server is not available")
                    telegramBot.sendStatusUpdate(
                        "Сервер анализа недоступен. Отчет будет отправлен позже.",
                        isError = true
                    )
                    return@launch
                }
                
                // Получаем анализ
                val analysisResponse = analysisClient.getDailyRepositoryAnalysis(config.targetRepository)
                
                if (analysisResponse != null) {
                    // Формируем период для отчета
                    val now = java.time.Instant.now()
                    val yesterday = now.minusSeconds(24 * 60 * 60)
                    val formatter = java.time.format.DateTimeFormatter.ISO_INSTANT
                    val period = "${formatter.format(yesterday.atOffset(java.time.ZoneOffset.UTC))} to ${formatter.format(now.atOffset(java.time.ZoneOffset.UTC))}"
                    
                    // Отправляем отчет
                    telegramBot.sendDailyReport(analysisResponse, config.targetRepository, period)
                    logger.info("Daily report sent successfully")
                } else {
                    logger.error("Failed to get analysis response")
                    telegramBot.sendStatusUpdate(
                        "Не удалось получить данные для анализа репозитория `${config.targetRepository}`",
                        isError = true
                    )
                }
                
            } catch (e: Exception) {
                logger.error("Error during daily report generation: ${e.message}", e)
                telegramBot.sendStatusUpdate(
                    "Ошибка при генерации ежедневного отчета: ${e.message}",
                    isError = true
                )
            }
        }
    }

    /**
     * Парсинг времени из строки в формате HH:mm
     */
    private fun parseTime(timeString: String): LocalTime {
        return try {
            LocalTime.parse(timeString, DateTimeFormatter.ofPattern("HH:mm"))
        } catch (e: Exception) {
            logger.warn("Invalid time format: $timeString, using default 09:00")
            LocalTime.of(9, 0)
        }
    }

    /**
     * Вычислить задержку до следующего выполнения
     */
    private fun calculateDelayUntilNextRun(targetTime: LocalTime): Long {
        val now = LocalDateTime.now()
        var nextRun = now.toLocalDate().atTime(targetTime)
        
        // Если время уже прошло сегодня, планируем на завтра
        if (nextRun.isBefore(now) || nextRun.isEqual(now)) {
            nextRun = nextRun.plusDays(1)
        }
        
        val delayMillis = java.time.Duration.between(now, nextRun).toMillis()
        logger.debug("Next run scheduled for: $nextRun (delay: ${delayMillis}ms)")
        
        return delayMillis
    }
}