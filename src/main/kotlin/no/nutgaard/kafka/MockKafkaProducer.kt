package no.nutgaard.kafka

class MockKafkaProducer {
    fun send(message: String) {
        println("[KAFKA] $message")
    }
}
