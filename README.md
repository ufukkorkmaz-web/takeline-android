# Takeline Studio for Android

An Android Studio project packaging your supplied Takeline Studio website into an Android WebView app. UI and audio editing remain in the original HTML/JS; recordings and scripts remain in the WebView's IndexedDB storage.

## Build
1. Install Android Studio (with Android SDK 35 and JDK 17).
2. Open this **TakelineStudioAndroid** folder as an Android Studio project, then let Gradle sync (requires internet for build dependencies).
3. Choose **Build > Build APK(s)** for a debug installation, or **Build > Generate Signed App Bundle / APK** to create a signed release.
4. For Play Store distribution, use the `.aab` output, a permanent signing key, and Play Console release management.

## Updating
Edit `app/src/main/assets/index.html` for website/UI changes. For every release increment `versionCode` (1 -> 2 -> 3...) and update `versionName` in `app/build.gradle`. Keep the **same `applicationId` and signing key**, and distribute through the same channel. Android's normal app updates retain IndexedDB storage, but users should export audio backups. This project packages the website: changing a separately hosted website **does not** update the app automatically.

## Important details
- The app loads its bundled UI at the trustworthy `https://appassets.androidplatform.net/assets/index.html` origin, not `file://`. This allows browser media APIs and IndexedDB.
- Android's microphone permission and WebView audio-capture permission are both handled. Test on actual Android hardware.
- Audio export and take downloads open Android's Save As document picker. The native bridge streams blobs in ~96 KiB chunks, avoiding huge one-shot base64 transfers.
- PDF and DOCX imports work through Android's document picker. The original website downloads PDF.js and Mammoth from a CDN on demand; first use needs internet. MP3 exporting also needs internet on first use, per original code.
- Browser speech recognition / Follow My Voice may not be available in Android WebView. A native Android speech-recognition integration would be a separate feature.
- Responsive UI is preserved but the original site is desktop-oriented: review touch and small-screen layout on devices.
- App is **not built or device-tested** here. This is source code, not a signed APK/AAB.
- The native JavaScript bridge assumes only trusted bundled content is loaded. Never allow untrusted pages or third-party frames into this WebView without further hardening.

## Privacy
Recordings remain on the device in local app data, except optional third-party browser speech recognition (if available) and any remote libraries fetched when needed. Uninstalling or clearing app data can remove saved takes. Android backup is disabled to avoid unexpected transfers of recorded audio.

## Build in a browser (no Android Studio)
1. Create a **private** GitHub repository, e.g. `takeline-android`.
2. Extract this ZIP. Upload the project contents (the files inside `TakelineStudioAndroid`, not that outer directory) at the repository root with GitHub's **Add file > Upload files**; ensure `.github/workflows/build-apk.yml` is present. Some operating systems hide the `.github` folder, so create that file through GitHub's **Add file > Create new file** if needed.
3. Commit to `main`. Open the repository's **Actions** tab, select **Build Android APK**, and click **Run workflow**, or open the automatically started run.
4. When the run has succeeded, open the run page and download **Takeline-Studio-debug-APK** under **Artifacts**. Unzip that download to find `app-debug.apk`.
5. Transfer the APK to your Android phone and open it to install. Android may ask you to allow installation from the app you use to open the file.

**Debug builds are for testing only.** Each separate cloud runner can generate a different debug signing key, which can prevent a new APK from installing as an update over the prior one. For reliable in-place updates and Play Store distribution, configure persistent *release* signing, protect signing credentials, keep the same `applicationId`, and increase `versionCode`. Do not uninstall the app between versions without exporting your recordings first; uninstalling can delete local app data.
