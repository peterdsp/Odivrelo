package dev.peterdsp.odivrelo.core

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.util.concurrent.atomic.AtomicLong

private val counter = AtomicLong(0)

internal actual fun createTestDirectory(name: String): String {
    val base = File(System.getProperty("java.io.tmpdir"), "odivrelo-test")
    val directory = File(base, name + "-" + counter.incrementAndGet())
    directory.deleteRecursively()
    directory.mkdirs()
    return directory.absolutePath
}

internal actual fun createDriver(schema: SqlSchema<QueryResult.Value<Unit>>): SqlDriver {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    schema.create(driver).value
    return driver
}
