package com.mipatrimonio.app.data.quotes

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RateLimiterTest {
    @Test fun `la novena llamada espera hasta completar un minuto`() = runTest {
        var now = 0L
        val waits = mutableListOf<Long>()
        val limiter = SlidingWindowRateLimiter(clock = { now }, wait = { duration -> waits += duration; now += duration })
        repeat(9) { limiter.acquire() }
        assertEquals(listOf(60_000L), waits)
    }
}
