package com.novacamera

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import dagger.hilt.android.HiltAndroidApp

/** Application entry point. Hilt generates the DI graph at compile time. */
@HiltAndroidApp
class NovaCameraApp : Application(), ImageLoaderFactory {
    /** One shared Coil loader that can also pull thumbnails out of video files. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(VideoFrameDecoder.Factory()) }
        .crossfade(true)
        .build()
}
