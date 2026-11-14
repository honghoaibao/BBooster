package com.honghoaibao.bbooster.core.shizuku.transport

import com.honghoaibao.bbooster.core.command.CommandDefinition
import com.honghoaibao.bbooster.core.command.CommandDispatchState
import com.honghoaibao.bbooster.core.command.CommandExecutionStatus
import com.honghoaibao.bbooster.core.command.CommandFailureCode
import com.honghoaibao.bbooster.core.command.CommandRegistry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectProcessTransportTest {

    @Test
    fun refreshRejectsUnavailableBinder() = runBlocking {
        val transport = newTransport(
            binder = false,
            permission = true,
            starter = FakeStarter()
        )

        val status = transport.refresh()

        assertEquals(TransportAvailability.UNAVAILABLE, status.state)
    }

    @Test
    fun refreshRejectsMissingShizukuPermission() = runBlocking {
        val transport = newTransport(
            binder = true,
            permission = false,
            starter = FakeStarter()
        )

        val status = transport.refresh()

        assertEquals(TransportAvailability.UNAVAILABLE, status.state)
        assertTrue(status.detail.orEmpty().contains("permission", ignoreCase = true))
    }

    @Test
    fun refreshReportsUnsupportedWhenAdapterCannotResolveApi() = runBlocking {
        val transport = newTransport(
            binder = true,
            permission = true,
            starter = FakeStarter(
                availability = ProcessStarterAvailability.UNSUPPORTED
            )
        )

        val status = transport.refresh()

        assertEquals(TransportAvailability.UNSUPPORTED, status.state)
    }

    @Test
    fun successfulProcessReturnsStdoutStderrAndExitCode() = runBlocking {
        val process = FakeProcess(
            stdout = "device-shell".toByteArray(),
            stderr = "diagnostic-note".toByteArray(),
            exitCode = 0
        )
        val starter = FakeStarter(process = process)
        val transport = newTransport(starter = starter)

        val result = transport.execute(command())

        assertEquals(CommandExecutionStatus.COMPLETED, result.status)
        assertEquals(CommandDispatchState.DISPATCHED, result.dispatchState)
        assertEquals(0, result.exitCode)
        assertEquals("device-shell", result.stdout)
        assertEquals("diagnostic-note", result.stderr)
        assertTrue(result.isSuccess)
        assertEquals(1, starter.startCount)
        assertEquals(TransportAvailability.AVAILABLE, transport.status.value.state)
        assertFalse(process.isAlive)
    }

    @Test
    fun nonZeroExitCodeIsFailureNotSuccess() = runBlocking {
        val process = FakeProcess(
            stdout = byteArrayOf(),
            stderr = "permission denied".toByteArray(),
            exitCode = 13
        )
        val transport = newTransport(starter = FakeStarter(process = process))

        val result = transport.execute(command())

        assertEquals(CommandExecutionStatus.FAILED, result.status)
        assertEquals(13, result.exitCode)
        assertFalse(result.isSuccess)
        assertEquals(CommandFailureCode.NON_ZERO_EXIT, result.failureCode)
    }

    @Test
    fun timeoutTerminatesProcessAndNeverReportsSuccess() = runBlocking {
        val process = FakeProcess(
            stdout = "partial".toByteArray(),
            stderr = byteArrayOf(),
            exitCode = -1,
            completesWithinTimeout = false
        )
        val transport = newTransport(starter = FakeStarter(process = process))

        val result = transport.execute(command())

        assertEquals(CommandExecutionStatus.TIMED_OUT, result.status)
        assertEquals(CommandDispatchState.DISPATCHED, result.dispatchState)
        assertEquals(CommandFailureCode.COMMAND_TIMED_OUT, result.failureCode)
        assertFalse(result.isSuccess)
        assertTrue(process.destroyed)
    }

    @Test
    fun combinedOutputIsBoundedByRegisteredCommandLimit() = runBlocking {
        val largeOutput = ByteArray(80 * 1024) { 'x'.code.toByte() }
        val process = FakeProcess(
            stdout = largeOutput,
            stderr = largeOutput,
            exitCode = 0
        )
        val transport = newTransport(starter = FakeStarter(process = process))

        val result = transport.execute(command())

        val capturedBytes = result.stdout.toByteArray(StandardCharsets.UTF_8).size +
            result.stderr.toByteArray(StandardCharsets.UTF_8).size

        assertTrue(result.outputTruncated)
        assertTrue(capturedBytes <= command().maxOutputBytes)
    }

    @Test
    fun commandNotInRegistryIsRejectedWithoutStartingProcess() = runBlocking {
        val starter = FakeStarter()
        val transport = newTransport(starter = starter)
        val altered = command().copy(timeoutMs = command().timeoutMs + 1L)

        val result = transport.execute(altered)

        assertEquals(CommandExecutionStatus.REJECTED, result.status)
        assertEquals(CommandDispatchState.NOT_DISPATCHED, result.dispatchState)
        assertEquals(CommandFailureCode.COMMAND_REJECTED, result.failureCode)
        assertEquals(0, starter.startCount)
    }

    @Test
    fun adapterFailureBeforeDispatchIsReportedAsNotStarted() = runBlocking {
        val starter = FakeStarter(
            startFailure = ProcessStartException(
                dispatchState = CommandDispatchState.NOT_DISPATCHED,
                failureCode = CommandFailureCode.TRANSPORT_UNSUPPORTED,
                detail = "Private method unavailable."
            )
        )
        val transport = newTransport(starter = starter)

        val result = transport.execute(command())

        assertEquals(CommandExecutionStatus.NOT_STARTED, result.status)
        assertEquals(CommandDispatchState.NOT_DISPATCHED, result.dispatchState)
        assertTrue(result.canSafelyFallback)
    }

    @Test
    fun adapterFailureAfterInvocationIsUnknownAndCannotFallback() = runBlocking {
        val starter = FakeStarter(
            startFailure = ProcessStartException(
                dispatchState = CommandDispatchState.UNKNOWN,
                failureCode = CommandFailureCode.PROCESS_START_FAILED,
                detail = "Remote invocation outcome unknown."
            )
        )
        val transport = newTransport(starter = starter)

        val result = transport.execute(command())

        assertEquals(CommandExecutionStatus.UNKNOWN, result.status)
        assertEquals(CommandDispatchState.UNKNOWN, result.dispatchState)
        assertFalse(result.canSafelyFallback)
    }

    @Test
    fun closeMarksTransportUnavailable() = runBlocking {
        val transport = newTransport(starter = FakeStarter())

        transport.close()

        assertEquals(TransportAvailability.UNAVAILABLE, transport.status.value.state)
        assertEquals(TransportAvailability.UNAVAILABLE, transport.refresh().state)
    }

    private fun command(): CommandDefinition =
        requireNotNull(CommandRegistry.find("caller_identity"))

    private fun newTransport(
        binder: Boolean = true,
        permission: Boolean = true,
        starter: FakeStarter
    ) = DirectProcessTransport(
        processStarter = starter,
        binderAvailable = { binder },
        permissionGranted = { permission }
    )

    private class FakeStarter(
        private val availability: ProcessStarterAvailability =
            ProcessStarterAvailability.AVAILABLE,
        private val process: Process = FakeProcess(
            stdout = byteArrayOf(),
            stderr = byteArrayOf(),
            exitCode = 0
        ),
        private val startFailure: ProcessStartException? = null
    ) : RemoteProcessStarter {
        var startCount: Int = 0
            private set

        override fun checkAvailability(): ProcessStarterAvailability = availability

        override fun start(command: CommandDefinition): Process {
            startCount++
            startFailure?.let { throw it }
            return process
        }
    }

    private class FakeProcess(
        stdout: ByteArray,
        stderr: ByteArray,
        private val exitCode: Int,
        private val completesWithinTimeout: Boolean = true
    ) : Process() {
        private val stdoutStream = ByteArrayInputStream(stdout)
        private val stderrStream = ByteArrayInputStream(stderr)
        private val stdinStream = ByteArrayOutputStream()
        var destroyed: Boolean = false
            private set
        private var finished: Boolean = false

        override fun getOutputStream(): OutputStream = stdinStream

        override fun getInputStream() = stdoutStream

        override fun getErrorStream() = stderrStream

        override fun waitFor(): Int {
            finished = true
            return exitCode
        }

        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean {
            if (!completesWithinTimeout) return false
            finished = true
            return true
        }

        override fun exitValue(): Int {
            check(finished) { "Process has not completed." }
            return exitCode
        }

        override fun destroy() {
            destroyed = true
            finished = true
        }

        override fun destroyForcibly(): Process {
            destroy()
            return this
        }

        override fun isAlive(): Boolean = !finished
    }
}
