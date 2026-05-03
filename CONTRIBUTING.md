# Contributing to Local LLM Server

First off — **thank you** for considering a contribution! This project turns
old Android flagships into private, OpenAI-compatible AI servers, and every
issue, doc fix, test, and pull request makes it better for the next person.

This document is the single source of truth for *how* to contribute. It
covers the dev environment, how to build and run the app, the coding
conventions enforced by CI, the test strategy, and the PR workflow.

---

## Table of Contents

1. [Code of Conduct](#code-of-conduct)
2. [Ways to Contribute](#ways-to-contribute)
3. [Project Layout](#project-layout)
4. [Prerequisites](#prerequisites)
5. [Initial Setup](#initial-setup)
6. [Building the App](#building-the-app)
7. [Running on a Device](#running-on-a-device)
8. [Development Workflow](#development-workflow)
9. [Coding Style](#coding-style)
10. [Testing](#testing)
11. [Continuous Integration](#continuous-integration)
12. [Submitting a Pull Request](#submitting-a-pull-request)
13. [Reporting Bugs](#reporting-bugs)
14. [Requesting Features](#requesting-features)
15. [Releases](#releases)
16. [Getting Help](#getting-help)

---

## Code of Conduct

This project adheres to the [Contributor Covenant](CODE_OF_CONDUCT.md). By
participating you are expected to uphold this code. Please report
unacceptable behavior to the maintainers via a private issue or email.

---

## Ways to Contribute

You don't have to write code to help:

- **Report bugs** — open an issue with logs and reproduction steps.
- **Improve documentation** — fix typos, clarify wording, add examples to
  `README.md`, `ARCHITECTURE.md`, or `api/README.md`.
- **Triage issues** — reproduce reports, ask for missing info, label them.
- **Test on real hardware** — every device/Android-version combination we
  validate makes the app more reliable.
- **Add tests** — coverage for `app/src/test/` and `app/src/androidTest/`
  is always welcome.
- **Submit code** — bug fixes, new endpoints, performance improvements,
  UI polish.

If you plan a non-trivial change, **open an issue first** to discuss the
approach. It saves everyone time and avoids wasted PRs.

---

## Project Layout

```
android-llm-server/
├── app/                       # Android application module
│   ├── build.gradle.kts       # App build config (SDK, deps, R8, signing)
│   ├── proguard-rules.pro     # R8/ProGuard rules
│   └── src/
│       ├── main/java/de/cyclenerd/android/llm/server/
│       │   ├── LlmServerApplication.kt
│       │   ├── data/          # Model catalog, downloads, validation
│       │   ├── inference/     # LiteRT engine, conversation pool, streaming
│       │   ├── network/       # IP detection, network monitoring
│       │   ├── perf/          # CPU/GPU/NPU tuning, baseline profiles
│       │   ├── security/      # Bind-address allow-list, request guards
│       │   ├── server/        # Ktor HTTP server, routes, models
│       │   ├── service/       # Foreground service lifecycle
│       │   ├── ui/            # Jetpack Compose screens & ViewModels
│       │   └── utils/         # Logger, helpers
│       ├── test/              # JVM unit tests
│       └── androidTest/       # On-device instrumented tests
├── api/                       # OpenAI-compatible API docs & examples
├── gradle/                    # Wrapper + JVM toolchain config
├── .github/                   # CI workflows, issue & PR templates
├── ARCHITECTURE.md            # In-depth design documentation
├── CONTRIBUTING.md            # ← you are here
├── README.md                  # User-facing intro
└── settings.gradle.kts        # Single module project
```

Read **[ARCHITECTURE.md](ARCHITECTURE.md)** before making structural
changes — it explains the threading model, the inference pipeline, and the
performance trade-offs that drive most of the non-obvious code.

---

## Prerequisites

You need:

| Tool                  | Version            | Notes                                                   |
| :-------------------- | :----------------- | :------------------------------------------------------ |
| **JDK**               | 21 (Temurin)       | Required by the Gradle 9.5 daemon.                      |
| **Android Studio**    | Ladybug or newer   | Optional but strongly recommended.                      |
| **Android SDK**       | API 36 + 37        | `compileSdk = 37`, `minSdk = 36`, `targetSdk = 37`.     |
| **Build Tools**       | 36.0.0             | Installed by Android Studio's SDK Manager.              |
| **Git**               | 2.x                | —                                                       |
| **Physical device**   | Android 16 (API 36)+, arm64-v8a, modern GPU | Emulators cannot run LiteRT inference. |
| **macOS / Linux / Windows** | — | All three work; macOS/Linux are the primary dev hosts. |

The Gradle wrapper (`./gradlew`) pins Gradle 9.5.0 — **do not install
Gradle globally**, always use the wrapper.

---

## Initial Setup

### 1. Fork and clone

```bash
# Fork the repo on GitHub, then:
git clone https://github.com/<your-username>/android-llm-server.git
cd android-llm-server

# Add the upstream remote so you can sync later
git remote add upstream https://github.com/Cyclenerd/android-llm-server.git
```

### 2. Configure the SDK location

Create `local.properties` in the repo root (it is git-ignored):

```properties
sdk.dir=/absolute/path/to/Android/sdk
```

Android Studio will write this file automatically when you open the
project for the first time.

### 3. Open in Android Studio

`File → Open → select the android-llm-server/ directory`. Wait for the
initial Gradle sync to finish (5–10 minutes on a cold cache).

---

## Building the App

All commands run from the repo root. Use the wrapper (`./gradlew`),
**not** a system Gradle install.

### Common tasks

```bash
# Compile and assemble everything (debug + release)
./gradlew build

# Debug APK only — installs faster, includes debuggable=true
./gradlew :app:assembleDebug

# Release APK with R8 full mode, resource shrinking, baseline profile
./gradlew :app:assembleRelease

# Install the debug APK on a connected device
./gradlew :app:installDebug

# Wipe build outputs
./gradlew clean
```

Output APKs land in:

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk`

> The release APK is signed with the auto-generated **debug** keystore so
> it installs directly on any device. It is **not** Play-Store-eligible —
> see `app/build.gradle.kts` for how to override the `signingConfig` if
> you need a real release key.

### Speeding up local builds

`gradle.properties` already enables the Gradle daemon, configuration
cache, build cache, and parallel execution. The first build is slow
because it downloads ~1 GB of dependencies; subsequent builds are
typically incremental and complete in seconds.

If you hit out-of-memory errors, increase the heap:

```properties
# in gradle.properties
org.gradle.jvmargs=-Xmx8192m -Xms2048m -XX:MaxMetaspaceSize=1024m -XX:+UseParallelGC
```

---

## Running on a Device

LiteRT inference requires a **real Android device** with a modern GPU and
the arm64-v8a ABI. Emulators will install the app but cannot run
inference.

1. Enable **Developer Options** on your device (tap *Build number* 7×).
2. Enable **USB debugging**.
3. Connect via USB, accept the RSA prompt.
4. Verify the device is visible:
   ```bash
   adb devices
   ```
5. Install and launch:
   ```bash
   ./gradlew :app:installDebug
   adb shell am start -n de.cyclenerd.android.llm.server/.ui.MainActivity
   ```
6. In the app, open **Model Management** → download **Gemma 4 E2B**
   (~2.4 GB; needs WiFi).
7. Return to the **Dashboard** → **Start Server**.
8. Hit the API:
   ```bash
   curl http://<device-ip>:8080/v1/chat/completions \
     -H "Content-Type: application/json" \
     -d '{"model":"gemma-4","messages":[{"role":"user","content":"Hi"}]}'
   ```

See [`api/README.md`](api/README.md) for the full API surface and more
example clients.

### Viewing logs

```bash
# Filter by package
adb logcat | grep "de.cyclenerd.android.llm.server"

# Filter by a single tag (e.g. the inference engine)
adb logcat -s LlmEngine

# Crash dumps
adb logcat AndroidRuntime:E *:S
```

To bump verbosity, set `Logger.logLevel = LogLevel.DEBUG` in
`utils/Logger.kt` (debug builds only — `Logger.d` calls are stripped from
release by R8).

---

## Development Workflow

1. **Sync with upstream** before starting work:
   ```bash
   git fetch upstream
   git checkout main
   git merge --ff-only upstream/master
   ```

2. **Create a feature branch** off `master`:
   ```bash
   git checkout -b feat/short-descriptive-name
   ```
   Branch naming convention:

   | Prefix      | Use for                  |
   | :---------- | :----------------------- |
   | `feat/`     | New feature              |
   | `fix/`      | Bug fix                  |
   | `docs/`     | Documentation only       |
   | `refactor/` | Code restructuring       |
   | `test/`     | Test-only changes        |
   | `perf/`     | Performance work         |
   | `chore/`    | Build / tooling / CI     |

3. **Make focused, atomic commits.** One logical change per commit.

4. **Run the full pre-commit gauntlet** before pushing:
   ```bash
   ./gradlew ktlintFormat       # auto-fix formatting
   ./gradlew ktlintCheck        # verify no remaining issues
   ./gradlew :app:lintDebug     # Android Lint
   ./gradlew :app:testDebugUnitTest  # JVM unit tests
   ```

5. **Push and open a PR** (see [Submitting a Pull Request](#submitting-a-pull-request)).

### Commit message format

We follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <short imperative summary>

<optional body — wrap at 72 chars; explain *why*, not *what*>

<optional footer — Closes #123, Refs #456, BREAKING CHANGE: ...>
```

**Types:** `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`,
`chore`, `ci`, `build`.

**Examples:**

```
feat(inference): add NPU acceleration for Pixel devices

Auto-detects the Edge TPU and falls back to GPU/CPU if unavailable.
Cuts decode latency by ~40% on Pixel 8 Pro.

Closes #123
```

```
fix(network): rebind server when WiFi changes networks

NetworkMonitor now reacts to ConnectivityManager callbacks instead of
polling, eliminating the 30 s stale-IP window after a network switch.
```

---

## Coding Style

### Formatting

Formatting is **enforced by ktlint in CI**. Always run before committing:

```bash
./gradlew ktlintFormat   # auto-fix what can be fixed
./gradlew ktlintCheck    # verify (CI runs this)
```

The `.editorconfig` file pins line endings (LF), encoding (UTF-8), and
indentation. Don't fight your editor — let it pick those up automatically.

### Kotlin conventions

**Clear, descriptive names:**

```kotlin
// Good
class LlmEngine(private val modelPath: String)

// Bad
class Engine(private val mp: String)
```

**Comments explain *why*, not *what*:**

```kotlin
// Good — captures non-obvious reasoning
// Pool of 3 conversations: avoids the ~50 MB allocation and 2-3 s
// warm-up cost incurred by creating a fresh LiteRT session per request.
private val conversationPool = ConversationPool(maxSize = 3)

// Bad — restates the code
// Creates a pool of conversations
private val conversationPool = ConversationPool(maxSize = 3)
```

**KDoc for every public API:**

```kotlin
/**
 * Loads a LiteRT model from disk into the inference engine.
 *
 * Cold load takes 10–30 s depending on model size and storage speed.
 * Safe to call from any dispatcher; internally hops to [Dispatchers.IO].
 *
 * @param path Absolute path to the `.task` model file.
 * @return [Result.success] with a ready-to-use engine, or [Result.failure]
 *   wrapping a [ModelNotFoundException] / [InsufficientMemoryException].
 */
suspend fun loadModel(path: String): Result<LlmEngine>
```

### Coroutines

**Pick the right dispatcher:**

```kotlin
// I/O — file, network, DataStore
suspend fun download() = withContext(Dispatchers.IO) { /* ... */ }

// CPU-bound work — JSON parsing, prompt formatting
suspend fun format() = withContext(Dispatchers.Default) { /* ... */ }

// Inference — uses the dedicated single-thread dispatcher in PerformanceManager
```

**Use the right scope:**

```kotlin
class DashboardViewModel : ViewModel() {
    fun refresh() {
        // Cancelled automatically when the ViewModel clears
        viewModelScope.launch { /* ... */ }
    }
}
```

Never use `GlobalScope` — it leaks coroutines past the lifecycle of the
component that started them.

### Error handling

Prefer `Result<T>` for recoverable errors at module boundaries; let
unrecoverable invariants throw:

```kotlin
suspend fun loadModel(path: String): Result<LlmEngine> = runCatching {
    LlmEngine(path).also { it.initialize() }
}.recoverCatching { e ->
    throw ModelLoadException("Failed to load model at $path", e)
}
```

---

## Testing

### Coverage targets

| Code area                       | Minimum coverage |
| :------------------------------ | :--------------- |
| Business logic (`data/`, `inference/`, `server/`) | 80% |
| Security-critical (`security/`, bind-address logic) | 100% |
| UI (`ui/`)                      | Smoke tests + screenshot tests welcome |

### Where tests live

- `app/src/test/` — **unit tests**, run on the JVM, no device required.
  Fast (< 1 s per test). Use these for everything that doesn't touch the
  Android framework.
- `app/src/androidTest/` — **instrumented tests**, run on a real device
  or emulator. Use only when you actually need an `Application`,
  `Context`, real Ktor server, or LiteRT engine.

### Writing a unit test

```kotlin
// app/src/test/java/de/cyclenerd/android/llm/server/network/NetworkUtilsTest.kt
class NetworkUtilsTest {
    @Test
    fun `mobile interfaces are filtered out`() {
        val ips = NetworkUtils.getLocalIpAddresses()
        assertTrue(ips.none { it.startsWith("rmnet") })
    }
}
```

### Writing an instrumented test

```kotlin
// app/src/androidTest/java/de/cyclenerd/android/llm/server/E2ETest.kt
@RunWith(AndroidJUnit4::class)
class E2ETest {
    @Test
    fun serverBindsOnlyToLocalAddresses() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // ...
    }
}
```

### Running tests

```bash
# All JVM unit tests
./gradlew :app:testDebugUnitTest

# A single test class
./gradlew :app:testDebugUnitTest --tests "*.NetworkUtilsTest"

# A single test method
./gradlew :app:testDebugUnitTest --tests "*.NetworkUtilsTest.mobile interfaces are filtered out"

# Instrumented tests (device/emulator must be connected)
./gradlew :app:connectedDebugAndroidTest
```

HTML reports:

- Unit: `app/build/reports/tests/testDebugUnitTest/index.html`
- Instrumented: `app/build/reports/androidTests/connected/index.html`

---

## Continuous Integration

Every push and every pull request runs against
**`.github/workflows/ci.yml`**:

| Job              | What it runs                                                  |
| :--------------- | :------------------------------------------------------------ |
| **Lint & format** | `./gradlew ktlintCheck` + `./gradlew :app:lintDebug`         |
| **Unit tests**    | `./gradlew :app:testDebugUnitTest`                           |
| **Build APK**     | `./gradlew :app:assembleRelease` (R8 full mode, shrunk, debug-signed) |

A separate **`instrumented-tests.yml`** workflow runs nightly (and
on-demand) against an emulator with KVM acceleration.

**Releases** (`.github/workflows/release.yml`) trigger on `v*` tags:
re-run lint + unit tests, build a signed release APK, generate a
changelog from `git log`, and publish a GitHub Release with the APK
attached.

If CI fails on your PR, click the failing job to see the full log and
the uploaded artefacts (lint HTML, test reports). Run the same Gradle
command locally to reproduce.

---

## Submitting a Pull Request

1. **Push your branch** to your fork:
   ```bash
   git push -u origin feat/your-feature
   ```

2. **Open the PR** against `master` on the upstream repo. The PR template
   in `.github/PULL_REQUEST_TEMPLATE.md` will be pre-filled.

3. **Fill in the description.** A good PR description answers:
   - **What** does this change?
   - **Why** is the change needed?
   - **How** was it tested? (Devices, Android versions, models.)
   - **Screenshots / logs** for UI or behavioural changes.
   - **Linked issue** — `Closes #123`.

4. **Self-review checklist** before requesting review:

   - [ ] `./gradlew ktlintFormat ktlintCheck :app:lintDebug` passes.
   - [ ] `./gradlew :app:testDebugUnitTest` passes.
   - [ ] New / changed code is covered by tests.
   - [ ] Public APIs have KDoc.
   - [ ] `README.md` / `ARCHITECTURE.md` / `api/README.md` updated if
         user-facing or architectural behaviour changed.
   - [ ] Tested on at least one physical device (note the model + Android
         version in the PR).
   - [ ] No secrets, keystores, or `local.properties` committed.
   - [ ] Commit messages follow Conventional Commits.

5. **Respond to review comments.** Push additional commits — **do not
   force-push** during review unless asked. The maintainer will squash on
   merge, so commit hygiene during review is less important than a
   readable diff.

6. **Keep your branch current.** If `master` moves, rebase or merge:
   ```bash
   git fetch upstream
   git rebase upstream/master      # preferred
   # or: git merge upstream/master
   ```

---

## Reporting Bugs

Use the **Bug report** issue template
(`.github/ISSUE_TEMPLATE/bug_report.md`). A great bug report includes:

- **Device + Android version** (e.g. *Pixel 8 Pro, Android 16*).
- **App version** (Settings → About, or commit SHA if you built it).
- **Model** in use (e.g. *Gemma 4 E2B*).
- **Exact reproduction steps** — numbered list, no skipped steps.
- **Expected vs actual behaviour.**
- **Logs**:
  ```bash
  adb logcat -d | grep "de.cyclenerd.android.llm.server" > bug.log
  ```
  Attach `bug.log` (redact your IP if it appears).
- **Screenshots / screen recording** for UI bugs.

Security vulnerabilities should **not** be filed as public issues —
contact the maintainers privately first.

---

## Requesting Features

Use the **Feature request** template. Strong feature requests answer:

- **Problem:** what user-visible pain point does this solve?
- **Proposed solution:** how would it work from the user's perspective?
- **Alternatives:** other approaches you considered and why they're worse.
- **Scope:** is this a small UI tweak, a new endpoint, a new backend?
- **Willing to implement?** Maintainers prioritise issues with a
  contributor lined up.

---

## Releases

Maintainers cut releases by tagging:

```bash
git tag -a v1.2.0 -m "Release 1.2.0"
git push upstream v1.2.0
```

The `release.yml` workflow takes over from there. Contributors should
**not** push tags.

Versioning follows [SemVer](https://semver.org/). Pre-release suffixes
(`-beta.1`, `-rc.2`) are auto-detected and marked as pre-releases on
GitHub.

---

## Getting Help

- **Architecture questions** → read [`ARCHITECTURE.md`](ARCHITECTURE.md)
  first; it covers the threading model, performance tuning, and the
  Ktor/LiteRT integration.
- **API questions** → see [`api/README.md`](api/README.md) and the
  example scripts in `api/examples.sh` and `api/examples.py`.
- **Build / setup issues** → open an issue with the `question` label and
  paste the full Gradle output.
- **General discussion** → GitHub Discussions (if enabled) or the
  `question` issue label.

---

**Thanks again for contributing — happy hacking!**
