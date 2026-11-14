package com.honghoaibao.bbooster.core.shizuku.remote

import android.content.Context
import android.util.Log
import androidx.annotation.Keep

/**
 * Temporary startup diagnostic for Shizuku UserService.
 *
 * This test intentionally does not execute shell commands. It only proves
 * that Shizuku can instantiate the service and complete an AIDL round trip.
 */
@Keep
class BBoosterUserService : IUserService.Stub {

    constructor() : super() {
        Log.i(TAG, "Default constructor entered.")
    }

    @Keep
    constructor(@Suppress("UNUSED_PARAMETER") context: Context) : this() {
        Log.i(TAG, "Context constructor entered.")
    }

    override fun execute(
        actionId: String?,
        timeoutMs: Long,
        callback: ICommandCallback?
    ) {
        if (callback == null) {
            Log.w(TAG, "AIDL execute called without a callback.")
            return
        }

        Log.i(TAG, "AIDL execute received. actionId=$actionId")

        try {
            callback.onCompleted(
                0,
                "USER_SERVICE_SMOKE_TEST_OK\n" +
                    "UserService was created and the AIDL callback returned successfully.\n" +
                    "No shell command was executed in this test build.",
                "",
                false
            )
        } catch (error: Exception) {
            Log.e(TAG, "Unable to return the AIDL smoke-test result.", error)
        }
    }

    override fun destroy() {
        Log.i(TAG, "Destroy requested.")
        System.exit(0)
    }

    private companion object {
        const val TAG = "BBoosterUserService"
    }
}
