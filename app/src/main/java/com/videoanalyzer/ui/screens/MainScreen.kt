package com.videoanalyzer.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.videoanalyzer.domain.model.AnalysisResult
import com.videoanalyzer.domain.model.FrameAnalysis
import com.videoanalyzer.domain.model.FrameClassification
import com.videoanalyzer.domain.model.VideoMetadata
import com.videoanalyzer.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    testVideoUri: Uri? = null,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }

    LaunchedEffect(testVideoUri) {
        testVideoUri?.let { uri ->
            viewModel.selectVideo(uri)
            kotlinx.coroutines.delay(500)
            viewModel.startAnalysis()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.selectVideo(it) }
    }

    LaunchedEffect(Unit) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        
        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(permission)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Video Temporal Integrity Analyzer",
                        fontWeight = FontWeight.Bold
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepBlue,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val currentState = state) {
                is AppState.Idle -> {
                    IdleScreen(
                        onSelectVideo = { videoPickerLauncher.launch("video/*") }
                    )
                }
                is AppState.Loading -> {
                    LoadingScreen()
                }
                is AppState.VideoSelected -> {
                    VideoSelectedScreen(
                        metadata = currentState.metadata,
                        onStartAnalysis = { viewModel.startAnalysis() },
                        onSelectNew = { videoPickerLauncher.launch("video/*") }
                    )
                }
                is AppState.Analyzing -> {
                    AnalyzingScreen(
                        progress = currentState.progress,
                        message = currentState.message
                    )
                }
                is AppState.Results -> {
                    ResultsScreen(
                        result = currentState.result,
                        onAnalyzeNew = { 
                            viewModel.reset()
                            videoPickerLauncher.launch("video/*")
                        }
                    )
                }
                is AppState.Error -> {
                    ErrorScreen(
                        message = currentState.message,
                        onRetry = { viewModel.reset() }
                    )
                }
            }
        }
    }
}

@Composable
private fun IdleScreen(onSelectVideo: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.VideoFile,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = DeepBlue
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Video Temporal Integrity Analyzer",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Detect frame drops and frame merges in your videos using temporal anomaly detection",
            fontSize = 14.sp,
            color = LightGray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            onClick = onSelectVideo,
            colors = ButtonDefaults.buttonColors(containerColor = DeepBlue),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Select Video", fontSize = 16.sp)
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = DeepBlue)
    }
}

@Composable
private fun VideoSelectedScreen(
    metadata: VideoMetadata,
    onStartAnalysis: () -> Unit,
    onSelectNew: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Video Metadata",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                MetadataRow("File Name", metadata.fileName)
                MetadataRow("Duration", formatDuration(metadata.duration))
                MetadataRow("Resolution", "${metadata.width} x ${metadata.height}")
                MetadataRow("FPS", "%.2f".format(metadata.fps))
                MetadataRow("Est. Frames", metadata.totalFrames.toString())
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onStartAnalysis,
            colors = ButtonDefaults.buttonColors(containerColor = Teal),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Icon(Icons.Default.Analytics, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Start Analysis", fontSize = 16.sp)
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        OutlinedButton(
            onClick = onSelectNew,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Select Different Video")
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = LightGray,
            fontSize = 14.sp
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun AnalyzingScreen(progress: Float, message: String) {
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "progress")
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.size(100.dp),
            color = Teal,
            trackColor = DarkSurface,
            strokeWidth = 8.dp
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "${(progress * 100).toInt()}%",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = message,
            color = LightGray,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultsScreen(
    result: AnalysisResult,
    onAnalyzeNew: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf<FrameClassification?>(null) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    
    val filteredFrames = remember(selectedCategory, result.frameAnalyses) {
        selectedCategory?.let { category ->
            result.frameAnalyses.filter { it.classification == category }
        } ?: emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Analysis Results",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TimelineVisualization(result.frameAnalyses, result.videoMetadata.duration)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ClickableStatCard(
                modifier = Modifier.weight(1f),
                title = "Normal",
                value = result.normalFrames.toString(),
                percentage = "%.1f%%".format(100f - result.errorPercentage),
                color = NormalGreen,
                classification = FrameClassification.NORMAL,
                onClick = { selectedCategory = FrameClassification.NORMAL }
            )
            ClickableStatCard(
                modifier = Modifier.weight(1f),
                title = "Drops",
                value = result.frameDrops.toString(),
                percentage = "%.1f%%".format(result.dropPercentage),
                color = FrameDropRed,
                classification = FrameClassification.FRAME_DROP,
                onClick = { selectedCategory = FrameClassification.FRAME_DROP }
            )
            ClickableStatCard(
                modifier = Modifier.weight(1f),
                title = "Merges",
                value = result.frameMerges.toString(),
                percentage = "%.1f%%".format(result.mergePercentage),
                color = FrameMergeAmber,
                classification = FrameClassification.FRAME_MERGE,
                onClick = { selectedCategory = FrameClassification.FRAME_MERGE }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Summary",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                SummaryRow("Total Frames Analyzed", result.totalFrames.toString())
                SummaryRow("Total Errors", "${result.frameDrops + result.frameMerges} (${"%.1f".format(result.errorPercentage)}%)")
                SummaryRow("Frame Drop Rate", "%.2f fps equivalent".format(result.videoMetadata.fps * (1 - result.dropPercentage / 100)))
                SummaryRow("Video FPS", "%.2f".format(result.videoMetadata.fps))
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Detection Method",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Hybrid temporal anomaly detection combining timestamp irregularity analysis with spatial motion and sharpness consistency checks using Laplacian variance.",
                    color = LightGray,
                    fontSize = 13.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onAnalyzeNew,
            colors = ButtonDefaults.buttonColors(containerColor = DeepBlue),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Analyze New Video", fontSize = 16.sp)
        }
    }

    selectedCategory?.let { category ->
        ModalBottomSheet(
            onDismissRequest = { selectedCategory = null },
            sheetState = sheetState,
            containerColor = DarkSurface
        ) {
            TimestampListContent(
                category = category,
                frames = filteredFrames,
                onDismiss = { selectedCategory = null }
            )
        }
    }
}

@Composable
private fun TimelineVisualization(frames: List<FrameAnalysis>, duration: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Timeline",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .clip(RoundedCornerShape(4.dp))
            ) {
                val displayFrames = if (frames.size > 100) {
                    frames.filterIndexed { index, _ -> index % (frames.size / 100) == 0 }
                } else frames
                
                displayFrames.forEach { frame ->
                    val color = when (frame.classification) {
                        FrameClassification.NORMAL -> NormalGreen
                        FrameClassification.FRAME_DROP -> FrameDropRed
                        FrameClassification.FRAME_MERGE -> FrameMergeAmber
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(color)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "0:00",
                    color = LightGray,
                    fontSize = 10.sp
                )
                Text(
                    text = formatTimestamp(duration / 2),
                    color = LightGray,
                    fontSize = 10.sp
                )
                Text(
                    text = formatTimestamp(duration),
                    color = LightGray,
                    fontSize = 10.sp
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LegendItem(color = NormalGreen, label = "Normal")
                LegendItem(color = FrameDropRed, label = "Drop")
                LegendItem(color = FrameMergeAmber, label = "Merge")
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = LightGray,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    percentage: String,
    color: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                color = LightGray,
                fontSize = 12.sp
            )
            Text(
                text = value,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = percentage,
                color = LightGray,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ClickableStatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    percentage: String,
    color: Color,
    classification: FrameClassification,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = LightGray,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.TouchApp,
                    contentDescription = "Tap for timestamps",
                    tint = LightGray,
                    modifier = Modifier.size(12.dp)
                )
            }
            Text(
                text = value,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = percentage,
                color = LightGray,
                fontSize = 12.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimestampListContent(
    category: FrameClassification,
    frames: List<FrameAnalysis>,
    onDismiss: () -> Unit
) {
    val categoryTitle = when (category) {
        FrameClassification.NORMAL -> "Normal Frames"
        FrameClassification.FRAME_DROP -> "Dropped Frames"
        FrameClassification.FRAME_MERGE -> "Merged Frames"
    }
    
    val categoryColor = when (category) {
        FrameClassification.NORMAL -> NormalGreen
        FrameClassification.FRAME_DROP -> FrameDropRed
        FrameClassification.FRAME_MERGE -> FrameMergeAmber
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(categoryColor, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$categoryTitle (${frames.size})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = LightGray
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (frames.isEmpty()) {
            Text(
                text = "No frames found",
                color = LightGray,
                modifier = Modifier.padding(vertical = 32.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                items(frames) { frame ->
                    TimestampRow(
                        frameIndex = frame.frameIndex,
                        timestampUs = frame.timestampUs
                    )
                }
            }
        }
    }
}

@Composable
private fun TimestampRow(
    frameIndex: Int,
    timestampUs: Long
) {
    val timestampSec = timestampUs / 1_000_000.0
    val timestampStr = formatTimestamp(timestampSec)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Frame $frameIndex",
            color = LightGray,
            fontSize = 14.sp
        )
        Text(
            text = "→",
            color = LightGray,
            fontSize = 14.sp
        )
        Text(
            text = timestampStr,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
    HorizontalDivider(color = DarkSurface, thickness = 1.dp)
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = LightGray,
            fontSize = 14.sp
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = FrameDropRed
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Error",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = message,
            color = LightGray,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = DeepBlue)
        ) {
            Text("Try Again")
        }
    }
}

private fun formatDuration(seconds: Double): String {
    val totalSeconds = seconds.toLong()
    val sec = totalSeconds % 60
    val min = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, min, sec)
    } else {
        String.format("%02d:%02d", min, sec)
    }
}

private fun formatTimestamp(seconds: Double): String {
    val totalMs = (seconds * 1000).toLong()
    val ms = totalMs % 1000
    val totalSec = totalMs / 1000
    val sec = totalSec % 60
    val min = totalSec / 60
    
    return if (min > 0) {
        String.format("%d:%02d.%03d", min, sec, ms)
    } else {
        String.format("0:%03d", ms)
    }
}
