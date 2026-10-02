package com.novacamera.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bundled + downloadable LUT packs (identity + cinematic presets). */
@Singleton
class LutManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class Lut(val id: String, val name: String, val assetPath: String)

    val builtIns = listOf(
        Lut("none", "None", ""),
        Lut("cinematic", "Cinematic", "luts/cinematic.png"),
        Lut("vivid", "Vivid", "luts/vivid.png"),
        Lut("mono", "Mono", "luts/mono.png"),
        Lut("sunset", "Sunset Glow", "luts/sunset.png"),
    )

    suspend fun loadPreview(id: String): Bitmap? = withContext(Dispatchers.IO) {
        if (id == "none" || id.isEmpty()) return@withContext null
        runCatching {
            context.assets.open("luts/$id.png").use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }

    fun cacheDir(): File = File(context.cacheDir, "luts").apply { mkdirs() }
}
