# Parameters Reference

This document provides detailed reference for all constants, parameters, and thresholds used in the Video Temporal Integrity Analyzer algorithms.

---

## Table of Contents

1. [Drop Detection Parameters](#drop-detection-parameters)
2. [Merge Detection Parameters](#merge-detection-parameters)
3. [Image Processing Parameters](#image-processing-parameters)
4. [Algorithm Constants](#algorithm-constants)
5. [UI Display Parameters](#ui-display-parameters)

---

## Drop Detection Parameters

### DROP_THRESHOLD_MULTIPLIER

| Property | Value |
|----------|-------|
| **Constant Name** | `DROP_THRESHOLD_MULTIPLIER` |
| **Value** | `1.5f` |
| **Type** | Float |
| **Location** | `VideoProcessor.kt` - Line 24 |

#### Description

Multiplied with the expected frame interval to determine the threshold for detecting dropped frames. If the gap between two consecutive frames exceeds this threshold, the frame(s) in between are considered dropped.

#### Formula

```
Drop Threshold = Expected Interval × DROP_THRESHOLD_MULTIPLIER
```

#### Example

```
Expected Interval: 33,333 μs (30 fps)
DROP_THRESHOLD_MULTIPLIER: 1.5
Drop Threshold: 33,333 × 1.5 = 49,999 μs

If actual gap: 66,666 μs (2 frames worth)
→ 66,666 > 49,999 → DROP detected
```

#### Effect on Results

| Value | Effect |
|-------|--------|
| 1.0 | Very strict - small gaps flagged as drops |
| 1.5 | Balanced (recommended) |
| 2.0 | Relaxed - only large gaps flagged |
| >2.0 | May miss actual drops |

---

## Merge Detection Parameters

### MERGE_DIFF_THRESHOLD

| Property | Value |
|----------|-------|
| **Constant Name** | `MERGE_DIFF_THRESHOLD` |
| **Value** | `10.0f` |
| **Type** | Float |
| **Location** | `VideoProcessor.kt` - Line 25 |

#### Description

Maximum pixel difference threshold to consider two frames as "similar." If the average pixel difference between frames is below this value, they are considered potentially merged/duplicate.

#### Formula

```
Similar if: pixelDiff < MERGE_DIFF_THRESHOLD
```

#### Pixel Difference Scale

| Diff Range | Interpretation |
|------------|----------------|
| 0 - 5 | Nearly identical (likely merge) |
| 5 - 10 | Very similar (possible merge) |
| 10 - 20 | Low motion |
| 20 - 50 | Normal motion |
| 50+ | High motion / scene change |

#### Effect on Results

| Value | Effect |
|-------|--------|
| 2.0 | Very sensitive - many false positives |
| 10.0 | Balanced (recommended) |
| 15.0 | Strict - fewer false positives |
| 20.0+ | May miss actual merges |

---

### MERGE_BUFFER_SIZE

| Property | Value |
|----------|-------|
| **Constant Name** | `MERGE_BUFFER_SIZE` |
| **Value** | `5` |
| **Type** | Int |
| **Location** | `VideoProcessor.kt` - Line 26 |

#### Description

Number of recent frames to keep in the rolling buffer for merge comparison. The actual buffer stores `MERGE_BUFFER_SIZE × 2 = 10` frames.

#### How It Works

```
Buffer stores: [frame_n-10, frame_n-9, ..., frame_n-1]

When processing frame_n:
- Compare with last 5 frames in buffer
- Count how many are similar
```

#### Effect on Results

| Value | Effect |
|-------|--------|
| 0 | Compare with all previous frames (memory intensive) |
| 3 | Faster, but may miss patterns |
| 5 | Balanced (recommended) |
| 10+ | Slower, diminishing returns |

---

### MERGE_SIMILARITY_COUNT

| Property | Value |
|----------|-------|
| **Constant Name** | `MERGE_SIMILARITY_COUNT` |
| **Value** | `2` |
| **Type** | Int |
| **Location** | `VideoProcessor.kt` - Line 27 |

#### Description

Minimum number of frames in the buffer that must be similar to the current frame to classify it as a MERGE.

#### Detection Formula

```
MERGE if:
  (similarCount >= MERGE_SIMILARITY_COUNT) AND (avgMotion < MERGE_DIFF_THRESHOLD * 1.5)
```

#### Example

```
MERGE_BUFFER_SIZE: 5 (buffer has last 5 frames)
MERGE_SIMILARITY_COUNT: 2
MERGE_DIFF_THRESHOLD: 10.0

Current frame compared with buffer:
- Frame -5: diff = 8.0  → similar (< 10)
- Frame -4: diff = 12.0 → not similar
- Frame -3: diff = 6.0  → similar (< 10)
- Frame -2: diff = 9.0  → similar (< 10)
- Frame -1: diff = 15.0 → not similar

similarCount = 3

Result: 3 >= 2 → MERGE detected ✓
```

#### Effect on Results

| Value | Effect |
|-------|--------|
| 1 | Very sensitive - single similar frame flags merge |
| 2 | Balanced (recommended) |
| 3+ | Strict - requires multiple similar frames |

---

## Image Processing Parameters

### Frame Scale Resolution

| Property | Value |
|----------|-------|
| **Width** | 64 pixels |
| **Height** | 36 pixels |
| **Total Pixels** | 2,304 |
| **Location** | `VideoProcessor.kt` - Lines 157, 279, 295 |

#### Description

Frames are scaled down to 64×36 pixels before pixel comparison. This significantly reduces computation while maintaining enough detail for accurate merge detection.

#### Why 64×36?

| Resolution | Pixels | Computation | Detail |
|------------|--------|-------------|--------|
| 1920×1080 | 2,073,600 | Very High | Maximum |
| 1280×720 | 921,600 | High | High |
| 640×360 | 230,400 | Medium | Medium |
| 64×36 | 2,304 | Very Low | Sufficient |

#### Performance Impact

```
Full HD (1920×1080): ~2M comparisons per frame
Scaled (64×36): ~2.3K comparisons per frame

Reduction: 99.9%
```

---

## Algorithm Constants

### MAX_FRAMES_TO_PROCESS

| Property | Value |
|----------|-------|
| **Constant Name** | `MAX_FRAMES_TO_PROCESS` |
| **Value** | `500` |
| **Type** | Int |
| **Location** | `VideoProcessor.kt` - Line 23 |

#### Description

Maximum number of frames to process. Currently not actively used but reserved for future optimization.

---

### BASELINE_FRAMES

| Property | Value |
|----------|-------|
| **Constant Name** | `BASELINE_FRAMES` |
| **Value** | `0` |
| **Type** | Int |
| **Location** | `VideoProcessor.kt` - Line 28 |

#### Description

Number of initial frames to skip for merge detection. Currently set to 0 to check all frames from the start.

#### History

| Value | Version | Behavior |
|-------|---------|----------|
| 20 | v1.0 | Skipped first 20 frames |
| 0 | v1.1+ | Checks all frames |

---

## UI Display Parameters

### Timeline Display

| Property | Value |
|----------|-------|
| **Max Display Frames** | 100 |
| **Height** | 32 dp |
| **Corner Radius** | 4 dp |

#### Display Logic

```kotlin
val displayFrames = if (frames.size > 100) {
    frames.filterIndexed { index, _ -> index % (frames.size / 100) == 0 }
} else frames
```

This ensures the timeline bar never shows more than 100 segments, regardless of total frames.

---

## Complete Parameter Summary

### Drop Detection

| Parameter | Value | Formula/Usage |
|-----------|-------|---------------|
| DROP_THRESHOLD_MULTIPLIER | 1.5 | threshold = interval × 1.5 |

### Merge Detection

| Parameter | Value | Formula/Usage |
|-----------|-------|---------------|
| MERGE_DIFF_THRESHOLD | 10.0 | similar if diff < 10.0 |
| MERGE_BUFFER_SIZE | 5 | compare with last 5 frames |
| MERGE_SIMILARITY_COUNT | 2 | need ≥2 similar frames |
| MERGE_AVG_MULTIPLIER | 1.5 | avgMotion < 10.0 × 1.5 |

### Image Processing

| Parameter | Value | Description |
|-----------|-------|-------------|
| Frame Width | 64 | pixels |
| Frame Height | 36 | pixels |
| Total Pixels | 2,304 | width × height |

### Display

| Parameter | Value | Description |
|-----------|-------|-------------|
| Timeline Max | 100 | max segments |
| Timeline Height | 32 | dp |

---

## Tuning Guide

### For More Drop Detection

```kotlin
DROP_THRESHOLD_MULTIPLIER = 1.3f  // More sensitive
```

### For Fewer Merge False Positives

```kotlin
MERGE_DIFF_THRESHOLD = 15.0f      // Stricter
MERGE_SIMILARITY_COUNT = 3         // Require 3 similar frames
```

### For More Merge Detection

```kotlin
MERGE_DIFF_THRESHOLD = 5.0f       // More sensitive
MERGE_SIMILARITY_COUNT = 1         // Any similar frame
```

---

## Version History

| Version | Changes |
|---------|---------|
| 1.0 | Initial release with basic merge detection |
| 1.1 | Multi-frame buffer, reduced false positives |
| 1.2 | Timeline timestamps added |
| 1.3 | Clickable results, bottom sheet timestamps |
