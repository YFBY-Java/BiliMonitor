package com.socialmonitor.ai.provider;

import com.socialmonitor.ai.dto.AiAnalysisRequest;
import com.socialmonitor.ai.dto.AiAnalysisResult;
import com.socialmonitor.ai.port.AiAnalysisPort;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MockAiAnalysisProvider implements AiAnalysisPort {

    @Override
    public AiAnalysisResult summarizeContent(AiAnalysisRequest request) {
        return mock("Mock summary for " + request.targetType() + ":" + request.targetId());
    }

    @Override
    public AiAnalysisResult analyzeSentiment(AiAnalysisRequest request) {
        return mock("Mock sentiment analysis completed.");
    }

    @Override
    public AiAnalysisResult detectAnomaly(AiAnalysisRequest request) {
        return mock("Mock anomaly detection completed.");
    }

    @Override
    public AiAnalysisResult generateReport(AiAnalysisRequest request) {
        return mock("Mock report generated.");
    }

    @Override
    public AiAnalysisResult suggestOperation(AiAnalysisRequest request) {
        return mock("Mock operation suggestion generated.");
    }

    private AiAnalysisResult mock(String summary) {
        return new AiAnalysisResult("mock", "mock-model", summary, "neutral", List.of("placeholder"), OffsetDateTime.now());
    }
}

