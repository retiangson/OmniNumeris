package com.omninumeris.infrastructure.storage

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.omninumeris.application.LocalTransactionRunner

class RoomTransactionRunner(private val database: RoomDatabase) : LocalTransactionRunner {
    override suspend fun <T> inTransaction(block: suspend () -> T): T =
        database.withTransaction(block)
}
