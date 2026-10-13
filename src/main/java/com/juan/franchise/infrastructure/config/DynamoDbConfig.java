package com.juan.franchise.infrastructure.config;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClientBuilder;

@Configuration
public class DynamoDbConfig {

    @Bean(destroyMethod = "close")
    DynamoDbAsyncClient dynamoDbAsyncClient(
            @Value("${dynamodb.endpoint:}") String endpoint,
            @Value("${aws.region}") String region) {

        DynamoDbAsyncClientBuilder builder = DynamoDbAsyncClient.builder().region(Region.of(region));

        if (StringUtils.hasText(endpoint)) {
            // Local mode (DynamoDB Local): custom endpoint + dummy credentials.
            builder.endpointOverride(URI.create(endpoint))
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")));
        } else {
            // AWS mode: ECS task role in the cloud, CLI profile/env vars on a dev machine.
            AwsCredentialsProvider provider = DefaultCredentialsProvider.builder().build();
            builder.credentialsProvider(provider);
        }
        return builder.build();
    }
}
