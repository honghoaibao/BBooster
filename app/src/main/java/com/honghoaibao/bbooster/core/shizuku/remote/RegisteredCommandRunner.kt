package com.honghoaibao.bbooster.core.shizuku.remote

import com.honghoaibao.bbooster.core.command.CommandDefinition
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Runs a registered command argument vector in the UserService process.
 * ProcessBuilder receives only the immutable argument list from CommandRegistry;
 * no shell interpreter or caller-provided command text is used.
 */
internal class RegisteredCommandRunner(
    private val startProcess: (List<String>) -> Process = { arguments ->
        ProcessBuilder(arguments).redirectErrorStream(false).start()
    }
) {
    private val closed = AtomicBoolean(false)
    private val activeProcesses = ConcurrentHashMap.newKeySet<Process>()
    private val streamReaders = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "BBooster-command-output").apply { isDaemon = true }
    }

    fun execute(command: CommandDefinition, requestedTimeoutMs: Long): CommandRunResult {
        if (closed.get()) {
            return CommandRunResult(
                started = false,
                rejectionReason = "Command runner is closed."
            )
        }

        val timeoutMs = requestedTimeoutMs.coerceAtLeast(1L).coerceAtMost(command.timeoutMs)
        val process = try {
            startProcess(command.arguments.toList())
        } catch (error: Exception) {
            return CommandRunResult(
                started = false,
                rejectionReason = "Process could not be started: ${error.javaClass.simpleName}: ${error.message.orEmpty()}"
            )
        }

        activeProcesses.add(process)
        if (closed.get()) {
            terminate(process)
            activeProcesses.remove(process)
            return CommandRunResult(
                started = true,
                exitCode = -1,
                stderr = "Command runner closed while the process was starting."
            )
        }

        val outputBudget = AtomicInteger(command.maxOutputBytes)
        val stdoutFuture = streamReaders.submit<OutputCapture> {
            capture(process.inputStream, outputBudget)
        }
        val stderrFuture = streamReaders.submit<OutputCapture> {
            capture(process.errorStream, outputBudget)
        }

        try {
            runCatching { process.outputStream.close() }

            val completed = try {
                process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                terminate(process)
                Thread.currentThread().interrupt()
                val stdout = awaitCapture(stdoutFuture, process.inputStream)
                val stderr = awaitCapture(stderrFuture, process.errorStream)
                return CommandRunResult(
                    started = true,
                    exitCode = -1,
                    stdout = stdout.text,
                    stderr = appendDetail(stderr.text, "Command worker was interrupted."),
                    outputTruncated = stdout.truncated || stderr.truncated
                )
            }

            if (!completed) {
                terminate(process)
                val stdout = awaitCapture(stdoutFuture, process.inputStream)
                val stderr = awaitCapture(stderrFuture, process.errorStream)
                return CommandRunResult(
                    started = true,
                    exitCode = -1,
                    stdout = stdout.text,
                    stderr = stderr.text,
                    timedOut = true,
                    outputTruncated = stdout.truncated || stderr.truncated
                )
            }

            val exitCode = runCatching { process.exitValue() }.getOrDefault(-1)
            val stdout = awaitCapture(stdoutFuture, process.inputStream)
            val stderr = awaitCapture(stderrFuture, process.errorStream)
            val readFailed = stdout.readFailed || stderr.readFailed

            return CommandRunResult(
                started = true,
                exitCode = if (readFailed && exitCode == 0) -1 else exitCode,
                stdout = stdout.text,
                stderr = if (readFailed) {
                    appendDetail(stderr.text, "One or more process output streams could not be read.")
                } else {
                    stderr.text
                },
                outputTruncated = stdout.truncated || stderr.truncated
            )
        } catch (error: Exception) {
            terminate(process)
            return CommandRunResult(
                started = true,
                exitCode = -1,
                stderr = "Command execution failed: ${error.javaClass.simpleName}: ${error.message.orEmpty()}"
            )
        } finally {
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
            runCatching { process.outputStream.close() }
            stdoutFuture.cancel(true)
            stderrFuture.cancel(true)
            activeProcesses.remove(process)
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        activeProcesses.toList().forEach(::terminate)
        streamReaders.shutdownNow()
    }

    private fun capture(stream: InputStream, remainingBudget: AtomicInteger): OutputCapture {
        val output = ByteArrayOutputStream()
        var truncated = false
        return try {
            stream.use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue

                    val captured = synchronized(remainingBudget) {
                        val remaining = remainingBudget.get()
                        val allowed = minOf(remaining, count).coerceAtLeast(0)
                        if (allowed > 0) {
                            output.write(buffer, 0, allowed)
                            remainingBudget.set(remaining - allowed)
                        }
                        allowed
                    }
                    if (captured < count) truncated = true
                }
            }
            OutputCapture(
                text = String(output.toByteArray(), StandardCharsets.UTF_8),
                truncated = truncated
            )
        } catch (_: Exception) {
            OutputCapture(
                text = String(output.toByteArray(), StandardCharsets.UTF_8),
                truncated = truncated,
                readFailed = true
            )
        }
    }

    private fun awaitCapture(future: Future<OutputCapture>, stream: InputStream): OutputCapture {
        return try {
            future.get(OUTPUT_DRAIN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            runCatching { stream.close() }
            future.cancel(true)
            OutputCapture("", truncated = false, readFailed = true)
        } catch (_: ExecutionException) {
            OutputCapture("", truncated = false, readFailed = true)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            runCatching { stream.close() }
            future.cancel(true)
            OutputCapture("", truncated = false, readFailed = true)
        }
    }

    private fun terminate(process: Process) {
        runCatching { process.destroy() }
        val stopped = runCatching {
            process.waitFor(TERMINATE_GRACE_MS, TimeUnit.MILLISECONDS)
        }.getOrDefault(false)
        if (!stopped) runCatching { process.destroyForcibly() }
    }

    private fun appendDetail(existing: String, detail: String): String =
        if (existing.isBlank()) detail else "$existing\n$detail"

    private data class OutputCapture(
        val text: String,
        val truncated: Boolean,
        val readFailed: Boolean = false
    )

    private companion object {
        const val OUTPUT_DRAIN_TIMEOUT_MS = 1_500L
        const val TERMINATE_GRACE_MS = 300L
    }
}

internal data class CommandRunResult(
    val started: Boolean,
    val exitCode: Int = -1,
    val stdout: String = "",
    val stderr: String = "",
    val timedOut: Boolean = false,
    val outputTruncated: Boolean = false,
    val rejectionReason: String? = null
)
