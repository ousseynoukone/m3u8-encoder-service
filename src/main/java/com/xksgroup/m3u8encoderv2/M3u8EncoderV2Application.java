package com.xksgroup.m3u8encoderv2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableDiscoveryClient
public class M3u8EncoderV2Application {
    public static void main(String[] args) {
        SpringApplication.run(M3u8EncoderV2Application.class, args);
    }
}


