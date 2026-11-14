package com.honghoaibao.bbooster.core.shizuku.transport

import android.content.pm.PackageManager
import com.honghoaibao.bbooster.core.command.CommandCapability
import com.honghoaibao.bbooster.core.command.CommandDefinition
import com.honghoaibao.bbooster.core.command.CommandDispatchState
import com.honghoaibao.bbooster.core.command.CommandExecutionMode
import com.honghoaibao.bbooster.core.command.CommandExecutionStatus
import com.honghoaibao.bbooster.core.command.CommandFailureCode
import com.honghoaibao.bbooster.core.command.CommandRegistry
import com.honghoaibao.bbooster.core.command.CommandResult
import com.honghoaibao.bbooster.core.command.CommandTransportType
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

/**
 * One-shot Shizuku process transport.
 *
 * Only immutable definitions registered in [CommandRegistry] are accepted.
 * This class does not provide a shell-string or arbitrary-argument API.
 */
class DirectProcessTransport internal constructor(
    private val processStarter: RemoteProcessStarter,
    private val binderAvailable: () -> Boolean,
    private val permissionGranted: () -> Boolean
) : ShizukuTransport {

    constructor() : this(
        processStarter = ShizukuNewProcessAdapter(),
        binderAvailable = { Shizuku.pingBinder() },
        permissionGranted = {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }
    )

    override val type: CommandTransportType = CommandTransportType.DIRECT_PROCESS

    private val mutableStatus = MutableStateFlow(
        TransportStatus(
            transport = type,
            state = TransportAvailability.CHECKING
        )
    )
    override val status: StateFlow<TransportStatus> = mutableStatus.asStateFlow()

    private val activeProcesses = ConcurrentHashMap.newKeySet<Process>()
    private val closed = AtomicBoolean(false)

    override suspend fun refresh(): TransportStatus = withContext(Dispatchers.IO) {
        val result = when {
            !safeBinderCheck() -> TransportStatus(
                transport = type,
                state = TransportAvailability.UNAVAILABLE,
                detail = "Shizuku Binder is unavailable."
            )

            !safePermissionCheck() -> TransportStatus(
                transport = type,
                state = TransportAvailability.UNAVAILABLE,
                detail = "BBooster does not have Shizuku permission."
            )

            safeStarterAvailability() == ProcessStarterAvailability.UNSUPPORTED ->
                TransportStatus(
                    transport = type,
                    state = TransportAvailability.UNSUPPORTED,
                    detail = "The installed Shizuku API does not expose the compatible process method."
                )

            else -> availableStatus()
        }
        mutableStatus.value = result
        result
    }

    override suspend fun execute(command: CommandDefinition): CommandResult =
        withContext(Dispatchers.IO) {
            val startedAt = System.nanoTime()

            if (!isRegistered(command)) {
                return@withContext result(
                    command = command,
                    status = CommandExecutionStatus.REJECTED,
                    dispatchState = CommandDispatchState.NOT_DISPATCHED,
                    failureCode = CommandFailureCode.COMMAND_REJECTED,
                    detail = "Command is not registered."
                )
            }

            if (CommandTransportType.DIRECT_PROCESS !in command.supportedTransports ||
                command.executionMode != CommandExecutionMode.ONE_SHOT
            ) {
                return@withContext result(
                    command = command,
                    status = CommandExecutionStatus.REJECTED,
                    dispatchState = CommandDispatchState.NOT_DISPATCHED,
                    failureCode = CommandFailureCode.TRANSPORT_UNSUPPORTED,
                    detail = "This command is not supported by one-shot Direct Process."
                )
            }

            if (!safeBinderCheck()) {
                setUnavailable("Shizuku Binder is unavailable.")
                return@withContext result(
                    command = command,
                    status = CommandExecutionStatus.NOT_STARTED,
                    dispatchState = CommandDispatchState.NOT_DISPATCHED,
                    failureCode = CommandFailureCode.SHIZUKU_UNAVAILABLE,
                    detail = "Shizuku Binder is unavailable.",
                    startedAt = startedAt
                )
            }

            if (!safePermissionCheck()) {
                setUnavailable("BBooster does not have Shizuku permission.")
                return@withContext result(
                    command = command,
                    status = CommandExecutionStatus.NOT_STARTED,
                    dispatchState = CommandDispatchState.NOT_DISPATCHED,
                    failureCode = CommandFailureCode.PERMISSION_REQUIRED,
                    detail = "BBooster does not have Shizuku permission.",
                    startedAt = startedAt
                )
            }

            if (safeStarterAvailability() != ProcessStarterAvailability.AVAILABLE) {
                mutableStatus.value = TransportStatus(
                    transport = type,
                    state = TransportAvailability.UNSUPPORTED,
                    detail = "Compatible Shizuku process API is unavailable."
                )
                return@withContext result(
                    command = command,
                    status = CommandExecutionStatus.NOT_STARTED,
                    dispatchState = CommandDispatchState.NOT_DISPATCHED,
                    failureCode = CommandFailureCode.TRANSPORT_UNSUPPORTED,
                    detail = "Compatible Shizuku process API is unavailable.",
                    startedAt = startedAt
                )
            }

            mutableStatus.value = TransportStatus(
                transport = type,
                state = TransportAvailability.STARTING,
                capabilities = setOf(CommandCapability.PROCESS_EXECUTION)
            )

            val process = try {
                // If the caller is cancelled while the reflective Binder call is
                // in flight, still receive the handle so it can be registered
                // and destroyed instead of leaking a remote process.
                withContext(NonCancellable + Dispatchers.IO) {
                    processStarter.start(command)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: ProcessStartException) {
                mutableStatus.value = when {
                    error.failureCode == CommandFailureCode.TRANSPORT_UNSUPPORTED ->
                        TransportStatus(
                            transport = type,
                            state = TransportAvailability.UNSUPPORTED,
                            detail = error.detail
                        )
                    error.dispatchState == CommandDispatchState.UNKNOWN ->
                        TransportStatus(
                            transport = type,
                            state = TransportAvailability.DEGRADED,
                            detail = "The remote process request outcome is unknown."
                        )
                    else -> availableStatus()
                }
                return@withContext result(
                    command = command,
                    status = if (error.dispatchState == CommandDispatchState.UNKNOWN) {
                        CommandExecutionStatus.UNKNOWN
                    } else {
                        CommandExecutionStatus.NOT_STARTED
                    },
                    dispatchState = error.dispatchState,
                    failureCode = error.failureCode,
                    detail = error.detail,
                    startedAt = startedAt
                )
            } catch (error: SecurityException) {
                setUnavailable("Shizuku denied the process request.")
                return@withContext result(
                    command = command,
                    status = CommandExecutionStatus.NOT_STARTED,
                    dispatchState = CommandDispatchState.NOT_DISPATCHED,
                    failureCode = CommandFailureCode.PERMISSION_REQUIRED,
                    detail = "Shizuku denied the process request.",
                    startedAt = startedAt
                )
            } catch (error: Exception) {
                mutableStatus.value = TransportStatus(
                    transport = type,
                    state = TransportAvailability.DEGRADED,
                    detail = "The remote process request outcome is unknown."
                )
                return@withContext result(
                    command = command,
                    status = CommandExecutionStatus.UNKNOWN,
                    dispatchState = CommandDispatchState.UNKNOWN,
                    failureCode = CommandFailureCode.PROCESS_START_FAILED,
                    detail = "The process request failed after invocation began.",
                    startedAt = startedAt
                )
            }

            activeProcesses.add(process)
            try {
                currentCoroutineContext().ensureActive()
                if (closed.get()) {
                    terminateQuietly(process)
                    closeProcessStreams(process)
                    return@withContext result(
                        command = command,
                        status = CommandExecutionStatus.CANCELLED,
                        dispatchState = CommandDispatchState.DISPATCHED,
                        failureCode = CommandFailureCode.PROCESS_INTERRUPTED,
                        detail = "Direct Process transport was closed after dispatch.",
                        startedAt = startedAt
                    )
                }

                executeStartedProcess(command, process, startedAt)
            } catch (cancelled: CancellationException) {
                // Cancellation can happen after the process handle is acquired
                // but before the child stream readers start. Always kill it.
                terminateQuietly(process)
                closeProcessStreams(process)
                throw cancelled
            } finally {
                activeProcesses.remove(process)
                closeProcessStreams(process)
                if (!closed.get() && safeBinderCheck() && safePermissionCheck()) {
                    mutableStatus.value = availableStatus()
                }
            }
        }

    override suspend fun close() = withContext(Dispatchers.IO) {
        closed.set(true)
        activeProcesses.toList().forEach { process ->
            terminateQuietly(process)
            closeProcessStreams(process)
        }
        mutableStatus.value = TransportStatus(
            transport = type,
            state = TransportAvailability.UNAVAILABLE,
            detail = "Direct Process transport is closed."
        )
    }

    private suspend fun executeStartedProcess(
        command: CommandDefinition,
        process: Process,
        startedAt: Long
    ): CommandResult = coroutineScope {
        val outputBudget = AtomicInteger(command.maxOutputBytes.coerceAtLeast(1))
        val stdoutTask = async(Dispatchers.IO) {
            captureOutput(process.inputStream, outputBudget)
        }
        val stderrTask = async(Dispatchers.IO) {
            captureOutput(process.errorStream, outputBudget)
        }

        try {
            runCatching { process.outputStream.close() }

            val completed = waitForRemoteProcess(process, command.timeoutMs)
            if (!completed) {
                terminateQuietly(process)
                val stdout = awaitOutput(stdoutTask, process.inputStream)
                val stderr = awaitOutput(stderrTask, process.errorStream)
                return@coroutineScope result(
                    command = command,
                    status = CommandExecutionStatus.TIMED_OUT,
                    dispatchState = CommandDispatchState.DISPATCHED,
                    failureCode = CommandFailureCode.COMMAND_TIMED_OUT,
                    detail = "The remote process exceeded its command timeout.",
                    startedAt = startedAt,
                    stdout = stdout.text,
                    stderr = stderr.text,
                    outputTruncated = stdout.truncated || stderr.truncated
                )
            }

            val exitCode = process.exitValue()
            val stdout = stdoutTask.await()
            val stderr = stderrTask.await()
            val streamFailed = stdout.readFailed || stderr.readFailed

            result(
                command = command,
                status = when {
                    streamFailed -> CommandExecutionStatus.FAILED
                    exitCode == 0 -> CommandExecutionStatus.COMPLETED
                    else -> CommandExecutionStatus.FAILED
                },
                dispatchState = CommandDispatchState.DISPATCHED,
                exitCode = exitCode,
                failureCode = when {
                    streamFailed -> CommandFailureCode.REMOTE_CALL_FAILED
                    exitCode != 0 -> CommandFailureCode.NON_ZERO_EXIT
                    else -> null
                },
                detail = when {
                    streamFailed ->
                        "One or more process output streams could not be read completely."
                    exitCode != 0 ->
                        "The remote process exited with a non-zero status."
                    else -> null
                },
                startedAt = startedAt,
                stdout = stdout.text,
                stderr = stderr.text,
                outputTruncated = stdout.truncated || stderr.truncated
            )
        } catch (cancelled: CancellationException) {
            terminateQuietly(process)
            closeProcessStreams(process)
            stdoutTask.cancel()
            stderrTask.cancel()
            throw cancelled
        } catch (error: Exception) {
            terminateQuietly(process)
            closeProcessStreams(process)
            stdoutTask.cancel()
            stderrTask.cancel()
            mutableStatus.value = TransportStatus(
                transport = type,
                state = TransportAvailability.DEGRADED,
                detail = "The process was interrupted or its Binder connection was lost."
            )
            result(
                command = command,
                status = CommandExecutionStatus.UNKNOWN,
                dispatchState = CommandDispatchState.UNKNOWN,
                failureCode = CommandFailureCode.PROCESS_INTERRUPTED,
                detail = "The remote process was interrupted or its Binder connection was lost.",
                startedAt = startedAt
            )
        }
    }

    private fun waitForRemoteProcess(process: Process, timeoutMs: Long): Boolean =
        if (process is ShizukuRemoteProcess) {
            process.waitForTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        } else {
            process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        }

    private suspend fun awaitOutput(
        task: kotlinx.coroutines.Deferred<OutputCapture>,
        stream: InputStream
    ): OutputCapture {
        val capture = withTimeoutOrNull(OUTPUT_DRAIN_TIMEOUT_MS) { task.await() }
        if (capture != null) return capture

        runCatching { stream.close() }
        task.cancel()
        return OutputCapture(text = "", truncated = false, readFailed = true)
    }

    private fun captureOutput(
        stream: InputStream,
        remainingBudget: AtomicInteger
    ): OutputCapture {
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
        } catch (error: Exception) {
            OutputCapture(
                text = String(output.toByteArray(), StandardCharsets.UTF_8),
                truncated = truncated,
                readFailed = true
            )
        }
    }

    private fun result(
        command: CommandDefinition,
        status: CommandExecutionStatus,
        dispatchState: CommandDispatchState,
        exitCode: Int? = null,
        failureCode: CommandFailureCode? = null,
        detail: String? = null,
        startedAt: Long = System.nanoTime(),
        stdout: String = "",
        stderr: String = "",
        outputTruncated: Boolean = false
    ) = CommandResult(
        commandId = command.id,
        transport = type,
        status = status,
        dispatchState = dispatchState,
        exitCode = exitCode,
        stdout = stdout,
        stderr = stderr,
        durationMs = ((System.nanoTime() - startedAt) / NANOS_PER_MILLISECOND).coerceAtLeast(0L),
        failureCode = failureCode,
        failureDetail = detail,
        outputTruncated = outputTruncated
    )

    private fun isRegistered(command: CommandDefinition): Boolean =
        CommandRegistry.find(command.id) == command

    private fun safeBinderCheck(): Boolean = try {
        !closed.get() && binderAvailable()
    } catch (_: Exception) {
        false
    }

    private fun safePermissionCheck(): Boolean = try {
        permissionGranted()
    } catch (_: Exception) {
        false
    }

    private fun safeStarterAvailability(): ProcessStarterAvailability = try {
        processStarter.checkAvailability()
    } catch (_: Exception) {
        ProcessStarterAvailability.UNSUPPORTED
    }

    private fun availableStatus() = TransportStatus(
        transport = type,
        state = TransportAvailability.AVAILABLE,
        capabilities = setOf(CommandCapability.PROCESS_EXECUTION)
    )

    private fun setUnavailable(detail: String) {
        mutableStatus.value = TransportStatus(
            transport = type,
            state = TransportAvailability.UNAVAILABLE,
            detail = detail
        )
    }

    private fun terminateQuietly(process: Process) {
        try {
            process.destroy()
        } catch (_: Exception) {
            // The remote Binder may already be dead.
        }
        try {
            if (process.isAlive) process.destroyForcibly()
        } catch (_: Exception) {
            // The remote process may already have exited or become unreachable.
        }
    }

    private fun closeProcessStreams(process: Process) {
        runCatching { process.inputStream.close() }
        runCatching { process.errorStream.close() }
        runCatching { process.outputStream.close() }
    }

    private data class OutputCapture(
        val text: String,
        val truncated: Boolean,
        val readFailed: Boolean = false
    )

    private companion object {
        const val OUTPUT_DRAIN_TIMEOUT_MS = 1_500L
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
