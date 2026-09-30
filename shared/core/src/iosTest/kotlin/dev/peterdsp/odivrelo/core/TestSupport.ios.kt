package dev.peterdsp.odivrelo.core

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.native.inMemoryDriver
import dev.peterdsp.odivrelo.core.io.Paths
import dev.peterdsp.odivrelo.core.io.PlatformFiles
import kotlin.concurrent.AtomicLong
import platform.Foundation.NSTemporaryDirectory

private val counter = AtomicLong(0L)

internal actual fun createTestDirectory(name: String): String {
    val directory = Paths.join(
        NSTemporaryDirectory(),
        "odivrelo-test",
        name + "-" + counter.addAndGet(1L).toString(),
    )
    PlatformFiles.deleteRecursively(directory)
    PlatformFiles.mkdirs(directory)
    return directory
}

internal actual fun createDriver(schema: SqlSchema<QueryResult.Value<Unit>>): SqlDriver =
    inMemoryDriver(schema)
