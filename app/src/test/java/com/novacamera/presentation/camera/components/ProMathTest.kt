package com.novacamera.presentation.camera.components

import org.junit.Assert.*
import org.junit.Test

class ProMathTest {
    @Test fun shutterAutoEndpoints() {
        assertEquals(0f, shutterToSlider(null))
        assertNull(sliderToShutter(0f))
        assertNull(sliderToShutter(0.01f))
    }

    @Test fun shutterRoundTripsAcrossRange() {
        // Interior of the range round-trips; the 1/8000 floor sits at the AUTO stop.
        val secs = listOf(1.0 / 1000, 1.0 / 250, 1.0 / 60, 0.5, 1.0, 5.0, 30.0)
        secs.forEach { sec ->
            val t = shutterToSlider(sec)
            assertTrue("slider in (0,1] for $sec", t > 0f && t <= 1f)
            val back = sliderToShutter(t)!!
            assertEquals("round trip $sec", sec, back, sec * 0.02)
        }
        assertEquals(0f, shutterToSlider(1.0 / 8000))
        assertNull(sliderToShutter(shutterToSlider(1.0 / 8000)))
    }

    @Test fun shutterSliderIsMonotonic() {
        val ts = listOf(0.1f, 0.3f, 0.5f, 0.7f, 0.9f, 1f)
        val secs = ts.map { sliderToShutter(it)!! }
        assertEquals(secs, secs.sorted())
    }

    @Test fun shutterFormat() {
        assertEquals("AUTO", formatShutter(null))
        assertEquals("AUTO", formatShutter(0.0))
        assertEquals("1/250s", formatShutter(1.0 / 250))
        assertEquals("2.0s", formatShutter(2.0))
    }

    @Test fun aspectRectSquareInSquare() {
        val r = aspectRect(100f, 100f, 1f)
        assertEquals(0f, r.left, 0.01f)
        assertEquals(0f, r.top, 0.01f)
        assertEquals(100f, r.right, 0.01f)
        assertEquals(100f, r.bottom, 0.01f)
    }

    @Test fun aspectRectPillarboxesWideView() {
        val r = aspectRect(200f, 100f, 1f)
        assertEquals(50f, r.left, 0.01f)
        assertEquals(150f, r.right, 0.01f)
        assertEquals(0f, r.top, 0.01f)
        assertEquals(100f, r.bottom, 0.01f)
    }

    @Test fun aspectRectLetterboxesTallView() {
        val r = aspectRect(100f, 200f, 16f / 9f)
        val h = 100f / (16f / 9f)
        assertEquals(0f, r.left, 0.01f)
        assertEquals(100f, r.right, 0.01f)
        assertEquals((200f - h) / 2f, r.top, 0.01f)
        assertEquals((200f + h) / 2f, r.bottom, 0.01f)
    }
}
