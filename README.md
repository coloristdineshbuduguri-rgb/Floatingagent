# OpenHitomi — Android floating assistant

A complete Kotlin Android source project with a cute HTML dashboard and six tool connections. Android 8.0+ (API 26); compile/target SDK 35. No paid SDKs or embedded credentials.

## Get your APK using GitHub (no local Android setup required)

1. Create a GitHub repository, preferably private.
2. Extract this ZIP. Upload the **contents of OpenHitomi**, with `settings.gradle.kts` at the repository root. Include the hidden `.github/workflows/build-apk.yml` folder (GitHub Desktop is convenient on Windows).
3. Open **Actions → Build OpenHitomi APK → Run workflow**. The workflow also runs when pushing to main/master.
4. Open the completed green run. Under **Artifacts**, download **OpenHitomi-debug-APK**. Extract it to obtain `app-debug.apk`.
5. Transfer the APK to Android, open it and allow installation from your chosen file manager if prompted.

Artifacts require GitHub sign-in and expire after 14 days. The APK is debug-signed, intended for personal testing, not a Play Store release. Fresh GitHub runners may generate different debug keys; if Android rejects an update because signatures differ, uninstall the old test app first (this clears settings). No repository was created and no workflow has been run for you.

## Local Android Studio build

Install JDK 17, Android SDK Platform 35, Build Tools 34.0.0 and Gradle 8.9. Open this directory in Android Studio. This archive intentionally uses a pinned installed Gradle in CI rather than including an unverified wrapper JAR. If Android Studio requests a wrapper, run `gradle wrapper --gradle-version 8.9` using your installed Gradle first. Then sync and build.

Commands with installed Gradle:

```sh
gradle :app:assembleDebug :app:lintDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

## Using the six features

### Floating Assistant

Tap the card, allow **Display over other apps**, return and tap again. Allow notifications when requested. Drag the bubble to move it; tap it to return to the assistant; hold for 0.7 seconds to stop it. Also stop it from the app or notification. Chat lives in the main activity; the overlay itself is a launcher bubble. It does not read or control other apps. Some manufacturer battery settings may stop overlays; it does not restart automatically after reboot.

### In-App Browser

Enter a complete HTTP(S) URL. A separate WebView opens with an address field and Go button. Android Back closes it. Sites requiring special sign-in integrations, popups, uploads, downloads or browser extensions may need a full browser. The browser has no native JavaScript bridge and cannot access the API key or assistant tools.

### Termux Integration

Install a maintained official Termux build separately. In Termux, set `allow-external-apps=true` in `~/.termux/termux.properties`, then run `termux-reload-settings`. OpenHitomi must be granted the Termux **Run commands** permission (App info → Permissions, possibly Additional permissions). Tap the card, enter a command, review it and tap Run. Commands execute with Termux's permissions, and output appears in Termux's visible terminal session. There is no automatic command execution by AI and no output capture in the assistant. Android/Termux can still reject launches; check Termux settings if no session appears.

### Solana Wallet Tools

Enter a public Solana wallet address. Queries mainnet public RPC for SOL and SPL Token/Token-2022 accounts, displaying mint addresses and quantities. Does not request private keys, connect wallets, sign or transfer funds. Token names, price feeds and transaction history are not included. Public RPC may rate-limit or reject requests; errors are shown.

### Ollama Support

Run Ollama separately and pull a model. Enter your server URL and exact installed model name. On a physical phone, `127.0.0.1` means the phone itself. To reach your computer use its trusted LAN IP, e.g. `http://192.168.1.20:11434`; the server must listen on that interface and the firewall must allow your phone. The emulator commonly uses `http://10.0.2.2:11434`. The APK does not bundle models or an Ollama runtime. HTTP is enabled for local endpoints; use only networks you trust and do not expose an unauthenticated Ollama server to the internet.

### xAI Bring-Your-Own-Key

Enter your own xAI API key and an exact model ID available to your account. It sends chat directly over HTTPS to `https://api.x.ai/v1/chat/completions`. API billing is separate from other subscriptions. Key remains in native activity memory, never stored in HTML, preferences or the repository; re-enter after process/activity recreation. Provider, model and Ollama URL persist locally. Chats stay in memory (last 10 successful turns); switching provider or Clear chat context resets the model context. The API key is not sent to Ollama.

## Validation status

Source structure, XML, JavaScript syntax and workflow configuration were checked when packaged. An Android SDK/Gradle toolchain was unavailable in the creation environment: **no APK compilation, lint run, emulator test or real-device test has been completed**. The included workflow performs `assembleDebug` and `lintDebug`; a green run is required before calling the APK build verified.

After building, test: launch; grant/deny overlay permission; drag/open/stop bubble; browse a website and close it; run `pwd` in Termux; query a known public wallet; chat through an Ollama model; chat through xAI; check invalid URL/model/key handling; rotate/reopen and confirm secrets are not persisted.

## Implementation references

- Android foreground services: https://developer.android.com/about/versions/14/changes/fgs-types-required
- AGP/Gradle compatibility: https://developer.android.com/build/releases/agp-8-7-0-release-notes
- Termux command intent: https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent
- Solana RPC: https://solana.com/docs/rpc/http
- Ollama API: https://github.com/ollama/ollama/blob/main/docs/api.md
- xAI API: https://docs.x.ai/developers/rest-api-reference/inference/chat-completions
