package com.socialmonitor.bilibili.adapter;

import com.socialmonitor.bilibili.client.BilibiliApiClient;
import com.socialmonitor.bilibili.client.BilibiliFetchException;
import com.socialmonitor.bilibili.domain.BilibiliFetchedUserSnapshot;
import com.socialmonitor.platform.domain.PlatformCredential;
import com.socialmonitor.platform.dto.CredentialValidateResult;
import com.socialmonitor.platform.dto.FetchResult;
import com.socialmonitor.platform.enums.PlatformCapability;
import com.socialmonitor.platform.enums.RiskLevel;
import com.socialmonitor.platform.port.SocialPlatformAdapter;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class BilibiliPlatformAdapter implements SocialPlatformAdapter {

    private final BilibiliApiClient apiClient;

    public BilibiliPlatformAdapter(BilibiliApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public String platformCode() {
        return "bilibili";
    }

    @Override
    public Set<PlatformCapability> capabilities() {
        return Set.of(
                PlatformCapability.ACCOUNT_PROFILE,
                PlatformCapability.CONTENT_LIST,
                PlatformCapability.CONTENT_DETAIL,
                PlatformCapability.COMMENT_LIST,
                PlatformCapability.DANMAKU_LIST,
                PlatformCapability.INTERACTION_METRIC,
                PlatformCapability.FOLLOWER_SNAPSHOT,
                PlatformCapability.TREND_TOPIC
        );
    }

    @Override
    public CredentialValidateResult validateCredential(PlatformCredential credential) {
        if (credential == null || credential.secretPayload() == null || credential.secretPayload().isEmpty()) {
            return CredentialValidateResult.invalid("No credential configured. Public/mock mode only.", RiskLevel.MEDIUM);
        }
        return CredentialValidateResult.valid("Credential shape accepted by placeholder adapter.");
    }

    @Override
    public CredentialValidateResult refreshCredential(PlatformCredential credential) {
        return CredentialValidateResult.invalid("Credential refresh is not implemented in the initial skeleton.", RiskLevel.MEDIUM);
    }

    @Override
    public FetchResult<Map<String, Object>> fetchAccount(String externalAccountId) {
        try {
            BilibiliFetchedUserSnapshot snapshot = apiClient.fetchUserCard(Long.parseLong(externalAccountId));
            Map<String, Object> data = Map.of(
                    "platform", platformCode(),
                    "externalAccountId", snapshot.mid().toString(),
                    "displayName", snapshot.nickname(),
                    "avatarUrl", snapshot.avatarUrl() == null ? "" : snapshot.avatarUrl(),
                    "profileUrl", snapshot.profileUrl(),
                    "followerCount", snapshot.followerCount(),
                    "followingCount", snapshot.followingCount() == null ? 0 : snapshot.followingCount(),
                    "fetchedAt", snapshot.fetchedAt().toString(),
                    "sourceEndpoint", snapshot.sourceEndpoint()
            );
            return FetchResult.success(data, snapshot.rawPayload());
        } catch (NumberFormatException exception) {
            return FetchResult.failure(
                    com.socialmonitor.platform.enums.FetchErrorType.PARSE_ERROR,
                    false,
                    RiskLevel.LOW,
                    "Bilibili mid must be numeric.",
                    null
            );
        } catch (BilibiliFetchException exception) {
            return FetchResult.failure(
                    exception.errorType(),
                    exception.retryable(),
                    exception.riskLevel(),
                    exception.getMessage(),
                    exception.rawPayload()
            );
        }
    }

    @Override
    public FetchResult<Map<String, Object>> fetchContents(String externalAccountId, String cursor) {
        return FetchResult.unsupported("Bilibili content list fetch is reserved for future implementation.");
    }

    @Override
    public FetchResult<Map<String, Object>> fetchContentDetail(String externalContentId) {
        return FetchResult.unsupported("Bilibili content detail fetch is reserved for future implementation.");
    }

    @Override
    public FetchResult<Map<String, Object>> fetchComments(String externalContentId, String cursor) {
        return FetchResult.unsupported("Bilibili comments fetch is reserved for future implementation.");
    }

    @Override
    public FetchResult<Map<String, Object>> fetchDanmaku(String externalContentId, String cursor) {
        return FetchResult.unsupported("Bilibili danmaku fetch is reserved for future implementation.");
    }

    @Override
    public FetchResult<Map<String, Object>> fetchInteractions(String externalContentId) {
        return FetchResult.unsupported("Bilibili interaction fetch is reserved for future implementation.");
    }

    @Override
    public FetchResult<Map<String, Object>> fetchFollowers(String externalAccountId, String cursor) {
        return FetchResult.unsupported("Bilibili followers fetch is reserved for future implementation.");
    }

    @Override
    public FetchResult<Map<String, Object>> fetchTrends(String cursor) {
        return FetchResult.unsupported("Bilibili trend fetch is reserved for future implementation.");
    }
}
