package com.novacamera.processing

import android.graphics.Bitmap

/**
 * 3D LUT: Adobe .cube parser + CPU trilinear apply. Pixel math is pure
 * ([applyToPixels]) so it stays unit-testable; the [Bitmap] wrapper is thin.
 *
 * Data layout: R fastest, then G, then B — index ((b*N + g)*N + r)*3.
 */
data class CubeLut(val title: String, val size: Int, val data: FloatArray) {
    init {
        require(size in 2..64) { "bad LUT size" }
        require(data.size == size * size * size * 3) { "bad LUT data" }
    }

    /** Trilinear sample. Inputs 0..1. */
    fun lookup(r: Float, g: Float, b: Float): FloatArray {
        val n = size
        fun axis(v: Float): Triple<Int, Int, Float> {
            val x = (v.coerceIn(0f, 1f) * (n - 1))
            val lo = x.toInt().coerceIn(0, n - 2)
            return Triple(lo, lo + 1, x - lo)
        }
        val (r0, r1, fr) = axis(r)
        val (g0, g1, fg) = axis(g)
        val (b0, b1, fb) = axis(b)
        fun at(ri: Int, gi: Int, bi: Int): FloatArray {
            val o = ((bi * n + gi) * n + ri) * 3
            return floatArrayOf(data[o], data[o + 1], data[o + 2])
        }
        fun mix(a: FloatArray, b2: FloatArray, t: Float) =
            FloatArray(3) { i -> a[i] + (b2[i] - a[i]) * t }
        val c00 = mix(at(r0, g0, b0), at(r1, g0, b0), fr)
        val c10 = mix(at(r0, g1, b0), at(r1, g1, b0), fr)
        val c01 = mix(at(r0, g0, b1), at(r1, g0, b1), fr)
        val c11 = mix(at(r0, g1, b1), at(r1, g1, b1), fr)
        return mix(mix(c00, c10, fg), mix(c01, c11, fg), fb)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CubeLut) return false
        return title == other.title && size == other.size && data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var r = title.hashCode()
        r = 31 * r + size
        r = 31 * r + data.contentHashCode()
        return r
    }

    companion object {
        /** Parses Adobe .cube (TITLE, LUT_3D_SIZE, 0..1 domain, RGB triplets). Null if unsupported. */
        fun parse(text: String): CubeLut? = runCatching {
            var title = "imported"
            var size = 0
            var minOk = true
            var maxOk = true
            val triples = mutableListOf<FloatArray>()
            text.lineSequence().forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith('#')) return@forEach
                val parts = line.split(Regex("\\s+"))
                when (parts[0]) {
                    "TITLE" -> title = line.substringAfter("TITLE").trim().trim('"').take(40)
                    "LUT_3D_SIZE" -> size = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    "DOMAIN_MIN" -> minOk = parts.drop(1).all { it.toFloatOrNull() == 0f }
                    "DOMAIN_MAX" -> maxOk = parts.drop(1).all { it.toFloatOrNull() == 1f }
                    "LUT_1D_SIZE" -> throw IllegalArgumentException("1D LUT")
                    else -> {
                        if (parts.size == 3) {
                            val t = FloatArray(3) { i -> parts[i].toFloat() }
                            triples += t
                        }
                    }
                }
            }
            require(size in 2..64 && minOk && maxOk) { "unsupported cube" }
            require(triples.size == size * size * size) { "data count" }
            val data = FloatArray(triples.size * 3)
            triples.forEachIndexed { i, t -> t.copyInto(data, i * 3) }
            CubeLut(title.ifEmpty { "imported" }, size, data)
        }.getOrNull()

        /** Built-ins generated in code (no assets): identity, B&W, warm, cool. */
        fun builtIn(id: String): CubeLut? {
            val n = 16
            fun gen(f: (Float, Float, Float) -> FloatArray, title: String): CubeLut {
                val d = FloatArray(n * n * n * 3)
                var o = 0
                for (b in 0 until n) for (g in 0 until n) for (r in 0 until n) {
                    val v = f(r / (n - 1f), g / (n - 1f), b / (n - 1f))
                    d[o++] = v[0]; d[o++] = v[1]; d[o++] = v[2]
                }
                return CubeLut(title, n, d)
            }
            return when (id) {
                "mono" -> gen({ r, g, b ->
                    val l = 0.299f * r + 0.587f * g + 0.114f * b
                    floatArrayOf(l, l, l)
                }, "Mono")
                "warm" -> gen({ r, g, b ->
                    floatArrayOf((r * 1.08f).coerceIn(0f, 1f), g, (b * 0.92f).coerceIn(0f, 1f))
                }, "Warm")
                "cool" -> gen({ r, g, b ->
                    floatArrayOf((r * 0.94f).coerceIn(0f, 1f), g, (b * 1.07f).coerceIn(0f, 1f))
                }, "Cool")
                else -> null
            }
        }

        /** Pure pixel op: ARGB ints in, ARGB ints out. */
        fun applyToPixels(pixels: IntArray, lut: CubeLut): IntArray {
            val out = IntArray(pixels.size)
            for (i in pixels.indices) {
                val p = pixels[i]
                val a = p ushr 24
                val v = lut.lookup(
                    ((p shr 16) and 0xFF) / 255f,
                    ((p shr 8) and 0xFF) / 255f,
                    (p and 0xFF) / 255f,
                )
                out[i] = (a shl 24) or
                    ((v[0] * 255f).toInt().coerceIn(0, 255) shl 16) or
                    ((v[1] * 255f).toInt().coerceIn(0, 255) shl 8) or
                    (v[2] * 255f).toInt().coerceIn(0, 255)
            }
            return out
        }
    }
}

/** Applies [lut] to a bitmap copy (leaves [src] untouched). */
fun applyCubeLut(src: Bitmap, lut: CubeLut): Bitmap {
    val w = src.width
    val h = src.height
    val px = IntArray(w * h)
    src.getPixels(px, 0, w, 0, 0, w, h)
    val out = src.copy(Bitmap.Config.ARGB_8888, true)
    out.setPixels(CubeLut.applyToPixels(px, lut), 0, w, 0, 0, w, h)
    return out
}
