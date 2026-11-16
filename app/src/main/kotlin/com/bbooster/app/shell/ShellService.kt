package com.bbooster.app.shell

import android.content.Context
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

/**
 * Runs in the process Shizuku starts for us (shell UID). It is the second line of defence: it only
 * executes argv that round-trips through [ShellCommand.fromArgv], so a compromised or buggy app
 * process still cannot run arbitrary programs here.
 *
 * Shizuku instantiates this class by name, using either constructor.
 */
class ShellService : IShellService.Stub {

    constructor() : super()

    @Suppress("unused", "UNUSED_PARAMETER")
    constructor(context: Context) : super()

    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(argv: Array<String>?, timeoutMs: Long): Bundle {
        val list = argv?.toList().orEmpty()
        val command = ShellCommand.fromArgv(list)
        if (command == null) {
            Log.w(TAG, "rejected argv (not whitelisted), size=${list.size}")
            return ShellProtocol.rejected("Command is not whitelisted")
        }

        val timeout = timeoutMs.coerceIn(ShellProtocol.MIN_TIMEOUT_MS, ShellProtocol.MAX_TIMEOUT_MS)
        val started = System.nanoTime()
        return try {
            val raw = ProcessRunner.run(list, timeout, ShellProtocol.MAX_OUTPUT_BYTES)
            val ms = (System.nanoTime() - started) / 1_000_000
            Log.i(TAG, "exec ${list.joinToString(" ")} -> exit=${raw.exitCode} timedOut=${raw.timedOut} ${ms}ms")
            if (raw.timedOut) {
                ShellProtocol.timeout()
            } else {
                ShellProtocol.completed(raw.exitCode, raw.stdout, raw.stderr)
            }
        } catch (e: Exception) {
            Log.w(TAG, "exec failed: ${list.joinToString(" ")}", e)
            ShellProtocol.error("${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "BBoosterShell"
    }
}
