package com.mipatrimonio.app.data.quotes

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex

class SlidingWindowRateLimiter(
    private val maximum: Int = 8,
    private val windowMillis: Long = 60_000L,
    private val clock: () -> Long = System::currentTimeMillis,
    private val wait: suspend (Long) -> Unit = { delay(it) },
) {
    private val calls = ArrayDeque<Long>()
    private val mutex = Mutex()

    suspend fun acquire() {
        mutex.lock()
        try {
            while (true) {
                val now = clock()
                while (calls.isNotEmpty() && now - calls.first() >= windowMillis) calls.removeFirst()
                if (calls.size < maximum) {
                    calls.addLast(now)
                    return
                }
                wait((windowMillis - (now - calls.first())).coerceAtLeast(1L))
            }
        } finally {
            mutex.unlock()
        }
    }
}
