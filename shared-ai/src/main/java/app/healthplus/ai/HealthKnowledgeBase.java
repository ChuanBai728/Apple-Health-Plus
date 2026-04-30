package app.healthplus.ai;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Professional health knowledge base with dual retrieval:
 * - Vector-based (pgvector cosine similarity, requires embedding)
 * - Keyword-based (fast fallback, no dependency)
 */
public class HealthKnowledgeBase {

    public record Entry(String id, String category, String title, String content, List<String> keywords) {}

    private final List<Entry> entries;
    private Function<float[], List<Entry>> vectorSearchFn;

    public HealthKnowledgeBase(List<Entry> entries) {
        this.entries = new CopyOnWriteArrayList<>(entries.isEmpty() ? builtinDefaults() : entries);
        System.out.println("[HealthKnowledgeBase] Loaded " + this.entries.size() + " entries");
    }

    public HealthKnowledgeBase() { this(List.of()); }

    /** Register JDBC-backed pgvector search callback — set by config after bean creation */
    public void setVectorSearch(Function<float[], List<Entry>> fn) { this.vectorSearchFn = fn; }

    public List<Entry> getEntries() { return List.copyOf(entries); }

    /** Vector-based retrieval — uses pgvector if available, falls back to keyword */
    public List<Entry> retrieve(String question, String category, int topK, float[] embedding) {
        if (embedding != null && vectorSearchFn != null && embedding.length > 0) {
            List<Entry> results = vectorSearchFn.apply(embedding);
            if (category != null) results = results.stream().filter(e -> e.category.equals(category)).toList();
            return results.stream().limit(topK).toList();
        }
        return retrieveKeyword(question, category, topK);
    }

    /** Backward-compatible: no embedding → keyword search */
    public List<Entry> retrieve(String question, String category, int topK) {
        return retrieve(question, category, topK, null);
    }

    /** Keyword-based retrieval (legacy fallback) */
    public List<Entry> retrieveKeyword(String question, String category, int topK) {
        if (question == null || question.isBlank()) {
            return category != null ? getByCategory(category) : entries.stream().limit(topK).toList();
        }
        List<String> tokens = tokenize(question);
        return entries.stream()
            .filter(e -> category == null || e.category.equals(category))
            .map(e -> new AbstractMap.SimpleEntry<>(e, score(e, tokens)))
            .filter(p -> p.getValue() > 0)
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
            .limit(topK)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    public List<Entry> getByCategory(String category) {
        return entries.stream().filter(e -> e.category.equals(category)).collect(Collectors.toList());
    }

    public static String formatContext(List<Entry> entries) {
        if (entries.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("\n=== 专业健康知识参考 ===\n");
        for (Entry e : entries) sb.append("[").append(e.title()).append("] ").append(e.content()).append("\n");
        return sb.toString();
    }

    public static String intentToCategory(AiIntent intent) {
        return switch (intent) {
            case SLEEP_ANALYSIS -> "睡眠";
            case HEART_CARDIOVASCULAR -> "心脏";
            case ACTIVITY_TREND -> "运动";
            case WEIGHT_CHANGE -> "身体测量";
            case RECOVERY_ANALYSIS -> "运动";
            case RISK_SIGNAL -> "心脏";
            default -> null;
        };
    }

    private double score(Entry entry, List<String> tokens) {
        double s = 0;
        for (String kw : entry.keywords())
            for (String tk : tokens) if (kw.contains(tk) || tk.contains(kw)) s += 1.0;
        for (String tk : tokens) if (entry.title().toLowerCase().contains(tk)) s += 2.0;
        return s;
    }

    private List<String> tokenize(String text) {
        return Arrays.stream(text.toLowerCase().split("[\\s，。！？、（）,.!?()]+"))
                .filter(t -> t.length() > 1).collect(Collectors.toList());
    }

    private static List<Entry> builtinDefaults() {
        return List.of(
            new Entry("sleep-1","睡眠","睡眠时长与健康","成人推荐7-9h（AASM）。<6h与心血管风险+48%相关。",List.of("sleep","睡眠","时长")),
            new Entry("heart-1","心脏","静息心率参考","成人60-100bpm。运动者40-60。>85与死亡率增加相关。",List.of("heart","心率","rhr")),
            new Entry("heart-2","心脏","HRV SDNN","成人20-70ms，健康年轻人40-100ms。↓>30%基线需关注。",List.of("heart","HRV","sdnn")),
            new Entry("activity-1","运动","每日步数",">10,000步理想。>8,000显著降低死亡率。<5,000久坐。",List.of("activity","步数","steps")),
            new Entry("activity-2","运动","WHO运动建议","每周≥150min中等或75min高强度有氧+2次力量训练。",List.of("activity","运动","WHO")),
            new Entry("body-1","身体测量","BMI参考","18.5-24.9健康。腰围（男>90女>85cm）更预测代谢风险。",List.of("body","BMI","体重")),
            new Entry("vital-1","生命体征","呼吸频率","成人12-20次/分。运动员8-12。>20可能与焦虑/感染相关。",List.of("vital","呼吸","respiratory")),
            new Entry("general-1","通用","数据解读","Apple Watch为消费级监测，非医疗诊断。趋势>单点数值。",List.of("general","Apple","精度")),
            new Entry("general-2","通用","免责声明","本分析仅供健康参考，不构成医疗诊断。请咨询执业医师。",List.of("general","disclaimer","免责"))
        );
    }
}
