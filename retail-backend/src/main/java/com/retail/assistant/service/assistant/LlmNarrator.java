package com.retail.assistant.service.assistant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Optional LLM layer. The deterministic engine computes the facts; the LLM may only rephrase them.
 * Disabled unless retail.ai.anthropic.api-key is set. Any failure falls back to the rule-based answer.
 */
@Component
public class LlmNarrator {

    private static final Logger log = LoggerFactory.getLogger(LlmNarrator.class);

    private static final String SYSTEM = """
            You are a retail operations assistant for a store manager.
            Answer ONLY from the FACTS provided. Never invent products, numbers, locations or orders.
            Keep every number exactly as given. Be concise: at most 5 sentences, then an action list if useful.
            If the facts do not answer the question, say so.""";

    private final String apiKey;
    private final String model;
    private final RestClient client;

    public LlmNarrator(@Value("${retail.ai.anthropic.api-key:}") String apiKey,
                       @Value("${retail.ai.anthropic.model:claude-sonnet-5-5}") String model,
                       @Value("${retail.ai.anthropic.base-url:https://api.anthropic.com}") String baseUrl,
                       @Value("${retail.ai.anthropic.timeout-seconds:15}") int timeoutSeconds) {
        this.apiKey = apiKey;
        this.model = model;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String model() {
        return model;
    }

    @SuppressWarnings("unchecked")
    public Optional<String> narrate(String question, String draftAnswer, String facts) {
        if (!enabled()) return Optional.empty();
        try {
            String prompt = "QUESTION:\n" + question + "\n\nFACTS:\n" + facts
                    + "\n\nDRAFT ANSWER (correct, may be rephrased):\n" + draftAnswer;
            Map<String, Object> body = Map.of(
                    "model", model,
                    "max_tokens", 600,
                    "system", SYSTEM,
                    "messages", List.of(Map.of("role", "user", "content", prompt)));
            Map<String, Object> resp = client.post()
                    .uri("/v1/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (resp == null) return Optional.empty();
            List<Map<String, Object>> content = (List<Map<String, Object>>) resp.get("content");
            if (content == null || content.isEmpty()) return Optional.empty();
            Object text = content.get(0).get("text");
            return text == null ? Optional.empty() : Optional.of(text.toString().trim());
        } catch (Exception e) {
            log.warn("LLM narration failed, using rule-based answer: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
