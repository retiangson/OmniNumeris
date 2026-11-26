package com.omninumeris.app

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceLayoutTest {
    @Test
    fun `phone and narrow split screen use stacked layout`() {
        assertEquals(WorkspaceLayout.Stacked, WorkspaceLayout.forWidth(360f))
        assertEquals(WorkspaceLayout.Stacked, WorkspaceLayout.forWidth(839.5f))
    }

    @Test
    fun `expanded width uses split layout starting at 840 dp`() {
        assertEquals(WorkspaceLayout.Split, WorkspaceLayout.forWidth(840f))
        assertEquals(WorkspaceLayout.Split, WorkspaceLayout.forWidth(1200f))
    }
}
