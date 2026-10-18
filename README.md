# ARS DJ — Android Studio source + cloud APK build

This repository package contains the initial Kotlin/Jetpack Compose UI scaffold and a GitHub Actions workflow to build a debug APK in the cloud.

## Build an APK using GitHub Actions
1. Create a GitHub repository in a browser (if github.com does not load, try switching network/browser or use a computer later).
2. Upload the *contents* of this folder to the repository root, including `.github/workflows/android-apk.yml` and the `android/` folder.
3. Commit the files to the `main` branch.
4. Open the repository's **Actions** tab. If asked, enable workflows.
5. Select **Build ARS DJ APK** and tap **Run workflow** (or push to `main` to trigger the workflow automatically).
6. Wait for the run to finish successfully. Open the completed run and download the artifact named **ARS-DJ-debug-apk**.
7. Extract the artifact ZIP and install `app-debug.apk` on your phone. Android may ask you to allow installs from that browser/files app.

## Important
- This creates a **debug/test APK**, not a production-signed release.
- The workflow has not been run or verified from this environment.
- The current app is an initial UI scaffold. Real mixing, audio rendering/export, and AI singing are not implemented yet.
- Do not put API keys or secrets in the Android app source.
