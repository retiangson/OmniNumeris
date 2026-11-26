package com.omninumeris.infrastructure.storage

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PrivateWorkspaceStorageTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `available directory is detected without writing content`() = runBlocking {
        val directory = temporaryFolder.newFolder()

        assertTrue(PrivateWorkspaceStorage { directory }.isAvailable())
        assertTrue(directory.list().orEmpty().isEmpty())
    }

    @Test
    fun `null and non-directory paths are unavailable`() = runBlocking {
        val file = temporaryFolder.newFile()

        assertFalse(PrivateWorkspaceStorage { null }.isAvailable())
        assertFalse(PrivateWorkspaceStorage { file }.isAvailable())
    }

    @Test
    fun `platform denial is reported as unavailable`() = runBlocking {
        assertFalse(PrivateWorkspaceStorage { throw SecurityException() }.isAvailable())
    }
}
