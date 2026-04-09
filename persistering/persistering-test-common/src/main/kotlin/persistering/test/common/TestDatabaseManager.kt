package no.nav.tiltakspenger.libs.persistering.test.common

import com.zaxxer.hikari.HikariDataSource
import io.github.oshai.kotlinlogging.KotlinLogging
import kotliquery.sessionOf
import no.nav.tiltakspenger.libs.common.TikkendeKlokke
import no.nav.tiltakspenger.libs.persistering.domene.SessionFactory
import no.nav.tiltakspenger.libs.persistering.infrastruktur.PostgresSessionFactory
import no.nav.tiltakspenger.libs.persistering.infrastruktur.SessionCounter
import no.nav.tiltakspenger.libs.persistering.infrastruktur.sqlQuery
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Clock
import javax.sql.DataSource

data class TestDatabaseConfig(
    val postgresVersion: String = "postgres:17-alpine",
    val postgresWalLevel: String = "logical",
    val migrationLocations: List<String> = listOf("db/migration"),
    val parallelPoolSize: Int = 15,
    val isolatedPoolSize: Int = 15,
    val minimumIdle: Int = 1,
    val idleTimeout: Long = 10001,
    val connectionTimeout: Long = 3000,
    val maxLifetime: Long = 30001,
    val initializationFailTimeout: Long = 5000,
    val flywayTable: String = "flyway_schema_history",
    val flywayLoggers: List<String> = listOf("slf4j"),
    val flywayEncoding: String = "UTF-8",
    /** Override the default clean strategy. If null, all tables except [flywayTable] are truncated. */
    val cleanStrategy: ((DataSource) -> Unit)? = null,
)

class TestDatabaseManager<T>(
    private val config: TestDatabaseConfig = TestDatabaseConfig(),
    private val idGeneratorsFactory: () -> T,
) {
    private val log = KotlinLogging.logger {}

    private val postgres: PostgreSQLContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        PostgreSQLContainer(config.postgresVersion).apply {
            withCommand("postgres", "-c", "wal_level=${config.postgresWalLevel}")
            start()
        }
    }

    /**
     * Schema for tests that operate on isolated "sak-id" data.
     * These tests can run in parallel without clearing data.
     */
    private val parallelDataSource: HikariDataSource by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        createSchemaAndDataSource("parallel", config.parallelPoolSize)
    }

    /**
     * Schema for tests that touch aggregate/cross-sak code (e.g. jobs).
     * Data is truncated before each test; tests run sequentially via [isolatedLock].
     */
    private val isolatedDataSource: HikariDataSource by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        createSchemaAndDataSource("isolated", config.isolatedPoolSize)
    }

    val idGenerators: T by lazy { idGeneratorsFactory() }

    private val parallelSessionCounter = SessionCounter(log)
    private val isolatedSessionCounter = SessionCounter(log)

    val sessionFactory: SessionFactory by lazy {
        PostgresSessionFactory(parallelDataSource, parallelSessionCounter)
    }

    private val isolatedSessionFactory: SessionFactory by lazy {
        PostgresSessionFactory(isolatedDataSource, isolatedSessionCounter)
    }

    private val isolatedLock = Any()

    fun withMigratedDb(
        runIsolated: Boolean = false,
        clock: Clock = TikkendeKlokke(),
        test: (SessionFactory, T, Clock) -> Unit,
    ) {
        if (runIsolated) {
            synchronized(isolatedLock) {
                cleanDatabase(isolatedDataSource)
                test(isolatedSessionFactory, idGenerators, clock)
            }
        } else {
            test(sessionFactory, idGenerators, clock)
        }
    }

    fun dataSource(runIsolated: Boolean = false): DataSource {
        return if (runIsolated) isolatedDataSource else parallelDataSource
    }

    fun sessionFactory(runIsolated: Boolean = false): SessionFactory {
        return if (runIsolated) isolatedSessionFactory else sessionFactory
    }

    private fun createSchemaAndDataSource(schemaName: String, poolSize: Int): HikariDataSource {
        HikariDataSource().apply {
            jdbcUrl = postgres.jdbcUrl
            maximumPoolSize = 2
            username = postgres.username
            password = postgres.password
        }.use { bootstrapDs ->
            sessionOf(bootstrapDs).use { session ->
                session.run(sqlQuery("CREATE SCHEMA IF NOT EXISTS $schemaName").asUpdate)
            }
        }

        return HikariDataSource().apply {
            jdbcUrl = "${postgres.jdbcUrl}&currentSchema=$schemaName"
            maximumPoolSize = poolSize
            minimumIdle = config.minimumIdle
            idleTimeout = config.idleTimeout
            connectionTimeout = config.connectionTimeout
            maxLifetime = config.maxLifetime
            username = postgres.username
            password = postgres.password
            initializationFailTimeout = config.initializationFailTimeout
            schema = schemaName
        }.also { ds ->
            migrateDatabase(ds, schemaName)
        }
    }

    private fun cleanDatabase(ds: DataSource) {
        config.cleanStrategy?.let {
            it(ds)
            return
        }
        sessionOf(ds).use { session ->
            val tables = session.run(
                sqlQuery(
                    """
                    SELECT tablename
                    FROM pg_tables
                    WHERE schemaname = current_schema()
                      AND tablename <> :flywayTable
                    """.trimIndent(),
                    "flywayTable" to config.flywayTable,
                ).map { row -> row.string("tablename") }.asList,
            )
            if (tables.isNotEmpty()) {
                session.run(
                    sqlQuery("TRUNCATE ${tables.joinToString(", ")} CASCADE").asUpdate,
                )
            }
        }
    }

    private fun migrateDatabase(dataSource: DataSource, schemaName: String): MigrateResult? {
        return Flyway
            .configure()
            .loggers(*config.flywayLoggers.toTypedArray())
            .encoding(config.flywayEncoding)
            .locations(*config.migrationLocations.toTypedArray())
            .schemas(schemaName)
            .table(config.flywayTable)
            .dataSource(dataSource)
            .load()
            .migrate()
    }
}
