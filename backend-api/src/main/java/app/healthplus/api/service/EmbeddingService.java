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
 * 文本向量嵌入服务 —— 调用 DeepSeek API 将文本转换为高维向量。
 *
 * <h3>职责</h3>
 * <p>将健康知识库中的文本（如健康建议、医学知识）转换为 1024 维的浮点向量，
 * 用于 pgvector 向量相似度检索，支撑 RAG（检索增强生成）的语义搜索能力。</p>
 *
 * <h3>设计思路</h3>
 * <ul>
 *   <li>使用 DeepSeek 的 OpenAI 兼容接口（/v1/embeddings），模型为 text-embedding-3-small</li>
 *   <li>通过 Spring {@link RestClient} 发起 HTTP 请求，避免引入额外的 SDK 依赖</li>
 *   <li>容错设计：API 调用失败时返回零向量，避免阻塞上层业务流程</li>
 *   <li>提供静态工具方法 {@link #toPgVector} 将向量转为 pgvector 库所需的字符串格式</li>
 * </ul>
 *
 * @see app.healthplus.sharedai.service.HealthKnowledgeBase
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final RestClient restClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;
    private final String model;

    /**
     * 构造注入配置参数并初始化 REST 客户端。
     *
     * @param apiKey DeepSeek API 密钥，从配置项 {@code spring.ai.openai.api-key} 读取
     * @param model  嵌入模型名称，默认为 text-embedding-3-small（1024 维）
     */
    public EmbeddingService(
            @Value("${spring.ai.openai.api-key}") String apiKey,
            @Value("${spring.ai.openai.embedding.model:text-embedding-3-small}") String model) {
        this.apiKey = apiKey;
        this.model = model;
        // DeepSeek 提供 OpenAI 兼容的 API 端点
        this.restClient = RestClient.builder()
                .baseUrl("https://api.deepseek.com/v1")
                .build();
    }

    /**
     * 为单段文本生成向量嵌入。
     * <p>调用 DeepSeek /v1/embeddings 接口，将文本编码为 1024 维的浮点向量。</p>
     *
     * @param text 待嵌入的文本内容
     * @return 1024 维的浮点向量；API 调用失败时返回全零向量（降级策略）
     */
    public float[] embed(String text) {
        try {
            // 构造 OpenAI 兼容格式的请求体
            String body = mapper.writeValueAsString(Map.of(
                    "model", model,
                    "input", text
            ));

            // 发送 POST 请求到 /embeddings 端点
            String response = restClient.post()
                    .uri("/embeddings")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            // 解析响应 JSON：data[0].embedding 是浮点数组
            JsonNode node = mapper.readTree(response);
            JsonNode embedding = node.path("data").get(0).path("embedding");

            // JSON 中的 double 精度足够，转为 float 以节省存储空间
            float[] vec = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                vec[i] = (float) embedding.get(i).asDouble();
            }
            return vec;
        } catch (Exception e) {
            // 降级策略：返回零向量，避免因 API 故障阻塞知识库初始化等流程
            // 零向量与任何向量的余弦相似度为 0，不会被检索命中
            log.error("Failed to generate embedding", e);
            return new float[1024]; // fallback: zero vector
        }
    }

    /**
     * 将浮点向量数组转换为 pgvector 兼容的字符串格式。
     * <p>pgvector 要求向量以 {@code [0.1,0.2,...]} 的方括号逗号分隔格式存储，
     * 此方法用于将 Java 数组序列化为该格式，可直接嵌入 SQL 语句。</p>
     *
     * @param vec 浮点向量数组
     * @return pgvector 格式的字符串，如 "[0.123,0.456,0.789]"
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
