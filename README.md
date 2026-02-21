# Video Temporal Integrity Analyzer

A production-ready Android application that detects temporal inconsistencies in video streams, including frame drops and frame merges, using advanced timestamp analysis and pixel-based detection algorithms.

## Overview

Video Temporal Integrity Analyzer is a standalone Android application that processes video files locally on the device to identify:

- **Frame Drops**: Missing frames due to encoding issues or transmission losses
- **Frame Merges**: Duplicate or merged frames that should be separate

The application uses a hybrid approach combining PTS (Presentation Timestamp) analysis for drop detection and multi-frame pixel comparison for merge detection.

## Features

### Core Capabilities
- **Local Processing**: All analysis runs on-device, no internet required
- **Accurate Drop Detection**: Uses MODE-based interval calculation
- **Advanced Merge Detection**: Multi-frame pixel comparison with rolling buffer
- **Interactive Results**: Visual timeline with clickable frame categories
- **Timestamp Details**: View exact timestamps for Normal, Drop, and Merge frames

### User Interface
- Clean, modern dark-themed UI
- Real-time progress indicators
- Visual timeline representation
- Detailed frame classification with timestamps
- Summary statistics with percentages

## Technical Architecture

### Technology Stack
| Component | Technology |
|-----------|------------|
| Language | Kotlin 2.1.0 |
| UI Framework | Jetpack Compose |
| Architecture | MVVM |
| Video Processing | Media3 + MediaMetadataRetriever |
| Build System | Gradle 9.1.0 |
| Target SDK | 34 (Android 14) |
| Min SDK | 24 (Android 7.0) |

### Project Structure
```
app/src/main/java/com/videoanalyzer/
├── MainActivity.kt          # Entry point
├── VideoAnalyzerApp.kt     # Application class
├── data/
│   └── processor/
│       └── VideoProcessor.kt   # Core algorithm
├── domain/
│   └── model/
│       └── Models.kt           # Data models
└── ui/
    ├── screens/
    │   ├── MainScreen.kt       # Main UI
    │   └── MainViewModel.kt    # ViewModel
    └── theme/
        └── Theme.kt            # Theme definitions
```

## Algorithm Overview

### Drop Detection Algorithm

1. **PTS Extraction**: Extract presentation timestamps from video track
2. **MODE Interval Calculation**: Find most common interval between consecutive frames
3. **Expected Frames**: `Total = Duration / Expected_Interval`
4. **Drop Detection**: Identify gaps larger than `Expected_Interval × 1.5`

### Merge Detection Algorithm

1. **Frame Extraction**: Extract frames using MediaMetadataRetriever
2. **Pixel Scaling**: Scale to 64×36 for efficient comparison
3. **Rolling Buffer**: Maintain last 10 frames in memory
4. **Multi-frame Comparison**: Compare current frame with buffer
5. **Merge Criteria**: 
   - Similar to ≥2 frames in buffer (diff < 10.0)
   - Average similarity < 15.0

## Build Instructions

### Prerequisites
- Java Development Kit (JDK) 25
- Android SDK with API 34
- Gradle 9.1.0 (included via wrapper)

### Build Commands

```bash
# Navigate to project directory
cd Pragyan_khel_team_vertex

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Clean build
./gradlew clean assembleDebug
```

### Output
Debug APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

## Installation

### Via ADB
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Manual Installation
1. Transfer the APK to your Android device
2. Enable "Install from unknown sources" in settings
3. Open the APK file and install

## Usage Guide

### Selecting a Video
1. Launch the application
2. Tap "Select Video" button
3. Choose a video file from your device

### Viewing Results
1. Select a video to analyze
2. Tap "Start Analysis"
3. Wait for processing to complete

### Understanding Results
- **Total**: Total expected frames based on video duration
- **Normal**: Frames that appear at correct intervals with normal content
- **Drops**: Frames that are missing from the stream
- **Merges**: Frames that contain duplicate/merged content

### Viewing Timestamps
- Tap on any category card (Normal/Drops/Merges)
- A bottom sheet displays all timestamps for that category
- Format: `Frame X → MM:SS.mmm`

## Parameters Reference

### Drop Detection
| Parameter | Value | Description |
|-----------|-------|-------------|
| DROP_THRESHOLD_MULTIPLIER | 1.5 | Gap threshold for drop detection |

### Merge Detection
| Parameter | Value | Description |
|-----------|-------|-------------|
| MERGE_DIFF_THRESHOLD | 10.0 | Max diff for "similar" classification |
| MERGE_BUFFER_SIZE | 5 | Recent frames to compare |
| MERGE_SIMILARITY_COUNT | 2 | Min similar frames to flag merge |
| Frame Scale | 64×36 | Resolution for pixel comparison |

## License

This project is developed for educational and research purposes.

## Support

For issues or questions, please refer to the algorithm documentation in the `docs/` folder.
