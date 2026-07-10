package com.socialmonitor.platform.service;

import com.socialmonitor.common.error.ErrorCode;
import com.socialmonitor.common.exception.BusinessException;
import com.socialmonitor.platform.port.SocialPlatformAdapter;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class PlatformAdapterRegistry {

    private final Map<String, SocialPlatformAdapter> adapters;

    public PlatformAdapterRegistry(Collection<SocialPlatformAdapter> adapters) {
        this.adapters = adapters.stream()
                .collect(Collectors.toUnmodifiableMap(SocialPlatformAdapter::platformCode, Function.identity()));
    }

    public Collection<SocialPlatformAdapter> adapters() {
        return adapters.values();
    }

    public SocialPlatformAdapter getRequired(String platformCode) {
        SocialPlatformAdapter adapter = adapters.get(platformCode);
        if (adapter == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "No adapter registered for platform: " + platformCode);
        }
        return adapter;
    }
}

