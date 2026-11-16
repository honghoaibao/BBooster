package com.bbooster.app.shell

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.bbooster.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration

object ShizukuConstants {
    const val PACKAGE = "moe.shizuku.privileged.api"
    const val RELEASES_URL = "https://github.com/RikkaApps/Shizuku/releases"
}

/**
 * [PrivilegedExecutor] backed by Shizuku. Commands run through a UserService ([ShellService]) rather
 * than Shizuku.newProcess, which the Shizuku API is phasing out.
 *
 * Threading: Shizuku listeners fire on the main thread; command execution happens on Dispatchers.IO.
 * Mutable fields are guarded by [lock].
 */
@Singleton
class ShizukuExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
) : PrivilegedExecutor {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Connecting)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    @Volatile private var shell: IShellService? = null
    private var verifiedUid: Int? = null
    private var failure: String? = null
    private var binding = false

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, ShellService::class.java.name),
    )
        .processNameSuffix("shell")
        .daemon(false)
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder?) {
            if (binder == null || !binder.pingBinder()) return
            synchronized(lock) {
                shell = IShellService.Stub.asInterface(binder)
                binding = false
            }
            scope.launch { verifyIdentity() }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Log.w(TAG, "shell service disconnected")
            clearService()
            refresh()
        }
    }

    init {
        Shizuku.addBinderReceivedListenerSticky { refresh() }
        Shizuku.addBinderDeadListener {
            Log.w(TAG, "Shizuku binder died")
            clearService()
            refresh()
        }
        Shizuku.addRequestPermissionResultListener { _, _ -> refresh() }
        refresh()
    }

    override fun refresh() {
        synchronized(lock) {
            val alive = Shizuku.pingBinder()
            val granted = alive && runCatching {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)
            // shouldShowRequestPermissionRationale() is true once the user picked "don't ask again".
            val denied = alive && !granted && runCatching {
                Shizuku.shouldShowRequestPermissionRationale()
            }.getOrDefault(false)

            if (!alive || !granted) {
                clearService()
            } else if (shell == null && !binding) {
                bindService()
            }

            _state.value = ConnectionStateResolver.resolve(
                ShizukuSnapshot(
                    installed = isShizukuInstalled(),
                    binderAlive = alive,
                    permissionGranted = granted,
                    permanentlyDenied = denied,
                    verifiedUid = verifiedUid,
                    failure = failure,
                ),
            )
        }
    }

    override fun requestAccess() {
        if (_state.value != ConnectionState.PermissionRequired) return
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
            .onFailure { Log.w(TAG, "requestPermission failed", it) }
    }

    override suspend fun execute(command: ShellCommand, timeout: Duration): CommandResult {
        val current = _state.value
        if (current !is ConnectionState.Ready) {
            return CommandResult.Unavailable("Not connected: $current")
        }
        return runCommand(command, timeout.inWholeMilliseconds)
    }

    private suspend fun runCommand(command: ShellCommand, timeoutMs: Long): CommandResult {
        val service = shell ?: return CommandResult.Unavailable("Shell service not bound")
        val clamped = timeoutMs.coerceIn(ShellProtocol.MIN_TIMEOUT_MS, ShellProtocol.MAX_TIMEOUT_MS)
        val argv = command.toArgv().toTypedArray()

        // A blocking Binder call can't be cancelled, so run it detached and wait with a timeout.
        // The service enforces its own timeout and kills the child; the grace covers Binder latency.
        val call = scope.async { service.exec(argv, clamped) }
        return try {
            val bundle = withTimeoutOrNull(clamped + CALL_GRACE_MS) { call.await() }
            if (bundle == null) {
                call.cancel()
                Log.w(TAG, "binder call timed out: ${argv.joinToString(" ")}")
                CommandResult.TimedOut
            } else {
                ShellProtocol.toResult(bundle)
            }
        } catch (e: CancellationException) {
            call.cancel()
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "binder call failed: ${argv.joinToString(" ")}", e)
            refresh() // dead binder / revoked permission changes the state
            CommandResult.Error("${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private suspend fun verifyIdentity() {
        val result = runCommand(ShellCommand.Whoami, IDENTITY_TIMEOUT_MS)
        synchronized(lock) {
            val uid = (result as? CommandResult.Success)?.stdout?.trim()?.toIntOrNull()
            if (uid == UID_SHELL || uid == UID_ROOT) {
                verifiedUid = uid
                failure = null
            } else {
                verifiedUid = null
                failure = "Identity check failed: $result"
            }
        }
        refresh()
    }

    private fun bindService() {
        binding = true
        failure = null
        runCatching { Shizuku.bindUserService(userServiceArgs, connection) }
            .onFailure {
                binding = false
                failure = it.message ?: it.javaClass.simpleName
                Log.w(TAG, "bindUserService failed", it)
            }
    }

    private fun clearService() {
        synchronized(lock) {
            shell = null
            verifiedUid = null
            binding = false
        }
    }

    private fun isShizukuInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo(ShizukuConstants.PACKAGE, PackageManager.PackageInfoFlags.of(0))
    }.isSuccess

    private companion object {
        const val TAG = "ShizukuExecutor"
        const val REQUEST_CODE = 1001
        const val CALL_GRACE_MS = 3_000L
        const val IDENTITY_TIMEOUT_MS = 5_000L
        const val UID_SHELL = 2000
        const val UID_ROOT = 0
    }
}
