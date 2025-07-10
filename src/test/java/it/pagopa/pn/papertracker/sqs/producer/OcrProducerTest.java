package it.pagopa.pn.papertracker.sqs.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sngular.apigenerator.asyncapi.business_model.model.event.Ocr_data_payloadDTO;
import io.awspring.cloud.sqs.operations.SendResult;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OcrProducerTest {

    private SqsTemplate sqsTemplate;
    private ObjectMapper objectMapper;
    private OcrProducer ocrProducer;

    @BeforeEach
    void setUp() {
        sqsTemplate = Mockito.mock(SqsTemplate.class);
        objectMapper = Mockito.mock(ObjectMapper.class);
        ocrProducer = new OcrProducer(sqsTemplate, objectMapper);
    }

    @Test
    @DisplayName("Send message successfully to SQS queue")
    void sendMessageSuccessfully() throws JsonProcessingException {
        Ocr_data_payloadDTO message = Ocr_data_payloadDTO.builder().build();
        String queueName = "testQueue";
        String jsonMessage = "{\"key\":\"value\"}";

        when(objectMapper.writeValueAsString(message)).thenReturn(jsonMessage);
        when(sqsTemplate.sendAsync(any())).thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        ocrProducer.send(queueName, message);

        verify(objectMapper).writeValueAsString(message);
        verify(sqsTemplate).sendAsync(any());
    }

    @Test
    @DisplayName("Handle JSON serialization error")
    void handleJsonSerializationError() throws JsonProcessingException {
        Ocr_data_payloadDTO message = Ocr_data_payloadDTO.builder().build();
        String queueName = "testQueue";

        when(objectMapper.writeValueAsString(message)).thenThrow(new JsonProcessingException("Serialization error") {});

        ocrProducer.send(queueName, message);

        verify(objectMapper).writeValueAsString(message);
        verifyNoInteractions(sqsTemplate);
    }

    @Test
    @DisplayName("Handle SQS send failure")
    void handleSqsSendFailure() throws JsonProcessingException {
        Ocr_data_payloadDTO message = Ocr_data_payloadDTO.builder().build();
        String queueName = "testQueue";
        String jsonMessage = "{\"key\":\"value\"}";

        when(objectMapper.writeValueAsString(message)).thenReturn(jsonMessage);
        when(sqsTemplate.sendAsync(any())).thenThrow(new RuntimeException("SQS send error"));

        ocrProducer.send(queueName, message);

        verify(objectMapper).writeValueAsString(message);
        verify(sqsTemplate).sendAsync(any());
    }
}