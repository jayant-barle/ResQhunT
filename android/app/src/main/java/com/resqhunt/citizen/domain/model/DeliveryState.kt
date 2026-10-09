package com.resqhunt.citizen.domain.model

enum class DeliveryState {
    CREATED,
    STORED_LOCALLY,
    RELAY_PENDING,
    TRANSFER_IN_PROGRESS,
    RECEIVED_BY_PEER,
    RELAYED_TO_PEER,
    RELAY_FAILED,
    SERVER_RECEIVED,
    COORDINATOR_ACKNOWLEDGED,
    ASSIGNED,
    IN_PROGRESS,
    RESOLVED
}

