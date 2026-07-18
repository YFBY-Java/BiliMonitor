package com.socialmonitor.douyin.auth.service;

import com.socialmonitor.douyin.auth.domain.DouyinOAuthCredential;
import com.socialmonitor.douyin.auth.domain.DouyinWebSessionCredential;

public interface DouyinCredentialProvider {

    DouyinOAuthCredential requireActiveOAuth();

    DouyinWebSessionCredential requireActiveWebSession();
}
