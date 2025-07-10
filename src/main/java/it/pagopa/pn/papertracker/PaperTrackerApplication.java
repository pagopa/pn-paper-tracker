package it.pagopa.pn.papertracker;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@RequiredArgsConstructor
public class PaperTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaperTrackerApplication.class, args);
    }

}