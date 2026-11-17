package com.reelmonster.app

import com.reelmonster.app.core.util.DateTimeUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class DateTimeUtilsTest {

    @Test
    fun testTimeEstimation() {
        // 1 reel = 1.5 min
        assertEquals(1.5, DateTimeUtils.estimateTimeMinutes(1), 0.001)

        // 24 reels = 36 min (Matches Screen 02 dashboard!)
        assertEquals(36.0, DateTimeUtils.estimateTimeMinutes(24), 0.001)

        // 54 reels = 81 min (1h 21m) (Matches Screen 10 Saturday binge!)
        assertEquals(81.0, DateTimeUtils.estimateTimeMinutes(54), 0.001)
        assertEquals("1h 21m", DateTimeUtils.formatEstimatedTime(81.0))
    }
}
