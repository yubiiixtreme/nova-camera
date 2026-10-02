package com.novacamera.util

import android.view.HapticFeedbackConstants
import android.view.View

/** Tactile feedback on shutter / dial interactions. */
fun View.tick() { performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
fun View.confirm() { performHapticFeedback(HapticFeedbackConstants.CONFIRM) }
