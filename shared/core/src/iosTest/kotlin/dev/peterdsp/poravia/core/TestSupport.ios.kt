package dev.peterdsp.poravia.core

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.native.inMemoryDriver
import dev.peterdsp.poravia.core.io.Paths
import dev.peterdsp.poravia.core.io.PlatformFiles
import kotlin.concurrent.AtomicLong
import platform.Foundation.NSTemporaryDirectory

private val counter = AtomicLong(0L)

internal actual fun createTestDirectory(name: String): String {
    val directory = Paths.join(
        NSTemporaryDirectory(),
        "poravia-test",
        name + "-" + counter.addAndGet(1L).toString(),
    )
    PlatformFiles.deleteRecursively(directory)
    PlatformFiles.mkdirs(directory)
    return directory
}

internal actual fun createDriver(schema: SqlSchema<QueryResult.Value<Unit>>): SqlDriver =
    inMemoryDriver(schema)
