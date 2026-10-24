package com.vstretch.app;

interface IShell {
    void destroy() = 16777114; // bắt buộc theo Shizuku UserService
    String exec(String cmd) = 1;

    /**
     * Kéo giãn thật qua SurfaceFlinger (xem Sf.kt).
     * lnW/lnH: kích thước màn hình logic (`wm size`) theo hướng tự nhiên của máy.
     * physW/physH: kích thước panel thật, hướng tự nhiên.
     * fill=true: lấp đầy panel (kéo giãn) + canh gác; fill=false: trả về hiển thị mặc định.
     * Trả về "OK: ..." hoặc "ERR: ...".
     */
    String setStretch(int lnW, int lnH, int physW, int physH, boolean fill) = 2;

    /** Văn bản chẩn đoán trạng thái kéo giãn. */
    String diag() = 3;
}
