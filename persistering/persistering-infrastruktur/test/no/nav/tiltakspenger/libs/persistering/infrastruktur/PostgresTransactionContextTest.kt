package no.nav.tiltakspenger.libs.persistering.infrastruktur

import arrow.core.Either
import io.github.oshai.kotlinlogging.KotlinLogging
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotliquery.queryOf
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
        runTest {
            val context = PostgresTransactionContext(dataSource, sessionCounter)
            context.withTransaction {}
            shouldThrow<IllegalStateException> {
                context.withTransaction { }
            }.message shouldBe "Den transaksjonelle sesjonen er lukket."
        }
    }

    @Test
    fun `kan ikke bruke samme context session flere ganger`() {
        runTest {
            val context = PostgresSessionContext(dataSource, sessionCounter)
            context.withSession {}
            shouldThrow<IllegalStateException> {
                context.withSession { }
            }.message shouldBe "Sesjonen er lukket."
        }
    }

    @Test
    fun `PostgresTransactionContext - må kalle withTransaction`() {
        runTest {
            val context = PostgresTransactionContext(dataSource, sessionCounter)
            shouldThrow<IllegalStateException> {
                context.withSession { }
            }.message shouldBe "Må først starte en withTransaction(...) før man kan kalle withSession(...) for en TransactionContext."
        }
    }

    @Test
    fun `flere operasjoner i en transaksjon`() {
        runTest {
            val tx = PostgresTransactionContext(dataSource, sessionCounter)
            tx.withTransaction { session ->

                session.runSuspend(
                    queryOf("create table test (test varchar not null)").asExecute,
                )

                session.runSuspend(
                    queryOf("insert into test (test) values ('Hello') ").asExecute,
                )

                session.runSuspend(
                    queryOf("insert into test (test) values ('World') ").asExecute,
                )
            }
            tx.isClosed() shouldBe true

            val sx = PostgresSessionContext(dataSource, sessionCounter)
            sx.withSession { session ->
                val resultat = session.runSuspend(
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
    }

    @Test
    fun rollback() {
        runTest {
            PostgresSessionContext(dataSource, sessionCounter).withSession { session ->
                session.runSuspend(
                    queryOf("create table rollback (test varchar not null)").asExecute,
                )
            }

            val tx = PostgresTransactionContext(dataSource, sessionCounter)
            Either.catch {
                tx.withTransaction { session ->
                    session.runSuspend(
                        queryOf("insert into rollback (test) values ('Hello') ").asExecute,
                    )

                    session.runSuspend(
                        queryOf("insert into rollback (test) values ('World') ").asExecute,
                    )

                    throw IllegalStateException("Rollback")
                }
            }
            tx.isClosed() shouldBe true

            val sx = PostgresSessionContext(dataSource, sessionCounter)
            sx.withSession { session ->
                val resultat = session.runSuspend(
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

    @Test
    fun `UpdateQueryAction - returnerer antall rader påvirket`() {
        runTest {
            PostgresSessionContext(dataSource, sessionCounter).withSession { session ->
                session.runSuspend(
                    queryOf("create table update_test (id serial primary key, name varchar not null)").asExecute,
                )

                session.runSuspend(
                    queryOf("insert into update_test (name) values ('a'), ('b'), ('c')").asExecute,
                )

                val updatedRows = session.runSuspend(
                    queryOf("update update_test set name = 'updated' where name in ('a', 'b')").asUpdate,
                )
                updatedRows shouldBe 2
            }
        }
    }

    @Test
    fun `UpdateQueryAction - returnerer 0 når ingen rader påvirkes`() {
        runTest {
            PostgresSessionContext(dataSource, sessionCounter).withSession { session ->
                session.runSuspend(
                    queryOf("create table update_zero_test (id serial primary key, name varchar not null)").asExecute,
                )

                val updatedRows = session.runSuspend(
                    queryOf("update update_zero_test set name = 'updated' where name = 'nonexistent'").asUpdate,
                )
                updatedRows shouldBe 0
            }
        }
    }

    @Test
    fun `UpdateQueryAction - delete returnerer antall slettede rader`() {
        runTest {
            PostgresSessionContext(dataSource, sessionCounter).withSession { session ->
                session.runSuspend(
                    queryOf("create table delete_test (id serial primary key, name varchar not null)").asExecute,
                )

                session.runSuspend(
                    queryOf("insert into delete_test (name) values ('a'), ('b'), ('c'), ('d')").asExecute,
                )

                val deletedRows = session.runSuspend(
                    queryOf("delete from delete_test where name in ('a', 'c', 'd')").asUpdate,
                )
                deletedRows shouldBe 3
            }
        }
    }

    @Test
    fun `UpdateAndReturnGeneratedKeyQueryAction - returnerer generert id`() {
        runTest {
            PostgresSessionContext(dataSource, sessionCounter).withSession { session ->
                session.runSuspend(
                    queryOf("create table generated_key_test (id serial primary key, name varchar not null)").asExecute,
                )

                val generatedKey1 = session.runSuspend(
                    queryOf("insert into generated_key_test (name) values ('first')").asUpdateAndReturnGeneratedKey,
                )
                generatedKey1 shouldBe 1L

                val generatedKey2 = session.runSuspend(
                    queryOf("insert into generated_key_test (name) values ('second')").asUpdateAndReturnGeneratedKey,
                )
                generatedKey2 shouldBe 2L

                val generatedKey3 = session.runSuspend(
                    queryOf("insert into generated_key_test (name) values ('third')").asUpdateAndReturnGeneratedKey,
                )
                generatedKey3 shouldBe 3L
            }
        }
    }

    @Test
    fun `ListResultQueryAction - returnerer tom liste når ingen rader finnes`() {
        runTest {
            PostgresSessionContext(dataSource, sessionCounter).withSession { session ->
                session.runSuspend(
                    queryOf("create table list_empty_test (id serial primary key, name varchar not null)").asExecute,
                )

                val result = session.runSuspend(
                    queryOf("select * from list_empty_test").map { row ->
                        row.string("name")
                    }.asList,
                )
                result shouldBe emptyList()
            }
        }
    }

    @Test
    fun `ListResultQueryAction - returnerer alle rader`() {
        runTest {
            PostgresSessionContext(dataSource, sessionCounter).withSession { session ->
                session.runSuspend(
                    queryOf("create table list_test (id serial primary key, name varchar not null, age int not null)").asExecute,
                )

                session.runSuspend(
                    queryOf("insert into list_test (name, age) values ('Alice', 30), ('Bob', 25), ('Charlie', 35)").asExecute,
                )

                val result = session.runSuspend(
                    queryOf("select name, age from list_test order by age").map { row ->
                        "${row.string("name")}:${row.int("age")}"
                    }.asList,
                )
                result shouldBe listOf("Bob:25", "Alice:30", "Charlie:35")
            }
        }
    }

    @Test
    fun `ListResultQueryAction - med parameterisert spørring`() {
        runTest {
            PostgresSessionContext(dataSource, sessionCounter).withSession { session ->
                session.runSuspend(
                    queryOf("create table list_param_test (id serial primary key, category varchar not null, value int not null)").asExecute,
                )

                session.runSuspend(
                    queryOf("insert into list_param_test (category, value) values ('A', 1), ('B', 2), ('A', 3), ('B', 4), ('A', 5)").asExecute,
                )

                val result = session.runSuspend(
                    queryOf(
                        "select value from list_param_test where category = :category order by value",
                        mapOf("category" to "A"),
                    ).map { row ->
                        row.int("value")
                    }.asList,
                )
                result shouldBe listOf(1, 3, 5)
            }
        }
    }

    @Test
    fun `NullableResultQueryAction - returnerer null når ingen rad finnes`() {
        runTest {
            PostgresSessionContext(dataSource, sessionCounter).withSession { session ->
                session.runSuspend(
                    queryOf("create table nullable_empty_test (id serial primary key, name varchar not null)").asExecute,
                )

                val result = session.runSuspend(
                    queryOf(
                        "select name from nullable_empty_test where id = :id",
                        mapOf("id" to 999),
                    ).map { row ->
                        row.string("name")
                    }.asSingle,
                )
                result shouldBe null
            }
        }
    }

    @Test
    fun `NullableResultQueryAction - returnerer verdi når rad finnes`() {
        runTest {
            PostgresSessionContext(dataSource, sessionCounter).withSession { session ->
                session.runSuspend(
                    queryOf("create table nullable_test (id serial primary key, name varchar not null)").asExecute,
                )

                session.runSuspend(
                    queryOf("insert into nullable_test (name) values ('TestName')").asExecute,
                )

                val result = session.runSuspend(
                    queryOf(
                        "select name from nullable_test where id = :id",
                        mapOf("id" to 1),
                    ).map { row ->
                        row.string("name")
                    }.asSingle,
                )
                result shouldBe "TestName"
            }
        }
    }

    @Test
    fun `NullableResultQueryAction - mapper komplekst objekt`() {
        runTest {
            data class Person(val id: Int, val name: String, val age: Int)

            PostgresSessionContext(dataSource, sessionCounter).withSession { session ->
                session.runSuspend(
                    queryOf("create table nullable_complex_test (id serial primary key, name varchar not null, age int not null)").asExecute,
                )

                session.runSuspend(
                    queryOf("insert into nullable_complex_test (name, age) values ('Alice', 30)").asExecute,
                )

                val result = session.runSuspend(
                    queryOf(
                        "select id, name, age from nullable_complex_test where name = :name",
                        mapOf("name" to "Alice"),
                    ).map { row ->
                        Person(
                            id = row.int("id"),
                            name = row.string("name"),
                            age = row.int("age"),
                        )
                    }.asSingle,
                )
                result shouldBe Person(id = 1, name = "Alice", age = 30)
            }
        }
    }

    @Test
    fun `ListResultQueryAction - mapper komplekse objekter`() {
        runTest {
            data class Item(val id: Int, val name: String, val price: Double)

            PostgresSessionContext(dataSource, sessionCounter).withSession { session ->
                session.runSuspend(
                    queryOf("create table list_complex_test (id serial primary key, name varchar not null, price decimal(10,2) not null)").asExecute,
                )

                session.runSuspend(
                    queryOf("insert into list_complex_test (name, price) values ('Apple', 1.50), ('Banana', 0.75), ('Cherry', 3.00)").asExecute,
                )

                val result = session.runSuspend(
                    queryOf("select id, name, price from list_complex_test order by price desc").map { row ->
                        Item(
                            id = row.int("id"),
                            name = row.string("name"),
                            price = row.double("price"),
                        )
                    }.asList,
                )
                result shouldBe listOf(
                    Item(id = 3, name = "Cherry", price = 3.00),
                    Item(id = 1, name = "Apple", price = 1.50),
                    Item(id = 2, name = "Banana", price = 0.75),
                )
            }
        }
    }

    @Test
    fun `ExecuteQueryAction - returnerer false ved vellykket DDL`() {
        runTest {
            PostgresSessionContext(dataSource, sessionCounter).withSession { session ->
                val result = session.runSuspend(
                    queryOf("create table execute_test (id serial primary key, name varchar not null)").asExecute,
                )
                // execute() returnerer false når resultatet er en update count, ikke et ResultSet
                result shouldBe false
            }
        }
    }

    @Test
    fun `ExecuteQueryAction - returnerer false ved vellykket insert`() {
        runTest {
            PostgresSessionContext(dataSource, sessionCounter).withSession { session ->
                session.runSuspend(
                    queryOf("create table execute_insert_test (id serial primary key, name varchar not null)").asExecute,
                )

                val result = session.runSuspend(
                    queryOf("insert into execute_insert_test (name) values ('test')").asExecute,
                )
                // execute() returnerer false når resultatet er en update count, ikke et ResultSet
                result shouldBe false
            }
        }
    }

    @Test
    fun `alle action-typer i samme transaksjon`() {
        runTest {
            val tx = PostgresTransactionContext(dataSource, sessionCounter)
            tx.withTransaction { session ->
                // ExecuteQueryAction - create table
                val createResult = session.runSuspend(
                    queryOf("create table all_actions_test (id serial primary key, name varchar not null, active boolean not null default true)").asExecute,
                )
                // execute() returnerer false når resultatet er en update count
                createResult shouldBe false

                // UpdateAndReturnGeneratedKeyQueryAction - insert and get key
                val key1 = session.runSuspend(
                    queryOf("insert into all_actions_test (name) values ('First')").asUpdateAndReturnGeneratedKey,
                )
                key1 shouldBe 1L

                val key2 = session.runSuspend(
                    queryOf("insert into all_actions_test (name) values ('Second')").asUpdateAndReturnGeneratedKey,
                )
                key2 shouldBe 2L

                // UpdateQueryAction - update rows
                val updatedCount = session.runSuspend(
                    queryOf("update all_actions_test set active = false where id = :id", mapOf("id" to 1)).asUpdate,
                )
                updatedCount shouldBe 1

                // NullableResultQueryAction - select single
                val single = session.runSuspend(
                    queryOf("select name, active from all_actions_test where id = :id", mapOf("id" to 1)).map { row ->
                        "${row.string("name")}:${row.boolean("active")}"
                    }.asSingle,
                )
                single shouldBe "First:false"

                // ListResultQueryAction - select all
                val all = session.runSuspend(
                    queryOf("select name from all_actions_test order by id").map { row ->
                        row.string("name")
                    }.asList,
                )
                all shouldBe listOf("First", "Second")
            }
        }
    }

    @Test
    fun `ExecuteQueryAction - returnerer true for SELECT`() {
        runTest {
            PostgresSessionContext(dataSource, sessionCounter).withSession { session ->
                session.runSuspend(
                    queryOf("create table execute_select_test (id serial primary key, name varchar not null)").asExecute,
                )

                val result = session.runSuspend(
                    queryOf("select * from execute_select_test").asExecute,
                )
                // execute() returnerer true når resultatet er et ResultSet
                result shouldBe true
            }
        }
    }

    @Test
    fun `ExecuteQueryAction - returnerer true for SELECT med data`() {
        runTest {
            PostgresSessionContext(dataSource, sessionCounter).withSession { session ->
                session.runSuspend(
                    queryOf("create table execute_select_data_test (id serial primary key, name varchar not null)").asExecute,
                )

                session.runSuspend(
                    queryOf("insert into execute_select_data_test (name) values ('test')").asExecute,
                )

                val result = session.runSuspend(
                    queryOf("select * from execute_select_data_test where id = 1").asExecute,
                )
                result shouldBe true
            }
        }
    }

    @Test
    fun `ExecuteQueryAction - returnerer true for CTE med SELECT`() {
        runTest {
            PostgresSessionContext(dataSource, sessionCounter).withSession { session ->
                session.runSuspend(
                    queryOf("create table execute_cte_test (id serial primary key, name varchar not null)").asExecute,
                )

                session.runSuspend(
                    queryOf("insert into execute_cte_test (name) values ('a'), ('b'), ('c')").asExecute,
                )

                // CTE som returnerer et ResultSet
                val result = session.runSuspend(
                    queryOf(
                        """
                    with names as (
                        select name from execute_cte_test
                    )
                    select * from names
                        """.trimIndent(),
                    ).asExecute,
                )
                result shouldBe true
            }
        }
    }
}
