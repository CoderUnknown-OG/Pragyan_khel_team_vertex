# Algorithm Documentation

This document provides detailed explanation of the algorithms used in the Video Temporal Integrity Analyzer for detecting frame drops and frame merges.

---

## Table of Contents

1. [Drop Detection Algorithm](#drop-detection-algorithm)
2. [Merge Detection Algorithm](#merge-detection-algorithm)
3. [Pixel Difference Method](#pixel-difference-method)
4. [Mathematical Formulas](#mathematical-formulas)

---

## Drop Detection Algorithm

### Overview

The drop detection algorithm identifies missing frames in a video stream by analyzing the Presentation Timestamps (PTS) of video frames. It uses statistical analysis to determine the expected frame interval and identifies drops when gaps exceed this threshold.

### Algorithm Steps

#### Step 1: PTS Extraction

```
Input: Video file (URI)
Output: Sorted list of presentation timestamps
```

Using `MediaExtractor`:
```kotlin
val extractor = MediaExtractor()
extractor.setDataSource(context, uri, null)

val timestamps = mutableListOf<Long>()
while (extractor.sampleTime >= 0) {
    timestamps.add(extractor.sampleTime)  // microseconds
    if (!extractor.advance()) break
}
val sortedPts = timestamps.sorted()
```

**Key Points:**
- Extracts PTS in microseconds (μs)
- Sorts timestamps to handle B-frame reordering
- Each timestamp represents when a frame should be displayed

#### Step 2: Calculate MODE Interval

```
Input: Sorted PTS list
Output: Most common interval between frames (μs)
```

The MODE (most frequent value) represents the expected frame interval:

```kotlin
private fun calculateModeInterval(pts: List<Long>): Long {
    val intervals = mutableListOf<Long>()
    for (i in 1 until pts.size) {
        val interval = pts[i] - pts[i - 1]
        if (interval > 0) intervals.add(interval)
    }
    
    // Round to nearest 100μs for precision
    val rounded = intervals.map { ((it / 100) * 100) }
    val grouped = rounded.groupingBy { it }.eachCount()
    
    return grouped.maxByOrNull { it.value }?.key ?: intervals.average().toLong()
}
```

**Why MODE?**
- Video frame rates are typically constant (24, 25, 30, 60 fps)
- The MODE interval represents the actual frame rate
- Handles variable frame rates better than average

#### Step 3: Calculate Expected Frames

```
Formula: Expected Frames = Duration / Expected Interval

Where:
- Duration in microseconds
- Expected Interval in microseconds
- Result in frames
```

```kotlin
val durationUs = (metadata.duration * 1_000_000).toLong()  // seconds → μs
val expectedFrames = (durationUs / expectedInterval).toInt()
```

**Example:**
```
Video Duration: 5.7 seconds
Expected Interval: 33,333 μs (30 fps)

Expected Frames = 5,700,000 / 33,333 = 171 frames
```

#### Step 4: Detect Dropped Frames

```
Logic: If gap between consecutive PTS > threshold → frame(s) dropped

Threshold = Expected Interval × 1.5
```

```kotlin
private const val DROP_THRESHOLD_MULTIPLIER = 1.5f

private fun detectDropFrames(pts: List<Long>, expectedInterval: Long): List<DropInfo> {
    val threshold = expectedInterval * DROP_THRESHOLD_MULTIPLIER
    val drops = mutableListOf<DropInfo>()
    
    for (i in 1 until pts.size) {
        val gap = pts[i] - pts[i - 1]
        
        if (gap > threshold) {
            // Calculate how many frames are missing
            val missingCount = ((gap / expectedInterval).toInt() - 1)
            
            if (missingCount > 0) {
                for (j in 0 until missingCount) {
                    drops.add(DropInfo(
                        frameIndex = i + j,
                        timestampUs = pts[i - 1] + ((j + 1) * expectedInterval),
                        gap = gap,
                        missingCount = missingCount
                    ))
                }
            }
        }
    }
    return drops
}
```

**Example:**
```
Expected Interval: 33,333 μs
Threshold: 33,333 × 1.5 = 49,999 μs
Actual Gap: 100,000 μs (3 frames worth)

Missing Count = (100,000 / 33,333) - 1 = 3 - 1 = 2 frames dropped
```

---

## Merge Detection Algorithm

### Overview

The merge detection algorithm identifies duplicate or merged frames by comparing pixel content across multiple consecutive frames. It uses a rolling buffer approach to detect repeated content patterns.

### Algorithm Steps

#### Step 1: Frame Extraction

```
Input: PTS timestamp
Output: Scaled pixel array (64×36)
```

```kotlin
val bitmap = retriever.getFrameAtTime(currentPts, MediaMetadataRetriever.OPTION_CLOSEST)
val currentPixels = getScaledPixels(bitmap, 64, 36)
```

**Why 64×36?**
- Reduces computation by 96% compared to full HD
- Still maintains enough detail for merge detection
- 2,304 pixels (64 × 36) is computationally efficient

#### Step 2: Rolling Buffer

```
Purpose: Store recent frames for multi-frame comparison
Size: 10 frames (MERGE_BUFFER_SIZE × 2)
```

```kotlin
val framePixelBuffer = mutableListOf<Pair<Int, IntArray>>()
// Stores: (frameIndex, pixelArray)

// Add new frame
framePixelBuffer.add(Pair(i, currentPixels))

// Keep buffer size limited
if (framePixelBuffer.size > MERGE_BUFFER_SIZE * 2) {
    framePixelBuffer.removeAt(0)
}
```

**Why a buffer?**
- Single-frame comparison causes false positives in low-motion videos
- True merges show similarity to MULTIPLE recent frames
- Buffer enables pattern detection

#### Step 3: Count Similar Frames

```kotlin
private fun countSimilarFrames(
    buffer: List<Pair<Int, IntArray>>,
    currentPixels: IntArray,
    threshold: Float
): Int {
    var count = 0
    for ((_, pixels) in buffer) {
        val diff = calculatePixelDiff(pixels, currentPixels)
        if (diff < threshold) {
            count++
        }
    }
    return count
}
```

#### Step 4: Merge Detection Criteria

```
MERGE detected if:
  1. similarCount >= 2 (similar to at least 2 frames in buffer)
  2. avgMotion < 15.0 (very high similarity)
```

```kotlin
if (i > MERGE_BUFFER_SIZE && motionScore > 0) {
    val similarCount = countSimilarFrames(
        framePixelBuffer.takeLast(MERGE_BUFFER_SIZE),
        currentPixels,
        MERGE_DIFF_THRESHOLD
    )
    
    val avgMotion = framePixelBuffer.takeLast(MERGE_BUFFER_SIZE).map {
        calculatePixelDiff(it.second, currentPixels)
    }.average()
    
    if (similarCount >= MERGE_SIMILARITY_COUNT && avgMotion < MERGE_DIFF_THRESHOLD * 1.5) {
        classification = FrameClassification.FRAME_MERGE
    }
}
```

---

## Pixel Difference Method

### Overview

The pixel difference method quantifies visual similarity between two frames by calculating the average Euclidean distance between corresponding pixels in RGB color space.

### Formula

For each pixel position `(x, y)`:

```
diff(x,y) = √((R₁ - R₂)² + (G₁ - G₂)² + (B₁ - B₂)²)

Average Difference = Σ(diff(x,y)) / (width × height)
```

### Implementation

```kotlin
private fun calculatePixelDiff(pixels1: IntArray, pixels2: IntArray): Float {
    var totalDiff = 0.0
    
    for (i in pixels1.indices) {
        // Extract RGB components from 32-bit integer
        val r1 = (pixels1[i] shr 16) and 0xFF
        val g1 = (pixels1[i] shr 8) and 0xFF
        val b1 = pixels1[i] and 0xFF
        
        val r2 = (pixels2[i] shr 16) and 0xFF
        val g2 = (pixels2[i] shr 8) and 0xFF
        val b2 = pixels2[i] and 0xFF
        
        // Calculate Euclidean distance in RGB space
        totalDiff += kotlin.math.sqrt(
            ((r1 - r2) * (r1 - r2) +
            (g1 - g2) * (g1 - g2) +
            (b1 - b2) * (b1 - b2)).toDouble()
        )
    }
    
    return (totalDiff / pixels1.size).toFloat()
}
```

### RGB Extraction

Each pixel is stored as a 32-bit integer: `0xRRGGBBAA`

```kotlin
// Bit layout: AA RR GG BB
val r = (pixel shr 16) and 0xFF  // Red: bits 16-23
val g = (pixel shr 8) and 0xFF   // Green: bits 8-15
val b = pixel and 0xFF            // Blue: bits 0-7
```

### Example Calculation

**Frame 1, Pixel(0,0):** RGB(100, 150, 200)
**Frame 2, Pixel(0,0):** RGB(110, 145, 205)

```
r_diff = 100 - 110 = -10
g_diff = 150 - 145 = 5
b_diff = 200 - 205 = -5

distance = √((-10)² + 5² + (-5)²)
         = √(100 + 25 + 25)
         = √150
         = 12.25
```

**For 2,304 pixels with average distance of 8.5:**
```
totalDiff = 8.5 × 2,304 = 19,584
average = 19,584 / 2,304 = 8.5
```

### Interpretation

| Average Diff | Interpretation |
|--------------|----------------|
| 0 - 10 | Very similar (likely merge/duplicate) |
| 10 - 20 | Low motion |
| 20 - 50 | Normal motion |
| 50+ | High motion / scene change |

---

## Mathematical Formulas

### 1. Expected Frame Interval

```
E = MODE(t₁ - t₀, t₂ - t₁, ..., tₙ - tₙ₋₁)

Where:
- E = Expected interval (μs)
- t = Sorted presentation timestamps
- MODE = Most frequent value
```

### 2. Expected Total Frames

```
F = D / E

Where:
- F = Expected number of frames
- D = Video duration (μs)
- E = Expected interval (μs)
```

### 3. Drop Detection Threshold

```
T = E × 1.5

Where:
- T = Threshold for drop detection (μs)
- E = Expected interval (μs)
```

### 4. Pixel Difference (Euclidean)

```
D = (1/N) × Σ√((Rᵢ₋₁ - Rᵢ)² + (Gᵢ₋₁ - Gᵢ)² + (Bᵢ₋₁ - Bᵢ)²)

Where:
- D = Average difference
- N = Total pixels (2,304 for 64×36)
- RGB = Color components
```

### 5. Merge Detection Condition

```
MERGE = (similarCount ≥ 2) ∧ (avgMotion < 15.0)

Where:
- similarCount = Frames with diff < 10.0 in buffer
- avgMotion = Average diff with buffer frames
```

---

## Constants Reference

| Constant | Value | Purpose |
|----------|-------|---------|
| DROP_THRESHOLD_MULTIPLIER | 1.5 | Drop detection sensitivity |
| MERGE_DIFF_THRESHOLD | 10.0 | Merge similarity threshold |
| MERGE_BUFFER_SIZE | 5 | Buffer size for comparison |
| MERGE_SIMILARITY_COUNT | 2 | Min similar frames |
| Frame Resolution | 64×36 | Pixel comparison size |

---

## Algorithm Limitations

### Drop Detection
- May not detect drops at video beginning/end
- Assumes relatively constant frame rate
- Cannot detect if frames are added (only missing)

### Merge Detection
- False positives possible in static/low-motion videos
- Limited buffer size may miss patterns
- Performance scales with video length

---

## Performance Characteristics

| Operation | Time Complexity | Space Complexity |
|-----------|---------------|------------------|
| PTS Extraction | O(n) | O(n) |
| MODE Calculation | O(n log n) | O(n) |
| Drop Detection | O(n) | O(n) |
| Frame Extraction | O(n) | O(1) per frame |
| Merge Detection | O(n × m) | O(m) |

Where:
- n = Total frames
- m = Buffer size (10)
