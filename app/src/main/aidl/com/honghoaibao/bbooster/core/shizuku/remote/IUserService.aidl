package com.honghoaibao.bbooster.core.shizuku.remote;

import com.honghoaibao.bbooster.core.shizuku.remote.ICommandCallback;

interface IUserService {
    void destroy() = 16777114;
    void execute(String actionId, long timeoutMs, ICommandCallback callback);
}
