package com.videoanalyzer.domain.model

data class VideoMetadata(
    val uri: String,
    val fileName: String,
    val duration: Double,  // in seconds
    val width: Int,
    val height: Int,
    val fps: Float,
    val totalFrames: Int
)

enum class FrameClassification {
    NORMAL,
    FRAME_DROP,
    FRAME_MERGE
}

data class FrameAnalysis(
    val frameIndex: Int,
    val timestampUs: Long,
    val classification: FrameClassification,
    val motionScore: Float,
    val sharpnessScore: Float,
    val confidenceScore: Float = 0f
)

data class AnalysisResult(
    val videoMetadata: VideoMetadata,
    val frameAnalyses: List<FrameAnalysis>,
    val totalFrames: Int,
    val frameDrops: Int,
    val frameMerges: Int,
    val normalFrames: Int,
    val errorPercentage: Float,
    val dropPercentage: Float,
    val mergePercentage: Float
)
