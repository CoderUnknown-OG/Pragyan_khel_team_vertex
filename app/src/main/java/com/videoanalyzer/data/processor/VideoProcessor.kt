package com.videoanalyzer.data.processor

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.videoanalyzer.domain.model.AnalysisResult
import com.videoanalyzer.domain.model.FrameAnalysis
import com.videoanalyzer.domain.model.FrameClassification
import com.videoanalyzer.domain.model.VideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class VideoProcessor(private val context: Context) {

    companion object {
        private const val TAG = "VideoProcessor"
        private const val MAX_FRAMES_TO_PROCESS = 500
        private const val DROP_THRESHOLD_MULTIPLIER = 1.5f
        private const val MERGE_DIFF_THRESHOLD = 10.0f
        private const val BASELINE_FRAMES = 0
        private const val MERGE_BUFFER_SIZE = 5
        private const val MERGE_SIMILARITY_COUNT = 2
    }

    fun getVideoMetadata(uri: Uri): VideoMetadata? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)

            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val fps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull() ?: 30f

            retriever.release()

            val fileName = uri.lastPathSegment ?: "video.mp4"
            val durationSec = durationMs / 1000.0

            VideoMetadata(
                uri = uri.toString(),
                fileName = fileName,
                duration = durationSec,
                width = width,
                height = height,
                fps = fps,
                totalFrames = (durationSec * fps).toInt()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting metadata: ${e.message}")
            null
        }
    }

    fun analyzeVideo(uri: Uri, metadata: VideoMetadata): Flow<ProgressUpdate> = flow {
        emit(ProgressUpdate(0.05f, "Extracting timestamps..."))

        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)

        val trackIndex = findVideoTrack(extractor)
        if (trackIndex == -1) {
            emit(ProgressUpdate(0f, "Error: No video track found"))
            extractor.release()
            return@flow
        }
        extractor.selectTrack(trackIndex)

        val timestamps = mutableListOf<Long>()
        while (extractor.sampleTime >= 0) {
            timestamps.add(extractor.sampleTime)
            if (!extractor.advance()) break
        }
        extractor.release()

        if (timestamps.isEmpty()) {
            emit(ProgressUpdate(0f, "Error: No frames found"))
            return@flow
        }

        val sortedPts = timestamps.sorted()
        emit(ProgressUpdate(0.1f, "Analyzing timestamps..."))

        // Step 1: Calculate MODE of intervals (expected interval in microseconds)
        val expectedInterval = calculateModeInterval(sortedPts)  // in microseconds
        
        // Step 2: Calculate Expected Frames = Duration / Expected Interval
        // metadata.duration is in seconds, convert to microseconds: duration * 1,000,000
        val durationUs = (metadata.duration * 1_000_000).toLong()
        val expectedFrames = (durationUs / expectedInterval).toInt()
        
        // Debug: Show calculation details
        Log.d(TAG, "Duration: ${metadata.duration}s, ExpectedInterval: ${expectedInterval}μs, ExpectedFrames: $expectedFrames")
        
        // Step 3: Actual frames = extracted PTS count
        val actualFrames = sortedPts.size
        
        // Step 4: Dropped frames = Expected - Actual
        val totalDropped = maxOf(0, expectedFrames - actualFrames)

        emit(ProgressUpdate(0.2f, "Expected: $expectedFrames, Actual: $actualFrames, Drops: $totalDropped"))

        // Step 5: Detect which frames are dropped (find gaps)
        val dropInfoList = detectDropFrames(sortedPts, expectedInterval)

        emit(ProgressUpdate(0.3f, "Extracting frames for merge detection..."))

        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)

        // Create set of dropped frame indices
        val droppedIndices = dropInfoList.map { it.frameIndex }.toSet()
        
        // Create timeline for ALL expected frames (including dropped)
        val frameAnalyses = mutableListOf<FrameAnalysis>()
        
        emit(ProgressUpdate(0.4f, "Detecting frame merges..."))

        var actualFrameIndex = 0

        // Generate all expected frame timestamps
        val expectedTimestamps = mutableListOf<Long>()
        for (i in 0 until expectedFrames) {
            expectedTimestamps.add((i * expectedInterval).toLong())
        }

        // Rolling buffer for merge detection (stores recent frame pixels)
        val framePixelBuffer = mutableListOf<Pair<Int, IntArray>>()  // (frameIndex, pixels)

        // Process each expected frame position
        for (i in 0 until expectedFrames) {
            val progress = 0.4f + 0.5f * (i.toFloat() / expectedFrames)
            
            if (i % 30 == 0) {
                emit(ProgressUpdate(progress, "Processing frame $i/$expectedFrames..."))
            }

            var classification = FrameClassification.NORMAL
            var motionScore = 0f

            // Check if this position is a dropped frame
            if (droppedIndices.contains(i)) {
                classification = FrameClassification.FRAME_DROP
            } else if (actualFrameIndex < sortedPts.size) {
                // This is an actual frame - check for merge
                val currentPts = sortedPts[actualFrameIndex]
                val bitmap = retriever.getFrameAtTime(currentPts, MediaMetadataRetriever.OPTION_CLOSEST)

                if (bitmap != null) {
                    val currentPixels = getScaledPixels(bitmap, 64, 36)
                    
                    // Calculate motion score (difference from most recent frame)
                    if (framePixelBuffer.isNotEmpty()) {
                        val lastFramePixels = framePixelBuffer.last().second
                        motionScore = calculatePixelDiff(lastFramePixels, currentPixels)
                    }

                    // Advanced merge detection: Check similarity with multiple recent frames
                    if (i > MERGE_BUFFER_SIZE && motionScore > 0) {
                        val similarCount = countSimilarFrames(framePixelBuffer.takeLast(MERGE_BUFFER_SIZE), currentPixels, MERGE_DIFF_THRESHOLD)
                        
                        // MERGE detected if:
                        // 1. Current frame is similar to at least 2 frames in the buffer (duplicate pattern)
                        // 2. AND the similarity is very high (much lower than motionThreshold)
                        val avgMotion = framePixelBuffer.takeLast(MERGE_BUFFER_SIZE).map { 
                            calculatePixelDiff(it.second, currentPixels) 
                        }.average()
                        
                        if (similarCount >= MERGE_SIMILARITY_COUNT && avgMotion < MERGE_DIFF_THRESHOLD * 1.5) {
                            classification = FrameClassification.FRAME_MERGE
                        }
                    }

                    // Add current frame to buffer
                    framePixelBuffer.add(Pair(i, currentPixels))
                    
                    // Keep buffer size limited
                    if (framePixelBuffer.size > MERGE_BUFFER_SIZE * 2) {
                        framePixelBuffer.removeAt(0)
                    }
                    
                    bitmap.recycle()
                }
                
                actualFrameIndex++
            }

            frameAnalyses.add(FrameAnalysis(
                frameIndex = i,
                timestampUs = expectedTimestamps[i],
                classification = classification,
                motionScore = motionScore,
                sharpnessScore = 0f,
                confidenceScore = if (classification != FrameClassification.NORMAL) 0.9f else 0.5f
            ))
        }

        retriever.release()

        // Count results
        val dropCount = frameAnalyses.count { it.classification == FrameClassification.FRAME_DROP }
        val mergeCount = frameAnalyses.count { it.classification == FrameClassification.FRAME_MERGE }
        val normalCount = frameAnalyses.size - dropCount - mergeCount

        val result = AnalysisResult(
            videoMetadata = metadata,
            frameAnalyses = frameAnalyses,
            totalFrames = expectedFrames,  // Expected frames = 171
            frameDrops = dropCount,       // 12
            frameMerges = mergeCount,       // 0
            normalFrames = normalCount,     // 159
            errorPercentage = ((dropCount + mergeCount).toFloat() / expectedFrames) * 100,
            dropPercentage = (dropCount.toFloat() / expectedFrames) * 100,
            mergePercentage = (mergeCount.toFloat() / expectedFrames) * 100
        )

        emit(ProgressUpdate(1.0f, "Complete! Total: $expectedFrames, Normal: $normalCount, Drops: $dropCount, Merges: $mergeCount", result))

    }.flowOn(Dispatchers.Default)

    private fun findVideoTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("video/") == true) return i
        }
        return -1
    }

    private fun calculateModeInterval(pts: List<Long>): Long {
        if (pts.size < 2) return 33333L

        val intervals = mutableListOf<Long>()
        for (i in 1 until pts.size) {
            val interval = pts[i] - pts[i - 1]
            if (interval > 0) intervals.add(interval)
        }

        if (intervals.isEmpty()) return 33333L

        // Round to nearest 100 microseconds for more precision
        val rounded = intervals.map { ((it / 100) * 100) }.groupingBy { it }.eachCount()
        return rounded.maxByOrNull { it.value }?.key ?: intervals.average().toLong()
    }

    data class DropInfo(val frameIndex: Int, val timestampUs: Long, val gap: Long, val missingCount: Int)

    private fun detectDropFrames(pts: List<Long>, expectedInterval: Long): List<DropInfo> {
        val drops = mutableListOf<DropInfo>()
        val threshold = (expectedInterval * DROP_THRESHOLD_MULTIPLIER).toLong()

        for (i in 1 until pts.size) {
            val gap = pts[i] - pts[i - 1]
            
            if (gap > threshold) {
                // Calculate how many frames are missing
                val missingCount = ((gap / expectedInterval).toInt() - 1)
                
                if (missingCount > 0) {
                    // The frame at pts[i] is present, but frames before it are dropped
                    for (j in 0 until missingCount) {
                        // Calculate the expected frame index that was dropped
                        val droppedIndex = i + j
                        val expectedTimestamp = pts[i - 1] + ((j + 1) * expectedInterval)
                        
                        drops.add(DropInfo(
                            frameIndex = droppedIndex,
                            timestampUs = expectedTimestamp,
                            gap = gap,
                            missingCount = missingCount
                        ))
                    }
                }
            }
        }
        return drops
    }

    private data class BaselineMetrics(val avgMotion: Float)

    private fun calculateBaseline(retriever: MediaMetadataRetriever, timestamps: List<Long>): BaselineMetrics {
        var totalDiff = 0f
        var count = 0
        var prevPixels: IntArray? = null

        for (i in timestamps.indices) {
            val bitmap = retriever.getFrameAtTime(timestamps[i], MediaMetadataRetriever.OPTION_CLOSEST)
            if (bitmap != null) {
                val currentPixels = getScaledPixels(bitmap, 64, 36)

                if (prevPixels != null) {
                    totalDiff += calculatePixelDiff(prevPixels, currentPixels)
                    count++
                }

                prevPixels = currentPixels
                bitmap.recycle()
            }
        }

        return if (count > 0) BaselineMetrics(totalDiff / count) else BaselineMetrics(10f)
    }

    private fun getScaledPixels(bitmap: Bitmap, width: Int, height: Int): IntArray {
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        scaled.recycle()
        return pixels
    }

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

    private fun calculatePixelDiff(pixels1: IntArray, pixels2: IntArray): Float {
        if (pixels1.size != pixels2.size) return Float.MAX_VALUE

        var totalDiff = 0.0
        for (i in pixels1.indices) {
            val r1 = (pixels1[i] shr 16) and 0xFF
            val g1 = (pixels1[i] shr 8) and 0xFF
            val b1 = pixels1[i] and 0xFF

            val r2 = (pixels2[i] shr 16) and 0xFF
            val g2 = (pixels2[i] shr 8) and 0xFF
            val b2 = pixels2[i] and 0xFF

            totalDiff += kotlin.math.sqrt(
                ((r1 - r2) * (r1 - r2) + (g1 - g2) * (g1 - g2) + (b1 - b2) * (b1 - b2)).toDouble()
            )
        }

        return (totalDiff / pixels1.size).toFloat()
    }
}

data class ProgressUpdate(
    val progress: Float,
    val message: String,
    val result: AnalysisResult? = null
)
