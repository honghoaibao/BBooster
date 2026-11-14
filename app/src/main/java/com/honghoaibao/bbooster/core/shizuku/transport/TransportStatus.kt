package com.honghoaibao.bbooster.core.shizuku.transport

import com.honghoaibao.bbooster.core.command.CommandCapability
import com.honghoaibao.bbooster.core.command.CommandTransportType

data class TransportStatus(
    val transport: CommandTransportType,
    val state: TransportAvailability,
    val capabilities: Set<CommandCapability> = emptySet(),
    val detail: String? = null
)

enum class TransportAvailability {
    CHECKING,
    AVAILABLE,
    STARTING,
    READY,
    DEGRADED,
    UNAVAILABLE,
    UNSUPPORTED
}
