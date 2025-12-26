# SysMetrics Overlay Guide

## Overview | Обзор

The `sysmetrics-overlay` module provides an in-app overlay (HUD) for real-time system metrics visualization without requiring `SYSTEM_ALERT_WINDOW` permission.

Модуль `sysmetrics-overlay` предоставляет внутри-приложения оверлей (HUD) для визуализации системных метрик в реальном времени без разрешения `SYSTEM_ALERT_WINDOW`.

---

## Installation | Установка

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("com.sysmetrics:sysmetrics-core:1.0.0")
    implementation("com.sysmetrics:sysmetrics-overlay:1.0.0")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'com.sysmetrics:sysmetrics-core:1.0.0'
    implementation 'com.sysmetrics:sysmetrics-overlay:1.0.0'
}
```

---

## Quick Start | Быстрый старт

### Basic Usage | Базовое использование

```kotlin
class MainActivity : AppCompatActivity() {
    private var overlayHandle: OverlayHandle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize SysMetrics first
        SysMetrics.initialize(applicationContext)
        
        // Attach overlay (debug builds only by default)
        overlayHandle = SysMetricsOverlay.attach(this)
    }

    override fun onDestroy() {
        overlayHandle?.detach()
        super.onDestroy()
    }
}
```

### With Custom Configuration | С пользовательской конфигурацией

```kotlin
val config = OverlayConfig(
    updateIntervalMs = 500L,        // Update every 500ms
    startExpanded = false,          // Start in collapsed mode
    showNetworkSpeed = true,        // Show network speed
    showFps = true,                 // Show FPS counter
    draggable = true,               // Allow drag to reposition
    textSizeSp = 12f,               // Text size
    opacity = 0.9f,                 // 90% opacity
    enableInRelease = false         // Disable in release builds
)

val handle = SysMetricsOverlay.attach(activity, config)
```

---

## Features | Возможности

### Collapsed Mode | Свёрнутый режим

Compact panel showing key metrics:
- **FPS** - Frames per second (color-coded: green/yellow/red)
- **CPU** - CPU usage percentage
- **RAM** - Memory usage percentage  
- **NET** - Network speed (↓ download ↑ upload)

Компактная панель с ключевыми метриками:
- **FPS** - Кадры в секунду (цветовая индикация: зелёный/жёлтый/красный)
- **CPU** - Использование процессора в процентах
- **RAM** - Использование памяти в процентах
- **NET** - Скорость сети (↓ загрузка ↑ отдача)

### Expanded Mode | Развёрнутый режим

Full metrics panel (tap "▼ More" to expand):

- **CPU Section**: Usage %, cores, frequency
- **Memory Section**: Usage %, used/available/total MB
- **Battery Section**: Level, status, health, temperature
- **Thermal Section**: CPU temperature, throttling status
- **Storage Section**: Used/available/total GB
- **Network Section**: Type, connection status, speeds, total bytes

Полная панель метрик (нажмите "▼ More" для развёртывания):

- **CPU**: Использование %, ядра, частота
- **Память**: Использование %, используется/доступно/всего МБ
- **Батарея**: Уровень, статус, здоровье, температура
- **Термальные**: Температура CPU, троттлинг
- **Хранилище**: Использовано/доступно/всего ГБ
- **Сеть**: Тип, статус подключения, скорости, всего байт

### Drag & Drop | Перетаскивание

The overlay can be dragged to any position on the screen (when `draggable = true`).

Оверлей можно перетащить в любую позицию на экране (когда `draggable = true`).

---

## Configuration Options | Параметры конфигурации

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `updateIntervalMs` | Long | 500 | Metrics update interval (min 100ms) |
| `startExpanded` | Boolean | false | Start in expanded mode |
| `showNetworkSpeed` | Boolean | true | Show network speed metrics |
| `showFps` | Boolean | true | Show FPS counter |
| `draggable` | Boolean | true | Allow drag to reposition |
| `textSizeSp` | Float | 11 | Text size (8-24 SP) |
| `backgroundColor` | Int | Black (200 alpha) | Background color |
| `textColor` | Int | White | Text color |
| `warningColor` | Int | Amber | Warning state color |
| `criticalColor` | Int | Red | Critical state color |
| `goodColor` | Int | Green | Good state color |
| `initialPositionX` | Float | 0 | Initial X position (0-1) |
| `initialPositionY` | Float | 0 | Initial Y position (0-1) |
| `opacity` | Float | 1.0 | Overall opacity (0-1) |
| `enableInRelease` | Boolean | false | Allow in release builds |

### Preset Configurations | Предустановленные конфигурации

```kotlin
// Debug mode (default)
val debugConfig = OverlayConfig.forDebug()

// Release testing (explicitly enabled)
val releaseConfig = OverlayConfig.forReleaseTesting()

// FPS only (minimal)
val fpsConfig = OverlayConfig.fpsOnly()

// Compact (smaller text)
val compactConfig = OverlayConfig.compact()
```

---

## OverlayHandle API

```kotlin
interface OverlayHandle {
    val isAttached: Boolean      // Check if overlay is attached
    val isExpanded: Boolean      // Check if in expanded mode
    
    fun detach()                 // Remove overlay
    fun toggleExpanded()         // Toggle expanded/collapsed
    fun setExpanded(expanded: Boolean)  // Set expanded state
    fun show()                   // Show overlay
    fun hide()                   // Hide overlay (without detaching)
    fun updateConfig(config: OverlayConfig)  // Update configuration
}
```

---

## FPS Monitoring | Мониторинг FPS

### How FPS is Calculated | Как рассчитывается FPS

The overlay uses `Choreographer.FrameCallback` to count frames:

1. **Frame Counting**: Counts frames over a 1-second window
2. **EMA Smoothing**: Applies Exponential Moving Average (α=0.3) for stable readings
3. **Jank Detection**: Identifies frames >16.67ms as janky
4. **FrameMetrics (API 24+)**: Additional frame timing data when available

Оверлей использует `Choreographer.FrameCallback` для подсчёта кадров:

1. **Подсчёт кадров**: Считает кадры за 1-секундное окно
2. **EMA сглаживание**: Применяет экспоненциальное скользящее среднее (α=0.3)
3. **Определение джанков**: Идентифицирует кадры >16.67мс как джанки
4. **FrameMetrics (API 24+)**: Дополнительные данные о времени кадра

### FPS Color Coding | Цветовая индикация FPS

| FPS Range | Color | Status |
|-----------|-------|--------|
| ≥55 | 🟢 Green | Smooth |
| 30-54 | 🟡 Yellow | Warning |
| <30 | 🔴 Red | Critical |

---

## Network Speed | Скорость сети

### How Network Speed is Calculated | Как рассчитывается скорость сети

Network speed is calculated from `TrafficStats` byte counters:

```
speed = (currentBytes - previousBytes) / intervalSeconds
```

- **RX (Download)**: `TrafficStats.getTotalRxBytes()` delta
- **TX (Upload)**: `TrafficStats.getTotalTxBytes()` delta

Скорость сети рассчитывается из счётчиков `TrafficStats`:

- **RX (Загрузка)**: Дельта `TrafficStats.getTotalRxBytes()`
- **TX (Отдача)**: Дельта `TrafficStats.getTotalTxBytes()`

---

## Lifecycle Management | Управление жизненным циклом

### Automatic Handling | Автоматическая обработка

The overlay automatically:
- **Pauses** when activity goes to background
- **Resumes** when activity returns to foreground
- **Detaches** when activity is destroyed

Оверлей автоматически:
- **Приостанавливается** при уходе activity в фон
- **Возобновляется** при возврате activity
- **Отсоединяется** при уничтожении activity

### Manual Control | Ручное управление

```kotlin
// Hide temporarily
overlayHandle?.hide()

// Show again
overlayHandle?.show()

// Detach completely
overlayHandle?.detach()
```

### Activity Changes | Смена Activity

For multi-activity apps, attach overlay in each activity:

```kotlin
class BaseActivity : AppCompatActivity() {
    protected var overlayHandle: OverlayHandle? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) {
            overlayHandle = SysMetricsOverlay.attach(this)
        }
    }
    
    override fun onDestroy() {
        overlayHandle?.detach()
        super.onDestroy()
    }
}
```

---

## Release Build Safety | Безопасность в релизных сборках

### Default Behavior | Поведение по умолчанию

By default, `SysMetricsOverlay.attach()` returns `null` in release builds, preventing accidental exposure.

По умолчанию `SysMetricsOverlay.attach()` возвращает `null` в релизных сборках.

### Enable for Release Testing | Включение для тестирования релиза

```kotlin
// Option 1: Via config
val config = OverlayConfig(enableInRelease = true)
SysMetricsOverlay.attach(activity, config)

// Option 2: Use preset
val config = OverlayConfig.forReleaseTesting()
SysMetricsOverlay.attach(activity, config)

// Option 3: Set debug flag manually
SysMetricsOverlay.setDebugBuild(true)
SysMetricsOverlay.attach(activity)
```

### Conditional Compilation | Условная компиляция

```kotlin
// Only include overlay in debug builds
if (BuildConfig.DEBUG) {
    overlayHandle = SysMetricsOverlay.attach(this)
}

// Or use feature flag
if (BuildConfig.DEBUG || FeatureFlags.ENABLE_METRICS_OVERLAY) {
    overlayHandle = SysMetricsOverlay.attach(
        this,
        OverlayConfig(enableInRelease = true)
    )
}
```

---

## Performance Considerations | Производительность

### Resource Usage | Использование ресурсов

| Metric | Value |
|--------|-------|
| Memory overhead | ~2-3 MB |
| CPU overhead | <1% (idle), <3% (updating) |
| Update latency | <5ms |
| Battery impact | Minimal |

### Best Practices | Лучшие практики

1. **Use appropriate interval**: 500ms is good balance, 1000ms for lower overhead
2. **Disable in production**: Keep `enableInRelease = false` unless testing
3. **Detach when not needed**: Call `detach()` to free resources
4. **Hide vs Detach**: Use `hide()` for temporary hiding, `detach()` for removal

1. **Используйте подходящий интервал**: 500мс — хороший баланс, 1000мс для меньшей нагрузки
2. **Отключайте в продакшене**: Держите `enableInRelease = false`
3. **Отсоединяйте когда не нужно**: Вызывайте `detach()` для освобождения ресурсов
4. **Hide vs Detach**: Используйте `hide()` для временного скрытия, `detach()` для удаления

---

## Troubleshooting | Устранение неполадок

### Overlay not appearing | Оверлей не появляется

1. Check if `SysMetrics.initialize()` was called
2. Check if running debug build (or `enableInRelease = true`)
3. Verify activity has a DecorView

### FPS shows 0 | FPS показывает 0

1. FPS needs ~1 second to calculate initial value
2. Check if activity is in foreground

### Network speed shows 0 | Скорость сети показывает 0

1. No network activity in the interval
2. Check network connectivity

### Memory leaks | Утечки памяти

1. Always call `detach()` in `onDestroy()`
2. Don't hold strong reference to Activity

---

## Module Structure | Структура модуля

```
sysmetrics-overlay/
├── src/main/kotlin/com/sysmetrics/overlay/
│   ├── SysMetricsOverlay.kt      # Main API
│   ├── OverlayConfig.kt          # Configuration
│   ├── OverlayHandle.kt          # Control interface
│   ├── fps/
│   │   └── FrameRateMonitor.kt   # FPS monitoring
│   └── view/
│       └── MetricsOverlayView.kt # UI component
└── src/test/kotlin/              # Unit tests
```

---

## Requirements | Требования

| Parameter | Value |
|-----------|-------|
| Min SDK | 21 (Android 5.0) |
| Target SDK | 34 (Android 14) |
| Dependencies | sysmetrics-core, AndroidX Core, AppCompat, Lifecycle |

### No Special Permissions Required | Специальные разрешения не требуются

The overlay uses in-app window attachment (DecorView) and does not require `SYSTEM_ALERT_WINDOW` or any other special permissions.

Оверлей использует внутреннее окно приложения (DecorView) и не требует `SYSTEM_ALERT_WINDOW` или других специальных разрешений.
