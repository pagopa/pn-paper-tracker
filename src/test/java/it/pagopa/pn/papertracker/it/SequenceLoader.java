package it.pagopa.pn.papertracker.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.pagopa.pn.papertracker.it.model.ProductTestCase;
import org.junit.jupiter.params.provider.Arguments;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class SequenceLoader {

    public static Stream<Arguments> loadScenarios(URI... uriList) throws Exception {

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

        return Stream.of(uriList)
                .flatMap(uri -> {
                    try {
                        Path dir = Path.of(uri);
                        return Files.walk(dir)
                                .filter(p -> p.toString().endsWith(".json"))
                                .sorted()
                                .map(path -> {
                                    try {
                                        String raw = Files.readString(path);
                                        ProductTestCase scenario = mapper.readValue(raw, ProductTestCase.class);
                                        return Arguments.of(path.getFileName().toString(), scenario);
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                });
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }
}
