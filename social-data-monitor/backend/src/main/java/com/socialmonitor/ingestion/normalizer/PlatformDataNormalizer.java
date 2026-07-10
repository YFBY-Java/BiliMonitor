package com.socialmonitor.ingestion.normalizer;

import java.util.Map;

public interface PlatformDataNormalizer {

    String platformCode();

    String supportsDataType();

    Map<String, Object> normalize(Map<String, Object> rawData);
}

