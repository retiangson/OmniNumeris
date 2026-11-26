package com.omninumeris.app

import com.omninumeris.application.CheckWorkspaceStorage
import com.omninumeris.domain.WorkspaceStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceViewModelTest {
    @Test
    fun `failed storage check can be retried without duplicate probes`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val storage = object : WorkspaceStorage {
                var checks = 0

                override suspend fun isAvailable(): Boolean = ++checks > 1
            }
            val viewModel = WorkspaceViewModel(CheckWorkspaceStorage(storage))

            assertEquals(WorkspaceStatus.Checking, viewModel.status.value)
            runCurrent()
            assertEquals(WorkspaceStatus.StorageUnavailable, viewModel.status.value)

            viewModel.retry()
            viewModel.retry()
            assertEquals(WorkspaceStatus.Checking, viewModel.status.value)
            runCurrent()

            assertEquals(WorkspaceStatus.Ready, viewModel.status.value)
            assertEquals(2, storage.checks)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
