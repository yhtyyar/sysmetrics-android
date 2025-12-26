# 📋 PROJECT_SPECIFICATION
## Полная техническая спецификация

**Версия:** 1.0.0  
**Статус:** ✅ Production Ready  
**Дата:** December 26, 2025

---

## 📑 Содержание

1. [Требования](#требования)
2. [Архитектура](#архитектура)
3. [Компоненты](#компоненты)
4. [API](#api)
5. [Производительность](#производительность)
6. [Безопасность](#безопасность)
7. [Развертывание](#развертывание)

---

## 📋 Требования

### Функциональные требования

1. **Сбор метрик FPS**
   - ✅ Использовать Choreographer API для точного сбора
   - ✅ Измерять FPS с точностью до 1 кадра
   - ✅ Скользящее окно минимум 60 кадров
   - ✅ Обновлять метрики каждый кадр

2. **Отображение оверлея**
   - ✅ Отображать FPS в реал-тайме на экране
   - ✅ Цветовая кодировка (зеленый/желтый/красный)
   - ✅ Показывать средний FPS
   - ✅ Отображать системные метрики (CPU, Memory)
   - ✅ Не блокировать взаимодействие пользователя с приложением

3. **Уведомления**
   - ✅ Toast уведомления при критических падениях FPS
   - ✅ Уведомления о пиках производительности
   - ✅ Очередь уведомлений (не более одного за раз)
   - ✅ Длительность 2-3 секунды

4. **Сохранение данных**
   - ✅ Сохранять метрики в Room БД
   - ✅ Асинхронные операции записи
   - ✅ Автоматическая очистка старых записей (>7 дней)
   - ✅ Быстрые запросы (<50ms)

### Нефункциональные требования

| Требование | Значение |
|-----------|----------|
| **Min API** | 21 (Android 5.0) |
| **Target API** | 34 (Android 14) |
| **Kotlin** | 1.9.10+ |
| **CPU Overhead** | <2% |
| **Memory** | <600KB |
| **UI Latency** | <10ms |
| **FPS Accuracy** | >99% |
| **Battery Impact** | <2% per 24h |

---

## 🏗️ Архитектура

### Слои приложения

```
┌─────────────────────────────────────┐
│    Presentation Layer (UI)          │
│  ├─ Activity/Fragment/Service       │
│  ├─ FpsOverlayView                  │
│  └─ FpsOverlayManager               │
├─────────────────────────────────────┤
│    Domain Layer (Business Logic)    │
│  └─ FpsMetricsCollector             │
│     (FPS calculation & distribution)│
├─────────────────────────────────────┤
│    Data Layer (Persistence)         │
│  ├─ FpsRepository                   │
│  ├─ Room Database                   │
│  └─ Local Storage                   │
├─────────────────────────────────────┤
│    Infrastructure                   │
│  ├─ Choreographer API               │
│  ├─ Coroutines                      │
│  └─ StateFlow/SharedFlow            │
└─────────────────────────────────────┘
```

### Диаграмма потока данных

```
Choreographer.postFrameCallback()
        │
        ▼
FpsMetricsCollector
        │
        ├──→ recordFrame(timeNanos)
        │
        ├──→ calculateFps()
        │
        ├──→ _fpsFlow.emit(fps) ──────┐
        │                             │
        ├──→ _averageFpsFlow.emit()  │
        │                             │
        └──→ _peakFlow.emit()         │
                                      │
            ┌─────────────────────────┤
            │                         │
            ▼                         ▼
    FpsOverlayView             FpsOverlayManager
   (onDraw with FPS)           (update UI + Toast)
                                       │
                                       ▼
                                FpsRepository
                                  (save to DB)
```

---

## 🔧 Компоненты

### 1. FpsMetricsCollector

**Назначение:** Сбор и вычисление метрик FPS

**Состояние:**
```kotlin
private val frameTimings: MutableList<Long>  // Временные метки кадров
private val _fpsFlow: MutableStateFlow<Int>  // Текущий FPS
private val _averageFpsFlow: MutableStateFlow<Int>  // Средний FPS
private val _peakFlow: SharedFlow<PeakEvent>  // События пиков
```

**Операции:**
```kotlin
fun startCollection()  // Начать сбор (регистрирует callback)
fun stopCollection()   // Остановить сбор
fun getCurrentStats(): FpsStats  // Текущая статистика
```

**Вычисления:**
```
FPS = количество кадров / времени между ними (в секундах)
Средний FPS = среднее значение за последние 120 кадров
```

### 2. FpsOverlayView

**Назначение:** Отрисовка FPS на прозрачном оверлее

**Отрисовываемые элементы:**
- Фоновый прямоугольник с округлыми углами (70% прозрачность)
- Текст FPS (крупный, цветной)
- Текст среднего FPS (белый)
- Текст CPU% (белый)
- Текст Memory% (белый)
- График истории FPS (линейная диаграмма)

**Цветовая схема:**
```
FPS >= 55 fps  → Green (#00FF00)
30-54 fps      → Yellow (#FFFF00)
< 30 fps       → Red (#FF0000)
```

### 3. FpsOverlayManager

**Назначение:** Управление всеми компонентами

**Ответственность:**
1. Управление жизненным циклом (attach/detach)
2. Запуск/остановка сбора FPS
3. Наблюдение за Flow'ами
4. Обновление UI
5. Отправка Toast уведомлений
6. Сохранение метрик в БД

**Жизненный цикл:**
```
attachToActivity()
├─ startCollection()
├─ addViewToDecor()
├─ observeFpsFlow()
├─ observePeakFlow()
└─ showToasts()

detachFromActivity()
├─ stopCollection()
├─ removeViewFromDecor()
├─ cancelCoroutines()
└─ dismissToasts()
```

### 4. FpsRepository

**Назначение:** Сохранение и запрос метрик

**Таблицы БД:**

```sql
-- Таблица метрик FPS
CREATE TABLE fps_records (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp INTEGER NOT NULL,
    currentFps INTEGER NOT NULL CHECK (currentFps >= 0),
    averageFps INTEGER NOT NULL CHECK (averageFps >= 0)
);
CREATE INDEX idx_fps_timestamp ON fps_records(timestamp DESC);

-- Таблица событий пиков
CREATE TABLE peak_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp INTEGER NOT NULL,
    type TEXT NOT NULL,  -- "DROP", "PEAK", "JANK"
    value INTEGER NOT NULL,
    severity TEXT NOT NULL  -- "LOW", "MEDIUM", "HIGH"
);
CREATE INDEX idx_peak_timestamp ON peak_events(timestamp DESC);

-- Таблица сессий
CREATE TABLE fps_sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    start_time INTEGER NOT NULL,
    duration INTEGER NOT NULL,
    average_fps REAL NOT NULL,
    peak_fps INTEGER NOT NULL
);
```

**Операции:**
```kotlin
suspend fun recordFps(currentFps: Int, averageFps: Int)
suspend fun recordPeakEvent(event: PeakEvent)
suspend fun getFpsRecordsBetween(start: Long, end: Long): List<FpsRecord>
suspend fun getStatisticsForPeriod(days: Int): FpsStatistics
suspend fun cleanupOldRecords(daysToKeep: Int = 7)
```

---

## 📡 API

### FpsMetricsCollector API

```kotlin
class FpsMetricsCollector {
    
    // Flows
    val fpsFlow: StateFlow<Int>                    // Текущий FPS
    val averageFpsFlow: StateFlow<Int>             // Средний FPS
    val peakFpsFlow: SharedFlow<PeakEvent>         // События пиков
    
    // Methods
    fun startCollection()                           // Начать сбор
    fun stopCollection()                            // Остановить сбор
    fun getCurrentStats(): FpsStats                 // Текущая статистика
}

data class FpsStats(
    val currentFps: Int,
    val averageFps: Int,
    val peakFps: Int,
    val minFps: Int,
    val frameCount: Int
)

data class PeakEvent(
    val timestamp: Long,
    val type: String,      // "DROP", "PEAK", "JANK"
    val value: Int,        // FPS value
    val severity: String   // "LOW", "MEDIUM", "HIGH"
)
```

### FpsOverlayManager API

```kotlin
class FpsOverlayManager(
    context: Context,
    fpsCollector: FpsMetricsCollector,
    repository: FpsRepository
) {
    
    // Lifecycle
    fun attachToActivity(lifecycleOwner: LifecycleOwner)
    fun detachFromActivity()
    
    // Controls
    fun toggleVisibility()
    fun getCurrentStats(): FpsStats
    fun setOverlayPosition(x: Int, y: Int)
    fun setOverlaySize(width: Int, height: Int)
}
```

### FpsRepository API

```kotlin
class FpsRepository(context: Context) {
    
    suspend fun recordFps(currentFps: Int, averageFps: Int)
    suspend fun recordPeakEvent(event: PeakEvent)
    suspend fun getFpsRecordsBetween(start: Long, end: Long): List<FpsRecord>
    suspend fun getPeakEventsBetween(start: Long, end: Long): List<PeakEvent>
    suspend fun getStatisticsForPeriod(days: Int): FpsStatistics
    suspend fun cleanupOldRecords(daysToKeep: Int = 7)
}

data class FpsStatistics(
    val averageFps: Double,
    val peakFps: Int,
    val minFps: Int,
    val p95Percentile: Int,
    val p99Percentile: Int,
    val totalFrames: Int,
    val droppedFrames: Int
)
```

---

## ⚡ Производительность

### Целевые метрики

| Метрика | Целевое значение | Метод измерения |
|---------|-----------------|-----------------|
| **FPS Accuracy** | >99% | Сравнение с системными метриками |
| **CPU Overhead** | <2% | Android Profiler |
| **Memory Overhead** | <600KB | Memory Profiler |
| **UI Latency** | <10ms | Frame rendering time |
| **Database Write** | <10ms async | Studio Profiler |
| **Database Query** | <50ms | Query profiling |
| **Battery Impact** | <2% per 24h | Battery Historian |

### Оптимизации

1. **Memory:**
   - Скользящее окно вместо неограниченного списка
   - Переиспользование Paint объектов
   - Отложенное выделение памяти

2. **CPU:**
   - Async database writes (Dispatchers.IO)
   - Минимальная инвалидация View
   - Оптимизированные вычисления FPS

3. **Battery:**
   - Minimal canvas drawing
   - Efficient frame callback registration
   - Proper coroutine management

---

## 🔒 Безопасность

### Защита данных

- ✅ Все операции БД в отдельном потоке (Dispatchers.IO)
- ✅ Room БД использует шифрование по умолчанию
- ✅ Нет утечек контекста (используем слабые ссылки)
- ✅ Нет сохранения чувствительных данных в логах

### Thread Safety

- ✅ StateFlow/SharedFlow thread-safe
- ✅ Synchronized доступ к frameTimings
- ✅ Правильное управление Coroutine Context
- ✅ Нет race conditions

---

## 📦 Развертывание

### Структура проекта

```
app/
├── src/main/kotlin/com/example/app/
│   ├── presentation/
│   │   ├── metrics/
│   │   │   └── FpsMetricsCollector.kt
│   │   └── overlay/
│   │       ├── FpsOverlayView.kt
│   │       └── FpsOverlayManager.kt
│   ├── storage/
│   │   └── FpsRepository.kt
│   └── MainActivity.kt
├── src/test/kotlin/
│   ├── FpsMetricsCollectorTest.kt
│   ├── FpsRepositoryTest.kt
│   └── IntegrationTest.kt
├── build.gradle.kts
└── AndroidManifest.xml
```

### Сборка и запуск

```bash
# Сборка
./gradlew build

# Запуск тестов
./gradlew test

# Запуск на устройстве
./gradlew installDebug

# Профилирование
./gradlew proguard
```

### Распространение

1. Включить в library (AAR)
2. Опубликовать в Maven Central
3. Использовать в других проектах

---

## 📊 Тестовое покрытие

### Unit Tests (70%+ coverage)
- FPS calculation accuracy
- Peak detection logic
- Database operations
- Flow emissions

### Integration Tests
- Activity integration
- Fragment integration
- Service background monitoring
- Memory leak detection

### Performance Tests
- Frame overhead <1ms
- Render time <3ms
- Memory allocation after startup

---

## 🔄 Версионирование

**Версия:** 1.0.0
- **Major:** 1 (основной выпуск)
- **Minor:** 0 (новые функции)
- **Patch:** 0 (исправления)

### План развития

**v1.1 (Q1 2026)**
- Jank frame detection
- Advanced analytics

**v2.0 (Q2 2026)**
- Jetpack Compose support
- Remote monitoring

---

**Документ завершен. Статус: ✅ PRODUCTION READY**
