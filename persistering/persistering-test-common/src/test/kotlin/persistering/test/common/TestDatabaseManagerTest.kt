package persistering.test.common

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotliquery.sessionOf
import no.nav.tiltakspenger.libs.common.TikkendeKlokke
import no.nav.tiltakspenger.libs.persistering.infrastruktur.sqlQuery
import no.nav.tiltakspenger.libs.persistering.test.common.TestDatabaseConfig
import no.nav.tiltakspenger.libs.persistering.test.common.TestDatabaseManager
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import javax.sql.DataSource

/** Simple stand-in for a repo-specific IdGenerators, used to verify the generic parameter. */
private data class FakeIdGenerators(val prefix: String = "test")

/** Shared manager – reused across all tests in this file (single container). */
private val manager = TestDatabaseManager(
    config = TestDatabaseConfig(
        migrationLocations = listOf("db/test-migration"),
    ),
    idGeneratorsFactory = { FakeIdGenerators() },
)

class TestDatabaseManagerTest {

    // -------------------------------------------------------
    // Schema & Flyway
    // -------------------------------------------------------

    @Test
    fun `parallel and isolated schemas are created and migrated`() {
        manager.withMigratedDb { sessionFactory, _, _ ->
            sessionFactory shouldNotBe null
        }
        manager.withMigratedDb(runIsolated = true) { sessionFactory, _, _ ->
            sessionFactory shouldNotBe null
        }
    }

    @Test
    fun `parallel and isolated schemas are independent`() {
        // Insert a row in parallel schema
        insertRow(manager.dataSource(runIsolated = false), "parallel_value")

        // It should not be visible in the isolated schema
        val isolatedRows = readRows(manager.dataSource(runIsolated = true))
        isolatedRows.none { it == "parallel_value" } shouldBe true

        // Insert in isolated schema
        manager.withMigratedDb(runIsolated = true) { _, _, _ ->
            insertRow(manager.dataSource(runIsolated = true), "isolated_value")
            val rows = readRows(manager.dataSource(runIsolated = true))
            rows.any { it == "isolated_value" } shouldBe true
        }

        // isolated_value should not be visible in parallel
        val parallelRows = readRows(manager.dataSource(runIsolated = false))
        parallelRows.none { it == "isolated_value" } shouldBe true
    }

    // -------------------------------------------------------
    // IdGenerators generic
    // -------------------------------------------------------

    @Test
    fun `idGenerators factory is called lazily and returns the correct type`() {
        manager.withMigratedDb { _, idGenerators, _ ->
            idGenerators shouldBe FakeIdGenerators("test")
        }
    }

    // -------------------------------------------------------
    // Clock
    // -------------------------------------------------------

    @Test
    fun `custom clock is forwarded`() {
        val clock = TikkendeKlokke()
        val t1 = clock.instant()
        manager.withMigratedDb(clock = clock) { _, _, c ->
            val t2 = c.instant()
            (t2 > t1) shouldBe true
        }
    }

    // -------------------------------------------------------
    // Clean / truncate
    // -------------------------------------------------------

    @Test
    fun `isolated run truncates all tables before the test`() {
        // First isolated run – insert data
        manager.withMigratedDb(runIsolated = true) { _, _, _ ->
            insertRow(manager.dataSource(runIsolated = true), "should_be_gone")
            readRows(manager.dataSource(runIsolated = true)) shouldHaveSize 1
        }
        // Second isolated run – data should be gone
        manager.withMigratedDb(runIsolated = true) { _, _, _ ->
            readRows(manager.dataSource(runIsolated = true)).shouldBeEmpty()
        }
    }

    @Test
    fun `parallel run does not truncate data between invocations`() {
        val uniqueValue = "persist_${System.nanoTime()}"
        manager.withMigratedDb { _, _, _ ->
            insertRow(manager.dataSource(runIsolated = false), uniqueValue)
        }
        manager.withMigratedDb { _, _, _ ->
            readRows(manager.dataSource(runIsolated = false)).any { it == uniqueValue } shouldBe true
        }
    }

    @Test
    fun `flyway table is not truncated`() {
        manager.withMigratedDb(runIsolated = true) { _, _, _ ->
            val flywayRows = sessionOf(manager.dataSource(runIsolated = true)).use { session ->
                session.run(
                    sqlQuery("SELECT count(*) as cnt FROM flyway_schema_history")
                        .map { it.int("cnt") }
                        .asSingle,
                )
            }
            (flywayRows!! > 0) shouldBe true
        }
    }

    // -------------------------------------------------------
    // Custom clean strategy
    // -------------------------------------------------------

    @Test
    fun `custom cleanStrategy is used when provided`() {
        var cleanCalled = false
        val customManager = TestDatabaseManager(
            config = TestDatabaseConfig(
                migrationLocations = listOf("db/test-migration"),
                cleanStrategy = { _ -> cleanCalled = true },
            ),
            idGeneratorsFactory = { FakeIdGenerators() },
        )
        customManager.withMigratedDb(runIsolated = true) { _, _, _ ->
            cleanCalled shouldBe true
        }
    }

    // -------------------------------------------------------
    // Concurrency
    // -------------------------------------------------------

    @Test
    fun `parallel tests can run concurrently without interference`() {
        val threads = 10
        val latch = CountDownLatch(threads)
        val executor = Executors.newFixedThreadPool(threads)
        val results = ConcurrentHashMap<Int, String>()

        (0 until threads).forEach { i ->
            executor.submit {
                try {
                    manager.withMigratedDb { _, _, _ ->
                        val value = "thread_$i"
                        insertRow(manager.dataSource(runIsolated = false), value)
                        results[i] = value
                    }
                } finally {
                    latch.countDown()
                }
            }
        }
        latch.await()
        executor.shutdown()

        results shouldHaveSize threads
        // All values should be present in the database
        val allRows = readRows(manager.dataSource(runIsolated = false))
        results.values.forEach { value ->
            allRows.any { it == value } shouldBe true
        }
    }

    @RepeatedTest(3)
    fun `isolated tests run sequentially - data is always clean at start`() {
        manager.withMigratedDb(runIsolated = true) { _, _, _ ->
            // Should always start clean
            readRows(manager.dataSource(runIsolated = true)).shouldBeEmpty()
            insertRow(manager.dataSource(runIsolated = true), "repeated_test_value")
            readRows(manager.dataSource(runIsolated = true)) shouldHaveSize 1
        }
    }

    // -------------------------------------------------------
    // SessionFactory / DataSource accessors
    // -------------------------------------------------------

    @Test
    fun `sessionFactory accessor returns correct instance for each mode`() {
        val parallel = manager.sessionFactory(runIsolated = false)
        val isolated = manager.sessionFactory(runIsolated = true)
        parallel shouldNotBe isolated
        // Calling again should return the same instance
        manager.sessionFactory(runIsolated = false) shouldBe parallel
        manager.sessionFactory(runIsolated = true) shouldBe isolated
    }

    @Test
    fun `dataSource accessor returns correct instance for each mode`() {
        val parallel = manager.dataSource(runIsolated = false)
        val isolated = manager.dataSource(runIsolated = true)
        parallel shouldNotBe isolated
    }

    // -------------------------------------------------------
    // Helpers — use a trivial test table created by db/test-migration
    // -------------------------------------------------------

    private fun insertRow(ds: DataSource, value: String) {
        sessionOf(ds).use { session ->
            session.run(
                sqlQuery(
                    "INSERT INTO test_table (value) VALUES (:value)",
                    "value" to value,
                ).asUpdate,
            )
        }
    }

    private fun readRows(ds: DataSource): List<String> {
        return sessionOf(ds).use { session ->
            session.run(
                sqlQuery("SELECT value FROM test_table")
                    .map { it.string("value") }
                    .asList,
            )
        }
    }
}
