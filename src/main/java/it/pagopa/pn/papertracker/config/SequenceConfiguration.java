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

    @Getter
    public enum SequenceDefinition {

        RECRN005C("RECRN005C", Set.of(
                new SequenceElement("RECRN0010", null, false, null),
                new SequenceElement("RECRN0011", null, false, null),
                new SequenceElement("RECRN005A", null, false, "1"),
                new SequenceElement("RECRN005B", Set.of(DocumentTypeEnum.PLICO), false, "1"),
                new SequenceElement("RECRN005C", null, false, "1")
        )),

        RECRN004C("RECRN004C", Set.of(
                new SequenceElement("RECRN0010", null, false, null),
                new SequenceElement("RECRN0011", null, false, null),
                new SequenceElement("RECRN004A", null, false, "1"),
                new SequenceElement("RECRN004B", Set.of(DocumentTypeEnum.PLICO), false, "1"),
                new SequenceElement("RECRN004C", null, false, "1")
        )),

        RECRN003C("RECRN003C", Set.of(
                new SequenceElement("RECRN0010", null, false, null),
                new SequenceElement("RECRN0011", null, false, null),
                new SequenceElement("RECRN003A", null, false, "1"),
                new SequenceElement("RECRN003B", Set.of(DocumentTypeEnum.AR), false, "1"),
                new SequenceElement("RECRN003C", null, false, "1")
        )),

        RECRN002F("RECRN002F", Set.of(
                new SequenceElement("RECRN002D", null, false, "1"),
                new SequenceElement("RECRN002E", Set.of(DocumentTypeEnum.AR, DocumentTypeEnum.INDAGINE), true, "1"),
                new SequenceElement("RECRN002F", null, false, "1")
        )),

        RECRN002C("RECRN002C", Set.of(
                new SequenceElement("RECRN002A", null, false, "1"),
                new SequenceElement("RECRN002B", Set.of(DocumentTypeEnum.PLICO), false, "1"),
                new SequenceElement("RECRN002C", null, false, "1")
        )),

        RECRN001C("RECRN001C", Set.of(
                new SequenceElement("RECRN001A", null, false, "1"),
                new SequenceElement("RECRN001B", Set.of(DocumentTypeEnum.AR), false, "1"),
                new SequenceElement("RECRN001C", null, false, "1")
        )),

        RECRN006("RECRN006", Set.of(
                new SequenceElement("RECRN0010", null, true, "1"),
                new SequenceElement("RECRN0011", null, true, "1"),
                new SequenceElement("RECRN006", null, false, "1")
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