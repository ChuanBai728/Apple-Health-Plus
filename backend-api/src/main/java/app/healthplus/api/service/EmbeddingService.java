package app.healthplus.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Calls DeepSeek text-embedding API to generate vector embeddings.
 * Model: text-embedding-3-small (1024 dimensions, OpenAI-compatible).
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final RestClient restClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;
    private final String model;

    public EmbeddingService(
            @Value("${spring.ai.openai.api-key}") String apiKey,
            @Value("${spring.ai.openai.embedding.model:text-embedding-3-small}") String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.deepseek.com/v1")
                .build();
    }

    /**
     * Generate a single embedding vector for a text.
     */
    public float[] embed(String text) {
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "model", model,
                    "input", text
            ));
            String response = restClient.post()
                    .uri("/embeddings")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode node = mapper.readTree(response);
            JsonNode embedding = node.path("data").get(0).path("embedding");
            float[] vec = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                vec[i] = (float) embedding.get(i).asDouble();
            }
            return vec;
        } catch (Exception e) {
            log.error("Failed to generate embedding", e);
            return new float[1024]; // fallback: zero vector
        }
    }

    /**
     * Format a float array into pgvector-compatible string: [0.1,0.2,...]
     */
    public static String toPgVector(float[] vec) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vec[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
