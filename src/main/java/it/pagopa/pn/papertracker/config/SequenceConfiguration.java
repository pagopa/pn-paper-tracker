package it.pagopa.pn.papertracker.config;

import it.pagopa.pn.papertracker.model.DocumentTypeEnum;
import it.pagopa.pn.papertracker.model.SequenceElement;
import lombok.Getter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Configuration
public class SequenceConfiguration {

    private static final String FINAL_EVENTS_GROUP = "finalEventsGroup";
    private static final String STOCK_EVENTS_GROUP = "stockEventsGroup";
    
    @Getter
    public enum SequenceDefinition {

        // AR

        RECRN005C("RECRN005C", Set.of(
                new SequenceElement("RECRN010", null, null),
                new SequenceElement("RECRN011", null, null),
                new SequenceElement("RECRN005A", null, FINAL_EVENTS_GROUP),
                new SequenceElement("RECRN005B", Set.of(DocumentTypeEnum.PLICO), FINAL_EVENTS_GROUP),
                new SequenceElement("RECRN005C", null, FINAL_EVENTS_GROUP)
        )),

        RECRN004C("RECRN004C", Set.of(
                new SequenceElement("RECRN010", null, null),
                new SequenceElement("RECRN011", null, null),
                new SequenceElement("RECRN004A", null, FINAL_EVENTS_GROUP),
                new SequenceElement("RECRN004B", Set.of(DocumentTypeEnum.PLICO), FINAL_EVENTS_GROUP),
                new SequenceElement("RECRN004C", null, FINAL_EVENTS_GROUP)
        )),

        RECRN003C("RECRN003C", Set.of(
                new SequenceElement("RECRN010", null, null),
                new SequenceElement("RECRN011", null, null),
                new SequenceElement("RECRN003A", null, FINAL_EVENTS_GROUP),
                new SequenceElement("RECRN003B", Set.of(DocumentTypeEnum.AR), FINAL_EVENTS_GROUP),
                new SequenceElement("RECRN003C", null, FINAL_EVENTS_GROUP)
        )),

        RECRN002F("RECRN002F", Set.of(
                new SequenceElement("RECRN002D", null, FINAL_EVENTS_GROUP),
                new SequenceElement("RECRN002E", Set.of(DocumentTypeEnum.PLICO), FINAL_EVENTS_GROUP),
                new SequenceElement("RECRN002F", null, FINAL_EVENTS_GROUP)
        )),

        RECRN002C("RECRN002C", Set.of(
                new SequenceElement("RECRN002A", null, FINAL_EVENTS_GROUP),
                new SequenceElement("RECRN002B", Set.of(DocumentTypeEnum.PLICO), FINAL_EVENTS_GROUP),
                new SequenceElement("RECRN002C", null, FINAL_EVENTS_GROUP)
        )),

        RECRN001C("RECRN001C", Set.of(
                new SequenceElement("RECRN001A", null, FINAL_EVENTS_GROUP),
                new SequenceElement("RECRN001B", Set.of(DocumentTypeEnum.AR), FINAL_EVENTS_GROUP),
                new SequenceElement("RECRN001C", null, FINAL_EVENTS_GROUP)
        )),

        // RIR

        RECRI003C("RECRI003C", Set.of(
                new SequenceElement("RECRI001", null, null),
                new SequenceElement("RECRI002", null, null),
                new SequenceElement("RECRI003A", null, FINAL_EVENTS_GROUP),
                new SequenceElement("RECRI003B", Set.of(DocumentTypeEnum.AR), FINAL_EVENTS_GROUP),
                new SequenceElement("RECRI003C", null, FINAL_EVENTS_GROUP)
                )),

        RECRI004C("RECRI004C", Set.of(
                new SequenceElement("RECRI001", null, null),
                new SequenceElement("RECRI002", null, null),
                new SequenceElement("RECRI004A", null, FINAL_EVENTS_GROUP),
                new SequenceElement("RECRI004B",  Set.of(DocumentTypeEnum.PLICO), FINAL_EVENTS_GROUP),
                new SequenceElement("RECRI004C", null, FINAL_EVENTS_GROUP)
                )),

        // 890

        RECAG001C("RECAG001C", Set.of(
                new SequenceElement("RECAG001A", null, FINAL_EVENTS_GROUP),
                new SequenceElement("RECAG001B", Set.of(DocumentTypeEnum._23L), FINAL_EVENTS_GROUP),
                new SequenceElement("RECAG001C", null, FINAL_EVENTS_GROUP)
        )),

        RECAG002C("RECAG002C", Set.of(
                new SequenceElement("RECAG002A", null, FINAL_EVENTS_GROUP),
                new SequenceElement("RECAG002B", Set.of(DocumentTypeEnum._23L), FINAL_EVENTS_GROUP),
                new SequenceElement("RECAG002C", null, FINAL_EVENTS_GROUP)
        )),

        RECAG003C("RECAG003C", Set.of(
                new SequenceElement("RECAG003A", null, FINAL_EVENTS_GROUP),
                new SequenceElement("RECAG003B", Set.of(DocumentTypeEnum.PLICO), FINAL_EVENTS_GROUP),
                new SequenceElement("RECAG003C", null, FINAL_EVENTS_GROUP)
        )),

        RECAG003F("RECAG003F", Set.of(
                new SequenceElement("RECAG003D", null, FINAL_EVENTS_GROUP),
                new SequenceElement("RECAG003E", Set.of(DocumentTypeEnum.PLICO), FINAL_EVENTS_GROUP),
                new SequenceElement("RECAG003F", null, FINAL_EVENTS_GROUP)
        )),

        RECAG005C("RECAG005C", Set.of(
                new SequenceElement("RECAG010", null, null),
                new SequenceElement("RECAG011A", null, STOCK_EVENTS_GROUP),
                new SequenceElement("RECAG011B", null, STOCK_EVENTS_GROUP),
                new SequenceElement("RECAG005A", null, FINAL_EVENTS_GROUP),
                new SequenceElement("RECAG005B", Set.of(DocumentTypeEnum.ARCAD, DocumentTypeEnum._23L), FINAL_EVENTS_GROUP),
                new SequenceElement("RECAG005C", null, FINAL_EVENTS_GROUP)
        )),

        RECAG006C("RECAG006C", Set.of(
                new SequenceElement("RECAG010", null, null),
                new SequenceElement("RECAG011A", null, STOCK_EVENTS_GROUP),
                new SequenceElement("RECAG011B", null, STOCK_EVENTS_GROUP),
                new SequenceElement("RECAG006A", null, FINAL_EVENTS_GROUP),
                new SequenceElement("RECAG006B", Set.of(DocumentTypeEnum.ARCAD, DocumentTypeEnum._23L), FINAL_EVENTS_GROUP),
                new SequenceElement("RECAG006C", null, FINAL_EVENTS_GROUP)
        )),

        RECAG007C("RECAG007C", Set.of(
                new SequenceElement("RECAG010", null, null),
                new SequenceElement("RECAG011A", null, STOCK_EVENTS_GROUP),
                new SequenceElement("RECAG011B", null, STOCK_EVENTS_GROUP),
                new SequenceElement("RECAG007A", null, FINAL_EVENTS_GROUP),
                new SequenceElement("RECAG007B", Set.of(DocumentTypeEnum.PLICO, DocumentTypeEnum.ARCAD, DocumentTypeEnum._23L), FINAL_EVENTS_GROUP),
                new SequenceElement("RECAG007C", null, FINAL_EVENTS_GROUP)
        )),

        RECAG008C("RECAG008C", Set.of(
                new SequenceElement("RECAG010", null, null),
                new SequenceElement("RECAG011A", null, STOCK_EVENTS_GROUP),
                new SequenceElement("RECAG011B", null, STOCK_EVENTS_GROUP),
                new SequenceElement("RECAG008A", null, FINAL_EVENTS_GROUP),
                new SequenceElement("RECAG008B", Set.of(), FINAL_EVENTS_GROUP),
                new SequenceElement("RECAG008C", null, FINAL_EVENTS_GROUP)
        ));

        private final String key;
        private final Set<SequenceElement> sequence;

        SequenceDefinition(String key, Set<SequenceElement> sequence) {
            this.key = key;
            this.sequence = sequence;
        }

        public static SequenceDefinition fromKey(String key) {
            return Stream.of(values())
                    .filter(definition -> definition.key.equals(key))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No sequence found for key: " + key));
        }
    }

    @Bean
    public Map<String, Set<SequenceElement>> sequenceMap() {
        return Stream.of(SequenceDefinition.values())
                .collect(Collectors.toMap(
                        SequenceDefinition::getKey,
                        SequenceDefinition::getSequence
                ));
    }
}