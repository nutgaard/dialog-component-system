package no.nutgaard

import com.github.ksuid.Ksuid
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nutgaard.database.Component
import no.nutgaard.database.DatabaseListener
import no.nutgaard.database.Dialog
import no.nutgaard.database.KafkaQueue
import no.nutgaard.kafka.MockKafkaProducer
import no.nutgaard.plugins.configureRouting
import no.nutgaard.plugins.configureSerialization
import no.nutgaard.plugins.configureSockets
import no.nutgaard.websocket.WsServer

fun Application.startApplication(configuration: Configuration) {
    val databaseListener = DatabaseListener(
        scope = CoroutineScope(this.coroutineContext),
        dataSource = configuration.database.createDatasource()
    )
    val notifyServer = WsServer()
    val kafkaProducer = MockKafkaProducer()
    val kafkaQueueRepository = KafkaQueue.RepositoryImpl(configuration.database.createDatasource())
    val dialogRepository = Dialog.RepositoryImpl(configuration.database.createDatasource())

    databaseListener.listen(PgChannels.kafkaQueueChannel) { notification ->
        notifyServer.send("Received notification: pid=${notification.pid} channel=${notification.name} payload=${notification.parameter}")

        val pending = kafkaQueueRepository.getAndAcquirePending()

        if (pending.isNotEmpty()) {
            delay(4000)
            kafkaQueueRepository.updateStatus(pending.map { it.id }, KafkaQueue.Status.Ok)
        }
    }
    databaseListener.listen(PgChannels.dialogChannel) {
        notifyServer.send("Received notification: pid=${it.pid} channel=${it.name} payload=${it.parameter}")
        val dialog = dialogRepository.getDialogOrNull(Ksuid.fromString(it.parameter))
        kafkaProducer.send("[DIALOG] ${Json.encodeToString(dialog)}")
    }
    databaseListener.listen(PgChannels.messageChannel) {
        notifyServer.send("Received notification: pid=${it.pid} channel=${it.name} payload=${it.parameter}")
        val dialog = dialogRepository.getMessageOrNull(Ksuid.fromString(it.parameter))
        kafkaProducer.send("[MESSAGE] ${Json.encodeToString(dialog)}")
    }

    configureSerialization()
    configureSockets()
    configureRouting()
    routing {
        singlePageApplication {
            react("src/main/resources/www")
        }
        webSocket("/notify") {
            try {
                notifyServer.register(this)
                incoming.receive()
            } catch (_: ClosedReceiveChannelException) {
            } finally {
                notifyServer.unregister(this)
            }
        }
        route("api") {
            route("dialog") {
                get {
                    call.respond(dialogRepository.getInbox(Dialog.Actor("12345678910"), Dialog.ActorType.ExternalUser, null))
                }

                get("{id}") {
                    val id = Ksuid.fromString(call.parameters["id"])
                    val dialog = dialogRepository.getDialogOrNull(id)
                    if (dialog != null) {
                        call.respond(dialog)
                    } else {
                        call.respondText("Dialog with id: $id was not found", status = HttpStatusCode.NotFound)
                    }
                }
                post {
                    val dialog = dialogRepository.createDialog(
                        source = Dialog.Source("STO"),
                        owner = Dialog.Actor("12345678910"),
                        ownerType = Dialog.ActorType.ExternalUser
                    )
                    call.respond(dialog)
                }
                post("{dialog_id}/message") {
                    val dialogId = Ksuid.fromString(call.parameters["dialog_id"])
                    val content = call.receive<String>()
                    val dialog = dialogRepository.createMessage(
                        dialogId = dialogId,
                        actor = Dialog.Actor("Z123654"),
                        actorType = Dialog.ActorType.Employee,
                        content = content
                    )
                    call.respond(dialog)
                }
                post("{dialog_id}/component") {
                    val dialogId = Ksuid.fromString(call.parameters["dialog_id"])
                    val component = call.receive<Component>()
                    call.respond(dialogRepository.addComponentToDialog(dialogId, component))
                }
            }
            route("message") {
                get("{id}") {
                    val id = Ksuid.fromString(call.parameters["id"])
                    val message = dialogRepository.getMessageOrNull(id)
                    if (message != null) {
                        call.respond(message)
                    } else {
                        call.respondText("Message with id: $id was not found", status = HttpStatusCode.NotFound)
                    }
                }
                post("{id}/component") {
                    val id = Ksuid.fromString(call.parameters["id"])
                    val component = call.receive<Component>()
                    call.respond(dialogRepository.addComponentToMessage(id, component))
                }
            }
        }
    }
}
