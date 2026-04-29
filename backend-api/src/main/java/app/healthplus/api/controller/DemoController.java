package app.healthplus.api.controller;

import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class DemoController {

    private static final UUID DEMO_UPLOAD_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final String DEMO_KEY = "demo/demo-export.zip";

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
