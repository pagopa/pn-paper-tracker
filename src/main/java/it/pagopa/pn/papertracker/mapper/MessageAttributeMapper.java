package it.pagopa.pn.papertracker.mapper;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.MessageHeaders;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor(access = AccessLevel.NONE)
public final class MessageAttributeMapper {

    public static Map<String, MessageAttributeValue> fromHeaders(MessageHeaders headers) {
        Map<String, MessageAttributeValue> attributes = new HashMap<>();

        headers.forEach((key, value) -> {
            if (value == null) {
                return;
            }

            MessageAttributeValue attribute = toMessageAttributeValue(value);
            if (attribute != null) {
                attributes.put(key, attribute);
            }
        });

        return attributes;
    }

    private static MessageAttributeValue toMessageAttributeValue(Object value) {

        if (value instanceof String s) {
            return stringAttr(s);
        }

        if (value instanceof Number n) {
            return stringAttr(n.toString());
        }

        if (value instanceof Boolean b) {
            return stringAttr(b.toString());
        }

        // fallback: serializzazione semplice
        return stringAttr(value.toString());
    }

    private static MessageAttributeValue stringAttr(String value) {
        return MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(value)
                .build();
    }
}
