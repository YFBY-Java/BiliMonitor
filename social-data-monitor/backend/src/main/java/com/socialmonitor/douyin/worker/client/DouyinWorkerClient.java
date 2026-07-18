package com.socialmonitor.douyin.worker.client;

import com.socialmonitor.douyin.worker.dto.WorkerConsume;
import com.socialmonitor.douyin.worker.dto.WorkerHealth;
import com.socialmonitor.douyin.worker.dto.WorkerQrImage;
import com.socialmonitor.douyin.worker.dto.WorkerSessionStart;
import com.socialmonitor.douyin.worker.dto.WorkerStatus;
import com.socialmonitor.douyin.worker.dto.WorkerValidation;
import java.util.Map;

public interface DouyinWorkerClient {

    WorkerHealth health();

    WorkerSessionStart start(int expiresInSeconds);

    WorkerQrImage qr(String workerSessionId);

    WorkerStatus status(String workerSessionId);

    WorkerConsume consume(String workerSessionId);

    void delete(String workerSessionId);

    WorkerValidation validate(Map<String, Object> bundle);
}
