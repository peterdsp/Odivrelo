package dev.peterdsp.poravia.core

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
