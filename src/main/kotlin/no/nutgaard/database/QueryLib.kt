package no.nutgaard.database

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.*
import javax.sql.DataSource

private val inClauseCache = mutableMapOf<Int, String>()
fun inClauseFor(collection: Collection<*>): String = inClauseCache.getOrPut(collection.size) {
    "?, ".repeat(collection.size).removeSuffix(", ")
}
open class Session(val connection: Connection) : AutoCloseable {
    fun execute(query: String, bind: PreparedStatement.() -> Unit): Boolean {
        return connection.prepareStatement(query).apply(bind).execute()
    }

    fun update(query: String, bind: PreparedStatement.() -> Unit): Int {
        return connection.prepareStatement(query).apply(bind).executeUpdate()
    }

    fun run(query: String, bind: PreparedStatement.() -> Unit = {}): ResultSet {
        return connection.prepareStatement(query).apply(bind).executeQuery()
    }

    inline fun <A> transaction(block: (Transaction) -> A): A {
        connection.autoCommit = false
        try {
            val result: A = block(Transaction(connection))
            connection.commit()
            return result
        } catch (e: Throwable) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }

    override fun close() {
        connection.close()
    }
}

class Transaction(connection: Connection) : Session(connection)

fun sessionOf(dataSource: DataSource) = Session(dataSource.connection)
fun <T> ResultSet.map(block: (Row) -> T): Sequence<T> {
    return Row(this).map(block)
}

data class Row(val underlying: ResultSet, val cursor: Int = 0) : Sequence<Row> {
    private class RowIterator(val rs: ResultSet, val position: Int) : Iterator<Row> {
        override fun next(): Row {
            return Row(rs, position + 1)
        }

        override fun hasNext(): Boolean {
            return !rs.isClosed && rs.next()
        }
    }

    override fun iterator(): Iterator<Row> {
        return RowIterator(underlying, cursor)
    }

    /**
     * Surely fetches nullable value from ResultSet.
     */
    private fun <A> nullable(v: A): A? {
        return if (underlying.wasNull()) null else v
    }

    fun any(column: String): Any = anyOrNull(column)!!
    fun byte(column: String): Byte = byteOrNull(column)!!
    fun int(column: String): Int = intOrNull(column)!!
    fun short(column: String): Short = shortOrNull(column)!!
    fun long(column: String): Long = longOrNull(column)!!
    fun float(column: String): Float = floatOrNull(column)!!
    fun double(column: String): Double = doubleOrNull(column)!!
    fun bytes(column: String): ByteArray = bytesOrNull(column)!!
    fun string(column: String): String = stringOrNull(column)!!

    fun instant(column: String): Instant = instantOrNull(column)!!
    fun zonedDateTime(column: String): ZonedDateTime = zonedDateTimeOrNull(column)!!
    fun offsetDateTime(column: String): OffsetDateTime = offsetDateTimeOrNull(column)!!
    fun localDateTime(column: String): LocalDateTime = localDateTimeOrNull(column)!!
    fun localDate(column: String): LocalDate = localDateOrNull(column)!!
    fun localTime(column: String): LocalTime = localTimeOrNull(column)!!
    fun kotlinLocalDate(column: String): kotlinx.datetime.LocalDateTime = kotlinLocalDateOrNull(column)!!

    fun anyOrNull(column: String): Any? = nullable(underlying.getObject(column))
    fun byteOrNull(column: String): Byte? = nullable(underlying.getByte(column))
    fun intOrNull(column: String): Int? = nullable(underlying.getInt(column))
    fun shortOrNull(column: String): Short? = nullable(underlying.getShort(column))
    fun longOrNull(column: String): Long? = nullable(underlying.getLong(column))
    fun floatOrNull(column: String): Float? = nullable(underlying.getFloat(column))
    fun doubleOrNull(column: String): Double? = nullable(underlying.getDouble(column))
    fun bytesOrNull(column: String): ByteArray? = nullable(underlying.getBytes(column))
    fun stringOrNull(column: String): String? = nullable(underlying.getString(column))

    fun instantOrNull(column: String): Instant? = nullable(underlying.getTimestamp(column)?.toInstant())
    fun zonedDateTimeOrNull(column: String): ZonedDateTime? = instantOrNull(column)?.let { ZonedDateTime.ofInstant(it, ZoneId.systemDefault()) }
    fun offsetDateTimeOrNull(column: String): OffsetDateTime? = instantOrNull(column)?.let { OffsetDateTime.ofInstant(it, ZoneId.systemDefault()) }
    fun localDateTimeOrNull(column: String): LocalDateTime? = nullable(underlying.getTimestamp(column)?.toLocalDateTime())
    fun localDateOrNull(column: String): LocalDate? = localDateTimeOrNull(column)?.toLocalDate()
    fun localTimeOrNull(column: String): LocalTime? = localDateTimeOrNull(column)?.toLocalTime()
    fun kotlinLocalDateOrNull(column: String): kotlinx.datetime.LocalDateTime? = offsetDateTimeOrNull(column)?.toKotlin()

    private fun OffsetDateTime.toKotlin() = kotlinx.datetime.LocalDateTime(
        year = this.year,
        month = this.month,
        dayOfMonth = this.dayOfMonth,
        hour = this.hour,
        minute = this.minute,
        second = this.second,
        nanosecond = this.nano
    )
}
