# GLM 4.6 Integration Research

## Текущее состояние архитектуры Koog

### 1. Структура провайдеров
В Koog уже поддерживаются следующие провайдеры:
- Google (GoogleModels.Gemini2_5Flash)
- OpenRouter (OpenRouterModels.Gemini2_5Flash, GPT5Nano)
- OpenAI
- Anthropic 
- DeepSeek
- Ollama

### 2. Конфигурация провайдеров
```kotlin
// Текущая конфигурация в Frameworks.kt:50-55
install(Koog) {
    llm {
        google(apiKey = googleApiKey ?: "your-google-api-key")
        openRouter(apiKey = openRouterApiKey ?: "your-openrouter-api-key")
    }
}
```

### 3. Пример кастомного провайдера
В проекте уже есть пример `ZAIConfig` в `/server/src/main/kotlin/ru/andvl/chatter/backend/koog/utils/LLMProvider.kt`, который показывает как создавать кастомный провайдер.

## Варианты интеграции GLM 4.6

### Вариант 1: Через OpenAI-совместимый API (Рекомендуется)

GLM 4.6 часто предоставляет OpenAI-совместимый endpoint. Если это так, можно использовать существующий OpenAI клиент:

```kotlin
// В Frameworks.kt
install(Koog) {
    llm {
        google(apiKey = googleApiKey ?: "your-google-api-key")
        openRouter(apiKey = openRouterApiKey ?: "your-openrouter-api-key")
        
        // GLM через OpenAI-совместимый API
        openAI(apiKey = glmApiKey ?: "your-glm-api-key") {
            baseUrl = "https://open.bigmodel.cn/api/paas/v4" // GLM API endpoint
            modelVersionsMap = mapOf(
                GLMModels.GLM4Plus to "glm-4-plus"
            )
        }
    }
}
```

#### Создание GLM моделей:
```kotlin
// Новый файл: koog-service/src/main/kotlin/ru/andvl/chatter/koog/service/GLMModels.kt
object GLMModels {
    val GLM4Plus = LLModel(
        provider = LLMProvider.OpenAI, // Используем OpenAI провайдер для совместимости
        id = "glm-4-plus",
        capabilities = listOf(
            LLMCapability.Temperature,
            LLMCapability.Tools,
            LLMCapability.Schema.JSON.Standard,
            LLMCapability.Completion
        ),
        contextLength = 128_000
    )
    
    val GLM4Air = LLModel(
        provider = LLMProvider.OpenAI,
        id = "glm-4-air",
        capabilities = listOf(
            LLMCapability.Temperature,
            LLMCapability.Completion,
            LLMCapability.Schema.JSON.Standard
        ),
        contextLength = 8_000
    )
}
```

### Вариант 2: Кастомный GLM провайдер

Если нужна более глубокая интеграция или специфические фичи GLM:

```kotlin
// Расширяем LLMConfig для GLM
fun KoogAgentsConfig.LLMConfig.glm(apiKey: String, configure: GLMConfig.() -> Unit = {}) {
    // Реализация аналогично ZAIConfig
}

class GLMConfig {
    var baseUrl: String = "https://open.bigmodel.cn/api/paas/v4"
    var modelVersionsMap: Map<LLModel, String> = mapOf(
        GLMModels.GLM4Plus to "glm-4-plus",
        GLMModels.GLM4Air to "glm-4-air"
    )
    var apiVersion: String = "v4"
    var timeoutConfig: ConnectionTimeoutConfig? = null
    var httpClient: HttpClient = HttpClient()
    
    fun timeouts(configure: TimeoutConfiguration.() -> Unit) {
        // Аналогично ZAIConfig
    }
}
```

### Вариант 3: Через OpenRouter (Промежуточное решение)

Если GLM доступен через OpenRouter, можно добавить GLM модели в OpenRouter конфигурацию:

```kotlin
object OpenRouterModels {
    // Существующие модели...
    
    // Добавляем GLM модели
    val GLM4Plus = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "zhipuai/glm-4-plus",
        capabilities = listOf(
            LLMCapability.Temperature,
            LLMCapability.Tools,
            LLMCapability.Schema.JSON.Standard
        ),
        contextLength = 128_000
    )
}
```

## Рекомендуемый план реализации

### Фаза 1: Исследование GLM API (1 день)
- [ ] Проверить предоставляет ли GLM OpenAI-совместимый API
- [ ] Изучить документацию GLM API для аутентификации и endpoints
- [ ] Протестировать базовые запросы к GLM API

### Фаза 2: Минимальная интеграция (1-2 дня)
- [ ] Создать `GLMModels.kt` с определениями моделей
- [ ] Добавить GLM в Provider enum если нужно
- [ ] Настроить конфигурацию через OpenAI-совместимый клиент
- [ ] Добавить переменную окружения для GLM API ключа

### Фаза 3: Интеграция в KoogService (1 день)
- [ ] Добавить `GLM` в Provider enum
- [ ] Обновить логику выбора модели в `KoogService.chat()`
- [ ] Добавить fallback для GLM моделей

### Фаза 4: Тестирование (1 день)
- [ ] Протестировать structured output с GLM
- [ ] Проверить работу с чеклистами
- [ ] Сравнить качество ответов с другими провайдерами

## Изменения в коде

### 1. Обновление Provider enum
```kotlin
// В KoogService.kt
enum class Provider {
    GOOGLE,
    OPENROUTER,
    GLM  // Добавляем GLM
}
```

### 2. Обновление логики выбора модели
```kotlin
// В KoogService.chat()
val model: LLModel = when (provider) {
    Provider.GOOGLE -> GoogleModels.Gemini2_5Flash
    Provider.OPENROUTER -> OpenRouterModels.Gemini2_5Flash
    Provider.GLM -> GLMModels.GLM4Plus  // Добавляем GLM
    else -> OpenRouterModels.Gemini2_5Flash
}
```

### 3. Обновление конфигурации
```kotlin
// В Frameworks.kt
install(Koog) {
    llm {
        google(apiKey = googleApiKey ?: "your-google-api-key")
        openRouter(apiKey = openRouterApiKey ?: "your-openrouter-api-key")
        
        // GLM через OpenAI-совместимый API
        openAI(apiKey = glmApiKey ?: "your-glm-api-key") {
            baseUrl = "https://open.bigmodel.cn/api/paas/v4"
            // Дополнительная конфигурация для GLM
        }
    }
}
```

### 4. Переменные окружения
```bash
# В .env
GLM_API_KEY=your_glm_api_key_here
```

```kotlin
// В Application.kt или где загружаются переменные
val glmApiKey = System.getProperty("GLM_API_KEY")
```

## Преимущества предложенного подхода

1. **Минимальные изменения**: Используем существующую архитектуру Koog
2. **Совместимость**: OpenAI-совместимый API позволяет использовать готовые клиенты
3. **Расширяемость**: Легко добавлять новые GLM модели
4. **Фallback**: Система останется работать если GLM недоступен
5. **Конфигурируемость**: API ключ и endpoint легко настраиваются

## Потенциальные проблемы

1. **API совместимость**: GLM может иметь отличия от OpenAI API
2. **Специфические параметры**: GLM может требовать уникальные параметры
3. **Rate limiting**: Нужно учесть лимиты GLM API
4. **Аутентификация**: Может отличаться от стандартной OpenAI схемы

## Следующие шаги

1. Проверить документацию GLM API на OpenAI-совместимость
2. Получить тестовый API ключ для GLM
3. Создать proof-of-concept интеграцию
4. Протестировать structured output
5. Интегрировать в основной код