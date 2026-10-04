package com.novacamera.ml

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * FOSS flavor: no ML Kit (no Google libraries). Analyzers pass frames
 * through untouched; camera, vault, scan-warp, and PDF keep working.
 */
@Singleton
class FrameAnalyzers @Inject constructor() {

    private val _barcode = MutableStateFlow<String?>(null)
    val barcode: StateFlow<String?> = _barcode.asStateFlow()
    private val _ocrText = MutableStateFlow("")
    val ocrText: StateFlow<String> = _ocrText.asStateFlow()

    fun barcodeAnalyzer(): ImageAnalysis.Analyzer = ImageAnalysis.Analyzer { it.close() }

    fun ocrAnalyzer(): ImageAnalysis.Analyzer = ImageAnalysis.Analyzer { it.close() }
}
