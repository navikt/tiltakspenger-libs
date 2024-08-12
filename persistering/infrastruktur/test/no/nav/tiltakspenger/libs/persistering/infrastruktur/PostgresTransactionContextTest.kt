package no.nav.tiltakspenger.libs.persistering.infrastruktur

import arrow.core.Either
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotliquery.queryOf
import mu.KotlinLogging
import no.nav.tiltakspenger.libs.persistering.infrastruktur.PostgresSessionContext.Companion.withSession
import no.nav.tiltakspenger.libs.persistering.infrastruktur.PostgresTransactionContext.Companion.withSession
import no.nav.tiltakspenger.libs.persistering.infrastruktur.PostgresTransactionContext.Companion.withTransaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import javax.sql.DataSource

@Execution(ExecutionMode.SAME_THREAD)
internal class PostgresTransactionContextTest {

    companion object {
        private lateinit var dataSource: DataSource
        private lateinit var sessionCounter: SessionCounter
        private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))

        private val logger = KotlinLogging.logger { }

        @JvmStatic
        @BeforeAll
        fun setup() {
            postgres.start()
            dataSource = PGSimpleDataSource().apply {
                setURL(postgres.jdbcUrl)
                user = postgres.username
                password = postgres.password
            }
            sessionCounter = SessionCounter(logger)
        }

        @JvmStatic
        @AfterAll
        fun close() {
            postgres.stop()
        }
    }

    @Test
    fun `kan ikke bruke samme context transaction flere ganger`() {
        val context = PostgresTransactionContext(dataSource, sessionCounter)
        context.withTransaction {}
        shouldThrow<IllegalStateException> {
            context.withTransaction { }
        }.message shouldBe "Den transaksjonelle sesjonen er lukket."
    }

    @Test
    fun `kan ikke bruke samme context session flere ganger`() {
        val context = PostgresSessionContext(dataSource, sessionCounter)
        context.withSession {}
        shouldThrow<IllegalStateException> {
            context.withSession { }
        }.message shouldBe "Sesjonen er lukket."
    }

    @Test
    fun `PostgresTransactionContext - må kalle withTransaction`() {
        val context = PostgresTransactionContext(dataSource, sessionCounter)
        shouldThrow<IllegalStateException> {
            context.withSession { }
        }.message shouldBe "Må først starte en withTransaction(...) før man kan kalle withSession(...) for en TransactionContext."
    }

    @Test
    fun `flere operasjoner i en transaksjon`() {
        val tx = PostgresTransactionContext(dataSource, sessionCounter)
        tx.withTransaction { session ->

            session.run(
                queryOf("create table test (test varchar not null)").asExecute,
            )

            session.run(
                queryOf("insert into test (test) values ('Hello') ").asExecute,
            )

            session.run(
                queryOf("insert into test (test) values ('World') ").asExecute,
            )
        }
        tx.isClosed() shouldBe true

        val sx = PostgresSessionContext(dataSource, sessionCounter)
        sx.withSession { session ->
            val resultat = session.run(
                queryOf(
                    "select * from test",
                ).map { row ->
                    row.string("test")
                }.asList,
            )
            resultat.size shouldBe 2
            resultat.first() shouldBe "Hello"
            resultat.last() shouldBe "World"
        }
    }

    @Test
    fun rollback() {
        PostgresSessionContext(dataSource, sessionCounter).withSession {
                session ->
            session.run(
                queryOf("create table rollback (test varchar not null)").asExecute,
            )
        }

        val tx = PostgresTransactionContext(dataSource, sessionCounter)
        Either.catch {
            tx.withTransaction { session ->
                session.run(
                    queryOf("insert into rollback (test) values ('Hello') ").asExecute,
                )

                session.run(
                    queryOf("insert into rollback (test) values ('World') ").asExecute,
                )

                throw IllegalStateException("Rollback")
            }
        }
        tx.isClosed() shouldBe true

        val sx = PostgresSessionContext(dataSource, sessionCounter)
        sx.withSession { session ->
            val resultat = session.run(
                queryOf(
                    "select * from rollback",
                ).map { row ->
                    row.string("test")
                }.asList,
            )
            resultat.size shouldBe 0
        }
    }
}
