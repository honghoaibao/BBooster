package com.honghoaibao.bbooster.core.logging

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Structured application logger. Writes are dispatched away from the caller
 * thread so UI and Binder callbacks do not perform disk I/O.
 */
class AppLogger(
    private val store: LogStore,
    private val writer: Executor,
    private val onStoreChanged: () -> Unit = {},
    private val clockMillis: () -> Long = System::currentTimeMillis
) {

    private val verboseLogging = AtomicBoolean(false)

    fun setVerboseLogging(enabled: Boolean) {
        verboseLogging.set(enabled)
    }

    fun isVerboseLoggingEnabled(): Boolean = verboseLogging.get()

    fun debug(module: LogModule, event: String, message: String) {
        log(LogLevel.DEBUG, module, event, message)
    }

    fun info(module: LogModule, event: String, message: String) {
        log(LogLevel.INFO, module, event, message)
    }

    fun warn(module: LogModule, event: String, message: String) {
        log(LogLevel.WARN, module, event, message)
    }

    fun error(module: LogModule, event: String, message: String) {
        log(LogLevel.ERROR, module, event, message)
    }

    fun log(level: LogLevel, module: LogModule, event: String, message: String) {
        if (level == LogLevel.DEBUG && !verboseLogging.get()) return

        val entry = LogEntry(
            timestampMillis = clockMillis().coerceAtLeast(0L),
            level = level,
            module = module,
            event = LogRedactor.sanitizeEvent(event),
            message = LogRedactor.redact(message)
        )

        runCatching {
            writer.execute {
                runCatching {
                    store.append(entry)
                    onStoreChanged()
                }
            }
        }
    }

    /**
     * Reads the bounded local log store. Call from Dispatchers.IO when used by UI.
     */
    fun query(query: LogQuery = LogQuery()): List<LogEntry> {
        val search = query.search.trim()
        val limit = query.limit.coerceIn(0, MAX_QUERY_LIMIT)
        if (limit == 0) return emptyList()

        return store.readAll()
            .asSequence()
            .filter { query.levels.isEmpty() || it.level in query.levels }
            .filter { query.modules.isEmpty() || it.module in query.modules }
            .filter {
                search.isBlank() ||
                    it.event.contains(search, ignoreCase = true) ||
                    it.message.contains(search, ignoreCase = true)
            }
            .sortedBy(LogEntry::timestampMillis)
            .toList()
            .takeLast(limit)
    }

    fun sizeBytes(): Long = store.sizeBytes()

    fun clear() {
        runCatching {
            writer.execute {
                runCatching {
                    store.clear()
                    onStoreChanged()
                }
            }
        }
    }

    /**
     * Clears logs after all previously queued writes and waits for completion.
     * Call from an I/O dispatcher, never the UI thread.
     */
    fun clearAndWait(timeoutMillis: Long = DEFAULT_FLUSH_TIMEOUT_MS): Boolean {
        if (timeoutMillis <= 0L) return false

        val latch = CountDownLatch(1)
        val succeeded = AtomicBoolean(false)
        return try {
            writer.execute {
                succeeded.set(
                    runCatching {
                        store.clear()
                        onStoreChanged()
                        true
                    }.getOrDefault(false)
                )
                latch.countDown()
            }
            latch.await(timeoutMillis, TimeUnit.MILLISECONDS) && succeeded.get()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        } catch (_: RuntimeException) {
            false
        }
    }

    /**
     * Waits for queued writes to finish. Use from an I/O dispatcher, never the UI thread.
     */
    fun flush(timeoutMillis: Long = DEFAULT_FLUSH_TIMEOUT_MS): Boolean {
        if (timeoutMillis <= 0L) return false
        val latch = CountDownLatch(1)
        return try {
            writer.execute { latch.countDown() }
            latch.await(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        } catch (_: RuntimeException) {
            false
        }
    }

    companion object {
        private const val DEFAULT_FLUSH_TIMEOUT_MS = 2_000L
        private const val MAX_QUERY_LIMIT = 10_000
    }
}

/**
 * Process-wide entry point used by existing modules. Initialize once from the
 * application entry point before constructing the Shizuku managers.
 */
object AppLog {

    private val writer = Executors.newSingleThreadExecutor(
        ThreadFactory { runnable ->
            Thread(runnable, "BBooster-LogWriter").apply { isDaemon = true }
        }
    )

    private val mutableRevision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = mutableRevision.asStateFlow()

    @Volatile
    private var delegate: AppLogger? = null

    @Synchronized
    fun initialize(directory: File) {
        if (delegate != null) return
        delegate = AppLogger(
            store = FileLogStore(directory),
            writer = writer,
            onStoreChanged = {
                mutableRevision.update { current -> if (current == Long.MAX_VALUE) 0L else current + 1L }
            }
        )
    }

    fun setVerboseLogging(enabled: Boolean) {
        delegate?.setVerboseLogging(enabled)
    }

    fun debug(module: LogModule, event: String, message: String) {
        delegate?.debug(module, event, message)
    }

    fun info(module: LogModule, event: String, message: String) {
        delegate?.info(module, event, message)
    }

    fun warn(module: LogModule, event: String, message: String) {
        delegate?.warn(module, event, message)
    }

    fun error(module: LogModule, event: String, message: String) {
        delegate?.error(module, event, message)
    }

    fun readLogs(query: LogQuery = LogQuery()): List<LogEntry> =
        delegate?.query(query).orEmpty()

    fun logSizeBytes(): Long = runCatching {
        delegate?.sizeBytes() ?: 0L
    }.getOrDefault(0L)

    fun clearLogs() {
        delegate?.clear()
    }

    fun clearLogsAndWait(timeoutMillis: Long = 2_000L): Boolean =
        delegate?.clearAndWait(timeoutMillis) ?: false

    fun flush(timeoutMillis: Long = 2_000L): Boolean =
        delegate?.flush(timeoutMillis) ?: true

    fun isInitialized(): Boolean = delegate != null
}
