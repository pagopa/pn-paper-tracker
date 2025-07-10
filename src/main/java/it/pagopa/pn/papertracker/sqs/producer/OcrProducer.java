package it.pagopa.pn.papertracker.sqs.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sngular.apigenerator.asyncapi.business_model.model.event.Ocr_data_payloadDTO;
import io.awspring.cloud.sqs.operations.SendResult;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;


@Component
@Slf4j
@RequiredArgsConstructor
public class OcrProducer {

    private final SqsTemplate template;
    private final ObjectMapper objectMapper;

    /**
     * Metodo per inviare un messaggio a una coda SQS specificata.
     *
     * @param queueName Nome della coda SQS a cui inviare il messaggio.
     * @param message   Oggetto da inviare, che verrà serializzato in formato JSON.
     */
    public void send(String queueName, Ocr_data_payloadDTO message) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(message);
            log.info("Invio messaggio alla coda {}: {}", queueName, jsonMessage);
            CompletableFuture<SendResult<String>> result = template.sendAsync(to -> to.queue(queueName)
                    .payload(jsonMessage)
            );
            log.info("Risultato invio messaggio: {}", result);
        } catch (Exception e) {
            log.error("Errore serializzazione o invio JSON", e);
        }
    }

}