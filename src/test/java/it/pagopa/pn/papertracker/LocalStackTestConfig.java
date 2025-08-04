package it.pagopa.pn.papertracker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

import static org.testcontainers.containers.localstack.LocalStackContainer.Service.DYNAMODB;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS;

@TestConfiguration
@Slf4j
public class LocalStackTestConfig {

    static LocalStackContainer localStack =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:1.0.4"))
                    .withServices(DYNAMODB, SQS)
                    .withClasspathResourceMapping("testcontainers/init.sh",
                            "/docker-entrypoint-initaws.d/make-storages.sh", BindMode.READ_ONLY)
                    .withClasspathResourceMapping("testcontainers/credentials",
                            "/root/.aws/credentials", BindMode.READ_ONLY)
                    .withNetworkAliases("localstack")
                    .withNetwork(Network.builder().build())
                    .withEnv("DISABLE_SSL_CERT_VALIDATION", "1")
                    .withEnv("SKIP_SSL_CERT_DOWNLOAD", "1")
                    .withEnv("USE_SSL", "false")
                    .withEnv("REQUESTS_CA_BUNDLE", "")
                    .withEnv("CURL_CA_BUNDLE", "")
                    .withEnv("DEBUG", "1")
                    .waitingFor(Wait.forLogMessage(".*Initialization terminated.*", 1)
                            .withStartupTimeout(Duration.ofSeconds(180)));

    static {
        localStack.start();

        // Set system properties for Spring Cloud AWS
        System.setProperty("spring.cloud.aws.credentials.access-key", localStack.getAccessKey());
        System.setProperty("spring.cloud.aws.credentials.secret-key", localStack.getSecretKey());
        System.setProperty("spring.cloud.aws.region.static", localStack.getRegion());
        System.setProperty("spring.cloud.aws.sqs.endpoint", localStack.getEndpointOverride(SQS).toString());
        System.setProperty("spring.cloud.aws.endpoint", localStack.getEndpointOverride(SQS).toString());

        // Legacy properties for backward compatibility
        System.setProperty("aws.endpoint-url", localStack.getEndpointOverride(DYNAMODB).toString());
        System.setProperty("aws.region", localStack.getRegion());

        try {
            System.setProperty("aws.sharedCredentialsFile",
                    new ClassPathResource("testcontainers/credentials").getFile().getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Bean
    @Primary
    public AwsCredentialsProvider awsCredentialsProvider() {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(localStack.getAccessKey(), localStack.getSecretKey())
        );
    }

    @Bean
    @Primary
    public SqsClient sqsClient() {
        return SqsClient.builder()
                .endpointOverride(localStack.getEndpointOverride(SQS))
                .region(Region.of(localStack.getRegion()))
                .credentialsProvider(awsCredentialsProvider())
                .build();
    }

    @Bean
    @Primary
    public DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
                .endpointOverride(localStack.getEndpointOverride(DYNAMODB))
                .region(Region.of(localStack.getRegion()))
                .credentialsProvider(awsCredentialsProvider())
                .build();
    }
}