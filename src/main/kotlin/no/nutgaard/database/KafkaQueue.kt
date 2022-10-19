package no.nutgaard.database

import com.github.ksuid.Ksuid
import java.time.OffsetDateTime
import javax.sql.DataSource

object KafkaQueue {
    enum class EntityType { Dialog, Message }
    enum class Status { Queued, Running, Ok, Failed }
    data class QueueEntry(
        val id: Long,
        val entityId: Ksuid,
        val entityType: EntityType,
        val componentType: String, // maxLength 50
        val created: OffsetDateTime,
        val updated: OffsetDateTime,
        val failedAttempts: Int,
        val status: Status,
        val message: String
    ) { companion object }

    interface Repository {
        fun getOrNull(id: Long): QueueEntry?
        fun get(id: Long): QueueEntry
        fun getPending(): List<QueueEntry>
        fun getFailed(): List<QueueEntry>
        fun getByStatus(status: Status): List<QueueEntry>
        fun getAndAcquirePending(): List<QueueEntry>
        fun updateStatus(id: Long, status: Status)
        fun updateStatus(ids: List<Long>, status: Status)
    }

    const val FAILED_LIMIT = 5
    class RepositoryImpl(db: DataSource) : Repository {
        private val session = sessionOf(db)

        override fun getOrNull(id: Long): QueueEntry? {
            return session
                .run("SELECT * FROM kafka_queue WHERE id = ?") {
                    setLong(1, id)
                }
                .map { it.toQueueEntry() }
                .singleOrNull()
        }

        override fun get(id: Long): QueueEntry = requireNotNull(getOrNull(id))

        override fun getByStatus(status: Status): List<QueueEntry> =
            getByStatusAndFailedAttempts(status, Int.MAX_VALUE)

        override fun getPending(): List<QueueEntry> =
            getByStatusAndFailedAttempts(Status.Queued, FAILED_LIMIT)

        override fun getFailed(): List<QueueEntry> =
            getByStatusAndFailedAttempts(Status.Failed, Int.MAX_VALUE)

        override fun getAndAcquirePending(): List<QueueEntry> {
            return session.run(
                """
                UPDATE kafka_queue
                SET status = 'Running', updated = NOW()
                WHERE id IN (
                    SELECT id
                    FROM kafka_queue
                    WHERE status = 'Queued' AND failed_attempts < 5
                    FOR UPDATE SKIP LOCKED
                    LIMIT 100
                )
                RETURNING *
                """.trimIndent()
            )
                .map { it.toQueueEntry() }
                .toList()
        }

        override fun updateStatus(id: Long, status: Status) {
            return updateStatus(listOf(id), status)
        }

        override fun updateStatus(ids: List<Long>, status: Status) {
            return session.transaction { tx ->
                ids.chunked(1000).all { subIds ->
                    tx.execute(
                        """
                    UPDATE kafka_queue
                    SET status = ?::queue_enum, updated = NOW()
                    WHERE id in (${inClauseFor(subIds)})
                        """.trimIndent()
                    ) {
                        setString(1, status.toString())
                        subIds.forEachIndexed { index, subid ->
                            setLong(2 + index, subid)
                        }
                    }
                }
            }
        }

        private fun getByStatusAndFailedAttempts(status: Status, failedAttempts: Int): List<QueueEntry> {
            return session.run(
                """
            SELECT id
            FROM kafka_queue
            WHERE status = ? AND failed_attempts < ?
                """.trimIndent()
            ) {
                setString(1, status.toString())
                setInt(2, failedAttempts)
            }
                .map { it.toQueueEntry() }
                .toList()
        }
    }

    private fun Row.toQueueEntry() = QueueEntry(
        id = long("id"),
        entityId = Ksuid.fromString(string("entity_id")),
        entityType = EntityType.valueOf(string("entity_type")),
        componentType = string("component_type"),
        created = offsetDateTime("created"),
        updated = offsetDateTime("updated"),
        failedAttempts = int("failed_attempts"),
        status = Status.valueOf(string("status")),
        message = string("message")
    )
}
