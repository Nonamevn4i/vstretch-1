package com.vstretch.app;

import com.vstretch.app.IKeyCb;

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

    /**
     * Sửa cảm ứng khi kéo giãn thật (xem Touch.kt): giữ thiết bị chạm và phát lại chạm đúng toạ độ hình đã kéo giãn.
     * on=false: nhả thiết bị chạm. Kích thước theo hướng tự nhiên của máy. Trả về "OK: ..." hoặc "ERR: ...".
     */
    String setTouch(boolean on, int lnW, int lnH, int physW, int physH,
                    int baseW, int baseH, int baseDpi,
                    boolean baseSizeOverride, boolean baseDensityOverride) = 4;

    /** Văn bản chẩn đoán phần sửa cảm ứng. */
    String touchDiag() = 5;

    /**
     * Nghe phím Volume − (xem Keys.kt): nhấn đúp -> gọi cb.onKey(114). cb=null: ngừng nghe.
     * Trả về "OK: ..." hoặc "ERR: ...".
     */
    String setKeyListener(IKeyCb cb) = 6;
}
