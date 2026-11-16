package com.bbooster.app.shell;

import android.os.Bundle;

// Runs inside the Shizuku-started process (shell UID). Transaction 16777114 is reserved by
// Shizuku for destroy(); see the Shizuku-API demo.
interface IShellService {
    void destroy() = 16777114;

    // argv must round-trip through ShellCommand.fromArgv, otherwise the call is rejected.
    // Result keys: see ShellProtocol.
    Bundle exec(in String[] argv, long timeoutMs) = 1;
}
