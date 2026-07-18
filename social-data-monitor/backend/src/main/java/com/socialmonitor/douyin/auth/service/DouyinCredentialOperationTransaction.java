package com.socialmonitor.douyin.auth.service;

import com.socialmonitor.douyin.auth.repository.DouyinCredentialRepository;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "app.douyin.auth", name = "enabled", havingValue = "true")
public class DouyinCredentialOperationTransaction {

    private final DouyinCredentialRepository credentials;

    public DouyinCredentialOperationTransaction(DouyinCredentialRepository credentials) {
        this.credentials = credentials;
    }

    @Transactional
    public <T> T execute(String authType, String operation, Supplier<T> action) {
        credentials.acquireOperationLock(authType, operation);
        return action.get();
    }
}
