# Система FPS Overlay

## Обзор

Система FPS Overlay обеспечивает мониторинг частоты кадров в реальном времени и отслеживание ресурсов приложения для Android. Она отображает метрики производительности в виде оверлея поверх UI вашего приложения.

## Возможности

- **Мониторинг FPS в реальном времени** - Захват тайминга кадров через Choreographer API
- **Метрики приложения** - Мониторинг CPU, памяти, heap и потоков только для вашего приложения
- **Визуальный оверлей** - Цветовая кодировка FPS с графиком истории
- **Обнаружение пиков** - Автоматическое обнаружение падений FPS, высокой производительности и jank
- **Toast уведомления** - Оповещения о значительных событиях производительности
- **Сохранение данных** - Room база данных для исторического анализа
- **Учёт жизненного цикла** - Автоматический старт/стоп в зависимости от lifecycle активности

## Быстрый старт

### 1. Базовая интеграция

```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var overlayManager: FpsOverlayManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Создание и подключение оверлея
        overlayManager = FpsOverlayManager.create(this)
        overlayManager.attachToActivity(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayManager.detachFromActivity()
    }
}
```

### 2. Пользовательская конфигурация

```kotlin
val config = FpsOverlayManager.OverlayConfig(
    gravity = Gravity.TOP or Gravity.END,  // Правый верхний угол
    marginTop = 100,  // Ниже статус-бара
    marginRight = 16,
    showToasts = true,  // Включить toast уведомления
    recordToDatabase = true  // Включить сохранение данных
)

overlayManager = FpsOverlayManager.create(
    context = this,
    logger = AndroidMetricsLogger.forDevelopment(),
    config = config
)
```

### 3. Программное наблюдение за метриками

```kotlin
lifecycleScope.launch {
    // Наблюдение за FPS метриками
    overlayManager.fpsCollector.fpsFlow.collect { metrics ->
        when {
            metrics.isCritical -> showPerformanceWarning()
            metrics.isSmooth -> hidePerformanceWarning()
        }
    }
}

lifecycleScope.launch {
    // Наблюдение за пиковыми событиями
    overlayManager.fpsCollector.peakEventFlow.collect { event ->
        when (event) {
            is FpsPeakEvent.FrameDrop -> logFrameDrop(event)
            is FpsPeakEvent.CriticalJank -> reportJank(event)
            is FpsPeakEvent.HighPerformance -> { /* отлично! */ }
        }
    }
}
```

## Компоненты

### FpsMetricsCollector

Собирает метрики FPS используя Choreographer API Android.

```kotlin
val collector = FpsMetricsCollector()

// Запуск сбора
collector.startCollection()

// Получение текущей статистики
val stats = collector.getCurrentStats()
println("Средний FPS: ${stats.averageFps}")
println("Мин FPS: ${stats.minFps}")
println("Janky кадров: ${stats.jankFrames}")

// Остановка сбора
collector.stopCollection()
```

**Ключевые свойства:**
- `fpsFlow: StateFlow<FpsMetrics>` - Текущие метрики FPS
- `peakEventFlow: SharedFlow<FpsPeakEvent>` - Пиковые события
- `isActive: Boolean` - Состояние сбора

### AppMetricsCollector

Собирает метрики потребления ресурсов приложения.

```kotlin
val appCollector = AppMetricsCollector(context)

// Запуск с интервалом 500мс
appCollector.startCollection(intervalMs = 500)

// Наблюдение за метриками
appCollector.metricsFlow.collect { metrics ->
    println("CPU приложения: ${metrics.cpuUsagePercent}%")
    println("Heap: ${metrics.heapUsageMb} MB / ${metrics.heapMaxMb} MB")
    println("Потоков: ${metrics.threadCount}")
}
```

**Собираемые метрики:**
- Процент использования CPU (только приложение)
- Использование памяти (PSS)
- Использование heap и макс. heap
- Native heap
- Количество потоков
- Сетевой I/O (если доступно)
- Открытые файловые дескрипторы

### FpsOverlayView

Кастомный View для отрисовки FPS оверлея.

```kotlin
val overlay = FpsOverlayView(context)

// Обновление метриками
overlay.updateMetrics(overlayMetrics)

// Или обновление по отдельности
overlay.updateFps(fpsMetrics)
overlay.updateAppMetrics(appMetrics)

// Очистка истории
overlay.clearHistory()
```

**Элементы отображения:**
- Текущий FPS (с цветовой кодировкой)
- Средний FPS
- Использование CPU приложением
- Использование памяти приложением
- График истории FPS (60 кадров)

**Цветовая кодировка:**
- 🟢 Зелёный: FPS ≥ 55 (плавно)
- 🟡 Жёлтый: FPS 30-54 (предупреждение)
- 🔴 Красный: FPS < 30 (критично)

### FpsRepository

Сохраняет метрики в Room базу данных.

```kotlin
val repository = FpsRepository.getInstance(context)

// Запись FPS
repository.recordFps(fpsMetrics)

// Запись пикового события
repository.recordPeakEvent(peakEvent)

// Получение статистики за последние 24 часа
val stats = repository.getStatisticsForPeriod(days = 1)

// Очистка старых данных
repository.cleanupOldRecords(daysToKeep = 7)
```

## Модели данных

### FpsMetrics

```kotlin
data class FpsMetrics(
    val timestamp: Long,
    val currentFps: Int,        // Мгновенный FPS
    val averageFps: Float,      // Средний за окно
    val minFps: Int,            // Минимум в окне
    val maxFps: Int,            // Максимум в окне
    val frameCount: Int,        // Кадров в окне
    val frameTimeMs: Float,     // Среднее время кадра
    val jankCount: Int          // Janky кадров
) {
    val isSmooth: Boolean       // FPS ≥ 55
    val isWarning: Boolean      // FPS 30-54
    val isCritical: Boolean     // FPS < 30
    val status: FpsStatus       // SMOOTH, WARNING, CRITICAL
    val jankPercentage: Float   // Процент jank
}
```

### AppMetrics

```kotlin
data class AppMetrics(
    val timestamp: Long,
    val packageName: String,
    val cpuUsagePercent: Float,     // CPU приложения
    val memoryUsageMb: Float,       // Общая память (PSS)
    val heapUsageMb: Float,         // Java heap
    val heapMaxMb: Float,           // Макс heap
    val nativeHeapMb: Float,        // Native heap
    val threadCount: Int,           // Активных потоков
    val networkRxBytes: Long,       // Получено байт
    val networkTxBytes: Long,       // Отправлено байт
    val openFileDescriptors: Int    // Открытых FD
) {
    val heapUsagePercent: Float     // Использование heap %
    val isHeapWarning: Boolean      // Heap ≥ 80%
    val isHeapCritical: Boolean     // Heap ≥ 95%
    val totalMemoryMb: Float        // Heap + native
    val memoryStatus: MemoryStatus  // HEALTHY, MODERATE, WARNING, CRITICAL
}
```

### FpsPeakEvent

```kotlin
sealed class FpsPeakEvent {
    data class FrameDrop(
        val timestamp: Long,
        val fps: Int,
        val delta: Int,         // Величина падения FPS
        val previousFps: Int
    ) : FpsPeakEvent() {
        val severity: Severity  // LOW, MEDIUM, HIGH
    }

    data class HighPerformance(
        val timestamp: Long,
        val fps: Int
    ) : FpsPeakEvent()

    data class CriticalJank(
        val timestamp: Long,
        val fps: Int,
        val duration: Long      // Длительность jank в мс
    ) : FpsPeakEvent()
}
```

## Целевые показатели производительности

| Метрика | Цель | Описание |
|---------|------|----------|
| Запись кадра | <1мс | Накладные расходы на кадр |
| Расчёт FPS | <2мс | Вычисление статистики |
| Память | <600КБ | Общий footprint оверлея |
| Накладные CPU | <2% | Накладные расходы сбора |
| Время отрисовки | <3мс | Отрисовка оверлея |
| Точность | >99% | Точность определения FPS |

## Лучшие практики

### 1. Только для Debug сборок

```kotlin
if (BuildConfig.DEBUG) {
    overlayManager = FpsOverlayManager.create(this)
    overlayManager.attachToActivity(this)
}
```

### 2. Условное отображение

```kotlin
// Переключение жестом встряхивания или кнопкой
overlayManager.toggleVisibility()

// Или прямое управление
overlayManager.hide()
overlayManager.show()
```

### 3. Очистка базы данных

```kotlin
// Запланировать периодическую очистку
lifecycleScope.launch {
    while (isActive) {
        delay(24.hours)
        repository.cleanupOldRecords(daysToKeep = 7)
    }
}
```

### 4. Экономия батареи

Оверлей автоматически приостанавливает сбор при остановке активности (ON_STOP) и возобновляет при запуске (ON_START).

## Устранение неполадок

### Оверлей не виден

1. Проверьте, был ли вызван `attachToActivity()`
2. Убедитесь, что активность имеет DecorView
3. Проверьте, не был ли вызван `hide()`

### Высокое потребление CPU

1. Уменьшите интервал сбора
2. Отключите запись в базу данных
3. Используйте более простую конфигурацию оверлея

### Утечки памяти

1. Всегда вызывайте `detachFromActivity()` в `onDestroy()`
2. Используйте `WeakReference` для ссылок на активность
3. Правильно отменяйте coroutine scopes

## Справочник API

Подробную документацию API см. в KDoc комментариях в исходных файлах:

- `FpsMetricsCollector.kt`
- `AppMetricsCollector.kt`
- `FpsOverlayView.kt`
- `FpsOverlayManager.kt`
- `FpsRepository.kt`

## Архитектура

```
com.sysmetrics/
├── domain/model/
│   ├── FpsMetrics.kt          # Модели FPS метрик
│   └── AppMetrics.kt          # Модели метрик приложения
├── data/fps/
│   ├── FpsMetricsCollector.kt # Сбор FPS через Choreographer
│   ├── AppMetricsCollector.kt # Сбор метрик приложения
│   ├── FpsRepository.kt       # Persistence слой
│   └── database/
│       ├── FpsDatabase.kt     # Room база данных
│       ├── FpsEntities.kt     # Entity классы
│       └── FpsDao.kt          # DAO интерфейсы
└── infrastructure/overlay/
    ├── FpsOverlayView.kt      # Кастомный View оверлея
    └── FpsOverlayManager.kt   # Управление lifecycle
```

## Примеры использования

### Мониторинг производительности UI

```kotlin
class PerformanceMonitor(private val context: Context) {
    private val repository = FpsRepository.getInstance(context)
    
    suspend fun checkPerformance(): PerformanceReport {
        val stats = repository.getStatisticsForPeriod(days = 1)
        
        return PerformanceReport(
            averageFps = stats.averageFps,
            jankRate = stats.jankRate,
            stability = stats.stabilityScore,
            recommendation = when {
                stats.averageFps < 30 -> "Критическая производительность"
                stats.jankRate > 5 -> "Частые подёргивания"
                stats.stabilityScore < 70 -> "Нестабильный FPS"
                else -> "Производительность в норме"
            }
        )
    }
}
```

### Интеграция с аналитикой

```kotlin
lifecycleScope.launch {
    fpsCollector.peakEventFlow.collect { event ->
        when (event) {
            is FpsPeakEvent.CriticalJank -> {
                analytics.logEvent("critical_jank", bundleOf(
                    "fps" to event.fps,
                    "duration" to event.duration,
                    "screen" to currentScreen
                ))
            }
            is FpsPeakEvent.FrameDrop -> {
                if (event.severity == FpsPeakEvent.Severity.HIGH) {
                    analytics.logEvent("severe_frame_drop", bundleOf(
                        "fps" to event.fps,
                        "delta" to event.delta
                    ))
                }
            }
            else -> { /* игнорировать */ }
        }
    }
}
```
