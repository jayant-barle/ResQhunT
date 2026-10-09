package com.resqhunt.citizen.domain.model

enum class DeliveryState {
    CREATED,
    STORED_LOCALLY,
    RELAY_PENDING,
    RELAYED_TO_PEER,
    SERVER_RECEIVED,
    COORDINATOR_ACKNOWLEDGED,
    ASSIGNED,
    IN_PROGRESS,
    RESOLVED
}
