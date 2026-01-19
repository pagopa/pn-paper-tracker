package it.pagopa.pn.papertracker.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessageHeaders;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MessageAttributeMapperTest {

    @Test
    void testFromHeaders_withStringValue() {
        // Arrange
        Map<String, Object> headerMap = new HashMap<>();
        headerMap.put("key1", "value1");
        MessageHeaders headers = new MessageHeaders(headerMap);

        // Act
        Map<String, MessageAttributeValue> result = MessageAttributeMapper.fromHeaders(headers);

        // Assert
        assertTrue(result.containsKey("key1"));
        assertEquals("String", result.get("key1").dataType());
        assertEquals("value1", result.get("key1").stringValue());
    }

    @Test
    void testFromHeaders_withNumberValue() {
        // Arrange
        Map<String, Object> headerMap = new HashMap<>();
        headerMap.put("intKey", 42);
        headerMap.put("longKey", 100L);
        headerMap.put("doubleKey", 3.14);
        MessageHeaders headers = new MessageHeaders(headerMap);

        // Act
        Map<String, MessageAttributeValue> result = MessageAttributeMapper.fromHeaders(headers);

        // Assert
        assertEquals("42", result.get("intKey").stringValue());
        assertEquals("100", result.get("longKey").stringValue());
        assertEquals("3.14", result.get("doubleKey").stringValue());
    }

    @Test
    void testFromHeaders_withBooleanValue() {
        // Arrange
        Map<String, Object> headerMap = new HashMap<>();
        headerMap.put("trueKey", true);
        headerMap.put("falseKey", false);
        MessageHeaders headers = new MessageHeaders(headerMap);

        // Act
        Map<String, MessageAttributeValue> result = MessageAttributeMapper.fromHeaders(headers);

        // Assert
        assertEquals("true", result.get("trueKey").stringValue());
        assertEquals("false", result.get("falseKey").stringValue());
    }

    @Test
    void testFromHeaders_withNullValue() {
        // Arrange
        Map<String, Object> headerMap = new HashMap<>();
        headerMap.put("nullKey", null);
        headerMap.put("validKey", "valid");
        MessageHeaders headers = new MessageHeaders(headerMap);

        // Act
        Map<String, MessageAttributeValue> result = MessageAttributeMapper.fromHeaders(headers);

        // Assert
        assertFalse(result.containsKey("nullKey"));
        assertTrue(result.containsKey("validKey"));
    }

    @Test
    void testFromHeaders_allAttributesHaveStringDataType() {
        // Arrange
        Map<String, Object> headerMap = new HashMap<>();
        headerMap.put("string", "value");
        headerMap.put("number", 42);
        headerMap.put("bool", true);
        MessageHeaders headers = new MessageHeaders(headerMap);

        // Act
        Map<String, MessageAttributeValue> result = MessageAttributeMapper.fromHeaders(headers);

        // Assert
        result.values().forEach(attr ->
                assertEquals("String", attr.dataType())
        );
    }
}