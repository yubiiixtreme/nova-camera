package com.novacamera.processing

import org.junit.Assert.*
import org.junit.Test

class CubeLutTest {
    private fun identity2(): String = buildString {
        appendLine("TITLE \"T\"")
        appendLine("LUT_3D_SIZE 2")
        for (b in 0..1) for (g in 0..1) for (r in 0..1) {
            appendLine("$r.0 $g.0 $b.0")
        }
    }

    @Test fun parsesMinimalCube() {
        val lut = CubeLut.parse(identity2())
        assertNotNull(lut)
        assertEquals(2, lut!!.size)
        assertEquals("T", lut.title)
        assertEquals(2 * 2 * 2 * 3, lut.data.size)
    }

    @Test fun rejectsGarbageAnd1D() {
        assertNull(CubeLut.parse("hello"))
        assertNull(CubeLut.parse("LUT_3D_SIZE 2\n0 0 0"))
        assertNull(CubeLut.parse("LUT_1D_SIZE 4\nLUT_3D_SIZE 2\n" + "0 0 0\n".repeat(8)))
    }

    @Test fun identityLookupRoundTrips() {
        val lut = CubeLut.parse(identity2())!!
        val v = lut.lookup(0.3f, 0.6f, 0.9f)
        assertEquals(0.3f, v[0], 0.03f)
        assertEquals(0.6f, v[1], 0.03f)
        assertEquals(0.9f, v[2], 0.03f)
    }

    @Test fun monoMakesGray() {
        val mono = CubeLut.builtIn("mono")!!
        val out = CubeLut.applyToPixels(intArrayOf(0xFFFF0000.toInt()), mono)
        val r = (out[0] shr 16) and 0xFF
        val g = (out[0] shr 8) and 0xFF
        val b = out[0] and 0xFF
        assertEquals(r, g)
        assertEquals(g, b)
        assertTrue("luma of pure red ~76, got $r", r in 73..79)
    }

    @Test fun warmPushesRedPullsBlue() {
        val warm = CubeLut.builtIn("warm")!!
        val out = CubeLut.applyToPixels(intArrayOf(0xFF808080.toInt()), warm)
        val r = (out[0] shr 16) and 0xFF
        val g = (out[0] shr 8) and 0xFF
        val b = out[0] and 0xFF
        assertTrue(r > 128)
        assertTrue(b < 128)
        assertTrue("green channel untouched, got $g", g in 125..131)
    }

    @Test fun unknownBuiltinIsNull() {
        assertNull(CubeLut.builtIn("nope"))
    }
}
