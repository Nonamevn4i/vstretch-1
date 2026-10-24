# StretchX — chỉnh độ phân giải / kéo dãn màn hình bằng Shizuku

App Android (Kotlin, không phụ thuộc AppCompat/Compose) giúp đổi **kích thước màn hình ảo** của máy
(`wm size` + `wm density` qua Shizuku) với thanh kéo **1.00x → 1.50x**, có **cửa sổ nổi** để chỉnh ngay khi đang chơi
và nút **Reset**. Có profile sẵn cho **Valorant Mobile bản Trung Quốc** (`com.tencent.tmgp.codev`).
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
4. **Bật cửa sổ nổi** → thoát app, mở game, chạm bong bóng để chỉnh. **Reset** = về mặc định.
5. Nút “Mở game & áp dụng” áp dụng trước rồi mở Valorant CN (khuyên dùng).

## Giới hạn quan trọng (đọc kỹ)
* Android luôn **giữ tỉ lệ** khi `wm size` khác tỉ lệ panel: phần dư thành **viền đen** (trên/dưới hoặc trái/phải).
  Không có API shell nào kéo méo toàn màn hình *và* giữ cảm ứng khớp. StretchX chọn độ phân giải ảo **lớn nhất có thể**
  để viền chỉ bằng phần bắt buộc và hiển thị % viền cho bạn.
* Kéo “Rộng” = màn hình ảo rộng hơn tỉ lệ máy (game tự fill 16:9 sẽ kéo ngang hình, viền trên/dưới).
  “Hẹp” = ngược lại. Hãy thử cả hai và xem hiệu quả thực tế với game.
* Mức tối đa tự giảm trên máy độ phân giải thấp (sàn 800×480 để không dãn quá mức).
* Đổi kích thước khi đang trong trận có thể làm game tải lại/văng → áp dụng trước khi vào game.
* Một số game/anti-cheat có thể phản ứng với việc đổi độ phân giải; tự chịu trách nhiệm khi dùng.

## Nếu bị kẹt màn hình sau khi reboot
`wm size` được hệ thống lưu lại. Nếu Shizuku chưa chạy, dùng PC: `adb shell wm size reset && adb shell wm density reset`
(hoặc bật lại Shizuku rồi bấm Reset trong app).
