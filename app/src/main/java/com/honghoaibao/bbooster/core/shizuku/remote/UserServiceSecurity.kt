package com.honghoaibao.bbooster.core.shizuku.remote

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Fail-closed authorization for calls received by the privileged UserService process. */
internal object UserServiceCallerPolicy {

    fun isAuthorized(expectedCallerUid: Int?, callingUid: Int): Boolean =
        expectedCallerUid != null &&
            expectedCallerUid >= 0 &&
            callingUid >= 0 &&
            callingUid == expectedCallerUid
}

/**
 * Creates a fixed-size command pool with a bounded pending queue.
 * Excess requests are rejected instead of accumulating without a limit.
 */
internal object UserServiceCommandExecutorFactory {

    fun create(
        maxConcurrentCommands: Int,
        maxQueuedCommands: Int,
        threadNamePrefix: String = DEFAULT_THREAD_NAME_PREFIX
    ): ThreadPoolExecutor {
        require(maxConcurrentCommands > 0) {
            "At least one command worker is required."
        }
        require(maxQueuedCommands > 0) {
            "The pending command queue must have a positive capacity."
        }
        require(threadNamePrefix.isNotBlank()) {
            "The command worker thread name prefix must not be blank."
        }

        val threadIndex = AtomicInteger()
        val threadFactory = ThreadFactory { runnable ->
            Thread(
                runnable,
                "$threadNamePrefix-${threadIndex.incrementAndGet()}"
            ).apply {
                isDaemon = true
            }
        }

        return ThreadPoolExecutor(
            maxConcurrentCommands,
            maxConcurrentCommands,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(maxQueuedCommands),
            threadFactory,
            ThreadPoolExecutor.AbortPolicy()
        )
    }

    private const val DEFAULT_THREAD_NAME_PREFIX = "BBooster-user-command"
}
