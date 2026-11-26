package com.omninumeris.inferencespike

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BenchmarkProfileTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test(expected = IllegalArgumentException::class)
    fun `invalid context is rejected before reaching JNI`() {
        BenchmarkProfile(context = 513)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unpinned model is rejected`() {
        val file = temporaryFolder.newFile()
        file.writeText("a different model")
        BenchmarkModel.verify(file)
    }

    @Test
    fun `checksum matches the standard SHA256 vector`() {
        val file = temporaryFolder.newFile()
        file.writeText("abc")
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256(file))
    }

    @Test
    fun `required headroom sits above the system low memory threshold`() {
        val mib = 1024L * 1024
        assertTrue(hasMemoryForBenchmark(768 * mib, 256 * mib, false))
        assertFalse(hasMemoryForBenchmark(767 * mib, 256 * mib, false))
        assertFalse(hasMemoryForBenchmark(768 * mib, 256 * mib, true))
    }

    @Test
    fun `throughput excludes first token`() {
        assertNull(outputTokensPerSecond(1, 100))
        assertNull(outputTokensPerSecond(4, 0))
        assertEquals(20.0, requireNotNull(outputTokensPerSecond(5, 200_000_000)), 0.0)
    }
}
