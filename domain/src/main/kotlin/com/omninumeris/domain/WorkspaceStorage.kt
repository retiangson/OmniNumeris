package com.omninumeris.domain

interface WorkspaceStorage {
    suspend fun isAvailable(): Boolean
}
