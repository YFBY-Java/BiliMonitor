package com.socialmonitor.ai.service;

import org.springframework.stereotype.Service;

@Service
public class AiJobService {

    public String createPlaceholderJob(String targetType, String targetId) {
        return "mock-ai-job:" + targetType + ":" + targetId;
    }
}

