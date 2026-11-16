package ar.edu.unicen.ticketya.catalog.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InternalToken {

    private final String expected;

    public InternalToken(@Value("${catalog.internal-token:}") String expected) {
        this.expected = expected;
    }

    public void require(String provided) {
        if (provided == null || expected == null || expected.isBlank()
                || !MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8),
                        expected.getBytes(StandardCharsets.UTF_8))) {
            throw ApiException.unauthorized("Falta o es inválido el encabezado X-Internal-Token");
        }
    }
}