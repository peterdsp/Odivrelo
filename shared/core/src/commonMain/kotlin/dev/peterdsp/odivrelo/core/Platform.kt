package dev.peterdsp.odivrelo.core

import app.cash.sqldelight.db.SqlDriver
import io.ktor.client.HttpClient

/**
 * The two things only a platform can supply: an HTTP engine and a SQLite
 * driver. Everything else in the core is common code, which is what lets the
 * same tests run on the JVM and on an iOS simulator.
 */
internal expect fun createPlatformHttpClient(): HttpClient

/**
 * Returns a driver whose schema has already been created or migrated. Doing the
 * migration here rather than in the core keeps the decision with the layer that
 * knows whether the underlying SQLite implementation runs the callbacks itself.
 */
internal expect fun createPlatformDriver(databasePath: String): SqlDriver

/**
 * Turns an unusable configuration into a typed failure.
 *
 * Both platform factories call this before building anything, so the message and
 * the error code are identical on Android and iOS, and so the mapping can be
 * tested in common code rather than once per platform.
 */
@Throws(OdivreloException::class)
internal fun requireValidConfig(config: CoreConfig) {
    val problem = config.validationError ?: return
    throw OdivreloException(
        code = dev.peterdsp.odivrelo.core.model.ErrorCode.INVALID_REQUEST,
        message = problem,
        kind = OdivreloFailureKind.INVALID_REQUEST,
    )
}
