package com.videoanalyzer.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videoanalyzer.data.processor.VideoProcessor
import com.videoanalyzer.domain.model.AnalysisResult
import com.videoanalyzer.domain.model.VideoMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AppState {
    object Idle : AppState()
    object Loading : AppState()
    data class VideoSelected(val metadata: VideoMetadata) : AppState()
    data class Analyzing(val progress: Float, val message: String) : AppState()
    data class Results(val result: AnalysisResult) : AppState()
    data class Error(val message: String) : AppState()
}

class MainViewModel : ViewModel() {
    private val _state = MutableStateFlow<AppState>(AppState.Idle)
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val _selectedVideoUri = MutableStateFlow<Uri?>(null)
    val selectedVideoUri: StateFlow<Uri?> = _selectedVideoUri.asStateFlow()

    private var videoProcessor: VideoProcessor? = null

    fun initialize(context: android.content.Context) {
        videoProcessor = VideoProcessor(context)
    }

    fun selectVideo(uri: Uri) {
        viewModelScope.launch {
            _state.value = AppState.Loading
            _selectedVideoUri.value = uri

            val processor = videoProcessor ?: run {
                _state.value = AppState.Error("Processor not initialized")
                return@launch
            }

            val metadata = processor.getVideoMetadata(uri)
            if (metadata != null) {
                _state.value = AppState.VideoSelected(metadata)
            } else {
                _state.value = AppState.Error("Failed to read video metadata")
            }
        }
    }

    fun startAnalysis() {
        val uri = _selectedVideoUri.value ?: return
        val currentState = _state.value
        
        if (currentState !is AppState.VideoSelected) return

        viewModelScope.launch {
            val processor = videoProcessor ?: run {
                _state.value = AppState.Error("Processor not initialized")
                return@launch
            }

            val metadata = currentState.metadata
            
            processor.analyzeVideo(uri, metadata).collect { update ->
                _state.value = AppState.Analyzing(update.progress, update.message)
                
                update.result?.let { result ->
                    _state.value = AppState.Results(result)
                }
            }
        }
    }

    fun reset() {
        _state.value = AppState.Idle
        _selectedVideoUri.value = null
    }
}
