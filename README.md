# Ti Android Runtime Node

Ti Android Runtime Node là một ứng dụng Android native (Kotlin + Jetpack Compose) đóng vai trò **mobile execution node** trong hệ sinh thái **Ti**.

## 📌 Tổng quan dự án
Ứng dụng thực hiện kết nối với Ti Device Gateway hoặc OmniRoute để nhận, điều phối và thực thi các tác vụ tự động hóa giao diện trực tiếp trên thiết bị Android:
* **Accessibility-first**: Tự động hóa sử dụng hệ thống Accessibility Tree.
* **Vision fallback**: Sử dụng OCR và VLM để xác định tọa độ khi giao diện phức tạp.
* **Policy-controlled**: Quản lý rủi ro chặt chẽ, luôn yêu cầu sự xác nhận của người dùng cho các tác vụ quan trọng.

Chi tiết kế hoạch triển khai và cấu trúc dự án vui lòng tham khảo tại tệp [PLAN.md](file:///Z:/01_PROJECTS/apps/Ti-Android/PLAN.md).

## Build TiRouter Android ARM64

APK chỉ đóng gói artifact TiRouter đã được xác thực là ELF ARM64. Artifact cũ
`termux/bin/tirouter-arm64-linux` là binary Windows (`MZ`) và không được dùng.

```powershell
Set-Location Z:\01_PROJECTS\apps\Ti-Android
PowerShell -ExecutionPolicy Bypass -File .\termux\build-tools\build-tirouter-android.ps1
```

Sau khi build artifact thành công:

```powershell
.\gradlew.bat :app:assembleDebug
```

Trong APK, TiRouter chạy dưới `TiRouterService` và lắng nghe mặc định tại
`http://127.0.0.1:1870`. Đây là cổng runtime canonical của TiRouter; client
Ti chuẩn vẫn dùng gateway canonical của hệ thống khi kết nối từ bên ngoài.
