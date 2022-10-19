package no.nutgaard

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.io.File
import javax.sql.DataSource

class Configuration(
    val database: DatabaseConfiguration
)
class DatabaseConfiguration(
    val jdbcUrl: String,
    val username: String,
    val password: String
) {
    private val dataSource: DataSource
    init {
        val config = HikariConfig()
        config.jdbcUrl = jdbcUrl
        config.username = username
        config.password = password
        config.addDataSourceProperty("cachePrepStmts", "true")
        config.addDataSourceProperty("prepStmtCacheSize", "250")
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        dataSource = HikariDataSource(config)
    }

    fun createDatasource(): DataSource = dataSource

    fun runFlyway() {
        migrate(
            "migration/V1_0__create_types",
            "migration/V1_1__create_domain_tables",
            "migration/V1_2__create_kafka_queue",
            "migration/V1_3__component_functions",
            "populate"
        )
        println("CREATED DATABASE")
    }

    private fun migrate(vararg names: String) {
        val sqls = names
            .associateWith { File("src/main/resources/db/$it.sql").readText() }

        this.createDatasource().connection.use { conn ->
            for ((name, sql) in sqls) {
                try {
                    conn.createStatement().use { stmt ->
                        stmt.execute(sql)
                    }
                    println("Migration complete: $name")
                } catch (e: Throwable) {
                    error("Migration failed: $name: $e")
                }
            }
        }
    }
}
