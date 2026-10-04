package com.novacamera.presentation.camera

import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.graphics.Paint
import android.view.ViewGroup.OnHierarchyChangeListener
import androidx.camera.view.PreviewView
import com.novacamera.domain.model.LiveFilter
import com.novacamera.processing.LiveFilters

/**
 * Live colour looks on the viewfinder. PreviewView in COMPATIBLE mode hosts a
 * TextureView, and a hardware-layer colour filter on it grades the preview on
 * the GPU at no extra cost. PreviewView swaps its child view whenever the
 * camera is (re)bound, so the filter is re-applied as children are added.
 */
fun PreviewView.installLiveFilter() {
    setOnHierarchyChangeListener(object : OnHierarchyChangeListener {
        override fun onChildViewAdded(parent: View?, child: View?) {
            (child as? TextureView)?.let { applyTo(it, tag as? LiveFilter ?: LiveFilter.NONE) }
        }
        override fun onChildViewRemoved(parent: View?, child: View?) = Unit
    })
}

fun PreviewView.setLiveFilter(filter: LiveFilter) {
    tag = filter
    for (i in 0 until (this as ViewGroup).childCount) {
        (getChildAt(i) as? TextureView)?.let { applyTo(it, filter) }
    }
}

private fun applyTo(view: TextureView, filter: LiveFilter) {
    val paint: Paint? = LiveFilters.paint(filter)
    view.setLayerType(if (paint != null) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE, paint)
}
