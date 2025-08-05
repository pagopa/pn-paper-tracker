package it.pagopa.pn.papertracker;

import it.pagopa.pn.papertracker.middleware.queue.producer.ExternalChannelOutputsMomProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
public abstract class BaseTest {

    @MockBean
    private ExternalChannelOutputsMomProducer externalChannelOutputsMomProducer;

    @Slf4j
    @SpringBootTest
    @ActiveProfiles("test")
    @Import(LocalStackTestConfig.class)
    @TestPropertySource(properties = {
            "spring.cloud.aws.sqs.listener.acknowledgement-mode=manual",
            "spring.cloud.aws.sqs.listener.poll-timeout=10s",
            "spring.cloud.aws.sqs.listener.max-messages-per-poll=10",
            "spring.cloud.aws.sqs.listener.queue-stop-timeout=30s",
            "spring.cloud.aws.sqs.listener.back-off-time=1s",
            "spring.cloud.aws.sqs.listener.max-concurrent-messages=10"
    })
    public static class WithLocalStack {
    }
}