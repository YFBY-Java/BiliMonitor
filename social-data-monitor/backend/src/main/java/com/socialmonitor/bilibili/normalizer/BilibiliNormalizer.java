package com.socialmonitor.bilibili.normalizer;

import com.socialmonitor.ingestion.normalizer.PlatformDataNormalizer;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class BilibiliNormalizer implements PlatformDataNormalizer {

    @Override
    public String platformCode() {
        return "bilibili";
    }

    @Override
    public String supportsDataType() {
        return "account";
    }

    @Override
    public Map<String, Object> normalize(Map<String, Object> rawData) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("platformCode", platformCode());
        normalized.put("externalId", rawData.get("externalAccountId"));
        normalized.put("displayName", rawData.get("displayName"));
        normalized.put("extension", rawData);
        return normalized;
    }
}

