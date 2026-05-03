---
name: Bug report
about: Report a reproducible problem in Local LLM Server
title: "Bug: <short summary of what is broken>"
labels: bug
assignees: ''
---

<!--
Thanks for taking the time to file a bug report!

Before submitting:
  1. Search existing issues (open AND closed) to avoid duplicates.
  2. Make sure you are on the latest release — your bug may already be fixed.
  3. Read the Troubleshooting section in README.md for common problems
     (connection refused, slow responses, battery drain, etc.).

Replace every "<...>" placeholder below. Delete sections that genuinely
do not apply, but keep the headings so others can scan the report quickly.
-->

## Summary

<!-- One sentence: what is broken? -->

## Environment

| Field                | Value                                                |
| :------------------- | :--------------------------------------------------- |
| **App version**      | <e.g. 1.0.0 — see Settings → About, or commit SHA>   |
| **Install source**   | <GitHub Release APK / built from source / CI build>  |
| **Device**           | <e.g. Samsung Galaxy S22+, Pixel 8 Pro>              |
| **Chipset / GPU**    | <e.g. Exynos 2200 / Xclipse 920>                     |
| **Android version**  | <e.g. Android 16 (API 36)>                           |
| **RAM (total/free)** | <e.g. 8 GB / 3.2 GB free at the time of the bug>     |
| **Free storage**     | <e.g. 12 GB free>                                    |
| **Model in use**     | <e.g. Gemma 4 E2B / Gemma 4 E4B / none>              |
| **Backend**          | <GPU / CPU / NPU>                                    |
| **Network**          | <WiFi 5 GHz / WiFi 2.4 GHz / Ethernet via USB-C>     |
| **Battery optimisation** | <"Don't optimize" / Optimised / Unsure>          |
| **Plugged in?**      | <Yes / No>                                           |

## Component

<!-- Tick the area(s) most relevant to the bug. -->

- [ ] App UI / Compose screens (`ui/`)
- [ ] Inference engine / LiteRT (`inference/`)
- [ ] Model download / catalog (`data/`)
- [ ] HTTP server / API endpoint (`server/`)
- [ ] Networking / IP detection (`network/`)
- [ ] Foreground service / lifecycle (`service/`)
- [ ] Performance / thermals (`perf/`)
- [ ] Security / bind-address (`security/`)
- [ ] Build / install / packaging
- [ ] Documentation
- [ ] Other / unsure

## Steps to Reproduce

<!-- Be precise. Someone should be able to reproduce by following these. -->

1. <Open the app and …>
2. <Tap …>
3. <Send the following request:>
   ```bash
   curl http://<device-ip>:8080/v1/chat/completions \
     -H "Content-Type: application/json" \
     -d '{"model":"gemma-4","messages":[{"role":"user","content":"Hi"}]}'
   ```
4. <Observe …>

## Expected Behaviour

<!-- What did you expect to happen? -->

## Actual Behaviour

<!-- What actually happened? Include error messages verbatim. -->

## Reproducibility

- [ ] Always (100%)
- [ ] Often (>50%)
- [ ] Sometimes (<50%)
- [ ] Once
- [ ] Cannot reproduce reliably

## Logs

<!--
Please attach logcat output. Capture it like this on a host with adb:

  adb logcat -c                                    # clear the buffer
  # …reproduce the bug on the device…
  adb logcat -d | grep "de.cyclenerd.android.llm.server" > bug.log

For crashes, also include the AndroidRuntime stack trace:

  adb logcat -d AndroidRuntime:E *:S >> bug.log

REDACT your local IP, WiFi SSID, and any prompts containing personal data
before pasting. Then either attach bug.log or paste the relevant excerpt
inside the code block below.
-->

<details>
<summary>logcat (click to expand)</summary>

```
<paste relevant log lines here>
```

</details>

## Screenshots / Recording

<!-- Drag-and-drop into this box for UI bugs. Optional. -->

## API Request / Response (if applicable)

<!--
Include the exact request and response when reporting an API bug.
Strip API keys and personal data first.
-->

<details>
<summary>Request</summary>

```http
POST /v1/chat/completions HTTP/1.1
Host: 192.168.x.x:8080
Content-Type: application/json

{ ... }
```

</details>

<details>
<summary>Response</summary>

```http
HTTP/1.1 500 Internal Server Error

{ ... }
```

</details>

## Workaround

<!-- Have you found a way to avoid the bug? Helps users hitting the same issue. -->

## Additional Context

<!--
Anything else? Recent changes (Android update, new device, custom ROM,
VPN, firewall, MagicNet/Tasker rules), other apps interfering, etc.
-->

## Checklist

- [ ] I searched existing issues and this is not a duplicate.
- [ ] I am running the latest released version of the app.
- [ ] I tested on a **physical device** (not an emulator — emulators
      cannot run LiteRT inference).
- [ ] I included device info, model, and reproduction steps.
- [ ] I attached or pasted logs (with personal data redacted).
