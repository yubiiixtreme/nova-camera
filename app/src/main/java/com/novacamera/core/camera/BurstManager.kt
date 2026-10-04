package com.novacamera.core.camera

import android.net.Uri

/** Tracks burst stacks (unified photo stack UX: best frame + filmstrip). */
object BurstManager {
    const val DEFAULT_BURST_COUNT = 10
    private val stacks = LinkedHashMap<String, List<Uri>>()
    private var seq = 0L

    fun registerStack(uris: List<Uri>): String {
        // Millis alone collide during rapid burst registration; suffix a counter.
        val id = "burst_${System.currentTimeMillis()}_${seq++}"
        stacks[id] = uris
        if (stacks.size > 20) stacks.remove(stacks.keys.first())
        return id
    }

    fun stack(id: String): List<Uri> = stacks[id] ?: emptyList()
    fun latest(): List<Uri> = stacks.values.lastOrNull() ?: emptyList()
    fun size(): Int = stacks.size
}
