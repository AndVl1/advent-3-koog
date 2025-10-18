# Koog Service Module

Обертка для Koog framework, использующая нативный Koog API без дублирования абстракций.

## Архитектура

### Основные компоненты

1. **KoogService** (`service/KoogService.kt`)
   - Простая обертка над нативным Koog API
   - Использует `aiAgent` напрямую
   - Встроенный fallback между провайдерами
   - Health check функциональность

2. **KoogConfig** (`config/KoogServiceConfig.kt`)
   - Минимальная конфигурация
   - Загрузка API ключей из environment variables
   - Koog обрабатывает роутинг моделей самостоятельно

## Возможности

### ✅ Использование нативного Koog
- Полная интеграция с Koog framework
- Встроенная маршрутизация моделей в Koog
- Поддержка всех моделей, доступных в Koog

### ✅ Fallback механизм
- Автоматический переключатель между провайдерами
- OpenRouter → Google AI → basic gemini
- Graceful degradation при ошибках

### ✅ Простота
- Минимум дублирования кода
- Прямое использование Koog возможностей
- Легкая конфигурация

## Использование

### Создание сервиса

```kotlin
// Создание сервиса из Application
val koogService = KoogServiceFactory.create(application)

// Или с environment variables
val koogService = KoogServiceFactory.createFromEnv(application)
```

### Отправка сообщения

```kotlin
// Использование с автоматическим выбором модели
val response = koogService.chat("What is AI?")

// С указанием провайдера
val response = koogService.chat("Hello!", Provider.OPENROUTER)

// С указанием конкретной модели
val response = koogService.chat("Explain", "gemini-2.0-flash-exp")
```

### Health check

```kotlin
val status = koogService.getHealthStatus()
```

## Поддерживаемые провайдеры

- **Google AI** - Gemini 2.0 Flash, 1.5 Pro, 1.5 Flash
- **OpenRouter** - Все модели через OpenRouter API
- **Basic Models** - Прямые вызовы по имени модели

## Эндпоинты

### POST /ai/chat
Отправка сообщения в AI

```
Content-Type: text/plain

Your message here
```

### GET /ai/health
Проверка здоровья AI сервисов

```json
{
  "status": "healthy",
  "message": "Koog AI service is operational",
  "providers": {
    "google": true,
    "openrouter": true
  }
}
```

## Конфигурация

API ключи загружаются через environment variables:

```bash
GOOGLE_API_KEY=your-google-api-key
OPENROUTER_API_KEY=your-openrouter-api-key
```

Они автоматически устанавливаются в Koog через install(Koog) в Application.module.

## Пример в server/Frameworks.kt

```kotlin
dependencies {
    provide<KoogService> { KoogServiceFactory.createFromEnv(this) }
}

routing {
    route("/ai") {
        post("/chat") {
            val koogService = call.application.dependencies.single<KoogService>()
            val response = koogService.chat(call.receive<String>())
            call.respondText(response)
        }
    }
}
```

## Преимущества

1. **Без дублирования** - Использует Koog как есть
2. **Надежность** - Встроенные механизмы Koog
3. **Производительность** - Нет дополнительных абстракций
4. **Гибкость** - Легко расширять при необходимости

## Модель использования

Сервис использует следующую логику:
1. Пробует OpenRouter Gemini 2.5 Flash
2. При ошибке переключается на Google AI Gemini 2.5 Flash  
3. При ошибке пытается вызвать basic gemini-2.0-flash-exp