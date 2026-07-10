package com.socialmonitor.platform.port;

import com.socialmonitor.platform.domain.PlatformCredential;
import com.socialmonitor.platform.dto.CredentialValidateResult;
import com.socialmonitor.platform.dto.FetchResult;
import com.socialmonitor.platform.enums.PlatformCapability;
import java.util.Map;
import java.util.Set;

public interface SocialPlatformAdapter {

    String platformCode();

    Set<PlatformCapability> capabilities();

    CredentialValidateResult validateCredential(PlatformCredential credential);

    CredentialValidateResult refreshCredential(PlatformCredential credential);

    FetchResult<Map<String, Object>> fetchAccount(String externalAccountId);

    FetchResult<Map<String, Object>> fetchContents(String externalAccountId, String cursor);

    FetchResult<Map<String, Object>> fetchContentDetail(String externalContentId);

    FetchResult<Map<String, Object>> fetchComments(String externalContentId, String cursor);

    FetchResult<Map<String, Object>> fetchDanmaku(String externalContentId, String cursor);

    FetchResult<Map<String, Object>> fetchInteractions(String externalContentId);

    FetchResult<Map<String, Object>> fetchFollowers(String externalAccountId, String cursor);

    FetchResult<Map<String, Object>> fetchTrends(String cursor);
}

