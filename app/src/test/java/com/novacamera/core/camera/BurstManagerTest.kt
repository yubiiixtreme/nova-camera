package com.novacamera.core.camera

import org.junit.Assert.*
import org.junit.Test

class BurstManagerTest {
    @Test fun registersAndCapsStacks() {
        repeat(25) { BurstManager.registerStack(listOf()) }
        assertTrue(BurstManager.latest().isEmpty())
    }

    @Test fun evictsOldestStacksBeyondCapOf20() {
        repeat(25) { BurstManager.registerStack(listOf()) }
        assertEquals(20, BurstManager.size())
    }

    @Test fun registeredStackIsRetrievableById() {
        val id = BurstManager.registerStack(listOf())
        assertTrue(BurstManager.stack(id).isEmpty())
        assertTrue(BurstManager.stack("missing-id").isEmpty())
    }
}
