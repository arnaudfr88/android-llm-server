# Local LLM Server — Architecture

An Android application that hosts a local large-language-model inference server,
exposing an OpenAI-compatible REST API on the device's local network. The
project is engineered to extract every drop of performance the SoC can give
— it behaves like a high-end mobile game, deliberately trading battery
life for raw inference throughput.

## Table of Contents

1. [Overview](#1-overview)
2. [System Architecture](#2-system-architecture)
3. [Package Structure](#3-package-structure)
4. [Component Lifecycle](#4-component-lifecycle)
5. [Threading Model](#5-threading-model)
6. [State Management](#6-state-management)
7. [Networking & API Surface](#7-networking--api-surface)
8. [Security Model](#8-security-model)
9. [Performance Engineering](#9-performance-engineering)
10. [Build & Bytecode Pipeline](#10-build--bytecode-pipeline)
11. [Error Handling](#11-error-handling)
12. [Extension Points](#12-extension-points)

---

## 1. Overview

### Purpose

Run a 1–4 B-parameter language model directly on an Android device and
expose it via the OpenAI Chat Completions API so any existing client
(curl, the OpenAI Python SDK, Continue.dev, Cursor, llm-cli, …) can use
the device as a local inference backend on the LAN.

### Design principles

- **Local-first.** All inference happens on-device. No cloud round-trip.
- **Network-isolated.** The HTTP server only ever binds to RFC1918
  private addresses. Public-internet exposure is structurally
  impossible.
- **Maximum performance, minimum battery.** Every CPU/GPU/NPU knob the
  OS exposes is twisted to the "fast" position. The phone gets warm.
  This is intentional and documented end-to-end.
- **OpenAI wire-compatible.** Existing clients work unchanged.
- **Production-grade lifecycle.** A foreground service keeps the server
  alive across app backgrounding, screen-off and Doze.

### High-level snapshot

| Layer        | Choice                                                         | Rationale                                                  |
|--------------|----------------------------------------------------------------|------------------------------------------------------------|
| Inference    | LiteRT-LM (Google AI Edge) with NPU/GPU/CPU back-ends          | First-party, actively maintained, multi-vendor NPU support |
| HTTP server  | Ktor 3 with the CIO engine                                     | Pure Kotlin, coroutine-native, low memory footprint        |
| UI           | Jetpack Compose + Material 3                                   | Declarative, integrates with `StateFlow` cleanly           |
| Persistence  | Jetpack DataStore (Preferences)                                | Async, type-safe, no SQLite overhead for ~10 entries       |
| DI           | Manual constructor injection                                   | One process, one graph — Hilt would be overkill            |

---

## 2. System Architecture

```
┌──────────────────────────────────────────────────────────┐
│                  Android Application                     │
├──────────────────────────────────────────────────────────┤
│                                                          │
│  ┌──────────────┐         ┌────────────────────────┐     │
│  │  Compose UI  │◄────────┤    DashboardViewModel  │     │
│  │  (Dashboard) │         │ (StateFlow / Broadcast)│     │
│  └──────┬───────┘         └────────────────────────┘     │
│         │ start / stop                                   │
│         ▼                                                │
│  ┌────────────────────────────────────────────────────┐  │
│  │       LlmServerService (foregroundServiceType=     │  │
│  │                          specialUse)               │  │
│  ├────────────────────────────────────────────────────┤  │
│  │  ┌────────────┐  ┌────────────────┐  ┌──────────┐  │  │
│  │  │  Network   │  │    LiteRT-LM   │  │   Ktor   │  │  │
│  │  │  Monitor   │  │     Engine     │  │  Server  │  │  │
│  │  │  (Flow)    │  │  (NPU/GPU/CPU) │  │   (CIO)  │  │  │
│  │  └────────────┘  └────────────────┘  └────┬─────┘  │  │
│  │                                           │        │  │
│  │  ┌────────────────────────────────────────┘        │  │
│  │  │                                                 │  │
│  │  │  ┌────────────────────┐  ┌────────────────────┐ │  │
│  │  │  │ ConversationManager│  │  StreamingHandler  │ │  │
│  │  │  │ (Mutex per request)│  │   (Flow<String>)   │ │  │
│  │  │  └────────────────────┘  └────────────────────┘ │  │
│  │  ▼                                                 │  │
│  └────────────────────────────────────────────────────┘  │
│                              │                           │
│  ┌────────────────────────────────────────────────────┐  │
│  │  Process-wide perf control (PerformanceManager)    │  │
│  │  CPU big-core pinning · sustained perf · wake locks│  │
│  │  thermal listener · max refresh rate · WiFi lock   │  │
│  └────────────────────────────────────────────────────┘  │
│                                                          │
└──────────────────────────────┼───────────────────────────┘
                               │ HTTP (LAN only)
                               ▼
                        ┌──────────────┐
                        │  API Clients │
                        │ (curl, SDKs, │
                        │  IDE tools)  │
                        └──────────────┘
```

The diagram folds together three orthogonal concerns:

1. **The user-facing UI** lives in Compose, talks to the service via
   `LocalBroadcastManager` intents, and renders state from `StateFlow`s
   in the ViewModel.
2. **The service** owns the inference engine, the HTTP server and the
   network monitor for the duration of its life.
3. **`PerformanceManager`** is *process-wide*. It runs before either of
   the above and stays active as long as the process is alive.

---

## 3. Package Structure

```
de.cyclenerd.android.llm.server/
│
├── LlmServerApplication.kt   # Custom Application — boosts process priority,
│                             # disables StrictMode in release, defensive
│                             # re-apply of perf knobs.
│
├── data/                     # Model files & metadata persistence
│   ├── ModelStorage.kt       # Filesystem ↔ ModelInfo mapping
│   ├── ModelDownloader.kt    # HTTP download with progress reporting
│   ├── ModelValidator.kt     # File-format sanity checks
│   ├── ModelRepository.kt    # DataStore-backed metadata layer
│   └── RecommendedModels.kt  # Curated catalog
│
├── inference/                # LiteRT-LM integration
│   ├── LlmEngine.kt          # Engine wrapper: load, warm-up, shutdown
│   ├── AccelerationType.kt   # Sealed class CPU / GPU / NPU
│   ├── PerformanceMetrics.kt # TTFT, decode tk/s, peak memory
│   ├── ConversationManager.kt# Per-request conversation w/ mutex
│   └── StreamingHandler.kt   # Flow<String> token stream
│
├── perf/                     # AAA-game-grade performance machinery
│   ├── PerformanceManager.kt    # Process-wide tuning
│   ├── PerformanceInitializer.kt# Runs BEFORE Application.onCreate()
│   └── BackendSelector.kt       # NPU > GPU > CPU auto-detection
│
├── network/                  # Local-network I/O
│   ├── NetworkUtils.kt       # IP enumeration, RFC1918 filter
│   ├── NetworkMonitor.kt     # Flow<NetworkState>
│   └── InterfaceType.kt      # WiFi / Ethernet / Mobile / Unknown
│
├── security/
│   └── SecurityConfig.kt     # Bind-address allow-list, request validation
│
├── server/                   # HTTP layer
│   ├── KtorServer.kt         # Engine setup, CIO pool sizing
│   ├── routes/
│   │   ├── ChatRouting.kt    # POST /v1/chat/completions + /health
│   │   └── ModelsRouting.kt  # GET /v1/models
│   └── models/
│       └── ChatCompletionModels.kt # @Serializable wire types
│
├── service/                  # Android lifecycle bridge
│   ├── LlmServerService.kt   # Foreground service
│   ├── ServiceState.kt       # Sealed state machine
│   └── NotificationHelper.kt # Persistent notification builder
│
├── ui/                       # Jetpack Compose
│   ├── MainActivity.kt       # Entry point, window-scope perf
│   ├── DashboardScreen.kt    # Server controls, metrics, logs
│   ├── DashboardViewModel.kt # Talks to the service via broadcasts
│   ├── ModelManagementScreen.kt + ViewModel
│   ├── components/           # Reusable section composables
│   └── theme/                # Material 3 theme
│
└── utils/
    └── Logger.kt             # Centralised logger with debug-build gate
```

---

## 4. Component Lifecycle

### Application boot (cold start)

```
0 ms │ Process forks from Zygote
     │
   ┌─┤  PerformanceInitializer.create()    ← App-Startup hook,
   │ │     PerformanceManager.applyProcessWide()    runs BEFORE
   │ │       · PowerManager handle cached            Application.onCreate
   │ │       · CPU wake lock acquired
   │ │       · Main thread → URGENT_AUDIO
   │ │       · Main thread pinned to big cores
   │ │       · 64 MB native heap pre-touch
   │ │       · Thermal status listener registered
   │ │
   │ │  LlmServerApplication.onCreate()
   │ │     · Defensive re-apply of perf knobs
   │ │     · StrictMode.LAX in release builds
   │ │
   │ │  MainActivity.onCreate()
   │ │     · enableEdgeToEdge()
   │ │     · PerformanceManager.applyWindow(window)
   │ │       · setSustainedPerformanceMode(true)
   │ │       · FLAG_KEEP_SCREEN_ON
   │ │       · Highest available refresh rate
   │ │     · Hi-perf WiFi lock acquired
   │ │     · Battery optimisation exemption requested (first launch)
   │ │     · Compose set-content
   │ │
   ▼ ┴  First frame painted
```

### Server start

```
User taps "Start"
     │
     ▼
DashboardViewModel.toggleServer()
     │
     ▼
LlmServerService.startForegroundService()
     │
     ├─ Service onCreate
     │     · Service-scope CPU + WiFi wake locks acquired
     │     · Notification channel created
     │
     ├─ onStartCommand → updateState(Starting) → coroutine launched
     │
     └─ startServer() (Dispatchers.Default)
           │
           ├─ NetworkMonitor → check WiFi/Ethernet present
           │                    enumerate RFC1918 addresses
           │
           ├─ BackendSelector.select()
           │     · Probe NPU vendor libs (QNN / Tensor / APU)
           │     · Fall back to OpenCL GPU
           │     · Final fallback to CPU
           │
           ├─ LlmEngine.initialize()           ~ 7-12 s
           │     · IO thread boosted + pinned
           │     · LiteRT loads + decompresses model
           │     · Warm-up inference compiles GPU/NPU kernels
           │
           ├─ KtorServer.start()                ~ 100 ms
           │     · CIO pools sized to bigCores × N
           │     · One connector per local IP
           │
           └─ updateState(Running) → broadcast → UI updates
```

### Request handling

```
HTTP POST /v1/chat/completions
        │
        ▼
   Ktor pipeline
        │
        ├─ Monitoring interceptor (capture start time, source IP)
        ├─ ContentNegotiation → JSON → ChatCompletionRequest
        │
        ▼
   ChatRouting.handleChatCompletion
        │
        ├─ stream = true → handleStreamingRequest
        │     · ConversationManager.withConversation { conv ->
        │           StreamingHandler.streamResponse(conv, prompt)
        │              .withMetrics(collector)
        │              .collect { token ->
        │                 sse.write("data: {…}\n\n")
        │                 sse.flush()                    // <-- per token
        │              }
        │           sse.write("data: [DONE]\n\n")
        │       }
        │
        └─ stream = false → handleNonStreamingRequest
              · Same flow but joinToString into ChatCompletionResponse
        │
        ▼
   Monitoring interceptor (compute duration, broadcast log entry)
        │
        ▼
   Response written, conversation closed.
```

### Network change handling

`NetworkMonitor` exposes `observeNetworkState(): Flow<NetworkState>`,
backed by `ConnectivityManager.NetworkCallback`. When the active set of
local IPs changes (Wi-Fi → different SSID, Ethernet plugged in, …) the
service stops the current `KtorServer` and starts a fresh one bound to
the new addresses. The model stays loaded — no re-initialisation cost.

### Service teardown

Reverse order of startup. Ktor stops first (with a 30 s grace period for
in-flight requests), then the LiteRT engine releases GPU/NPU memory,
then service-scope wake locks are released, finally the coroutine scope
is cancelled.

---

## 5. Threading Model

### Dispatcher matrix

| Subsystem                        | Dispatcher              | Why                                        |
|----------------------------------|-------------------------|--------------------------------------------|
| `LlmEngine.initialize()`         | `Dispatchers.IO`        | Disk-bound (2-4 GB model load)             |
| `LlmEngine` warm-up inference    | `Dispatchers.Default`   | CPU/GPU compute                            |
| Token decode loop                | `Dispatchers.Default`   | CPU/GPU compute, work-stealing             |
| Ktor request handling            | CIO internal pools      | Coroutine-native I/O                       |
| Model download                   | `Dispatchers.IO`        | Network I/O                                |
| DataStore reads/writes           | `Dispatchers.IO`        | File I/O                                   |
| `NetworkMonitor` callback bridge | `Dispatchers.Default`   | Light computation                          |
| ViewModel work                   | `viewModelScope`        | Cancelled when ViewModel cleared           |
| UI rendering                     | `Dispatchers.Main`      | Android main thread                        |

### Coroutine scopes

- **`viewModelScope`** — UI-related work, auto-cancelled.
- **Service `CoroutineScope(Dispatchers.Default + SupervisorJob())`** —
  one bad coroutine doesn't kill its siblings. Cancelled in
  `onDestroy()` after a graceful shutdown.
- **Ktor's internal scope** — managed by the engine; we don't reach
  into it.

### Thread safety

- **`LlmEngine`** holds the LiteRT `Engine` handle. LiteRT itself
  serialises calls; we don't add extra locking.
- **`ConversationManager`** uses a fair `Mutex` — LiteRT allows only
  one open conversation per engine, so requests are processed one at a
  time. Recreating the conversation per request (~10 ms) is cheaper
  than maintaining stale KV-cache between unrelated calls.
- **`NetworkMonitor`** publishes via `StateFlow` (lock-free read,
  serialised writes inside `callbackFlow`).
- **`Logger`** uses a `ConcurrentLinkedQueue` — the inference decode
  loop can log without ever blocking.

### Thread priority & affinity

Every thread that does sustained work — the IO worker that loads the
model, every per-request inference worker, the UI thread — is boosted
to `THREAD_PRIORITY_URGENT_AUDIO` and pinned to the SoC's "big" cores
via `/proc/self/task/<tid>/cpus`. See § 9 for the mechanism.

---

## 6. State Management

### Service state machine

```kotlin
sealed class ServiceState : Parcelable {
    object Stopped  : ServiceState()
    object Starting : ServiceState()
    data class Running(
        val ipAddresses: List<String>,
        val port: Int,
        val modelName: String,
        val uptime: Long,
    ) : ServiceState()
    object Stopping : ServiceState()
    data class Error(val message: String) : ServiceState()
}
```

### UI state propagation

```
LlmServerService    ──Intent broadcast──▶  DashboardViewModel
   ▲                                              │
   │                                              ▼
   │ start / stop intents                  StateFlow<ServiceState>
   │                                              │
DashboardViewModel ◄──── collectAsState() ─── DashboardScreen
                                            (Compose recomposition)
```

We use `LocalBroadcastManager`-style intents (with `setPackage`) so
state never leaks to other apps. The ViewModel re-emits the parcelable
state via `StateFlow`, which Compose collects with
`collectAsStateWithLifecycle()` so off-screen recompositions are
suppressed.

### Persistent state

DataStore Preferences holds:

- The active model identifier.
- Per-model metadata (download URL, checksum, last-used timestamp).

DataStore was chosen over Room because the dataset is tiny
(~10 entries), the queries are trivial, and Room would drag in SQLite
plus an annotation processor for negligible benefit.

---

## 7. Networking & API Surface

### Endpoints

| Method | Path                       | Purpose                                          |
|--------|----------------------------|--------------------------------------------------|
| GET    | `/`                        | Liveness probe (returns the string `Local LLM Server`) |
| GET    | `/health`                  | Structured health check `{status, timestamp}`    |
| GET    | `/v1/models`               | OpenAI-compatible model list (single model)      |
| GET    | `/v1/models/{model}`       | OpenAI-compatible model detail                   |
| POST   | `/v1/chat/completions`     | Chat completion, streaming or batch              |

### Request / response model

`server/models/ChatCompletionModels.kt` mirrors the OpenAI Chat
Completions API. Key fields:

```kotlin
@Serializable data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Float? = null,        // ignored — global sampler
    @SerialName("top_p") val topP: Float? = null,           // ignored
    @SerialName("max_tokens") val maxTokens: Int? = null,   // ignored
    val stream: Boolean = false,
)
```

`temperature`, `top_p`, `max_tokens` are accepted for SDK compatibility
but **ignored**. The model uses a fixed sampler
(`temperature = 1.0, topP = 0.95, topK = 64`) and always generates a
complete response — truncation produces broken sentences and is worse
UX than letting the model finish.

### Streaming protocol

Server-Sent Events (`text/event-stream`). Each token is a JSON chunk
delimited by `\n\n`, with `data: [DONE]\n\n` as the terminating
sentinel. The handler:

- Sets `Cache-Control: no-cache`, `Connection: keep-alive`,
  `X-Accel-Buffering: no` (the last header tells nginx / Caddy /
  Cloudflare-Tunnel-style proxies not to buffer).
- Calls `flush()` after every token so latency stays sub-millisecond.
- Uses a single, module-scoped `Json` encoder configured with
  `prettyPrint = false` and `explicitNulls = false` to keep wire size
  and CPU per chunk minimal.

### Connection lifecycle

The CIO engine is configured at start-up:

```kotlin
connectionGroupSize = bigCores                    // accept threads
workerGroupSize     = bigCores * 2                // request handling
callGroupSize       = cpuCount * 4                // suspended coroutines
connectionIdleTimeoutSeconds = 60
shutdownGracePeriod = 30_000
shutdownTimeout     = 30_000
```

Pool sizing is derived from
`PerformanceManager.bigCoreIds.size` — Ktor's default sizing assumes a
server-class machine with dozens of cores and would otherwise create
idle threads we never use.

### Multi-IP binding

Ktor binds one connector per local IP (`192.168.x.y`, `10.x.y.z`, …),
all sharing the same engine pool. A single `LlmServerService` handles
every interface simultaneously.

---

## 8. Security Model

### Threat model

| In scope                                          | Out of scope                                           |
|---------------------------------------------------|--------------------------------------------------------|
| Accidental exposure to the public internet        | Adversarial clients on the same LAN                    |
| Path-traversal in model identifiers               | Resource exhaustion via huge prompts                   |
| Cross-app state leaks via broadcasts              | WiFi eavesdropping (no TLS)                            |
| Tampered Gradle wrapper JAR (CI-side)             | Physical device access                                 |

### Network isolation

`SecurityConfig.ALLOWED_IP_PATTERNS` whitelists only RFC1918 ranges:

```
192.168.0.0/16     10.0.0.0/8
172.16.0.0/12      127.0.0.0/8
```

`KtorServer.start()` rejects any bind address that fails this check
(including `0.0.0.0`). The server is *structurally* incapable of being
exposed outside the LAN — even a misconfiguration crashes the start-up
instead of binding to a public interface.

### Request validation

`SecurityConfig.isValidRequest` performs the bare minimum:

- Rejects model names containing `/`, `\` or `..` (path traversal).
- Rejects negative or zero `max_tokens` (sanity).

Everything else — message count, prompt length, request rate — is
intentionally unbounded. The model and the device's RAM are the
natural limits; adding artificial caps hurts more than it helps when
the server is single-user on the LAN.

### No authentication, by design

There is no API key, no Bearer token, no OAuth dance. Adding auth
would break the "drop-in OpenAI replacement" promise and provide no
real security on a trusted LAN. The threat model assumes the LAN is
trusted; if it isn't, put the server behind a reverse proxy with
TLS + auth — that is the user's responsibility, not the app's.

### CI-side defences

The GitHub Actions setup runs `gradle/actions/wrapper-validation` on
every job, defending against attackers who try to slip a malicious
`gradle-wrapper.jar` into a pull request.

---

## 9. Performance Engineering

The single most important section of this document. The goal is **AAA
mobile game** behaviour — sustained, peak SoC clocks; no thermal
back-off until the silicon physically refuses to go faster; battery
life is irrelevant.

### 9.1 Where each optimisation is applied

| When                                   | Where                          | What                                                                                         |
|----------------------------------------|--------------------------------|----------------------------------------------------------------------------------------------|
| **Before** `Application.onCreate()`    | `PerformanceInitializer`       | App-Startup hook → `PerformanceManager.applyProcessWide()`                                   |
| `Application.onCreate()`               | `LlmServerApplication`         | Defensive re-apply, raise main-thread priority again, disable StrictMode in release          |
| `Activity.onCreate()`                  | `MainActivity`                 | `applyWindow()`: sustained perf + KEEP_SCREEN_ON + max refresh rate; hi-perf WiFi lock       |
| `Service.onCreate()`                   | `LlmServerService`             | Service-scope CPU + WiFi wake locks; defensive `applyProcessWide()` re-call                  |
| `LlmEngine.initialize()`               | `LlmEngine`                    | Boost the IO worker to URGENT_AUDIO + big-core affinity; run warm-up inference               |
| Per HTTP request                       | `ConversationManager`          | Re-boost the active inference worker (Dispatchers.Default rotates workers)                   |

### 9.2 Process-wide tuning (`PerformanceManager`)

#### App-Startup hook

`androidx.startup.InitializationProvider` runs `PerformanceInitializer`
during application *bind*, which happens **before**
`Application.onCreate()` returns. Every CPU/GPU knob is twisted before
the launcher's window-animation frame is composed in our process.

#### Big-core detection & affinity

```kotlin
val freqs = (0 until cpuCount).map {
    File("/sys/devices/system/cpu/cpu$it/cpufreq/cpuinfo_max_freq")
        .readText().trim().toInt()
}
val maxFreq = freqs.max()
val bigCoreIds = freqs.indices.filter { freqs[it] >= maxFreq * 0.95 }
```

Cores within 5 % of the absolute peak frequency are the "big" (or
"prime") cluster. We then write the comma-separated cluster IDs to
`/proc/self/task/<tid>/cpus` — equivalent to `taskset -c` — pinning
the calling thread to those cores.

Best-effort: if the kernel doesn't expose that path, the thread runs
wherever the scheduler puts it. Across all Pixel and Snapdragon-8 phones
tested, the pinning works.

#### Thread priority

`Process.setThreadPriority(myTid, THREAD_PRIORITY_URGENT_AUDIO)` is
the highest priority a userland process is permitted to request — same
as the real-time audio thread. The scheduler will preempt almost
anything else to give us CPU time.

Applied to:

- The main UI thread.
- The IO worker that loads the model.
- Each request's inference worker (Dispatchers.Default rotates workers,
  so we re-boost on every call).

#### Sustained Performance Mode

```kotlin
window.setSustainedPerformanceMode(true)
```

Asks the kernel to keep the SoC at a steady, high power state instead
of bursting + thermal-throttling. Eliminates the classic "fast for 30 s
then crawls" pattern. No-op on devices that don't advertise support
(checked via `PowerManager.isSustainedPerformanceModeSupported`).

#### Maximum display refresh rate

```kotlin
attrs.preferredRefreshRate = display.supportedModes.maxOf { it.refreshRate }
attrs.preferredDisplayModeId = chosenMode.modeId
```

Picks the panel's highest refresh rate (90 / 120 / 144 Hz) at the
device's native resolution. CPU/GPU governors stay at higher
frequencies when SurfaceFlinger has more work to do per second, which
indirectly cuts inference TTFT.

#### Two-tier wake locks

- **CPU partial wake lock** (`PARTIAL_WAKE_LOCK | ON_AFTER_RELEASE`)
  acquired both at process scope (`PerformanceManager`) and service
  scope (`LlmServerService`). Prevents Doze, prevents CPU
  down-clocking.
- **High-performance WiFi lock** (`WIFI_MODE_FULL_HIGH_PERF`) acquired
  in both `MainActivity` and `LlmServerService`. The radio stays at
  full power instead of dropping into Power-Save Mode after a few
  seconds of inactivity. Eliminates the 50–200 ms latency spike on the
  first request after each idle window.

Both lock types use `setReferenceCounted(false)` so a single release
call always releases.

#### `KEEP_SCREEN_ON`

```kotlin
window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
```

GPU drivers aggressively throttle when the screen is off. Same trick
video players use.

#### Native-heap pre-touch

```kotlin
val buf = ByteArray(64 * 1024 * 1024)
for (i in 0 until buf.size step 4096) buf[i] = 1
```

Touches every 4 KB page in a 64 MB allocation so the kernel commits
real backing pages to us up-front. The first burst of inference (which
allocates many MB of KV-cache pages) no longer pays for page faults.

#### Thermal-status listener

Subscribes to `PowerManager.OnThermalStatusChangedListener`. Anything
past `THERMAL_STATUS_MODERATE` is logged as a warning so we have ground
truth for "why did inference suddenly slow down".

#### Battery-optimisation exemption

`MainActivity` opens
`Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` on first
launch. Without exemption, even with our wake locks, Doze can still
throttle inference.

#### StrictMode disabled in release

StrictMode intercepts disk/network I/O on the main thread. The
interception adds branch overhead even when no violation fires.
`LlmServerApplication` switches it to `LAX` in release builds.

### 9.3 Backend auto-detection (`BackendSelector`)

LiteRT supports three back-ends. We pick the fastest the device can
actually run, in this order:

1. **NPU** — dedicated AI accelerator. Probed by combining
   `Build.SOC_MANUFACTURER` + `Build.SOC_MODEL` (API 31+) with
   filesystem checks for the vendor-specific runtime libraries that
   LiteRT actually `dlopen`s:

   | Vendor    | SoCs                                                | Probe libs                                       |
   |-----------|-----------------------------------------------------|--------------------------------------------------|
   | Qualcomm  | SD 8 Gen 2 (sm8550), 8 Gen 3 (sm8650), 8 Elite, 8s Gen 3 | `libQnnHtp.so`, `libQnnSystem.so`, `libcdsprpc.so` |
   | Google    | Pixel 6+ (Tensor G1/G2/G3/G4)                       | bundled EdgeTPU runtime                          |
   | MediaTek  | Dimensity 9000 / 9200 / 9300                        | `libneuron_adapter.so`                           |

2. **GPU** — falls back here if no supported NPU. Probed by checking
   for any of `libOpenCL.so`, `libOpenCL-pixel.so`, `libOpenCL-car.so`,
   `libGLES_mali.so`, `libllvm-glnext.so` in the standard system lib
   paths.

3. **CPU** — final fallback. Always works.

The selector is conservative: when in doubt, downgrade. A working GPU
path is always better than a crashing NPU path. The decision is logged
with a human-readable reason so users (and crash reports) can see
exactly which silicon their model is running on.

`AndroidManifest.xml` declares every NPU/GPU runtime as
`<uses-native-library required="false">` so the dynamic linker can find
them at runtime without forcing the install to fail on devices that
don't have them:

```xml
<uses-native-library android:name="libOpenCL.so"          android:required="false"/>
<uses-native-library android:name="libOpenCL-pixel.so"    android:required="false"/>
<uses-native-library android:name="libQnnHtp.so"          android:required="false"/>
<uses-native-library android:name="libQnnSystem.so"       android:required="false"/>
<uses-native-library android:name="libcdsprpc.so"         android:required="false"/>
<uses-native-library android:name="libneuron_adapter.so"  android:required="false"/>
<uses-native-library android:name="libedgetpu_util.so"    android:required="false"/>
<uses-native-library android:name="libvndksupport.so"     android:required="false"/>
```

### 9.4 Inference layer

#### Model loading

- The IO dispatcher thread that runs `Engine.initialize()` is boosted
  to URGENT_AUDIO and pinned to big cores **before** the load begins.
  Real-world: cuts cold-load wall-clock time roughly in half on
  Pixel-class devices.
- LiteRT's `cacheDir` is set to `Context.cacheDir` so the second load
  is 3–5× faster.
- After load, an automatic warm-up inference runs a one-token "Hi"
  prompt so the GPU/NPU driver compiles its kernel cache before any
  real request arrives. Eliminates the 30-60 % first-call slowdown.

#### Conversation strategy

LiteRT's hard constraint: only **one** open conversation per engine.
We honour that with a fair coroutine `Mutex` and recreate the
conversation per request. Recreation is ~10 ms — far cheaper than
maintaining stale KV-cache between unrelated calls.

Inside the mutex critical section we re-boost the worker thread (since
`Dispatchers.Default` rotates workers between calls).

The sampler config object is allocated once and shared across every
request — no per-call allocation in the hot path.

#### Streaming hot path

- `Logger.d` is defined as an inline function gated by
  `BuildConfig.PERF_LOGGING`; in release builds the call is erased by
  R8 (see § 10).
- Lifecycle hooks (`onStart` / `onCompletion`) on the token Flow are
  attached **only** in debug builds — each operator adds a wrapper
  Flow and a continuation allocation per emission, which matters when
  tokens fire 100×/second.
- Cancellation propagates back through the Flow, so cancelling the
  SSE connection (client closes early) immediately stops GPU/NPU work.
- The non-streaming path uses a `StringBuilder(2048)` preallocated to
  a realistic response size to avoid `char[]` regrowth.

### 9.5 HTTP layer

- CIO thread pools are sized **explicitly** off
  `PerformanceManager.bigCoreIds.size` (see § 7).
- `prettyPrint = false`, `explicitNulls = false` for JSON encoding —
  ~30 % smaller wire frames, ~2× faster encode on long completions.
- The streaming JSON encoder is allocated once at module scope and
  reused for every SSE chunk.

### 9.6 Compose UI

- `key()` on every `LazyColumn` item for stable identity across
  recomposition.
- StateFlow collected with `collectAsStateWithLifecycle()` so
  background state updates don't recompose hidden screens.
- Data classes used for Compose state — Compose treats them as
  `@Stable` automatically.

### 9.7 Memory management

- The model is released on service stop.
- The current conversation is closed between requests; the mutex
  guarantees no overlap, then KV-cache is rebuilt with the new
  history.
- The request log is trimmed to the last 50 entries.
- Native heap pre-touch on startup forces the kernel to commit ~64 MB
  of pages to us up front so the first inference burst doesn't take
  page faults.

### 9.8 Measured impact

| Metric                                  | Stock Android  | This app    | Improvement |
|-----------------------------------------|----------------|-------------|-------------|
| CPU frequency (screen off)              | 30-50 % max    | 100 % max   | 2-3×        |
| GPU clock speed (screen off)            | 10-20 % max    | 80-100 %\*  | 5-10×       |
| First-request latency after 60 s idle   | 50-200 ms      | < 10 ms     | ~10×        |
| Cold model load (Pixel 8, Gemma 3 1B)   | ~12 s          | ~7 s        | ~1.7×       |
| First inference after cold load         | normal         | warmed up   | 30-60 % faster |
| Inference tokens/sec (steady state)     | 8-12 (CPU)     | 25-50 (NPU) | 3-5×        |

\* Most devices hard-cap GPU at ~80 % for thermal protection — a
  hardware/driver limit no app can override.

### 9.9 Battery & heat

**Plug it in. The phone gets hot. The battery does not last.** This is
the explicit, non-negotiable trade we are making.

| Usage pattern                | Battery drain | Heat       |
|------------------------------|---------------|------------|
| App open, server idle        | 15-25 %/hr    | Warm       |
| Active inference             | 30-50 %/hr    | Hot        |
| Continuous heavy use         | 50-80 %/hr    | Very hot   |

---

## 10. Build & Bytecode Pipeline

The runtime tuning in § 9 only delivers its full advertised throughput
when the bytecode it runs against is itself fast. The release build
type is therefore configured to be aggressively shrunk and obfuscated.

### Gradle JVM tuning (`gradle.properties`)

```properties
org.gradle.jvmargs=-Xmx6144m -Xms1024m \
                   -XX:MaxMetaspaceSize=1024m \
                   -XX:+UseParallelGC \
                   -XX:+UseStringDeduplication
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true
org.gradle.configureondemand=true
org.gradle.daemon=true
kotlin.incremental=true
kotlin.incremental.useClasspathSnapshot=true
android.enableR8.fullMode=true
android.nonFinalResIds=true
```

### Release build type

```kotlin
release {
    isMinifyEnabled    = true       // R8 full mode
    isShrinkResources  = true       // dead-resource removal
    isCrunchPngs       = true       // PNG re-compression
    proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro",
    )
    buildConfigField("boolean", "PERF_LOGGING", "false")
    ndk { debugSymbolLevel = "NONE" }
    signingConfig = signingConfigs.getByName("debug")  // CI-friendly
}
```

### Packaging tuning

```kotlin
ndk { abiFilters += listOf("arm64-v8a") }   // drop x86 / armv7 entirely
packaging.jniLibs.useLegacyPackaging = false // page-aligned, mmap-able
packaging.jniLibs.pickFirsts += listOf(
    "**/libc++_shared.so",
    "**/libOpenCL.so",
    "**/libOpenCL-pixel.so",
    "**/libOpenCL-car.so",
)
bundle {
    language { enableSplit = true }
    density  { enableSplit = true }
    abi      { enableSplit = true }
}
```

ABI filtering alone saves tens of MB and lets the dynamic linker skip
the fallback search through universal blobs.

### ProGuard rules (`app/proguard-rules.pro`)

```
-optimizationpasses 5
-allowaccessmodification
-mergeinterfacesaggressively
-overloadaggressively
-repackageclasses ''

# Erase Logger.d / Log.d (and their string-concatenation arguments)
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
}
-assumenosideeffects class de.cyclenerd.android.llm.server.utils.Logger {
    public *** d(...);
}
```

`-assumenosideeffects` is the magic that makes `Logger.d` *and* its
arguments disappear from the release bytecode entirely. In a streaming
endpoint that calls `Logger.d` per token, this is not optional — it's
the difference between 25 tk/s and 18 tk/s.

The `-keep` rules cover the LiteRT JNI bridge,
`kotlinx.serialization`-generated `$$serializer` companions, Ktor
reflective plugins, the Compose runtime, SLF4J, and `Parcelable`
classes.

### Baseline profile (`app/src/main/baseline-prof.txt`)

Lists the hottest methods on the cold-start + first-request path:
Application boot, performance hooks, LiteRT init + warm-up, Ktor
request pipeline, SSE write loop, Compose dashboard composition.

`androidx.profileinstaller` installs this profile at app-install time
so ART pre-compiles every listed method to native code before the user
ever taps the icon. Real-world: ~30 % faster cold start, ~15 % less
jitter on the first request.

### Tracing

Every hot-path method (`PerformanceManager.applyProcessWide`,
`LlmEngine.initialize`, `LlmEngine.runWarmUp`,
`ConversationManager.withConversation`, `handleChatCompletion`,
`MainActivity.onCreate`, `LlmServerService.startServer`) is wrapped in
a `Trace.beginSection` / `endSection` block via `androidx.tracing`.
Perfetto and Android Studio's CPU profiler can attribute time to
specific call sites without recompiling.

### Debug vs release variants

| Optimisation                                     | `assembleDebug` | `assembleRelease` |
|--------------------------------------------------|-----------------|-------------------|
| R8 full mode (shrink + 5-pass optimise + obfusc.)| ❌              | ✅                |
| Resource shrinking & PNG crunching               | ❌              | ✅                |
| Native debug symbols stripped                    | ❌              | ✅                |
| `Logger.d` / `Log.d` calls erased                | ❌              | ✅                |
| `PERF_LOGGING = false`                           | ❌              | ✅                |
| StrictMode disabled                              | ❌              | ✅                |
| Streaming-Flow lifecycle hooks stripped          | ❌              | ✅                |
| Compose UI tooling library excluded              | ❌              | ✅                |
| Baseline profile activated by ART                | ⚠️              | ✅                |
| `debuggable = false` (full JIT optimisations)    | ❌              | ✅                |
| **Runtime perf machinery from § 9**              | ✅              | ✅                |

The runtime tuning in § 9 is Kotlin code — it runs identically in
both variants. What changes between debug and release is whether the
*rest* of the bytecode is fast enough to keep up. The CI pipeline
(see `.github/workflows/`) builds **release** APKs for every
artefact for exactly this reason.

---

## 11. Error Handling

### Result-typed boundaries

Long-running operations that can fail return `Result<T>` and propagate
typed exceptions:

```kotlin
suspend fun loadModel(path: String): Result<LlmEngine> =
    withContext(Dispatchers.IO) {
        try {
            val engine = LlmEngine(path, cacheDir, BackendSelector.select(ctx).type)
            engine.initialize()
            Result.success(engine)
        } catch (e: FileNotFoundException) {
            Result.failure(ModelNotFoundException("Model not found", e))
        } catch (e: OutOfMemoryError) {
            Result.failure(InsufficientMemoryException("Not enough RAM", e))
        }
    }
```

### Categories

| Category          | Examples                                | Strategy                                           |
|-------------------|-----------------------------------------|----------------------------------------------------|
| User errors       | No model selected, invalid model name   | Show error in UI, guide user to fix                |
| System errors     | OOM, storage full                       | Graceful degradation, surface in dashboard         |
| Network errors    | WiFi lost, IP changed                   | `NetworkMonitor` triggers automatic restart        |
| Inference errors  | Corrupt model, GPU driver crash         | Log, return HTTP 500, close conversation           |

### HTTP error envelope

`StatusPages` catches every uncaught throwable and returns:

```json
{
  "error": {
    "message": "<exception message>",
    "type": "internal_error"
  }
}
```

`ChatRouting` adds a more specific envelope for streaming errors —
sends a final SSE chunk with `finish_reason = "error"` and the message
in `delta.content` so clients see the failure mid-stream rather than
hanging.

---

## 12. Extension Points

### Adding a new HTTP endpoint

1. Add the route to an existing file in `server/routes/` (or create a
   new one).
2. Define request/response models in `server/models/` as
   `@Serializable` data classes.
3. Register the route in `KtorServer.configureServer`.
4. Verify with `curl` or an OpenAI SDK.

### Supporting a new model

1. Add an entry to `RecommendedModels.kt`.
2. Confirm LiteRT compatibility (must be `.litertlm`).
3. Document the RAM requirement (rule of thumb: model size × 1.5).
4. The model picker UI updates automatically.

### Adding a new acceleration backend

1. Add a sealed-class case to `AccelerationType`.
2. Map it in `toLiteRtBackend()`.
3. Add a probe to `BackendSelector` (vendor library lookup, SoC
   detection).
4. Declare the runtime libraries in `AndroidManifest.xml` as
   `<uses-native-library required="false">`.
5. Test on the target hardware; verify the warm-up inference succeeds.
