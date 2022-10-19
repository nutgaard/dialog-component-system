package no.nutgaard.database

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.postgresql.PGNotification
import org.postgresql.jdbc.PgConnection
import java.util.concurrent.atomic.AtomicBoolean
import javax.sql.DataSource
import kotlin.time.Duration.Companion.seconds

typealias Callback = suspend (PGNotification) -> Unit

class DatabaseListener(
    private val scope: CoroutineScope,
    dataSource: DataSource
) {
    private var running = AtomicBoolean(false)
    private val connection: PgConnection by lazy { dataSource.connection.unwrap(PgConnection::class.java) }
    private val callbacks = mutableMapOf<String, MutableSet<Callback>>()

    fun listen(channel: String, callback: Callback): ChannelCallback {
        if (!running.get()) {
            start()
        }
        callbacks
            .getOrPut(channel) {
                listenTo(channel)
                mutableSetOf()
            }
            .add(callback)

        return ChannelCallback(this, channel, callback)
    }

    fun unlisten(channel: String, callback: Callback) {
        val channelCallbacks = callbacks[channel]
        channelCallbacks?.remove(callback)
        if (channelCallbacks?.isEmpty() == true) {
            callbacks.remove(channel)
            unlistenTo(channel)
        }

        if (callbacks.isEmpty()) {
            stop()
        }
    }

    private fun listenTo(channel: String) {
        scope.launch {
            connection.createStatement().use {
                println("[ChannelListener] Connection to $channel")
                it.execute("LISTEN $channel")
            }
        }
    }

    private fun unlistenTo(channel: String) {
        scope.launch {
            connection.createStatement().use {
                println("[ChannelListener] Disconnecting from $channel")
                it.execute("UNLISTEN $channel")
            }
        }
    }

    private fun start() {
        if (running.getAndSet(true)) return
        scope.launch {
            connection.createStatement().use {
                println("[ChannelListener] Connecting to database")
                while (true) {
                    if (!running.get()) break
                    val notifications = connection
                        .getNotifications(10.seconds.inWholeMilliseconds.toInt())
                        ?: emptyArray()

                    for (notification in notifications) {
                        val channelCallbacks = callbacks[notification.name] ?: emptySet()
                        for (callback in channelCallbacks) {
                            callback(notification)
                        }
                    }
                }
                println("[ChannelListener] Disconnecting from database")
            }
        }
    }

    private fun stop() {
        println("[ChannelListener] Stopping Database listener")
        callbacks.clear()
        running.set(false)
    }
}
class ChannelCallback(
    private val listener: DatabaseListener,
    private val channel: String,
    private val callback: Callback
) {
    fun unlisten() {
        listener.unlisten(channel, callback)
    }
}
