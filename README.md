# Bulk Scanner 📄🚀

A visually polished, offline, bulk-document scanner application for Android, built with **Kotlin** and **Jetpack Compose**. The app adheres strictly to **Material Design 3 (M3)** standards, featuring a dark, high-contrast visual theme designed for low-light efficiency and professional use.

Whether you need to quickly scan single receipts or bulk-compile multi-page documents, Bulk Scanner handles the entire pipeline—from smart camera capture to interactive perspective cropping, real-time image enhancements, page reordering, and final compilation into compact PDF files or ZIP archives.

---

## 🌟 Key Features

### 1. Advanced Scan Configuration
- **Batch Customization**: Set specific batch names with automatic time-stamped defaults.
- **Multiple Capture Modes**: Swap between **Bulk** scan workflows and **Single** page grabs.
- **Intelligent Capture Triggers**: Choose between **Auto-Capture** (with computer vision stability lock) and **Manual Shutter** control.
- **Preconfigured Enhancements**: Set target presets such as *Document*, *Handwriting*, *Color*, *Black & White*, *Low Light*, or *Original*.
- **Flexible Default Formats**: Select your target compilation file format (PDF, Images, or Both).

### 2. Dual-Mode Viewfinder (CameraX & Interactive Simulator)
- **Real Camera Engine**: Built on the Android Jetpack **CameraX** API for optimized capture speed, hardware flashlight integration, and low-latency image stream.
- **Interactive Desk Simulator**: A dynamic, drift-enabled workspace preview designed to simulate physical document scanning. Change document types, calibrate margins, and adjust alignment sliders to test the engine's behaviors seamlessly without needing a physical camera.
- **Smart Perspective HUD Overlay**: A responsive neon-colored quad frame that wraps pages, changing colors dynamically (*Red* for misaligned, *Yellow* for stabilizing, *Neon Green* for locked status) with automatic countdown circular rings.
- **Live Warning Systems**: Active HUD sensors alerting users to *Motion Blur*, *Low-Light environments*, *Misaligned paper boundaries*, or *Active Camera movement*.

### 3. Interactive Page Editor
- **Precision Crop Tool**: Readjust perspective boundaries on the original document with drag-and-drop crop handles. Features a **magnifying glass viewport bubble** that pops up over your dragging finger for pixel-perfect adjustments.
- **Rotate Tool**: Easily rotate individual pages in 90-degree steps.
- **Preset Enhancements**: Dynamically apply or switch specialized filter presets (*Document contrast correction, Handwriting cleanup, Vibrant color boost, High-contrast black & white*) to maximize readability.

### 4. Review Grid & Document Compiler
- **Rearrange & Reorder**: Shift scanned pages left or right instantly to correct binding sequences.
- **Warning Filters**: Filter the list to display *Problems Only* to quickly locate blurry or badly-cropped pages.
- **Error-Resistant Workflow**: One-tap **Undo** capability in the top bar to instantly recover accidentally deleted pages.
- **Batch Exporter**: Bottom sheet compilation tool supporting instant PDF building and compact ZIP archives.

---

## 🛠️ Technology Stack & Architecture

- **Language**: Kotlin (100% type-safe, built with coroutines and flows)
- **UI Framework**: Jetpack Compose (Material Design 3)
- **Navigation**: Jetpack Navigation Compose with type-safe route serializations
- **Data Persistence**: **Room Database** (SQLite backend) to store batch entities, page processing configs, coordinates, and metadata safely across app restarts.
- **Image Loading**: Coil for high-performance cached image previews
- **Architecture Pattern**: MVVM (Model-View-ViewModel) with robust reactive `StateFlow` state pipelines.

---

## 🚀 How to Build and Install

### Prerequisites
- **Android Studio** (Koala or newer recommended)
- **Android SDK** (Compile SDK: `34`, Target SDK: `34`, Min SDK: `26` for Room and CameraX compliance)
- **JDK 17** (Required by Gradle and modern AGP)

### Building the Project
You can build the application directly from the command line using Gradle. 

> **Important**: Always use `gradle` instead of `./gradlew` in this development workspace.

1. **Clean the project build**:
   ```bash
   gradle clean
   ```

2. **Assemble the Debug APK**:
   ```bash
   gradle assembleDebug
   ```
   *The compiled APK file will be generated at:*  
   `app/build/outputs/apk/debug/app-debug.apk`

3. **Run Unit & Robolectric Tests**:
   ```bash
   gradle :app:testDebugUnitTest
   ```

4. **Verify UI & Screens via Screenshot Tests (Roborazzi)**:
   ```bash
   gradle :app:verifyRoborazziDebug
   ```

---

## 🤖 GitHub Actions CI/CD (Auto-Release Workflow)

A professional automatic release pipeline is configured in `.github/workflows/release.yml`. It builds your debug APK and automatically deploys a new GitHub Release when version tags are pushed.

### 🔑 Setting up `GITHUB_TOKEN`
The `${{ secrets.GITHUB_TOKEN }}` variable used in the workflow is a **built-in secret automatically generated by GitHub** for every repository run. You **do not** need to manually create or paste any token! 

However, because the workflow creates a Release and uploads assets, you must ensure that your repository grants **Write permissions** to GitHub Actions:
1. Go to your repository on [GitHub](https://github.com).
2. Click on the **Settings** tab (the gear icon on the top right).
3. In the left navigation menu, under **Actions**, click on **General**.
4. Scroll down to the **Workflow permissions** section.
5. Select **Read and write permissions**.
6. Click **Save**.

### ⚡ How to Trigger a Build in GitHub
There are three ways to trigger a build workflow on GitHub:

1. **Pushing to Branch (`main` / `master`)**:
   - Every push to `main` or `master` will trigger the build job. It will checkout the code, decode the debug keystore, and build & compile the APK, uploading it as a safe build artifact for download in the Actions run dashboard.
2. **Pushing a Version Tag (Triggers GitHub Release)**:
   - When you are ready to ship a release, tag your commit and push it to GitHub:
     ```bash
     git tag v1.0.0
     git push origin v1.0.0
     ```
   - This triggers the full pipeline. It compiles the APK, creates a fresh GitHub Release named `Release v1.0.0`, and automatically attaches the compiled `app-debug.apk` file directly to the release notes page.
3. **Manual Trigger (Workflow Dispatch)**:
   - Go to your repository's **Actions** tab on GitHub.
   - Click on the **Android APK Auto-Release** workflow in the left-hand list.
   - Click the **Run workflow** dropdown button on the right, select your branch, and click the green **Run workflow** button to execute the build on-demand at any time.

---

## 🧪 Testing Requirements

To ensure the application remains stable and bug-free across iterations, we adhere to a strict testing protocol.

### Scope of Testing
- **Unit Tests**: Added robust coverage for pure business logic inside ViewModels, Daos, and helper utility modules.
- **UI & Flow Tests**: Designed to verify interactive workflows across major screens (Scan Configuration, Live HUD Overlay, Editor Screen, and Review Grid).
- **Integration Tests**: Tested cohesive sequences including:
  - Permission handling simulator & camera/environment warnings.
  - Multi-page scanning simulation, coordinate-based cropping adjustments, rotation angle shifts, and filters preset applications.
  - Page lifecycle events: deletion cascading, undo history stack restoration, and drag-and-drop ordering sequences.
  - Compilation outputs: multi-page PDF assembling and compact ZIP folder aggregation.

### Test Suite Execution
We implement a **Red-Green-Refactor development loop**. After introducing any major feature or updating UI widgets, run the full test suite locally and verify all states pass successfully before staging changes:
```bash
gradle :app:testDebugUnitTest
```

---

## 📲 Installing on a Device / Emulator

### Method 1: Android Studio (Recommended)
1. Open Android Studio.
2. Select **File > Open** and choose this project's root folder.
3. Wait for the Gradle Sync to complete.
4. Connect an Android device with **USB Debugging** enabled, or start an Android Virtual Device (AVD).
5. Click the green **Run (Play)** button in the top toolbar or press `Shift + F10`.

### Method 2: Manual ADB Command (After compiling)
If you have the Android SDK Platform Tools installed on your computer, you can install the generated APK using:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 📝 License

This project is licensed under the MIT License. See the `LICENSE` file for details.
