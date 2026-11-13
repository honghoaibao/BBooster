package com.honghoaibao.bbooster.core.command

/**
 * Closed set of read-only diagnostic operations.
 * The app never accepts arbitrary shell strings or user-provided arguments.
 */
enum class CommandAction(
    val id: String,
    internal val arguments: List<String>
) {
    CALLER_IDENTITY("caller_identity", listOf("id")),
    ANDROID_RELEASE("android_release", listOf("getprop", "ro.build.version.release")),
    ANDROID_SDK("android_sdk", listOf("getprop", "ro.build.version.sdk"));

    companion object {
        fun fromId(id: String): CommandAction? = entries.firstOrNull { it.id == id }
    }
}
