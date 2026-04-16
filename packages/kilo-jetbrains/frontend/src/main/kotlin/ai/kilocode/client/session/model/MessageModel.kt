package ai.kilocode.client.session.model

import ai.kilocode.rpc.dto.MessageDto

/** A single message with its accumulated part text. */
class MessageModel(val info: MessageDto) {
    val parts = LinkedHashMap<String, StringBuilder>()
}
