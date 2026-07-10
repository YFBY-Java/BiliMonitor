package com.socialmonitor.platform.controller;

import com.socialmonitor.common.response.ApiResponse;
import com.socialmonitor.platform.enums.PlatformCapability;
import com.socialmonitor.platform.port.SocialPlatformAdapter;
import com.socialmonitor.platform.service.PlatformAdapterRegistry;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platforms")
public class PlatformController {

    private final PlatformAdapterRegistry adapterRegistry;

    public PlatformController(PlatformAdapterRegistry adapterRegistry) {
        this.adapterRegistry = adapterRegistry;
    }

    @GetMapping("/adapters")
    public ApiResponse<List<AdapterView>> adapters() {
        List<AdapterView> adapters = adapterRegistry.adapters().stream()
                .map(adapter -> new AdapterView(adapter.platformCode(), adapter.capabilities()))
                .sorted(Comparator.comparing(AdapterView::platformCode))
                .toList();
        return ApiResponse.ok(adapters);
    }

    public record AdapterView(String platformCode, Set<PlatformCapability> capabilities) {
    }
}

