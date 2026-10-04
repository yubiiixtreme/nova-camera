package com.novacamera.core.camera

import org.junit.Assert.*
import org.junit.Test

class BracketingTest {
    @Test fun threeFramesStepOne() {
        assertEquals(listOf(-1, 0, 1), LowLatencyCaptureHandler.bracketEvs(3, 1))
    }

    @Test fun fiveFramesStepOne() {
        assertEquals(listOf(-2, -1, 0, 1, 2), LowLatencyCaptureHandler.bracketEvs(5, 1))
    }

    @Test fun sevenFramesStepTwo() {
        assertEquals(listOf(-6, -4, -2, 0, 2, 4, 6), LowLatencyCaptureHandler.bracketEvs(7, 2))
    }

    @Test fun evenFramesRoundedUpToOdd() {
        assertEquals(listOf(-2, -1, 0, 1, 2), LowLatencyCaptureHandler.bracketEvs(4, 1))
    }

    @Test fun stepClampedToThree() {
        assertEquals(listOf(-3, 0, 3), LowLatencyCaptureHandler.bracketEvs(3, 9))
    }

    @Test fun sweepIsSymmetricAroundZero() {
        val evs = LowLatencyCaptureHandler.bracketEvs(7, 1)
        assertEquals(0, evs.sum())
        assertEquals(evs, evs.reversed().map { -it })
    }
}
