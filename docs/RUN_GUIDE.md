# Run Guide

This guide provides step-by-step instructions for building, installing, and using the Video Temporal Integrity Analyzer application.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Environment Setup](#environment-setup)
3. [Building the Application](#building-the-application)
4. [Installing the APK](#installing-the-apk)
5. [Using the Application](#using-the-application)
6. [Troubleshooting](#troubleshooting)

---

## Prerequisites

### Hardware Requirements
| Component | Minimum | Recommended |
|-----------|---------|-------------|
| CPU | Quad-core | Octa-core |
| RAM | 4 GB | 6 GB |
| Storage | 500 MB | 1 GB |
| Display | 720p | 1080p+ |

### Software Requirements
| Software | Version | Notes |
|----------|---------|-------|
| Operating System | Windows 10/11 | May work on macOS/Linux |
| Java JDK | 25 | Required for Gradle |
| Android SDK | API 34 | For building |
| Android Device | API 24+ | For running app |

### Checking Java Version

```bash
# Windows
java -version

# Expected output:
# openjdk version "25.x.x"
```

---

## Environment Setup

### 1. Install Java JDK 25

Download from: https://adoptium.net/

```bash
# Verify installation
java -version
```

### 2. Install Android SDK

If Android Studio is installed:
```
Android Studio → SDK Manager → SDK Platforms → Check "Android 14 (API 34)"
```

If standalone SDK:
```bash
# Set ANDROID_HOME environment variable
export ANDROID_HOME=C:/Users/YourName/AppData/Local/Android/Sdk
```

### 3. Configure local.properties

Ensure `local.properties` contains SDK path:

```properties
# Windows
sdk.dir=C\:\\Users\\YourName\\AppData\\Local\\Android\\Sdk
```

---

## Building the Application

### Method 1: Using Gradle Wrapper (Recommended)

```bash
# Navigate to project directory
cd E:\Pragyan_khel_team_vertex

# Build debug APK
.\gradlew.bat assembleDebug

# Or on Linux/Mac
./gradlew assembleDebug
```

### Method 2: Using Android Studio

1. Open Android Studio
2. File → Open → Select `Pragyan_khel_team_vertex` folder
3. Wait for Gradle sync to complete
4. Build → Build Bundle(s) / APK(s) → Build APK(s)

### Build Output

After successful build, the APK will be at:

```
app/build/outputs/apk/debug/app-debug.apk
```

### Build Troubleshooting

| Error | Solution |
|-------|----------|
| "Java 25 not supported" | Update Gradle to 9.1.0 in `gradle-wrapper.properties` |
| "SDK not found" | Check `local.properties` has correct SDK path |
| "Kotlin compilation error" | Ensure Kotlin version 2.1.0 in `build.gradle.kts` |

---

## Installing the APK

### Option 1: Using ADB (Recommended)

```bash
# Connect device via USB (ensure USB debugging enabled)
adb devices

# Install APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# To reinstall (overwrite)
adb install -r -d app/build/outputs/apk/debug/app-debug.apk
```

### Option 2: Manual Installation

1. Transfer `app-debug.apk` to your Android device
2. Open File Manager
3. Navigate to the APK file
4. Tap to install
5. If prompted, enable "Install from unknown sources"

### Enabling USB Debugging

On your Android device:
1. Settings → About Phone
2. Tap "Build Number" 7 times
3. Settings → Developer Options
4. Enable "USB Debugging"

---

## Using the Application

### Launching the App

```
Find "Video Temporal Integrity Analyzer" in your app drawer
OR
adb shell am start -n com.videoanalyzer/.MainActivity
```

### Step-by-Step Usage

#### 1. Select Video

```
App Launch
    ↓
Tap "Select Video" button
    ↓
File picker opens
    ↓
Choose video file
    ↓
Video metadata displayed
```

#### 2. Analyze Video

```
Video selected
    ↓
Review metadata (duration, FPS, resolution)
    ↓
Tap "Start Analysis"
    ↓
Progress screen shows:
    - Extracting timestamps
    - Analyzing timestamps
    - Extracting frames
    - Detecting merges
    ↓
Results displayed
```

#### 3. View Results

**Main Results Screen:**
- Timeline visualization
- Statistics cards (Normal/Drops/Merges)
- Summary information
- Detection method description

**Viewing Timestamps:**

```
Tap on "Normal" card
    ↓
Bottom sheet opens
    ↓
Shows list: "Frame X → MM:SS.mmm"
    ↓
Scroll to view all timestamps
    ↓
Tap outside or swipe down to close
```

Same for "Drops" and "Merges" cards.

### Result Interpretation

| Metric | Description |
|--------|-------------|
| Total | Expected frames based on duration |
| Normal | Correct frames at correct times |
| Drops | Missing frames (gaps in stream) |
| Merges | Duplicate/merged frames |

---

## Understanding Results

### Timeline Bar

The timeline shows the entire video with color-coded segments:

| Color | Meaning |
|-------|---------|
| Green | Normal frame |
| Red | Dropped frame |
| Amber | Merged frame |

Time markers show:
- Start: 0:00
- Middle: duration/2
- End: total duration

### Timestamp Format

Timestamps are displayed as:

```
Frame N  →  M:SS.mmm
```

Where:
- N = Frame index (0-based)
- M = Minutes
- SS = Seconds
- mmm = Milliseconds

Example: `Frame 150 → 4:59.234`

---

## Troubleshooting

### App Crashes on Launch

**Possible Causes:**
1. Insufficient storage
2. Corrupted installation

**Solutions:**
```bash
# Uninstall previous version
adb uninstall com.videoanalyzer

# Clean install
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Analysis Takes Too Long

**Possible Causes:**
1. Large video file
2. High resolution (4K+)

**Solutions:**
- Use shorter videos for testing
- Reduce video resolution

### No Frames Detected

**Possible Causes:**
1. Unsupported video format
2. Corrupted video file

**Supported Formats:**
- MP4 (H.264/H.265)
- MKV
- WebM
- AVI

### Incorrect Results

**Known Limitations:**
- Variable frame rate videos may have slight inaccuracies
- Very dark or very bright videos may affect merge detection
- Videos with significant compression artifacts may show false positives

---

## Uninstalling

```bash
# Via ADB
adb uninstall com.videoanalyzer

# Via Device
Settings → Apps → Video Temporal Integrity Analyzer → Uninstall
```

---

## Additional Information

### Processing Time Estimate

| Video Length | Resolution | Estimated Time |
|-------------|------------|----------------|
| 5 seconds | 720p | 10-15 seconds |
| 30 seconds | 1080p | 45-60 seconds |
| 1 minute | 4K | 2-3 minutes |

### Battery Usage

During analysis, the app uses:
- CPU: High
- Memory: ~200MB
- Battery: Moderate to High

### Privacy

- All processing is done **on-device**
- No data is sent to any server
- No internet permission required

---

## Contact & Support

For technical issues or questions:
1. Review the Algorithm Documentation (`docs/ALGORITHM.md`)
2. Check the Parameters Reference (`docs/PARAMETERS.md`)
3. Examine the source code in `app/src/main/java/com/videoanalyzer/`
