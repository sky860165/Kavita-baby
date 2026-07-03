# Kavita Baby AI

Floating AI study companion. Tap the bubble, ask a question out loud, it
searches indexed PDFs on the phone for relevant context, asks Gemini, and
speaks the answer back.

## ⚠️ Before you build

This project does **not** include your Gemini API key — that's intentional.

1. Copy `local.properties.template` → `local.properties` (same folder as `settings.gradle`)
2. Open it and set:
   ```
   sdk.dir=<your Android SDK path>          (Android Studio usually fills this in automatically)
   GEMINI_API_KEY=<your own key>
   ```
3. Get a key at https://aistudio.google.com/apikey — use a **fresh** key,
   never one that's been pasted anywhere outside your own machine.
4. `local.properties` is already in `.gitignore` — don't remove it from there,
   and never commit this file or share it.

## How to build

1. Open the project root folder (this one, containing `settings.gradle`) in Android Studio.
2. Let Gradle sync (first sync will download dependencies — needs internet).
3. Build → Build Bundle(s)/APK(s) → Build APK(s), or just Run ▶ on a connected device.
4. On first launch the app will ask for:
   - Microphone permission (for voice input)
   - "Display over other apps" permission (for the floating bubble) — this
     opens a system settings screen, grant it and come back to the app.

## What it does

- Scans the phone for PDFs (via MediaStore) and indexes their text into a
  local Room database, broken into page-level chunks.
- Shows a draggable floating bubble over other apps.
- Tap the bubble → mic starts listening → speech is transcribed →
  relevant PDF chunks are searched locally → question + context (if any)
  goes to Gemini → answer is spoken back via TTS.
- If no relevant PDF content is found, Gemini answers from general
  knowledge instead, so it's not limited to only what's in the notes.
- Manual re-scan: call `PdfScannerService.scanAllPdfs(forceRescan = true)`
  from wherever you want to trigger it (e.g. a settings button) if you want
  to add UI for it later — the underlying logic already supports it.

## Project structure

```
app/src/main/java/com/kavitababy/
  MainActivity.kt          — permissions flow, launches widget + chat UI
  KavitaBabyApp.kt          — Application class
  ai/AIRepository.kt        — Gemini API calls, prompt building
  data/
    PdfChunk.kt              — Room entity
    PdfChunkDao.kt           — Room DAO
    KavitaDatabase.kt        — Room database
    PdfScannerService.kt     — finds + indexes PDFs
    RagSearchEngine.kt       — keyword search over indexed chunks
  voice/VoiceManager.kt     — speech-to-text + text-to-speech wrapper
  service/FloatingWidgetService.kt — the floating bubble + voice pipeline
  ui/ChatScreen.kt          — optional in-app chat UI (Compose)
  ui/theme/KavitaBabyTheme.kt
```

## Notes on permissions

- `SYSTEM_ALERT_WINDOW` (draw over other apps) has to be granted manually
  by the user via a system settings screen — there's no permission dialog
  for it, `MainActivity` opens that screen automatically on first launch.
- If you deny the overlay permission, the app still opens normally but the
  floating bubble won't appear until you grant it (Settings → Apps →
  Kavita Baby AI → Display over other apps).
