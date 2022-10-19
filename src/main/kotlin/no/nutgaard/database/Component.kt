package no.nutgaard.database

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@Serializable(with = ComponentSerializer::class)
data class Component(
    val type: String,
    val data: Map<String, Any?>? = null
)

private object ComponentSerializer : KSerializer<Component> {
    override val descriptor = MapSerializer(
        String.serializer(),
        JsonElement.serializer()
    ).descriptor

    override fun serialize(encoder: Encoder, value: Component) {
        val extra: Array<Pair<String, JsonElement>> = value.data
            ?.map { it.key to it.value.toJsonElement() }
            ?.toTypedArray()
            ?: emptyArray()

        MapSerializer(String.serializer(), JsonElement.serializer()).serialize(
            encoder,
            mapOf(
                "type" to JsonPrimitive(value.type),
                *extra
            )
        )
    }

    override fun deserialize(decoder: Decoder): Component {
        val jsonObject = decoder.decodeSerializableValue(JsonObject.serializer())

        val content = jsonObject
            .map { it.key to it.value.fromJsonElement() }
            .toMap(mutableMapOf())

        val type = requireNotNull(content["type"])
        assert(type is String)
        content.remove("type")

        return Component(type as String, if (content.isEmpty()) null else content)
    }

    private fun Any?.toJsonElement(): JsonElement {
        return when (val that = this) {
            null -> JsonNull
            is String? -> JsonPrimitive(that)
            is Number? -> JsonPrimitive(that)
            is Boolean? -> JsonPrimitive(that)
            is Iterable<*> -> buildJsonArray {
                for (element in that) {
                    add(element.toJsonElement())
                }
            }

            is Array<*> -> buildJsonArray {
                for (element in that) {
                    add(element.toJsonElement())
                }
            }

            is Map<*, *> -> buildJsonObject {
                for ((key, value) in that) {
                    put(key.toString(), value.toJsonElement())
                }
            }

            else -> {
                error("Unknown type to serializer to json: $that")
            }
        }
    }

    private fun JsonElement.fromJsonElement(): Any? {
        return when (val that = this) {
            is JsonNull -> null
            is JsonPrimitive -> when {
                that.content.equals("true", ignoreCase = true) -> true
                that.content.equals("false", ignoreCase = true) -> false
                !that.isString -> that.int
                else -> that.contentOrNull
            }

            is JsonArray -> buildList {
                for (element in that) {
                    add(element.fromJsonElement())
                }
            }

            is JsonObject -> buildMap {
                for ((key, value) in that) {
                    put(key, value.fromJsonElement())
                }
            }
        }
    }
}

@JvmInline
value class ComponentJson(val value: String) {
    fun asComponentList(): List<Component> = Json.decodeFromString(value)
}
