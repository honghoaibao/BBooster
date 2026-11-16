package com.bbooster.app.di

import com.bbooster.app.shell.PrivilegedExecutor
import com.bbooster.app.shell.ShizukuExecutor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ExecutorModule {
    /** Shizuku is the only backend for now; a Wireless-ADB executor will be selected here later. */
    @Binds
    abstract fun bindPrivilegedExecutor(impl: ShizukuExecutor): PrivilegedExecutor
}
