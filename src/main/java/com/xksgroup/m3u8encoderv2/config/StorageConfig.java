package com.xksgroup.m3u8encoderv2.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class StorageConfig {

    @Bean
    @Profile("r2") // run with: --spring.profiles.active=r2
    public S3Client r2Client(

            @Value("${r2.accessKeyId}") String accessKey,
            @Value("${r2.secretAccessKey}") String secret,
            @Value("${r2.endpoint}") String endpoint

            ) {

        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1) // required by SDK; R2 ignores it
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(false)
                        .build())
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secret)))
                .build();
    }

    @Bean
    @Profile("r2")
    public S3Presigner r2Presigner(
            @Value("${r2.accessKeyId}") String accessKey,
            @Value("${r2.secretAccessKey}") String secret,
            @Value("${r2.endpoint}") String endpoint
    ) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secret)))
                .build();
    }

    @Bean
    @Profile("aws") // run with: --spring.profiles.active=aws
    public S3Client awsClient(
            @Value("${aws.region}") String region,
            @Value("${aws.accessKeyId}") String accessKey,
            @Value("${aws.secretAccessKey}") String secret
    ) {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secret)))
                .build();
    }

    @Bean
    @Profile("aws")
    public S3Presigner awsPresigner(
            @Value("${aws.region}") String region,
            @Value("${aws.accessKeyId}") String accessKey,
            @Value("${aws.secretAccessKey}") String secret
    ) {
        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secret)))
                .build();
    }
}
