package com.omninumeris.application

import com.omninumeris.domain.WorkspaceStorage

class CheckWorkspaceStorage(private val storage: WorkspaceStorage) {
    suspend operator fun invoke(): Boolean = storage.isAvailable()
}
