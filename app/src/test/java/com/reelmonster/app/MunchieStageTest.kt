package com.reelmonster.app

import com.reelmonster.app.domain.model.MunchieStage
import org.junit.Assert.assertEquals
import org.junit.Test

class MunchieStageTest {

    @Test
    fun testStageProgression() {
        // Stage 1: Baby (0 - 5)
        assertEquals(MunchieStage.BABY, MunchieStage.fromCount(0))
        assertEquals(MunchieStage.BABY, MunchieStage.fromCount(3))
        assertEquals(MunchieStage.BABY, MunchieStage.fromCount(5))

        // Stage 2: Curious (6 - 15)
        assertEquals(MunchieStage.CURIOUS, MunchieStage.fromCount(6))
        assertEquals(MunchieStage.CURIOUS, MunchieStage.fromCount(10))
        assertEquals(MunchieStage.CURIOUS, MunchieStage.fromCount(15))

        // Stage 3: Hungry (16 - 30)
        assertEquals(MunchieStage.HUNGRY, MunchieStage.fromCount(16))
        assertEquals(MunchieStage.HUNGRY, MunchieStage.fromCount(24))
        assertEquals(MunchieStage.HUNGRY, MunchieStage.fromCount(30))

        // Stage 4: Stuffed (31 - 50)
        assertEquals(MunchieStage.STUFFED, MunchieStage.fromCount(31))
        assertEquals(MunchieStage.STUFFED, MunchieStage.fromCount(45))
        assertEquals(MunchieStage.STUFFED, MunchieStage.fromCount(50))

        // Stage 5: Megachonk Overfed (51+)
        assertEquals(MunchieStage.MEGACHONK, MunchieStage.fromCount(51))
        assertEquals(MunchieStage.MEGACHONK, MunchieStage.fromCount(100))
    }
}
