package com.kubiki.metis.config;

import com.kubiki.palamedes.grpc.ReasonerServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcClientConfig {

    private ManagedChannel channel;

    @Bean
    public ManagedChannel palamedesChannel(MetisProperties properties) {
        String host = properties.palamedes().host();
        int port = properties.palamedes().port();
        channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        return channel;
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
        }
    }

    @Bean
    public ReasonerServiceGrpc.ReasonerServiceBlockingStub reasonerServiceStub(
            ManagedChannel palamedesChannel) {
        return ReasonerServiceGrpc.newBlockingStub(palamedesChannel);
    }
}
