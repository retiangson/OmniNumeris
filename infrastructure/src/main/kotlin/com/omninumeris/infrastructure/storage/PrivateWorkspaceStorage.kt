package com.omninumeris.infrastructure.storage

import com.omninumeris.domain.WorkspaceStorage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PrivateWorkspaceStorage(
    private val filesDirectory: () -> File?,
) : WorkspaceStorage {
    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val directory = filesDirectory()
            directory != null && directory.isDirectory && directory.canWrite() && directory.usableSpace > 0
        } catch (_: SecurityException) {
            false
        }
    }
}
