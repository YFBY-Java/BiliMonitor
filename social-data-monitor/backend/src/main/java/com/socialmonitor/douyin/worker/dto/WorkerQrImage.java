package com.socialmonitor.douyin.worker.dto;

public record WorkerQrImage(byte[] bytes, String contentType) {

    public WorkerQrImage {
        bytes = bytes == null ? new byte[0] : bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
