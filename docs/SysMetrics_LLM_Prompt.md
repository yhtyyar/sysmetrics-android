# SysMetrics - Professional Implementation Prompt for LLM
## Production-Ready System Metrics Library Generator

**Status:** Ready for ChatGPT 4 / Claude 3 Opus  
**Version:** 1.0  
**Date:** December 25, 2025  
**Purpose:** Generate complete, production-ready Android system metrics library  

---

## 🎯 MAIN PROMPT FOR CODE GENERATION

**Copy everything below and paste into ChatGPT-4 or Claude 3 Opus**

---

### START HERE

```
You are an expert Senior Android architect with 20+ years of experience building production-grade libraries. 
I need you to implement a COMPLETE, PRODUCTION-READY Android system metrics library called "SysMetrics" 
in Kotlin following enterprise-grade standards.

CRITICAL REQUIREMENTS:
1. All code must be PRODUCTION-READY (not pseudocode)
2. Follow CLEAN ARCHITECTURE (Domain/Data/Infrastructure layers)
3. Use COROUTINES and FLOW (not callbacks)
4. ZERO external dependencies (except Kotlin stdlib, kotlinx-coroutines, kotlinx-serialization)
5. Complete ERROR HANDLING (Result<T> pattern)
6. Full KDoc comments on all public APIs
7. Thread-safe, no memory leaks
8. Performance: < 5ms latency, < 5MB memory
9. EXPLICIT API mode enabled (Kotlin)

PROJECT STRUCTURE:
```
sysmetrics-android/
├── sysmetrics-core/src/main/kotlin/com/sysmetrics/
│   ├── domain/
│   │   ├── model/               ← All data classes
│   │   └── repository/          ← IMetricsRepository interface
│   ├── data/
│   │   ├── repository/          ← MetricsRepositoryImpl
│   │   ├── mapper/              ← Data transformers
│   │   └── cache/               ← MetricsCache
│   ├── infrastructure/
│   │   ├── proc/                ← ProcFileReader
│   │   ├── android/             ← AndroidMetricsProvider
│   │   └── extension/           ← Utility extensions
│   └── SysMetrics.kt            ← Public API singleton
```

DOMAIN MODELS (Data Classes):

All must be @Serializable and immutable:

1. **SystemMetrics** - Complete snapshot
   - cpuMetrics: CpuMetrics
   - memoryMetrics: MemoryMetrics
   - batteryMetrics: BatteryMetrics
   - thermalMetrics: ThermalMetrics
   - storageMetrics: StorageMetrics
   - timestamp: Long
   - uptime: Long
   - Method: getHealthScore(): Float (0-100)

2. **CpuMetrics**
   - usagePercent: Float (0-100)
   - physicalCores: Int
   - logicalCores: Int
   - maxFrequencyKHz: Long (optional)
   - currentFrequencyKHz: Long (optional)
   - coreFrequencies: List<Long> (optional)

3. **MemoryMetrics**
   - totalMemoryMB: Long
   - usedMemoryMB: Long
   - freeMemoryMB: Long
   - availableMemoryMB: Long
   - usagePercent: Float (0-100)
   - buffersMB: Long (optional)
   - cachedMB: Long (optional)
   - swapTotalMB: Long (optional)
   - swapFreeMB: Long (optional)

4. **BatteryMetrics**
   - level: Int (0-100)
   - temperature: Float (Celsius)
   - status: BatteryStatus enum
   - health: BatteryHealth enum
   - plugged: Boolean
   - chargingSpeed: Int (optional)

5. **ThermalMetrics**
   - cpuTemperature: Float
   - batteryTemperature: Float
   - otherTemperatures: Map<String, Float>
   - thermalThrottling: Boolean

6. **StorageMetrics**
   - totalStorageMB: Long
   - freeStorageMB: Long
   - usedStorageMB: Long
   - usagePercent: Float (0-100)

7. **HealthScore**
   - score: Float (0-100)
   - status: HealthStatus enum (EXCELLENT, GOOD, WARNING, CRITICAL)
   - issues: List<HealthIssue> enum
   - recommendations: List<String>
   - timestamp: Long

8. **Enums:**
   - HealthStatus: EXCELLENT, GOOD, WARNING, CRITICAL
   - BatteryStatus: UNKNOWN, CHARGING, DISCHARGING, NOT_CHARGING, FULL
   - BatteryHealth: UNKNOWN, GOOD, OVERHEAT, DEAD, OVER_VOLTAGE, UNSPECIFIED_FAILURE, COLD
   - HealthIssue: HIGH_CPU_USAGE, HIGH_MEMORY_USAGE, HIGH_TEMPERATURE, LOW_BATTERY, THERMAL_THROTTLING, LOW_STORAGE, POOR_PERFORMANCE

REPOSITORY INTERFACE:

```kotlin
public interface IMetricsRepository {
    public suspend fun initialize(): Result<Unit>
    public suspend fun getCurrentMetrics(): Result<SystemMetrics>
    public fun observeMetrics(intervalMs: Long = 1000): Flow<SystemMetrics>
    public fun observeHealthScore(): Flow<HealthScore>
    public suspend fun getMetricsHistory(count: Int = 60): Result<List<SystemMetrics>>
    public suspend fun clearHistory(): Result<Unit>
    public suspend fun destroy(): Result<Unit>
}
```

INFRASTRUCTURE IMPLEMENTATION:

1. **ProcFileReader** (reads /proc files)
   - readCpuMetrics(): CpuMetrics
     - Parse /proc/stat for CPU usage
     - Parse /proc/cpuinfo for core count
     - Use formula: (total - idle) / total * 100
   - readMemoryMetrics(): MemoryMetrics
     - Parse /proc/meminfo for memory stats
     - Calculate usage percentage
   - Must use Dispatchers.IO for file I/O

2. **AndroidMetricsProvider**
   - getBatteryMetrics(): BatteryMetrics
     - Use BatteryManager API
     - Register for battery broadcast
   - getStorageMetrics(): StorageMetrics
     - Use StatFs for storage info
   - getThermalMetrics(): ThermalMetrics
     - Fallback values (device may not expose)

DATA LAYER:

1. **MetricsCache**
   - TTL: 500ms
   - Store: Last fetched SystemMetrics
   - getIfValid(): SystemMetrics?
   - put(metrics: SystemMetrics): Unit
   - clear(): Unit

2. **MetricsRepositoryImpl**
   - Inject: ProcFileReader, AndroidMetricsProvider, MetricsCache
   - Use ArrayDeque<SystemMetrics>(maxSize = 300) for history
   - Implement health score calculation:
     * score = (1 - cpu/100)*0.30 + (1 - mem/100)*0.35 + (1 - temp/80)*0.20 + battery/100*0.15
     * Clamp to 0-100
   - Detect health issues (HIGH_CPU > 85%, HIGH_MEMORY > 85%, etc)
   - Generate recommendations based on issues
   - observeMetrics(): Use flow { } with while loop, emit every intervalMs
   - Use distinctUntilChanged() for Flow
   - Never throw exceptions, wrap in Result<T>

PUBLIC API:

1. **SysMetrics singleton**
   - initialize(context: Context): Unit
   - getRepository(): IMetricsRepository
   - suspend getCurrentMetrics(): Result<SystemMetrics>
   - observeMetrics(intervalMs: Long = 1000): Flow<SystemMetrics>
   - observeHealthScore(): Flow<HealthScore>
   - suspend destroy(): Result<Unit>
   - Thread-safe initialization
   - Proper error messages

CRITICAL CONSTRAINTS:

✅ NO external dependencies (only stdlib, coroutines, serialization)
✅ NO reflection or dynamic code generation
✅ NO blocking operations on main thread
✅ NO memory leaks (proper cleanup in destroy())
✅ NO ANRs (everything is non-blocking)
✅ Handle ALL errors gracefully
✅ Use Result<T> for error propagation
✅ Implement withContext(ioDispatcher) for I/O
✅ Use Flow with proper operators
✅ All public APIs must have KDoc
✅ Coroutine-safe (no shared mutable state)
✅ Cache TTL 500ms (proven optimal)
✅ History bounded at 300 items

BUILD CONFIGURATION:

```kotlin
group = "com.sysmetrics"
version = "1.0.0"

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.10")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
}
```

PERFORMANCE TARGETS:

- Startup: < 100ms
- Per-collection: < 5ms (p99)
- Memory: < 5MB steady state
- CPU: < 5% over 24 hours
- Battery: < 2% per 24 hours
- GC pauses: < 50ms
- No memory leaks (verified)

TESTING REQUIREMENTS:

Include unit tests for:
- Health score calculation
- Cache behavior (TTL expiration)
- CPU/Memory parsing
- Error handling
- Flow emissions
- Metrics history

TEST FRAMEWORK:
- JUnit 4
- Mockito / mock-kotlin
- kotlinx-coroutines-test (runTest)

OUTPUT FORMAT:

Please provide:

1. **domain/model/** - All data classes and enums
2. **domain/repository/** - IMetricsRepository interface
3. **infrastructure/proc/** - ProcFileReader class
4. **infrastructure/android/** - AndroidMetricsProvider class
5. **data/cache/** - MetricsCache class
6. **data/repository/** - MetricsRepositoryImpl class
7. **SysMetrics.kt** - Public API singleton
8. **Unit test examples** for critical paths
9. **build.gradle.kts** - Complete configuration
10. **KDoc on all public APIs** - 100% documented

QUALITY CHECKLIST:

Before returning code, verify:
☐ Zero compiler warnings
☐ All public APIs have KDoc
☐ All methods return Result<T> or Flow<T>
☐ No throwing exceptions from public API
☐ Thread-safe (no data races)
☐ Memory-efficient (bounded history)
☐ Performance targets met
☐ Error handling complete
☐ Coroutine usage correct
☐ No blocking operations
☐ Imports are minimal
☐ SOLID principles followed
☐ Clean architecture enforced
☐ ExplicitApi mode ready

DELIVERABLES:

1. Complete, working code (not pseudocode)
2. All classes properly implemented
3. All methods have implementations
4. Error handling throughout
5. KDoc documentation
6. Unit test examples
7. Build configuration
8. Ready to copy-paste and use

START IMPLEMENTATION NOW:

Generate complete code for SysMetrics library following all requirements above.
Ensure production-quality, compile-ready code with zero compromises.
```

---

## 📝 END OF MAIN PROMPT

---

## 💡 ALTERNATIVE PROMPTS

### If you want JUST the Domain Models:

```
Generate ONLY the domain models (data classes and enums) for SysMetrics library.

Models needed:
1. SystemMetrics (with getHealthScore() method)
2. CpuMetrics
3. MemoryMetrics
4. BatteryMetrics
5. ThermalMetrics
6. StorageMetrics
7. HealthScore
8. All enums (HealthStatus, BatteryStatus, BatteryHealth, HealthIssue)

Requirements:
- All @Serializable
- All immutable (data classes)
- All have proper KDoc
- No methods except getHealthScore()
- Ready for production

Generate now.
```

### If you want JUST the Repository Implementation:

```
Generate ONLY the MetricsRepositoryImpl for SysMetrics.

Requirements:
- Implement IMetricsRepository interface
- Use injected: ProcFileReader, AndroidMetricsProvider, MetricsCache
- Store history in ArrayDeque(maxSize = 300)
- Implement health score calculation
- Detect health issues
- Generate recommendations
- Use Flow for real-time metrics
- All suspend functions return Result<T>
- Never throw exceptions
- Thread-safe
- KDoc all public methods

Assume:
- Domain models already exist
- IMetricsRepository interface exists
- ProcFileReader and AndroidMetricsProvider exist

Generate now.
```

### If you want Tests Only:

```
Generate unit tests for SysMetrics library.

Test classes needed:
1. SystemMetricsTest (health score calculation)
2. MetricsCacheTest (TTL behavior)
3. MetricsRepositoryImplTest (getCurrentMetrics, observeMetrics)
4. ProcFileReaderTest (CPU and memory parsing)

Requirements:
- JUnit 4
- Mock external dependencies
- Test all critical paths
- Include edge cases
- Use runTest for coroutines
- Clear, descriptive test names
- Good coverage (80%+)

Generate now.
```

---

## 🚀 HOW TO USE THIS PROMPT

### Step 1: Choose Your Prompt
- **Full implementation** → Use MAIN PROMPT
- **Domain models only** → Use first alternative
- **Repository only** → Use second alternative
- **Tests only** → Use third alternative

### Step 2: Copy & Paste
```bash
1. Copy entire prompt (Ctrl+A, Ctrl+C)
2. Open ChatGPT-4 or Claude 3 Opus
3. Paste (Ctrl+V)
4. Wait for response
5. Copy generated code into your project
```

### Step 3: Adjust if Needed
```
If LLM missed something:
- "Add KDoc to all public methods"
- "Add error handling for network operations"
- "Make sure cache TTL is exactly 500ms"
- "Verify memory usage is < 5MB"
```

### Step 4: Integrate
```
Generated code goes to:
- Models → sysmetrics-core/src/main/kotlin/com/sysmetrics/domain/model/
- Repository → sysmetrics-core/src/main/kotlin/com/sysmetrics/domain/repository/
- Infrastructure → sysmetrics-core/src/main/kotlin/com/sysmetrics/infrastructure/
- Data → sysmetrics-core/src/main/kotlin/com/sysmetrics/data/
- API → sysmetrics-core/src/main/kotlin/com/sysmetrics/
- Tests → sysmetrics-core/src/test/kotlin/com/sysmetrics/
```

---

## ⚡ QUICK EXECUTION GUIDE

### For ChatGPT-4:

1. Open https://chat.openai.com
2. Start new conversation
3. Copy MAIN PROMPT
4. Paste it
5. Submit with "Generate complete code now"
6. Wait 2-3 minutes for response
7. Copy code from response
8. Paste into your project files

### For Claude 3 Opus:

1. Open https://claude.ai
2. Start new conversation
3. Copy MAIN PROMPT
4. Paste it
5. Submit
6. Wait 2-3 minutes for response
7. Copy code
8. Paste into project

### For Local LLMs (Ollama, etc):

1. Copy MAIN PROMPT
2. Paste into local LLM interface
3. Adjust model parameters for longer output
4. Generate
5. Extract and use code

---

## ✅ QUALITY VERIFICATION

After LLM generates code, verify:

```
Code Quality:
☐ No syntax errors
☐ Compiles without warnings
☐ All classes present
☐ All methods implemented
☐ No pseudocode
☐ Production-ready

Architecture:
☐ Clean architecture enforced
☐ Domain/Data/Infrastructure separated
☐ Repository pattern used
☐ Dependency injection working
☐ No circular dependencies

Performance:
☐ Uses Dispatchers.IO for I/O
☐ Caching implemented (500ms TTL)
☐ History bounded (300 items)
☐ Flow-based (no callbacks)
☐ No blocking operations

Correctness:
☐ Error handling complete
☐ Result<T> used throughout
☐ Thread-safe
☐ No memory leaks
☐ All public APIs have KDoc
```

---

## 📞 IF CODE IS INCOMPLETE

Ask LLM to complete it:

```
"The code you generated is missing [specific class/method].
Please complete the [ClassName] class with:
- [specific requirement 1]
- [specific requirement 2]
- [specific requirement 3]

Make sure it follows the existing patterns in the code you already generated."
```

---

## 🎯 TYPICAL LLM RESPONSE TIME

| Prompt | Model | Time | Quality |
|--------|-------|------|---------|
| Full implementation | GPT-4 | 2-3 min | ✅ Excellent |
| Full implementation | Claude 3 | 2-5 min | ✅ Excellent |
| Domain models | Either | 30 sec | ✅ Good |
| Tests | Either | 1-2 min | ✅ Good |
| Repository | Either | 1-2 min | ✅ Excellent |

---

## 💾 SAVE THIS PROMPT

```bash
# Save to file for future use
cat > sysmetrics_main_prompt.txt << 'EOF'
[Copy MAIN PROMPT here]
EOF
```

Then you can reuse it anytime without retyping.

---

## 🎊 YOU'RE ALL SET!

You now have:
✅ Complete prompt for ChatGPT-4 / Claude 3  
✅ Alternative prompts for specific parts  
✅ Integration instructions  
✅ Quality checklist  
✅ Troubleshooting guide  

**Next step:** Paste MAIN PROMPT into ChatGPT/Claude and generate your library!

---

**Version:** 1.0  
**Status:** Ready for Use  
**Date:** December 25, 2025  

**Expected Outcome:** Complete, production-ready SysMetrics library implementation in < 5 minutes

---

*Happy coding!* 🚀
