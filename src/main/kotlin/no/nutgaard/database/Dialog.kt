@file:UseSerializers(KsuidSerializer::class)

package no.nutgaard.database

import com.github.ksuid.Ksuid
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nutgaard.KsuidSerializer
import javax.sql.DataSource

object Dialog {
    @JvmInline
    @Serializable
    value class Actor(val value: String)

    @JvmInline
    @Serializable
    value class Source(val value: String)

    enum class ActorType { Employee, System, ExternalUser, ExternalOrganization }

    @Serializable
    open class DialogEntry(
        val id: Ksuid,
        val source: Source,
        val owner: Actor,
        val ownerType: ActorType,
        val created: LocalDateTime,
        val components: List<Component>
    )

    @Serializable
    class MessageEntry(
        val id: Ksuid,
        val dialogId: Ksuid,
        val actor: Actor,
        val actorType: ActorType,
        val created: LocalDateTime,
        val components: List<Component>,
        val content: String
    )

    @Serializable
    class DialogDTO(
        val id: Ksuid,
        val source: Source,
        val owner: Actor,
        val ownerType: ActorType,
        val created: LocalDateTime,
        val components: List<Component>,
        val messages: List<MessageEntry>
    )

    interface Repository {
        fun createDialog(id: Ksuid = Ksuid.newKsuid(), source: Source, owner: Actor, ownerType: ActorType): DialogEntry
        fun createMessage(
            id: Ksuid = Ksuid.newKsuid(),
            dialogId: Ksuid,
            actor: Actor,
            actorType: ActorType,
            content: String
        ): MessageEntry

        fun getDialogOrNull(id: Ksuid): DialogEntry?
        fun getDialog(id: Ksuid): DialogEntry
        fun getMessageOrNull(id: Ksuid): MessageEntry?
        fun getMessage(id: Ksuid): MessageEntry
        fun getByOwner(owner: Actor, ownerType: ActorType, source: Source?): List<DialogEntry>
        fun getInbox(owner: Actor, ownerType: ActorType, source: Source?): List<DialogDTO>
        fun addComponentToDialog(id: Ksuid, component: Component): DialogEntry
        fun addComponentToMessage(id: Ksuid, component: Component): MessageEntry
    }

    class RepositoryImpl(db: DataSource) : Repository {
        val session = sessionOf(db)

        override fun createDialog(
            id: Ksuid,
            source: Source,
            owner: Actor,
            ownerType: ActorType
        ): DialogEntry {
            return session
                .run(
                    """
                    INSERT INTO dialog (id, source, owner, owner_type)
                    VALUES (?, ?, ?, ?::actor_enum)
                    RETURNING *
                    """.trimIndent()
                ) {
                    setString(1, id.toString())
                    setString(2, source.value)
                    setString(3, owner.value)
                    setString(4, ownerType.toString())
                }.map { it.toDialogEntry() }
                .single()
        }

        override fun createMessage(
            id: Ksuid,
            dialogId: Ksuid,
            actor: Actor,
            actorType: ActorType,
            content: String
        ): MessageEntry {
            return session
                .run(
                    """
                    INSERT INTO message (id, dialog_id, actor, actor_type, content)
                    VALUES (?, ?, ?, ?::actor_enum, ?)
                    RETURNING *
                    """.trimIndent()
                ) {
                    setString(1, id.toString())
                    setString(2, dialogId.toString())
                    setString(3, actor.value)
                    setString(4, actorType.toString())
                    setString(5, content)
                }.map { it.toMessageEntry() }
                .single()
        }

        override fun getDialogOrNull(id: Ksuid): DialogEntry? {
            return session
                .run("SELECT * FROM dialog WHERE id = ?") {
                    setString(1, id.toString())
                }
                .map { it.toDialogEntry() }
                .singleOrNull()
        }

        override fun getDialog(id: Ksuid): DialogEntry = requireNotNull(getDialogOrNull(id))

        override fun getMessageOrNull(id: Ksuid): MessageEntry? {
            return session
                .run("SELECT * FROM message WHERE id = ?") {
                    setString(1, id.toString())
                }
                .map { it.toMessageEntry() }
                .singleOrNull()
        }

        override fun getMessage(id: Ksuid): MessageEntry = requireNotNull(getMessageOrNull(id))

        override fun getByOwner(owner: Actor, ownerType: ActorType, source: Source?): List<DialogEntry> {
            val sourceClause = if (source == null) "" else "AND source = ?"
            return session
                .run("SELECT * FROM dialog WHERE owner = ? AND owner_type = ?::actor_enum $sourceClause") {
                    setString(1, owner.value)
                    setString(2, ownerType.toString())
                    if (source != null) {
                        setString(3, source.value)
                    }
                }
                .map { it.toDialogEntry() }
                .toList()
        }

        override fun getInbox(owner: Actor, ownerType: ActorType, source: Source?): List<DialogDTO> {
            val sourceClause = if (source == null) "" else "AND source = ?"
            session.transaction { tx ->
                val dialogs = tx
                    .run("SELECT * FROM dialog WHERE owner = ? AND owner_type = ?::actor_enum $sourceClause") {
                        setString(1, owner.value)
                        setString(2, ownerType.toString())
                        if (source != null) {
                            setString(3, source.value)
                        }
                    }
                    .map { it.toDialogEntry() }
                    .toList()

                val messages = dialogs
                    .map { it.id }
                    .chunked(1000)
                    .map { ids ->
                        tx
                            .run("SELECT * FROM message WHERE dialog_id IN (${inClauseFor(ids)})") {
                                ids.forEachIndexed { index, ksuid ->
                                    setString(1 + index, ksuid.toString())
                                }
                            }
                            .map { it.toMessageEntry() }
                            .toList()
                    }
                    .flatten()
                    .groupBy(MessageEntry::dialogId)

                return dialogs.map {
                    DialogDTO(
                        id = it.id,
                        source = it.source,
                        owner = it.owner,
                        ownerType = it.ownerType,
                        created = it.created,
                        components = it.components,
                        messages = messages[it.id] ?: emptyList()
                    )
                }
            }
        }

        override fun addComponentToDialog(id: Ksuid, component: Component): DialogEntry {
            return session
                .run("SELECT * FROM add_component_to_dialog(?, ?::jsonb)") {
                    setString(1, id.toString())
                    setString(2, Json.encodeToString(component))
                }
                .map { it.toDialogEntry() }
                .single()
        }

        override fun addComponentToMessage(id: Ksuid, component: Component): MessageEntry {
            return session
                .run("SELECT * FROM add_component_to_message(?, ?::jsonb)") {
                    setString(1, id.toString())
                    setString(2, Json.encodeToString(component))
                }
                .map { it.toMessageEntry() }
                .single()
        }
    }

    private fun Row.toDialogEntry() = DialogEntry(
        id = Ksuid.fromString(string("id")),
        source = Source(string("source")),
        owner = Actor(string("owner")),
        ownerType = ActorType.valueOf(string("owner_type")),
        created = kotlinLocalDate("created"),
        components = ComponentJson(string("components")).asComponentList()
    )

    private fun Row.toMessageEntry() = MessageEntry(
        id = Ksuid.fromString(string("id")),
        dialogId = Ksuid.fromString(string("dialog_id")),
        actor = Actor(string("actor")),
        actorType = ActorType.valueOf(string("actor_type")),
        created = kotlinLocalDate("created"),
        components = ComponentJson(string("components")).asComponentList(),
        content = string("content")
    )
}
