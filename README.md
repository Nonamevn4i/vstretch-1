# StretchX — kéo giãn màn hình THẬT (True Stretch) bằng Shizuku

App Android (Kotlin, không phụ thuộc AppCompat/Compose) đổi **kích thước màn hình ảo** (`wm size` + `wm density`)
rồi **kéo giãn hình ra toàn bộ panel ở tầng SurfaceFlinger** — không còn viền đen. Thanh kéo **1.00x → 1.50x**,
**cửa sổ nổi** để chỉnh ngay khi đang chơi và nút **Reset**. Có profile sẵn cho **Valorant Mobile bản Trung Quốc** (`com.tencent.tmgp.codev`).
Tối ưu bố cục 2 cột cho tablet (RedMagic Astra: 2400×1504, 16:10).

## Lấy file APK (arm64-v8a)

**Cách 1 – GitHub Actions (không cần cài Android Studio)**
1. Tạo repo mới trên GitHub, upload toàn bộ nội dung thư mục này (giữ nguyên `.github/`).
2. Vào tab **Actions → Build APK (arm64-v8a) → Run workflow**.
3. Sau ~5 phút, tải **StretchX-arm64-v8a-apk** ở mục Artifacts → giải nén ra `app-release.apk`.

**Cách 2 – Android Studio**
Mở thư mục này → Sync → **Build > Build APK(s)**. File ở `app/build/outputs/apk/release/`.
(Hoặc dòng lệnh: `gradle :app:assembleRelease` với Gradle 8.7, JDK 17.)

APK ký bằng debug key nên cài thẳng được; muốn phát hành thì tự ký lại bằng keystore của bạn.

## Cách dùng
1. Cài & mở **Shizuku**, bấm Start (Gỡ lỗi không dây: Android 11+ không cần PC).
2. Mở StretchX → **Cấp quyền** Shizuku.
3. Chọn hệ số / hướng / % độ phân giải → **Áp dụng**. Hoặc bấm preset 16:9, 2:1, 21:9 (tự tính theo tỉ lệ máy).
4. **Bật cửa sổ nổi** → thoát app, mở game, chạm bong bóng để chỉnh. **Reset** = độ phân giải gốc, chiều rộng nhỏ nhất 1200 dp.
5. Nút “Mở game & áp dụng” áp dụng trước rồi mở Valorant CN (khuyên dùng).

## Cách "kéo giãn thật" hoạt động (v2.0)
Bản cũ chỉ dùng `wm size`: Android luôn **giữ tỉ lệ** nên phần dư thành **viền đen** (ví dụ 2880×1606 trong panel 2880×1800
→ viền ≈11%) — nhìn như bị "che" chứ không kéo giãn.

Bản này làm thêm bước thứ hai, đúng cách scrcpy điều khiển màn hình: tiến trình shell của Shizuku (có quyền
`ACCESS_SURFACE_FLINGER`) gửi cho SurfaceFlinger phép chiếu mới **toàn bộ màn hình logic → TOÀN BỘ panel**
(`SurfaceControl.setDisplayProjection`). Hình bị kéo giãn lấp đầy, trong khi game vẫn thấy màn hình logic 16:9 hợp lệ
nên **không bị khóa tỉ lệ** và không biết gì về việc kéo giãn.

* Nút **Kéo giãn thật: BẬT/TẮT** (mặc định BẬT). Tắt = quay lại kiểu cũ (có viền đen).
* Bong bóng nổi được giữ cách hai cạnh ngang ít nhất 64 dp, để cửa sổ điều khiển không che cử chỉ vuốt cạnh của RedMagic Game Space.
* **Chẩn đoán**: hiện cách lấy token màn hình, cách gửi lệnh tới SurfaceFlinger, phép chiếu đang dùng, lỗi (nếu có).
* Một luồng canh gác (chạy trong tiến trình Shizuku, sống tiếp cả khi app bị kill giữa trận) tự áp dụng lại khi bạn
  xoay màn hình, và **tự dừng** nếu kích thước logic bị đổi từ nơi khác (adb/app khác) để không ép phép chiếu cũ.

## Giới hạn quan trọng (đọc kỹ)
* **Kéo giãn thật chỉ biến đổi hình ảnh, không biến đổi sự kiện cảm ứng.** Nếu game giới hạn vùng nhận chạm theo khung hình ban đầu,
  phần hình được kéo vào viền có thể không nhận chạm hoặc vị trí chạm bị lệch. Shizuku chạy với quyền shell không có quyền sửa
  ánh xạ cảm ứng hệ thống (`SET_INPUT_CALIBRATION` chỉ dành cho hệ thống); muốn chạm chính xác ở mọi mép, hãy tắt
  **Kéo giãn thật** hoặc dùng chế độ/tỉ lệ màn hình được game hỗ trợ. Không thể khắc phục an toàn chỉ bằng phép chiếu SurfaceFlinger.
* Kéo "Rộng" = màn hình ảo rộng hơn tỉ lệ máy → hình kéo giãn theo chiều **dọc**. "Hẹp" = ngược lại → kéo giãn theo chiều **ngang**
  (kiểu "4:3 kéo giãn" quen thuộc: mục tiêu trông to bề ngang hơn).
* Mức tối đa tự giảm trên máy độ phân giải thấp (sàn 800×480 để không dãn quá mức).
* Đổi kích thước khi đang trong trận có thể làm game tải lại/văng → áp dụng trước khi vào game.
* Phụ thuộc API ẩn của Android (10 → 16). Nếu ROM của hãng chặn/đổi API, mục **Chẩn đoán** sẽ báo rõ bước nào lỗi và app tự
  rơi về kiểu cũ (có viền đen) chứ không làm hỏng màn hình.
* Một số game/anti-cheat có thể phản ứng với việc đổi độ phân giải; tự chịu trách nhiệm khi dùng.

## Nếu bị kẹt màn hình sau khi reboot
`wm size` được hệ thống lưu lại. Nếu Shizuku chưa chạy, dùng PC: `adb shell wm size reset && adb shell wm density reset`
(hoặc bật lại Shizuku rồi bấm Reset trong app).

Phép chiếu kéo giãn của SurfaceFlinger **không** được lưu qua lần khởi động lại: nếu hình còn bị kéo lạ sau khi reset,
chỉ cần **xoay màn hình một lần** hoặc **khởi động lại máy** là về bình thường.

Nút Reset khôi phục độ phân giải về panel gốc và đặt mật độ để **chiều rộng nhỏ nhất xấp xỉ 1200 dp**. Mật độ này là override
có chủ ý nên khác DPI gốc của máy; lệnh khôi phục khẩn cấp `wm density reset` sẽ đưa mật độ về mặc định của nhà sản xuất.
