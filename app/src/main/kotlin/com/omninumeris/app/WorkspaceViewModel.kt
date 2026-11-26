package com.omninumeris.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omninumeris.application.CheckWorkspaceStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class WorkspaceStatus {
    Checking,
    Ready,
    StorageUnavailable,
}

@HiltViewModel
internal class WorkspaceViewModel @Inject constructor(
    private val checkWorkspaceStorage: CheckWorkspaceStorage,
) : ViewModel() {
    private val mutableStatus = MutableStateFlow(WorkspaceStatus.Checking)
    val status = mutableStatus.asStateFlow()

    init {
        checkStorage()
    }

    fun retry() {
        if (mutableStatus.compareAndSet(WorkspaceStatus.StorageUnavailable, WorkspaceStatus.Checking)) {
            checkStorage()
        }
    }

    private fun checkStorage() {
        viewModelScope.launch {
            mutableStatus.value = if (checkWorkspaceStorage()) {
                WorkspaceStatus.Ready
            } else {
                WorkspaceStatus.StorageUnavailable
            }
        }
    }
}
