# SysMetrics Android Library

## Документация на русском языке

---

# 📚 Полное руководство по библиотеке SysMetrics

## Содержание

1. [Введение](#введение)
2. [Установка](#установка)
3. [Быстрый старт](#быстрый-старт)
4. [Архитектура](#архитектура)
5. [API Reference](#api-reference)
6. [Модели данных](#модели-данных)
7. [Примеры использования](#примеры-использования)
8. [Экспорт данных](#экспорт-данных)
9. [Производительность](#производительность)
10. [Лучшие практики](#лучшие-практики)
11. [Устранение неполадок](#устранение-неполадок)

---

## Введение

**SysMetrics** — это высокопроизводительная библиотека для сбора системных метрик Android-устройств. Библиотека предоставляет полную информацию о состоянии системы: CPU, память, батарея, температура, хранилище и сеть.

### Ключевые особенности

- 📊 **Комплексный мониторинг** — CPU, RAM, батарея, температура, хранилище, сеть
- 🔄 **Реактивный API** — Flow-based потоковая передача данных
- 💪 **Оценка здоровья** — Автоматический расчёт состояния системы
- 🏗️ **Clean Architecture** — Разделение на Domain/Data/Infrastructure слои
- 🔒 **Потокобезопасность** — Безопасный конкурентный доступ
- ⚡ **Высокая производительность** — <5мс задержка, <5МБ памяти
- 🎯 **Без зависимостей** — Только Kotlin stdlib, Coroutines, Serialization
- 📤 **Экспорт данных** — CSV и JSON форматы

---

## Установка

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("com.sysmetrics:sysmetrics-core:1.0.0")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'com.sysmetrics:sysmetrics-core:1.0.0'
}
```

### Требования

| Параметр | Значение |
|----------|----------|
| Min SDK | 21 (Android 5.0) |
| Target SDK | 34 (Android 14) |
| Kotlin | 1.9.10+ |
| Coroutines | 1.7.3+ |

---

## Быстрый старт

### Шаг 1: Инициализация

Инициализируйте библиотеку в классе `Application`:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Инициализация SysMetrics (вызывается один раз)
        SysMetrics.initialize(this)
    }
}
```

### Шаг 2: Получение текущих метрик

```kotlin
lifecycleScope.launch {
    SysMetrics.getCurrentMetrics()
        .onSuccess { metrics ->
            // Использование процессора
            val cpuUsage = metrics.cpuMetrics.usagePercent
            
            // Использование памяти
            val memoryUsage = metrics.memoryMetrics.usagePercent
            
            // Уровень батареи
            val batteryLevel = metrics.batteryMetrics.level
            
            // Температура CPU
            val cpuTemp = metrics.thermalMetrics.cpuTemperature
            
            // Скорость сети
            val downloadSpeed = metrics.networkMetrics.rxBytesPerSecond
            
            Log.d("SysMetrics", "CPU: $cpuUsage%, RAM: $memoryUsage%")
        }
        .onFailure { error ->
            Log.e("SysMetrics", "Ошибка получения метрик", error)
        }
}
```

### Шаг 3: Наблюдение за метриками в реальном времени

```kotlin
lifecycleScope.launch {
    SysMetrics.observeMetrics(intervalMs = 1000)
        .collect { metrics ->
            // Обновление UI с новыми данными
            updateDashboard(metrics)
        }
}
```

### Шаг 4: Мониторинг состояния здоровья системы

```kotlin
lifecycleScope.launch {
    SysMetrics.observeHealthScore()
        .collect { healthScore ->
            when (healthScore.status) {
                HealthStatus.EXCELLENT -> showGreenIndicator()
                HealthStatus.GOOD -> showYellowIndicator()
                HealthStatus.WARNING -> showOrangeIndicator()
                HealthStatus.CRITICAL -> showRedIndicator()
            }
            
            // Отображение рекомендаций
            healthScore.recommendations.forEach { recommendation ->
                showNotification(recommendation)
            }
        }
}
```

### Шаг 5: Очистка ресурсов

```kotlin
override fun onTerminate() {
    super.onTerminate()
    runBlocking {
        SysMetrics.destroy()
    }
}
```

---

## Архитектура

### Структура проекта

```
sysmetrics-core/
├── domain/                          # Доменный слой
│   ├── model/                       # Модели данных
│   │   ├── SystemMetrics.kt         # Полный снимок метрик
│   │   ├── CpuMetrics.kt            # Метрики процессора
│   │   ├── MemoryMetrics.kt         # Метрики памяти
│   │   ├── BatteryMetrics.kt        # Метрики батареи
│   │   ├── ThermalMetrics.kt        # Термальные метрики
│   │   ├── StorageMetrics.kt        # Метрики хранилища
│   │   ├── NetworkMetrics.kt        # Сетевые метрики
│   │   ├── HealthScore.kt           # Оценка здоровья
│   │   └── Enums.kt                 # Перечисления
│   └── repository/                  # Интерфейсы репозиториев
│       └── IMetricsRepository.kt
├── data/                            # Слой данных
│   ├── repository/                  # Реализации репозиториев
│   │   └── MetricsRepositoryImpl.kt
│   ├── cache/                       # Кэширование
│   │   └── MetricsCache.kt          # TTL-кэш (500мс)
│   ├── mapper/                      # Маппинг данных
│   │   └── MetricsMapper.kt
│   └── export/                      # Экспорт данных
│       └── MetricsExporter.kt       # CSV/JSON экспорт
├── infrastructure/                  # Инфраструктурный слой
│   ├── proc/                        # Чтение /proc файлов
│   │   └── ProcFileReader.kt
│   ├── android/                     # Android API
│   │   ├── AndroidMetricsProvider.kt
│   │   └── NetworkMetricsProvider.kt
│   └── extension/                   # Расширения
│       └── Extensions.kt
└── SysMetrics.kt                    # Публичный API (Singleton)
```

### Диаграмма архитектуры

```
┌─────────────────────────────────────────────────────────────┐
│                    PRESENTATION LAYER                        │
│                   (Ваше приложение)                          │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │  Activity   │  │  ViewModel  │  │      Service        │  │
│  └──────┬──────┘  └──────┬──────┘  └──────────┬──────────┘  │
└─────────┼────────────────┼───────────────────┼──────────────┘
          │                │                   │
          ▼                ▼                   ▼
┌─────────────────────────────────────────────────────────────┐
│                     SYSMETRICS LIBRARY                       │
│  ┌───────────────────────────────────────────────────────┐  │
│  │                    SysMetrics.kt                       │  │
│  │                  (Public API Singleton)                │  │
│  └───────────────────────────┬───────────────────────────┘  │
│                              │                               │
│  ┌───────────────────────────▼───────────────────────────┐  │
│  │                  DOMAIN LAYER                          │  │
│  │  ┌─────────────────────┐  ┌─────────────────────────┐ │  │
│  │  │  IMetricsRepository │  │     Domain Models       │ │  │
│  │  └──────────┬──────────┘  └─────────────────────────┘ │  │
│  └─────────────┼─────────────────────────────────────────┘  │
│                │                                             │
│  ┌─────────────▼─────────────────────────────────────────┐  │
│  │                    DATA LAYER                          │  │
│  │  ┌──────────────────┐  ┌─────────────┐  ┌───────────┐ │  │
│  │  │MetricsRepository │  │MetricsCache │  │ Exporter  │ │  │
│  │  │      Impl        │  │  (500ms)    │  │ CSV/JSON  │ │  │
│  │  └────────┬─────────┘  └─────────────┘  └───────────┘ │  │
│  └───────────┼───────────────────────────────────────────┘  │
│              │                                               │
│  ┌───────────▼───────────────────────────────────────────┐  │
│  │               INFRASTRUCTURE LAYER                     │  │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐ │  │
│  │  │ProcFileReader│  │AndroidMetrics│  │NetworkMetrics│ │  │
│  │  │  /proc/stat  │  │   Provider   │  │   Provider   │ │  │
│  │  │  /proc/meminfo│ │  BatteryMgr  │  │ TrafficStats │ │  │
│  │  └──────────────┘  └──────────────┘  └──────────────┘ │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

---

## API Reference

### SysMetrics (Singleton)

Главная точка входа в библиотеку.

| Метод | Описание | Возвращает |
|-------|----------|------------|
| `initialize(context)` | Инициализация библиотеки | `Unit` |
| `isInitialized()` | Проверка инициализации | `Boolean` |
| `getCurrentMetrics()` | Получение текущих метрик | `Result<SystemMetrics>` |
| `observeMetrics(intervalMs)` | Поток метрик | `Flow<SystemMetrics>` |
| `observeHealthScore()` | Поток оценки здоровья | `Flow<HealthScore>` |
| `getMetricsHistory(count)` | История метрик | `Result<List<SystemMetrics>>` |
| `clearHistory()` | Очистка истории | `Result<Unit>` |
| `getRepository()` | Доступ к репозиторию | `IMetricsRepository` |
| `destroy()` | Освобождение ресурсов | `Result<Unit>` |

### IMetricsRepository

Интерфейс репозитория для продвинутого использования.

```kotlin
public interface IMetricsRepository {
    suspend fun initialize(): Result<Unit>
    suspend fun getCurrentMetrics(): Result<SystemMetrics>
    fun observeMetrics(intervalMs: Long = 1000L): Flow<SystemMetrics>
    fun observeHealthScore(): Flow<HealthScore>
    suspend fun getMetricsHistory(count: Int = 60): Result<List<SystemMetrics>>
    suspend fun clearHistory(): Result<Unit>
    suspend fun destroy(): Result<Unit>
}
```

---

## Модели данных

### SystemMetrics

Полный снимок всех системных метрик.

```kotlin
data class SystemMetrics(
    val cpuMetrics: CpuMetrics,
    val memoryMetrics: MemoryMetrics,
    val batteryMetrics: BatteryMetrics,
    val thermalMetrics: ThermalMetrics,
    val storageMetrics: StorageMetrics,
    val networkMetrics: NetworkMetrics,
    val timestamp: Long,
    val uptime: Long
)
```

### CpuMetrics

Метрики процессора.

| Поле | Тип | Описание |
|------|-----|----------|
| `usagePercent` | `Float` | Использование CPU (0-100%) |
| `physicalCores` | `Int` | Количество физических ядер |
| `logicalCores` | `Int` | Количество логических ядер |
| `maxFrequencyKHz` | `Long?` | Максимальная частота (кГц) |
| `currentFrequencyKHz` | `Long?` | Текущая частота (кГц) |
| `coreFrequencies` | `List<Long>?` | Частоты каждого ядра |

### MemoryMetrics

Метрики оперативной памяти.

| Поле | Тип | Описание |
|------|-----|----------|
| `totalMemoryMB` | `Long` | Общая память (МБ) |
| `usedMemoryMB` | `Long` | Используемая память (МБ) |
| `freeMemoryMB` | `Long` | Свободная память (МБ) |
| `availableMemoryMB` | `Long` | Доступная память (МБ) |
| `usagePercent` | `Float` | Использование (0-100%) |
| `buffersMB` | `Long?` | Буферы (МБ) |
| `cachedMB` | `Long?` | Кэш (МБ) |
| `swapTotalMB` | `Long?` | Общий swap (МБ) |
| `swapFreeMB` | `Long?` | Свободный swap (МБ) |

### BatteryMetrics

Метрики батареи.

| Поле | Тип | Описание |
|------|-----|----------|
| `level` | `Int` | Уровень заряда (0-100%) |
| `temperature` | `Float` | Температура (°C) |
| `status` | `BatteryStatus` | Статус зарядки |
| `health` | `BatteryHealth` | Здоровье батареи |
| `plugged` | `Boolean` | Подключено к питанию |
| `chargingSpeed` | `Int?` | Скорость зарядки |

### ThermalMetrics

Термальные метрики.

| Поле | Тип | Описание |
|------|-----|----------|
| `cpuTemperature` | `Float` | Температура CPU (°C) |
| `batteryTemperature` | `Float` | Температура батареи (°C) |
| `otherTemperatures` | `Map<String, Float>` | Другие датчики |
| `thermalThrottling` | `Boolean` | Тепловой троттлинг |

### StorageMetrics

Метрики хранилища.

| Поле | Тип | Описание |
|------|-----|----------|
| `totalStorageMB` | `Long` | Общий объём (МБ) |
| `freeStorageMB` | `Long` | Свободно (МБ) |
| `usedStorageMB` | `Long` | Использовано (МБ) |
| `usagePercent` | `Float` | Использование (0-100%) |

### NetworkMetrics

Сетевые метрики.

| Поле | Тип | Описание |
|------|-----|----------|
| `rxBytes` | `Long` | Получено байт (всего) |
| `txBytes` | `Long` | Отправлено байт (всего) |
| `rxBytesPerSecond` | `Long` | Скорость загрузки (Б/с) |
| `txBytesPerSecond` | `Long` | Скорость отдачи (Б/с) |
| `isConnected` | `Boolean` | Подключено к сети |
| `connectionType` | `NetworkType` | Тип соединения |
| `networkName` | `String?` | Имя сети (SSID) |
| `signalStrength` | `Int?` | Сила сигнала (dBm) |

### HealthScore

Оценка состояния системы.

| Поле | Тип | Описание |
|------|-----|----------|
| `score` | `Float` | Оценка (0-100) |
| `status` | `HealthStatus` | Статус здоровья |
| `issues` | `List<HealthIssue>` | Обнаруженные проблемы |
| `recommendations` | `List<String>` | Рекомендации |
| `timestamp` | `Long` | Время оценки |

### Перечисления

```kotlin
enum class HealthStatus {
    EXCELLENT,  // Отлично (80-100)
    GOOD,       // Хорошо (60-79)
    WARNING,    // Внимание (40-59)
    CRITICAL    // Критично (0-39)
}

enum class BatteryStatus {
    UNKNOWN, CHARGING, DISCHARGING, NOT_CHARGING, FULL
}

enum class BatteryHealth {
    UNKNOWN, GOOD, OVERHEAT, DEAD, OVER_VOLTAGE, 
    UNSPECIFIED_FAILURE, COLD
}

enum class HealthIssue {
    HIGH_CPU_USAGE,      // CPU > 85%
    HIGH_MEMORY_USAGE,   // RAM > 85%
    HIGH_TEMPERATURE,    // Температура высокая
    LOW_BATTERY,         // Батарея < 15%
    THERMAL_THROTTLING,  // Тепловой троттлинг
    LOW_STORAGE,         // Хранилище > 90%
    POOR_PERFORMANCE     // Низкая производительность
}

enum class NetworkType {
    NONE, WIFI, MOBILE, ETHERNET, BLUETOOTH, VPN, UNKNOWN
}
```

---

## Примеры использования

### Пример 1: Мониторинг в Activity

```kotlin
class MonitorActivity : AppCompatActivity() {
    
    private var metricsJob: Job? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_monitor)
        
        // Убедимся, что SysMetrics инициализирован
        if (!SysMetrics.isInitialized()) {
            SysMetrics.initialize(applicationContext)
        }
    }
    
    override fun onResume() {
        super.onResume()
        startMonitoring()
    }
    
    override fun onPause() {
        super.onPause()
        stopMonitoring()
    }
    
    private fun startMonitoring() {
        metricsJob = lifecycleScope.launch {
            SysMetrics.observeMetrics(intervalMs = 1000)
                .collect { metrics ->
                    updateUI(metrics)
                }
        }
    }
    
    private fun stopMonitoring() {
        metricsJob?.cancel()
        metricsJob = null
    }
    
    private fun updateUI(metrics: SystemMetrics) {
        binding.apply {
            cpuProgress.progress = metrics.cpuMetrics.usagePercent.toInt()
            cpuText.text = "${metrics.cpuMetrics.usagePercent.toInt()}%"
            
            memoryProgress.progress = metrics.memoryMetrics.usagePercent.toInt()
            memoryText.text = "${metrics.memoryMetrics.usedMemoryMB}/${metrics.memoryMetrics.totalMemoryMB} MB"
            
            batteryProgress.progress = metrics.batteryMetrics.level
            batteryText.text = "${metrics.batteryMetrics.level}%"
            
            tempText.text = "${metrics.thermalMetrics.cpuTemperature}°C"
            
            networkText.text = metrics.networkMetrics.getFormattedDownloadSpeed()
        }
    }
}
```

### Пример 2: Фоновый сервис

```kotlin
class MetricsService : Service() {
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    override fun onCreate() {
        super.onCreate()
        SysMetrics.initialize(applicationContext)
        startMetricsCollection()
    }
    
    private fun startMetricsCollection() {
        scope.launch {
            SysMetrics.observeHealthScore()
                .collect { healthScore ->
                    if (healthScore.status == HealthStatus.CRITICAL) {
                        showWarningNotification(healthScore)
                    }
                }
        }
    }
    
    private fun showWarningNotification(healthScore: HealthScore) {
        val issues = healthScore.issues.joinToString(", ")
        // Показать уведомление о проблемах
    }
    
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
```

### Пример 3: ViewModel с StateFlow

```kotlin
class MetricsViewModel : ViewModel() {
    
    private val _metrics = MutableStateFlow<SystemMetrics?>(null)
    val metrics: StateFlow<SystemMetrics?> = _metrics.asStateFlow()
    
    private val _healthScore = MutableStateFlow<HealthScore?>(null)
    val healthScore: StateFlow<HealthScore?> = _healthScore.asStateFlow()
    
    init {
        observeMetrics()
        observeHealthScore()
    }
    
    private fun observeMetrics() {
        viewModelScope.launch {
            SysMetrics.observeMetrics(intervalMs = 1000)
                .collect { _metrics.value = it }
        }
    }
    
    private fun observeHealthScore() {
        viewModelScope.launch {
            SysMetrics.observeHealthScore()
                .collect { _healthScore.value = it }
        }
    }
    
    fun refreshMetrics() {
        viewModelScope.launch {
            SysMetrics.getCurrentMetrics()
                .onSuccess { _metrics.value = it }
        }
    }
}
```

---

## Экспорт данных

### Экспорт в CSV

```kotlin
lifecycleScope.launch {
    val historyResult = SysMetrics.getMetricsHistory(count = 100)
    
    historyResult.onSuccess { history ->
        MetricsExporter.exportToCsv(history)
            .onSuccess { csvContent ->
                // Сохранить в файл или поделиться
                saveToFile("metrics.csv", csvContent)
            }
    }
}
```

### Экспорт в JSON

```kotlin
lifecycleScope.launch {
    val historyResult = SysMetrics.getMetricsHistory(count = 100)
    
    historyResult.onSuccess { history ->
        MetricsExporter.exportToJson(history)
            .onSuccess { jsonContent ->
                // Сохранить в файл или отправить на сервер
                uploadToServer(jsonContent)
            }
    }
}
```

### Генерация отчёта

```kotlin
lifecycleScope.launch {
    val historyResult = SysMetrics.getMetricsHistory(count = 300)
    
    historyResult.onSuccess { history ->
        MetricsExporter.generateSummaryReport(history)
            .onSuccess { report ->
                // Показать или сохранить отчёт
                showReportDialog(report)
            }
    }
}
```

---

## Производительность

### Целевые показатели

| Метрика | Цель | Достигнуто |
|---------|------|------------|
| Время запуска | <100мс | ✅ |
| Задержка сбора | <5мс (p99) | ✅ |
| Использование памяти | <5МБ | ✅ |
| Использование CPU | <5% (24ч) | ✅ |
| TTL кэша | 500мс | ✅ |
| Размер истории | 300 записей | ✅ |

### Формула расчёта Health Score

```
score = (1 - cpu/100) × 0.30 + 
        (1 - memory/100) × 0.35 + 
        (1 - temp/80) × 0.20 + 
        (battery/100) × 0.15
```

| Компонент | Вес | Описание |
|-----------|-----|----------|
| CPU | 30% | Меньше использование = выше оценка |
| Memory | 35% | Меньше использование = выше оценка |
| Temperature | 20% | Ниже температура = выше оценка (макс 80°C) |
| Battery | 15% | Выше заряд = выше оценка |

---

## Лучшие практики

### ✅ Рекомендуется

1. **Инициализируйте один раз** в `Application.onCreate()`
2. **Используйте Flow** для реактивного обновления UI
3. **Отменяйте корутины** при уничтожении компонента
4. **Используйте Result<T>** для обработки ошибок
5. **Ограничивайте интервал** минимум 100мс
6. **Вызывайте destroy()** при завершении работы

### ❌ Не рекомендуется

1. Не инициализируйте в каждом Activity
2. Не блокируйте главный поток
3. Не игнорируйте ошибки в Result
4. Не устанавливайте интервал < 100мс
5. Не храните Context в долгоживущих объектах

### Пример корректной обработки жизненного цикла

```kotlin
class MyActivity : AppCompatActivity() {
    
    private var metricsJob: Job? = null
    
    override fun onStart() {
        super.onStart()
        metricsJob = lifecycleScope.launch {
            SysMetrics.observeMetrics()
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collect { updateUI(it) }
        }
    }
    
    override fun onStop() {
        super.onStop()
        metricsJob?.cancel()
    }
}
```

---

## Устранение неполадок

### Проблема: IllegalStateException при вызове методов

**Причина:** SysMetrics не инициализирован.

**Решение:**
```kotlin
if (!SysMetrics.isInitialized()) {
    SysMetrics.initialize(applicationContext)
}
```

### Проблема: CPU показывает 0% при первом вызове

**Причина:** Для расчёта использования CPU требуется два замера.

**Решение:** Используйте `observeMetrics()` вместо одиночного вызова или вызовите `getCurrentMetrics()` дважды с задержкой.

### Проблема: Температура равна 0

**Причина:** Устройство не предоставляет данные о температуре.

**Решение:** Проверяйте значение перед использованием:
```kotlin
if (metrics.thermalMetrics.cpuTemperature > 0) {
    showTemperature(metrics.thermalMetrics.cpuTemperature)
}
```

### Проблема: Утечка памяти

**Причина:** Не отменена подписка на Flow.

**Решение:** Используйте `lifecycleScope` или отменяйте Job вручную.

---

## Поддержка

- **GitHub Issues:** [github.com/yhtyyar/sysmetrics-android/issues](https://github.com/yhtyyar/sysmetrics-android/issues)
- **Email:** support@sysmetrics.dev

---

*Документация версии 1.0.0 | © 2024 SysMetrics*
