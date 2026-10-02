package com.novacamera.core.camera

import org.junit.Assert.*
import org.junit.Test

class BurstManagerTest {
    @Test fun registersAndCapsStacks() {
        repeat(25) { BurstManager.registerStack(listOf()) }
        assertTrue(BurstManager.latest().isEmpty())
    }
}
