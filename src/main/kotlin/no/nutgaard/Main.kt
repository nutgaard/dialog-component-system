package no.nutgaard

import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    val configuration = Configuration(
        database = DatabaseConfiguration(
            jdbcUrl = "jdbc:postgresql://localhost:5432/dialog-component-system?loggerLevel=OFF",
            username = "test",
            password = "test"
        )
    )

    configuration.database.runFlyway()

    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        startApplication(configuration)
    }.start(wait = true)
}
