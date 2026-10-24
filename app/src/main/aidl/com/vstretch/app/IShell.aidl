package com.vstretch.app;

interface IShell {
    void destroy() = 16777114; // bắt buộc theo Shizuku UserService
    String exec(String cmd) = 1;
}
