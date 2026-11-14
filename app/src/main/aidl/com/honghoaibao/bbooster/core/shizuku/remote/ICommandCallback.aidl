package com.honghoaibao.bbooster.core.shizuku.remote;

oneway interface ICommandCallback {
    void onCompleted(int exitCode, String stdout, String stderr, boolean timedOut, boolean outputTruncated);
    void onRejected(String reason);
}
