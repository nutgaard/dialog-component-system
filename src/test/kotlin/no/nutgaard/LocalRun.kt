package no.nutgaard

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.testcontainers.containers.PostgreSQLContainer

class SpecifiedPostgreSQLContainer : PostgreSQLContainer<SpecifiedPostgreSQLContainer>("postgres:14.5-alpine")

fun main() {
    val db = SpecifiedPostgreSQLContainer()
    db.start()

    val configuration = Configuration(
        database = DatabaseConfiguration(
            jdbcUrl = db.jdbcUrl,
            username = "test",
            password = "test"
        )
    )

    configuration.database.runFlyway()

    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        startApplication(configuration)
    }.start(wait = true)
}
