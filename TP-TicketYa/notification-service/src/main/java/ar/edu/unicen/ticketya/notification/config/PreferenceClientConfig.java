package ar.edu.unicen.ticketya.notification.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class PreferenceClientConfig {

    @Bean
    public RestClient preferenceRestClient(
            RestClient.Builder builder,
            @Value("${notification.preferences.base-url:http://localhost:8084}") String baseUrl,
            @Value("${notification.preferences.connect-timeout:2s}") Duration connectTimeout,
            @Value("${notification.preferences.read-timeout:3s}") Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) connectTimeout.toMillis());
        factory.setReadTimeout((int) readTimeout.toMillis());
        return builder.baseUrl(baseUrl).requestFactory(factory).build();
    }
}
