package it.pagopa.pn.papertracker.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility per il masking dei dati sensibili negli oggetti destinati al logging.
 * <p>
 * La classe converte un oggetto Java in un albero JSON e maschera ricorsivamente
 * tutti i campi indicati, a qualsiasi profondità della struttura.
 */
@Component
@RequiredArgsConstructor
public class LogUtility {

    private static final String MASK = "***";

    private final ObjectMapper objectMapper;

    /**
     * Maschera i campi sensibili di un oggetto Java, identificati per nome,
     * indipendentemente dal livello di annidamento.
     *
     * <p>Il metodo:
     * <ul>
     *   <li>converte l'oggetto in un albero JSON</li>
     *   <li>attraversa ricorsivamente la struttura</li>
     *   <li>sostituisce il valore dei campi indicati con {@value #MASK}</li>
     * </ul>
     *
     * @param obj        oggetto da loggare; se {@code null} viene restituita la stringa {@code "null"}
     * @param fieldNames nomi dei campi da mascherare (case-sensitive)
     * @return rappresentazione JSON dell'oggetto con i campi sensibili mascherati
     */
    public String maskSensitiveData(Object obj, String... fieldNames) {
        if (obj == null) {
            return "null";
        }

        try {
            JsonNode rootNode = objectMapper.valueToTree(obj);
            Set<String> fieldsToMask = new HashSet<>(Arrays.asList(fieldNames));

            maskRecursively(rootNode, fieldsToMask);

            return objectMapper.writeValueAsString(rootNode);

        } catch (Exception e) {
            // Fallback sicuro in caso di errori di serializzazione
            return obj.toString();
        }
    }

    /**
     * Visita ricorsivamente un {@link JsonNode} e maschera i campi
     * il cui nome è presente nell'insieme {@code fieldsToMask}.
     *
     * <p>
     * Il metodo supporta:
     * <ul>
     *   <li>oggetti JSON</li>
     *   <li>array JSON</li>
     *   <li>strutture annidate di qualsiasi profondità</li>
     * </ul>
     *
     * @param node          nodo corrente dell'albero JSON
     * @param fieldsToMask  insieme dei nomi dei campi da mascherare
     */
    private void maskRecursively(JsonNode node, Set<String> fieldsToMask) {

        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;

            objectNode.fieldNames().forEachRemaining(fieldName -> {
                JsonNode child = objectNode.get(fieldName);

                if (fieldsToMask.contains(fieldName)) {
                    objectNode.set(fieldName, objectMapper.getNodeFactory().textNode(MASK));
                } else {
                    maskRecursively(child, fieldsToMask);
                }
            });

        } else if (node.isArray()) {
            for (JsonNode element : node) {
                maskRecursively(element, fieldsToMask);
            }
        }
    }
}