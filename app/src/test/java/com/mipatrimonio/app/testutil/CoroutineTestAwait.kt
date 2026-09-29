package com.mipatrimonio.app.testutil

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val DEFAULT_AWAIT_TIMEOUT_MILLIS = 10_000L

suspend fun <T> Flow<T>.awaitValue(
    timeoutMillis: Long = DEFAULT_AWAIT_TIMEOUT_MILLIS,
    predicate: suspend (T) -> Boolean,
): T = withContext(Dispatchers.Default) {
    withTimeout(timeoutMillis) { first(predicate) }
}

suspend fun <T> Deferred<T>.awaitValue(
    timeoutMillis: Long = DEFAULT_AWAIT_TIMEOUT_MILLIS,
): T = withContext(Dispatchers.Default) {
    withTimeout(timeoutMillis) { await() }
}
