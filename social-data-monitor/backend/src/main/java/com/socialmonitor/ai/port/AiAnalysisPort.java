package com.socialmonitor.ai.port;

import com.socialmonitor.ai.dto.AiAnalysisRequest;
import com.socialmonitor.ai.dto.AiAnalysisResult;

public interface AiAnalysisPort {

    AiAnalysisResult summarizeContent(AiAnalysisRequest request);

    AiAnalysisResult analyzeSentiment(AiAnalysisRequest request);

    AiAnalysisResult detectAnomaly(AiAnalysisRequest request);

    AiAnalysisResult generateReport(AiAnalysisRequest request);

    AiAnalysisResult suggestOperation(AiAnalysisRequest request);
}

