package app.healthplus.api.controller;

import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 演示模式控制器。
 * <p>
 * 职责：提供一键加载预置演示数据的能力，方便新用户体验完整功能而无需上传真实数据。
 * 演示数据为预先准备好的示例 export.zip，存储在 MinIO/Loc alStorage 的 demo 目录下。
 * </p>
 * <p>
 * 对应 API 端点：{@code POST /api/v1/demo}
 * </p>
 */
@RestController
@RequestMapping("/api/v1")
public class DemoController {

    /** 预置演示数据的固定上传 ID（使用全 d 的 UUID 便于识别） */
    private static final UUID DEMO_UPLOAD_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    /** 演示文件在对象存储中的 key */
    private static final String DEMO_KEY = "demo/demo-export.zip";

    /**
     * 加载演示数据。
     * <p>
     * 返回演示数据的元信息，前端可直接使用这些信息跳转到报告页面，
     * 无需实际执行文件上传流程。
     * </p>
     *
     * @return 包含 uploadId、storageKey、uploadUrl 和 status 的 Map
     */
    @PostMapping("/demo")
    public Map<String, String> loadDemo() {
        return Map.of(
                "uploadId", DEMO_UPLOAD_ID.toString(),
                "storageKey", DEMO_KEY,
                "uploadUrl", DEMO_KEY,
                "status", "ready"
        );
    }
}
