package app.healthplus.ai;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 专业健康知识库，支持双模式检索。
 * <p>本类是 AI 分析的核心知识来源，存储了覆盖 12 个健康类别的专业知识条目（共 85 条），
 * 并提供两种检索方式供 AI 回答健康问题时引用：</p>
 * <ul>
 *   <li><b>向量检索</b>（pgvector 余弦相似度）：通过 DeepSeek embedding 将问题向量化，
 *       在 PostgreSQL pgvector 中进行近似最近邻搜索，精度高但依赖 embedding 服务</li>
 *   <li><b>关键词检索</b>（快速回退）：基于分词和关键词匹配的本地检索，无需外部依赖，
 *       作为向量检索不可用时的兜底方案</li>
 * </ul>
 *
 * <h3>内置默认知识</h3>
 * <p>当外部知识库未加载时，自动使用 {@link #builtinDefaults()} 中的 9 条基础健康知识，
 * 涵盖睡眠、心率、HRV、步数、运动、BMI、呼吸频率等常见指标。</p>
 */
public class HealthKnowledgeBase {

    /**
     * 知识库条目。
     *
     * @param id       条目唯一标识（如 "sleep-1"、"heart-1"）
     * @param category 所属健康类别（如 "睡眠"、"心脏"、"运动"、"身体测量"）
     * @param title    条目标题
     * @param content  专业知识内容（含参考数值、标准范围等）
     * @param keywords 关键词列表，用于关键词检索模式
     */
    public record Entry(String id, String category, String title, String content, List<String> keywords) {}

    /** 知识条目列表（线程安全，支持运行时动态追加） */
    private final List<Entry> entries;

    /**
     * 向量检索回调函数。
     * <p>由配置类在 Bean 初始化后注入，接收 float[] embedding 返回最相似的知识条目。
     * 为 null 时表示向量检索不可用，将回退到关键词检索。</p>
     */
    private Function<float[], List<Entry>> vectorSearchFn;

    /**
     * 构造知识库实例。
     * <p>如果传入的条目列表为空，则自动加载内置默认知识。</p>
     *
     * @param entries 外部加载的知识条目列表（通常来自数据库）
     */
    public HealthKnowledgeBase(List<Entry> entries) {
        this.entries = new CopyOnWriteArrayList<>(entries.isEmpty() ? builtinDefaults() : entries);
        System.out.println("[HealthKnowledgeBase] Loaded " + this.entries.size() + " entries");
    }

    /** 无参构造，使用内置默认知识 */
    public HealthKnowledgeBase() { this(List.of()); }

    /**
     * 注册向量检索回调函数。
     * <p>由 Spring 配置类在 Bean 创建后注入，将 pgvector 检索逻辑绑定到本知识库。</p>
     *
     * @param fn 接收 embedding 向量、返回最相似条目列表的函数
     */
    public void setVectorSearch(Function<float[], List<Entry>> fn) { this.vectorSearchFn = fn; }

    /** 获取所有知识条目的不可变副本 */
    public List<Entry> getEntries() { return List.copyOf(entries); }

    /**
     * 带 embedding 的向量检索（主检索入口）。
     * <p>优先使用 pgvector 向量检索；若 embedding 为 null 或向量检索未注册，
     * 自动回退到关键词检索。</p>
     *
     * @param question 用户问题文本
     * @param category 可选的类别过滤（如 "睡眠"、"心脏"），null 表示不过滤
     * @param topK     返回的最大条目数
     * @param embedding 问题的向量表示（由 DeepSeek embedding 服务生成），可为 null
     * @return 按相关性排序的知识条目列表
     */
    public List<Entry> retrieve(String question, String category, int topK, float[] embedding) {
        if (embedding != null && vectorSearchFn != null && embedding.length > 0) {
            List<Entry> results = vectorSearchFn.apply(embedding);
            if (category != null) results = results.stream().filter(e -> e.category.equals(category)).toList();
            return results.stream().limit(topK).toList();
        }
        return retrieveKeyword(question, category, topK);
    }

    /**
     * 无 embedding 的向量检索（向后兼容）。
     * <p>直接回退到关键词检索。</p>
     */
    public List<Entry> retrieve(String question, String category, int topK) {
        return retrieve(question, category, topK, null);
    }

    /**
     * 关键词检索（本地快速回退方案）。
     * <p>将问题分词后，与每条知识的关键词和标题进行匹配评分，
     * 返回得分最高的 topK 条结果。</p>
     *
     * @param question 用户问题文本
     * @param category 可选的类别过滤
     * @param topK     返回的最大条目数
     * @return 按匹配得分降序排列的知识条目列表
     */
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

    /**
     * 按类别获取所有知识条目。
     *
     * @param category 健康类别名称（如 "睡眠"、"心脏"）
     * @return 该类别下的所有条目
     */
    public List<Entry> getByCategory(String category) {
        return entries.stream().filter(e -> e.category.equals(category)).collect(Collectors.toList());
    }

    /**
     * 将知识条目格式化为 AI prompt 参考上下文。
     * <p>输出格式为 "=== 专业健康知识参考 ===" 开头，每条以 "[标题] 内容" 形式排列。</p>
     *
     * @param entries 待格式化的知识条目列表
     * @return 格式化后的文本，可直接嵌入 AI prompt
     */
    public static String formatContext(List<Entry> entries) {
        if (entries.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("\n=== 专业健康知识参考 ===\n");
        for (Entry e : entries) sb.append("[").append(e.title()).append("] ").append(e.content()).append("\n");
        return sb.toString();
    }

    /**
     * 将 AI 意图映射到对应的知识库类别。
     * <p>用于根据用户问题的意图快速定位相关知识类别，缩小检索范围。</p>
     *
     * @param intent AI 识别的用户意图枚举
     * @return 对应的知识类别名称，若无匹配则返回 null（表示通用查询）
     */
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

    /**
     * 计算单条知识与分词列表的匹配得分。
     * <p>评分规则：关键词命中 +1 分/次，标题命中 +2 分/次。</p>
     */
    private double score(Entry entry, List<String> tokens) {
        double s = 0;
        for (String kw : entry.keywords())
            for (String tk : tokens) if (kw.contains(tk) || tk.contains(kw)) s += 1.0;
        for (String tk : tokens) if (entry.title().toLowerCase().contains(tk)) s += 2.0;
        return s;
    }

    /**
     * 将文本分词为小写 token 列表。
     * <p>按中英文标点和空白字符切分，过滤掉长度 <= 1 的短 token。</p>
     */
    private List<String> tokenize(String text) {
        return Arrays.stream(text.toLowerCase().split("[\\s，。！？、（）,.!?()]+"))
                .filter(t -> t.length() > 1).collect(Collectors.toList());
    }

    /**
     * 内置默认知识条目（9 条）。
     * <p>当数据库知识未加载时作为兜底数据，涵盖睡眠、心率、HRV、步数、
     * WHO 运动建议、BMI、呼吸频率、数据解读和免责声明。</p>
     */
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
