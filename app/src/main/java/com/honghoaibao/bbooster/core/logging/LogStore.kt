package com.honghoaibao.bbooster.core.logging

interface LogStore {
    fun append(entry: LogEntry)

    fun readAll(): List<LogEntry>

    fun clear()

    fun sizeBytes(): Long
}
