package com.vstretch.app;

/** Callback từ tiến trình shell (Keys.kt) về app khi người dùng nhấn đúp Volume −. */
oneway interface IKeyCb {
    void onKey(int code);
}
