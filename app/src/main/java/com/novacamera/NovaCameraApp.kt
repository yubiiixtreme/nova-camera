package com.novacamera

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Application entry point. Hilt generates the DI graph at compile time. */
@HiltAndroidApp
class NovaCameraApp : Application()
