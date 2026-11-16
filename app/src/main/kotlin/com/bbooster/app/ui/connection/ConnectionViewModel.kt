package com.bbooster.app.ui.connection

import androidx.lifecycle.ViewModel
import com.bbooster.app.shell.ConnectionState
import com.bbooster.app.shell.PrivilegedExecutor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val executor: PrivilegedExecutor,
) : ViewModel() {
    val state: StateFlow<ConnectionState> = executor.state

    fun refresh() = executor.refresh()

    fun requestAccess() = executor.requestAccess()
}
