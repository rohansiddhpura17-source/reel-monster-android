package com.reelmonster.app

import com.reelmonster.app.domain.model.MunchieMascotEngine
import com.reelmonster.app.domain.model.MunchieStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MunchieMascotEngineTest {

    @Test
    fun testStageThresholds() {
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
        assertEquals(MunchieStage.HUNGRY, MunchieStage.fromCount(25))
        assertEquals(MunchieStage.HUNGRY, MunchieStage.fromCount(30))

        // Stage 4: Stuffed (31 - 50)
        assertEquals(MunchieStage.STUFFED, MunchieStage.fromCount(31))
        assertEquals(MunchieStage.STUFFED, MunchieStage.fromCount(40))
        assertEquals(MunchieStage.STUFFED, MunchieStage.fromCount(50))

        // Stage 5: Megachonk (51+)
        assertEquals(MunchieStage.MEGACHONK, MunchieStage.fromCount(51))
        assertEquals(MunchieStage.MEGACHONK, MunchieStage.fromCount(99))
        assertEquals(MunchieStage.MEGACHONK, MunchieStage.fromCount(500))
    }

    @Test
    fun testStageTransitions() {
        assertEquals(MunchieStage.CURIOUS, MunchieMascotEngine.getNextStage(MunchieStage.BABY))
        assertEquals(MunchieStage.HUNGRY, MunchieMascotEngine.getNextStage(MunchieStage.CURIOUS))
        assertEquals(MunchieStage.STUFFED, MunchieMascotEngine.getNextStage(MunchieStage.HUNGRY))
        assertEquals(MunchieStage.MEGACHONK, MunchieMascotEngine.getNextStage(MunchieStage.STUFFED))
        assertNull(MunchieMascotEngine.getNextStage(MunchieStage.MEGACHONK))
    }

    @Test
    fun testEvolutionProgressMath() {
        // Baby stage (0 to 5 reels, total 6 steps)
        assertEquals(0.0f, MunchieMascotEngine.getProgressToNextStage(0), 0.01f)
        assertEquals(0.5f, MunchieMascotEngine.getProgressToNextStage(3), 0.01f)
        assertEquals(6, MunchieMascotEngine.getReelsUntilNextStage(0))
        assertEquals(1, MunchieMascotEngine.getReelsUntilNextStage(5))

        // Curious stage (6 to 15 reels, total 10 steps)
        assertEquals(0.0f, MunchieMascotEngine.getProgressToNextStage(6), 0.01f)
        assertEquals(0.4f, MunchieMascotEngine.getProgressToNextStage(10), 0.01f)
        assertEquals(10, MunchieMascotEngine.getReelsUntilNextStage(6))
        assertEquals(1, MunchieMascotEngine.getReelsUntilNextStage(15))

        // Megachonk stage (51+, maxed out)
        assertEquals(1.0f, MunchieMascotEngine.getProgressToNextStage(51), 0.01f)
        assertEquals(1.0f, MunchieMascotEngine.getProgressToNextStage(100), 0.01f)
        assertEquals(0, MunchieMascotEngine.getReelsUntilNextStage(51))
    }

    @Test
    fun testFunnyMessageSelection() {
        // Verify messages exist for all 5 stages
        MunchieStage.entries.forEach { stage ->
            val message = MunchieMascotEngine.getMessage(stage, 0)
            assertNotNull(message)
            assertTrue(message.isNotBlank())
        }

        // Verify required canonical messages from product spec
        val babyMessages = MunchieMascotEngine.STAGE_MESSAGES[MunchieStage.BABY] ?: emptyList()
        assertTrue(babyMessages.any { it.contains("Just one more?") })

        val curiousMessages = MunchieMascotEngine.STAGE_MESSAGES[MunchieStage.CURIOUS] ?: emptyList()
        assertTrue(curiousMessages.any { it.contains("I can smell another Short!") })

        val hungryMessages = MunchieMascotEngine.STAGE_MESSAGES[MunchieStage.HUNGRY] ?: emptyList()
        assertTrue(hungryMessages.any { it.contains("Feed me another Reel!") })
        assertTrue(hungryMessages.any { it.contains("Your thumb is working overtime!") })

        val stuffedMessages = MunchieMascotEngine.STAGE_MESSAGES[MunchieStage.STUFFED] ?: emptyList()
        assertTrue(stuffedMessages.any { it.contains("Burp! Your evening disappeared.") })
        assertTrue(stuffedMessages.any { it.contains("Homework called. It misses you.") })

        // Verify safe index bounds handling
        val msgWithNegative = MunchieMascotEngine.getMessage(MunchieStage.HUNGRY, -5)
        assertTrue(msgWithNegative.isNotBlank())
        val msgWithLargeIndex = MunchieMascotEngine.getMessage(MunchieStage.STUFFED, 999)
        assertTrue(msgWithLargeIndex.isNotBlank())
    }
}
