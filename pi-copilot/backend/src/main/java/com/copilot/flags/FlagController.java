package com.copilot.flags;

import com.copilot.persistence.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** Proxies the feature flag of the target application so the rollout console can flip it. */
@RestController
@RequestMapping("/api/flags")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class FlagController {

    private final AuditService audit;
    private final RestClient client = RestClient.create();

    public record FlagRequest(String targetBaseUrl, boolean encryption) {}

    @GetMapping
    public Map<?, ?> get(@RequestParam String targetBaseUrl) {
        return client.get().uri(targetBaseUrl + "/pi/flag").retrieve().body(Map.class);
    }

    @PostMapping
    public Map<?, ?> set(@RequestBody FlagRequest req) {
        Map<?, ?> resp = client.post().uri(req.targetBaseUrl() + "/pi/flag")
                .body(Map.of("encryption", req.encryption())).retrieve().body(Map.class);
        audit.log("FLAG_" + (req.encryption() ? "ON" : "OFF"), req.targetBaseUrl(), String.valueOf(resp));
        return resp;
    }
}
