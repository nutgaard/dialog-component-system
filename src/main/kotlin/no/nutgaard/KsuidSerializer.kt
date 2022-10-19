package no.nutgaard

import com.github.ksuid.Ksuid
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object KsuidSerializer : KSerializer<Ksuid> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Ksuid", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): Ksuid {
        return Ksuid.fromString(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: Ksuid) {
        encoder.encodeString(value.toString())
    }
}
