package com.bbooster.app.shell

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Runs an argv (never a shell string) with a hard timeout. Both streams are drained on their own
 * threads so a chatty command can't deadlock, and kept only up to [maxOutputBytes].
 */
internal object ProcessRunner {
    data class Raw(val exitCode: Int, val stdout: String, val stderr: String, val timedOut: Boolean)

    /** @throws java.io.IOException if the process cannot be started (e.g. binary missing). */
    fun run(argv: List<String>, timeoutMs: Long, maxOutputBytes: Int): Raw {
        val process = ProcessBuilder(argv).start()
        process.outputStream.close() // no stdin
        val out = StreamCollector(process.inputStream, maxOutputBytes).also { it.start() }
        val err = StreamCollector(process.errorStream, maxOutputBytes).also { it.start() }

        val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            process.waitFor(2, TimeUnit.SECONDS)
        }
        out.join(1_000)
        err.join(1_000)

        return Raw(
            exitCode = if (finished) process.exitValue() else -1,
            stdout = out.text(),
            stderr = err.text(),
            timedOut = !finished,
        )
    }

    private class StreamCollector(
        private val stream: InputStream,
        private val max: Int,
    ) : Thread("bbooster-stream") {
        private val buffer = ByteArrayOutputStream()

        init {
            isDaemon = true
        }

        override fun run() {
            val chunk = ByteArray(8 * 1024)
            try {
                stream.use {
                    while (true) {
                        val n = it.read(chunk)
                        if (n < 0) break
                        val room = max - buffer.size()
                        // Keep draining after the cap so the child never blocks on a full pipe.
                        if (room > 0) buffer.write(chunk, 0, minOf(n, room))
                    }
                }
            } catch (e: java.io.IOException) {
                // Stream closed because the process was destroyed; what we have is enough.
            }
        }

        // String(bytes, charset) works on every API level; ByteArrayOutputStream.toString(Charset) may not.
        fun text(): String = String(buffer.toByteArray(), Charsets.UTF_8)
    }
}
