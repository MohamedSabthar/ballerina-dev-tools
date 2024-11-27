import ballerinax/kafka;

service on new kafka:Listener(bootstrapServers = "localhost:9092", groupId = "order-group-id", topics = ["order-topic"]}) {
    remote function onConsumerRecord(kafka:AnydataConsumerRecord[] records) returns error? {
        do {
        } on fail error err {
            // handle error
        }
    }

    remote function onError(kafka:Error kafkaError) returns error? {
        do {
        } on fail error err {
            // handle error
        }
    }
}
