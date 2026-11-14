package com.honghoaibao.bbooster.core.shizuku.remote

import com.honghoaibao.bbooster.core.command.CommandRegistry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegisteredCommandRunnerTest {

    @Test
    fun runnerPassesOnlyRegistryArgumentVectorToProcessStarter() {
        val definition = requireNotNull(CommandRegistry.find("android_release"))
        var receivedArguments: List<String>? = null
        val runner = RegisteredCommandRunner { arguments ->
            receivedArguments = arguments
            FakeProcess(byteArrayOf(), byteArrayOf(), 0)
        }

        val result = runner.execute(definition, definition.timeoutMs)
        runner.close()

        assertTrue(result.started)
        assertEquals(listOf("getprop", "ro.build.version.release"), receivedArguments)
    }

    @Test
    fun processStartFailureIsReportedBeforeDispatch() {
        val definition = requireNotNull(CommandRegistry.find("caller_identity"))
        val runner = RegisteredCommandRunner {
            throw IllegalStateException("process API unavailable")
        }

        val result = runner.execute(definition, definition.timeoutMs)
        runner.close()

        assertFalse(result.started)
        assertTrue(result.rejectionReason.orEmpty().contains("process API unavailable"))
    }

    @Test
    fun outputFromBothStreamsRespectsCombinedByteLimit() {
        val definition = requireNotNull(CommandRegistry.find("caller_identity"))
        val large = ByteArray(definition.maxOutputBytes) { 'x'.code.toByte() }
        val runner = RegisteredCommandRunner {
            FakeProcess(large, large, 0)
        }

        val result = runner.execute(definition, definition.timeoutMs)
        runner.close()

        val totalBytes = result.stdout.toByteArray().size + result.stderr.toByteArray().size
        assertTrue(result.outputTruncated)
        assertTrue(totalBytes <= definition.maxOutputBytes)
    }

    @Test
    fun nonZeroProcessExitIsReturnedToCaller() {
        val definition = requireNotNull(CommandRegistry.find("caller_identity"))
        val runner = RegisteredCommandRunner {
            FakeProcess(byteArrayOf(), "failed".toByteArray(), 7)
        }

        val result = runner.execute(definition, definition.timeoutMs)
        runner.close()

        assertTrue(result.started)
        assertEquals(7, result.exitCode)
        assertEquals("failed", result.stderr)
    }

    @Test
    fun timedOutProcessIsTerminatedAndReported() {
        val definition = requireNotNull(CommandRegistry.find("caller_identity"))
        lateinit var process: FakeProcess
        val runner = RegisteredCommandRunner {
            FakeProcess(byteArrayOf(), byteArrayOf(), -1, completesWithinTimeout = false)
                .also { process = it }
        }

        val result = runner.execute(definition, definition.timeoutMs)
        runner.close()

        assertTrue(result.started)
        assertTrue(result.timedOut)
        assertTrue(process.destroyed)
    }

    private class FakeProcess(
        stdout: ByteArray,
        stderr: ByteArray,
        private val code: Int,
        private val completesWithinTimeout: Boolean = true
    ) : Process() {
        private val out = ByteArrayInputStream(stdout)
        private val err = ByteArrayInputStream(stderr)
        private val input = ByteArrayOutputStream()
        var destroyed = false
            private set
        private var finished = false

        override fun getOutputStream(): OutputStream = input
        override fun getInputStream() = out
        override fun getErrorStream() = err

        override fun waitFor(): Int {
            finished = true
            return code
        }

        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean {
            if (!completesWithinTimeout) return false
            finished = true
            return true
        }

        override fun exitValue(): Int {
            check(finished)
            return code
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
