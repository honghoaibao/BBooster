package com.honghoaibao.bbooster.core.shizuku.remote

import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserServiceSecurityTest {

    @Test
    fun acceptsOnlyTheExpectedApplicationUid() {
        assertTrue(UserServiceCallerPolicy.isAuthorized(10_234, 10_234))
    }

    @Test
    fun rejectsDifferentCallerUid() {
        assertFalse(UserServiceCallerPolicy.isAuthorized(10_234, 10_235))
    }

    @Test
    fun rejectsWhenExpectedUidCannotBeResolved() {
        assertFalse(UserServiceCallerPolicy.isAuthorized(null, 10_234))
    }

    @Test
    fun rejectsInvalidUids() {
        assertFalse(UserServiceCallerPolicy.isAuthorized(-1, 10_234))
        assertFalse(UserServiceCallerPolicy.isAuthorized(10_234, -1))
    }

    @Test
    fun acceptsDestroyOnlyFromTheUserServiceControllerUid() {
        assertTrue(UserServiceCallerPolicy.isAuthorizedServiceController(2_000, 2_000))
        assertTrue(UserServiceCallerPolicy.isAuthorizedServiceController(0, 0))
        assertFalse(UserServiceCallerPolicy.isAuthorizedServiceController(2_000, 10_234))
        assertFalse(UserServiceCallerPolicy.isAuthorizedServiceController(-1, -1))
    }

    @Test
    fun rejectsRequestsWhenWorkersAndQueueAreFull() {
        val workerStarted = CountDownLatch(1)
        val releaseWorker = CountDownLatch(1)
        val executor = UserServiceCommandExecutorFactory.create(
            maxConcurrentCommands = 1,
            maxQueuedCommands = 1,
            threadNamePrefix = "UserServiceSecurityTest"
        )

        try {
            executor.execute {
                workerStarted.countDown()
                releaseWorker.await()
            }
            assertTrue(workerStarted.await(1, TimeUnit.SECONDS))

            executor.execute { }
            assertEquals(1, executor.queue.size)

            val rejected = runCatching {
                executor.execute { }
            }.exceptionOrNull()

            assertTrue(rejected is RejectedExecutionException)
            assertEquals(1, executor.queue.size)
        } finally {
            releaseWorker.countDown()
            executor.shutdown()
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS))
        }
    }

    @Test
    fun rejectsInvalidExecutorCapacity() {
        val noWorkers = runCatching {
            UserServiceCommandExecutorFactory.create(0, 1)
        }.exceptionOrNull()
        val noQueue = runCatching {
            UserServiceCommandExecutorFactory.create(1, 0)
        }.exceptionOrNull()

        assertTrue(noWorkers is IllegalArgumentException)
        assertTrue(noQueue is IllegalArgumentException)
    }
}
