package com.honghoaibao.bbooster.core.shizuku.transport

import com.honghoaibao.bbooster.core.command.CommandDefinition
import com.honghoaibao.bbooster.core.command.CommandRegistry
import rikka.shizuku.Shizuku
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Compatibility boundary for Shizuku's deprecated/private newProcess method.
 *
 * Shizuku API 13.1.5 keeps this method private. Reflection is intentionally
 * isolated here and is never exposed to features or command callers.
 */
internal class ShizukuNewProcessAdapter : RemoteProcessStarter {

    private val methodResult: MethodResolution by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED
    ) {
        resolveMethod()
    }

    override fun checkAvailability(): ProcessStarterAvailability =
        when (val resolution = methodResult) {
            is MethodResolution.Available -> ProcessStarterAvailability.AVAILABLE
            is MethodResolution.Unsupported ->
                ProcessStarterAvailability.UNSUPPORTED
        }

    override fun start(command: CommandDefinition): Process {
        val registered = CommandRegistry.find(command.id)
        if (registered == null || registered != command) {
            throw ProcessStartException(
                dispatchState = com.honghoaibao.bbooster.core.command.CommandDispatchState.NOT_DISPATCHED,
                failureCode = com.honghoaibao.bbooster.core.command.CommandFailureCode.COMMAND_REJECTED,
                detail = "Command is not an unchanged entry in CommandRegistry."
            )
        }

        val method = when (val resolution = methodResult) {
            is MethodResolution.Available -> resolution.method
            is MethodResolution.Unsupported -> throw ProcessStartException(
                dispatchState = com.honghoaibao.bbooster.core.command.CommandDispatchState.NOT_DISPATCHED,
                failureCode = com.honghoaibao.bbooster.core.command.CommandFailureCode.TRANSPORT_UNSUPPORTED,
                detail = resolution.reason
            )
        }

        return try {
            val process = method.invoke(
                null,
                command.arguments.toTypedArray(),
                null,
                null
            )

            process as? Process ?: throw ProcessStartException(
                dispatchState = com.honghoaibao.bbooster.core.command.CommandDispatchState.UNKNOWN,
                failureCode = com.honghoaibao.bbooster.core.command.CommandFailureCode.PROCESS_START_FAILED,
                detail = "Shizuku newProcess returned an unexpected process object."
            )
        } catch (error: ProcessStartException) {
            throw error
        } catch (error: InvocationTargetException) {
            // The remote method was entered. Its remote side may have accepted
            // the process request before the exception reached this process.
            throw ProcessStartException(
                dispatchState = com.honghoaibao.bbooster.core.command.CommandDispatchState.UNKNOWN,
                failureCode = com.honghoaibao.bbooster.core.command.CommandFailureCode.PROCESS_START_FAILED,
                detail = "Shizuku rejected or lost the remote process request.",
                cause = error.targetException ?: error
            )
        } catch (error: ReflectiveOperationException) {
            throw ProcessStartException(
                dispatchState = com.honghoaibao.bbooster.core.command.CommandDispatchState.NOT_DISPATCHED,
                failureCode = com.honghoaibao.bbooster.core.command.CommandFailureCode.TRANSPORT_UNSUPPORTED,
                detail = "The Shizuku process method could not be invoked.",
                cause = error
            )
        } catch (error: SecurityException) {
            throw ProcessStartException(
                dispatchState = com.honghoaibao.bbooster.core.command.CommandDispatchState.NOT_DISPATCHED,
                failureCode = com.honghoaibao.bbooster.core.command.CommandFailureCode.PERMISSION_REQUIRED,
                detail = "Access to the Shizuku process method was denied.",
                cause = error
            )
        } catch (error: RuntimeException) {
            throw ProcessStartException(
                dispatchState = com.honghoaibao.bbooster.core.command.CommandDispatchState.UNKNOWN,
                failureCode = com.honghoaibao.bbooster.core.command.CommandFailureCode.REMOTE_CALL_FAILED,
                detail = "The Shizuku process request failed.",
                cause = error
            )
        }
    }

    private fun resolveMethod(): MethodResolution {
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                METHOD_NAME,
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )

            if (Modifier.isStatic(method.modifiers).not()) {
                return MethodResolution.Unsupported(
                    "Shizuku newProcess is not a static method on this API."
                )
            }

            if (Process::class.java.isAssignableFrom(method.returnType).not()) {
                return MethodResolution.Unsupported(
                    "Shizuku newProcess does not return a java.lang.Process."
                )
            }

            method.isAccessible = true
            MethodResolution.Available(method)
        } catch (error: ReflectiveOperationException) {
            MethodResolution.Unsupported(
                "This Shizuku API does not expose the compatible newProcess signature."
            )
        } catch (error: SecurityException) {
            MethodResolution.Unsupported(
                "The runtime denied reflective access to Shizuku newProcess."
            )
        } catch (error: RuntimeException) {
            MethodResolution.Unsupported(
                "The Shizuku newProcess compatibility check failed."
            )
        }
    }

    private sealed interface MethodResolution {
        data class Available(val method: Method) : MethodResolution
        data class Unsupported(val reason: String) : MethodResolution
    }

    private companion object {
        const val METHOD_NAME = "newProcess"
    }
}
