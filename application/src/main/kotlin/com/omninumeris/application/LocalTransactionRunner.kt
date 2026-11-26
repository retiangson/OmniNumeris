package com.omninumeris.application

/** Runs database operations atomically. External file operations are outside the transaction. */
interface LocalTransactionRunner {
    suspend fun <T> inTransaction(block: suspend () -> T): T
}
