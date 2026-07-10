package com.socialmonitor.ingestion.service;

import com.socialmonitor.ingestion.normalizer.PlatformDataNormalizer;
import java.util.Collection;
import org.springframework.stereotype.Service;

@Service
public class NormalizerRegistry {

    private final Collection<PlatformDataNormalizer> normalizers;

    public NormalizerRegistry(Collection<PlatformDataNormalizer> normalizers) {
        this.normalizers = normalizers;
    }

    public Collection<PlatformDataNormalizer> normalizers() {
        return normalizers;
    }
}

