package com.honghoaibao.bbooster.core.logging

data class LogEntry(
    val timestampMillis: Long,
    val level: LogLevel,
    val module: LogModule,
    val event: String,
    val message: String
)

enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}

enum class LogModule {
    APP,
    SHIZUKU,
    TRANSPORT,
    COMMAND_ENGINE,
    DEVICE,
    UI,
    REPORT
}

data class LogQuery(
    val levels: Set<LogLevel> = emptySet(),
    val modules: Set<LogModule> = emptySet(),
    val search: String = "",
    val limit: Int = 500
)
