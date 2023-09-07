package com.lkd;

import com.lkd.emq.MqttProducer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.circuitbreaker.EnableCircuitBreaker;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.servlet.HandlerExceptionResolver;


@SpringBootApplication
@EnableDiscoveryClient
@EnableTransactionManagement
@EnableCircuitBreaker
@EnableConfigurationProperties
@EnableScheduling
@EnableFeignClients
@EnableAsync
public class ProductionServiceApplication{
    @Bean
    public HandlerExceptionResolver sentryExceptionResolver() {
        return new io.sentry.spring.SentryExceptionResolver();
    }
    public static void main(String[] args) {
        ConfigurableApplicationContext run = SpringApplication.run(ProductionServiceApplication.class, args);
        MqttProducer mqttProducer = run.getBean(MqttProducer.class);
        mqttProducer.send("lkdtest","{'name':'abc'}");
    }
}
