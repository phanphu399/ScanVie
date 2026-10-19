# ScanViet

Ứng dụng quét tài liệu cho Android, viết bằng Kotlin + Jetpack Compose. Mục tiêu: **quét giấy → PDF nhiều trang rõ nét → OCR tiếng Việt ngay trên máy**, miễn phí, không cần đăng nhập, không có máy chủ của ứng dụng.

> Tài liệu thiết kế: [`PLAN.md`](PLAN.md) (định vị, KPI, lộ trình) · [`MVP_SPEC.md`](MVP_SPEC.md) (phạm vi đã chốt) · [`ARCHITECTURE.md`](ARCHITECTURE.md) (lưu trữ, schema, module) · [`AGENTS.md`](AGENTS.md) (quy tắc kỹ thuật bắt buộc).

---

## Trạng thái hiện tại: xong Giai đoạn 1 (nền tảng)

**Đã chạy được:**

- Danh sách tài liệu thật từ Room, sắp xếp theo lần cập nhật mới nhất.
- Tìm kiếm tiếng Việt **không cần gõ dấu** ("hoa don" ra "Hóa đơn điện").
- Chi tiết tài liệu: số trang, ngày tạo/sửa, dung lượng thật trên đĩa, cảnh báo khi thiếu file ảnh.
- Đổi tên tài liệu (chỉ sửa metadata, **không** đổi tên file trên đĩa).
- Xoá mềm → **Thùng rác** → khôi phục, hoặc xoá vĩnh viễn (xoá cả file).
- Cài đặt: chủ đề Sáng/Tối/Theo hệ thống, công tắc OCR tự động, giữ ảnh gốc, xem dung lượng đang dùng.
- Dọn dẹp lúc mở app: xoá tài liệu trong thùng rác quá 30 ngày, xoá thư mục mồ côi, chuyển tài liệu mất hết file ảnh vào thùng rác.
- Chủ đề Sáng/Tối, giao diện tiếng Việt, Material 3.

**Chưa có (đúng phạm vi, không phải lỗi):** chức năng quét thật, OCR, xuất PDF. Màn hình "Quét" hiện là màn hình tạm nói rõ tính năng đang được phát triển.

> Trong bản **debug** có một công cụ thử nghiệm ở màn hình Quét: nút tạo tài liệu 2 trang mẫu (ảnh JPEG tự sinh) để thử danh sách/đổi tên/xoá/thùng rác trên máy thật mà chưa cần camera. Công cụ này bị R8 loại khỏi bản release (`BuildConfig.DEBUG`).

---

## Yêu cầu môi trường

| Thành phần | Phiên bản đã kiểm chứng |
|---|---|
| JDK | 21 (Temurin) — đặt biến `JAVA_HOME` |
| Android SDK | `platforms;android-35`, `build-tools;35.0.0` |
| Gradle | 8.11.1 (dùng wrapper, không cần cài) |
| AGP / Kotlin | 8.7.3 / 2.1.0 |
| Android Studio | không bắt buộc — build bằng dòng lệnh được |

Tạo `local.properties` ở thư mục gốc (file này **không** commit):

```properties
sdk.dir=C\:\\Users\\<tên-bạn>\\AppData\\Local\\Android\\Sdk
```

---

## Build & test

```bash
# APK debug
./gradlew assembleDebug

# Toàn bộ unit test (Room + file + ViewModel + use case)
./gradlew testDebugUnitTest

# Bản release: bắt lỗi R8/ProGuard (không dùng để phát hành khi chưa có keystore)
./gradlew assembleRelease
```

Trên Windows dùng `gradlew.bat`. APK debug nằm ở `app/build/outputs/apk/debug/`.

**Kết quả đã chạy thật:** 75 unit test, 0 lỗi (`FormattersTest` 9, `VnTextNormalizerTest` 8, `LocalDocumentFileStorageTest` 12, `DocumentStorageMaintenanceTest` 7, `OfflineDocumentRepositoryTest` 16, `CreateDocumentUseCaseTest` 7, `RenameDocumentUseCaseTest` 3, `LibraryViewModelTest` 13).

Test dùng **Robolectric** (Room in-memory + `filesDir` giả) nên chạy được trên máy không có emulator. Chưa có test instrumented — sẽ bổ sung khi có thiết bị/emulator.

---

## Cấu trúc project

Một module `:app` duy nhất (theo `AGENTS.md`), nhưng ranh giới package đặt **trùng tên module tương lai** để việc tách module sau này chỉ là đổi thư mục:

```
app/src/main/java/vn/scanviet/app/
├── core/         → :core:common   DispatcherProvider, VnTextNormalizer, Formatters, TimeProvider
├── domain/       → :core:model    model, DocumentRules, DocumentRepository, use case, exception
├── data/         → :core:data     Room (entity/dao/database), DataStore, FileStorage, repository impl
├── ui/           → :feature:*     theme, components, library, scan, settings, root
└── navigation/   → :app           ScanVietRoutes + ScanVietNavHost
```

Quy tắc phụ thuộc (kiểm tra được bằng mắt qua import):

- `domain/` **không** import Compose, Room, hay `data/`.
- `ui/` không truy cập `data/` trực tiếp — chỉ qua interface trong `domain/`.
- `navigation/` là nơi **duy nhất** biết tất cả màn hình; các màn hình chỉ nhận callback.

### Lưu trữ

| Loại dữ liệu | Nơi lưu | Ghi chú |
|---|---|---|
| Ảnh trang scan | `filesDir/documents/<uuid>/page_0001.jpg` | đường dẫn **tương đối** trong database |
| Metadata tài liệu | Room `scanviet.db` | `exportSchema = true`, schema v1 đã commit ở `app/schemas/` |
| Cài đặt | DataStore Preferences | chủ đề, OCR tự động, giữ ảnh gốc |
| Vùng nháp khi scan | `cacheDir/scan_scratch/` | dọn sạch khi mở app |

Không xin quyền bộ nhớ ngoài. Xuất/chia sẻ sẽ dùng SAF + share sheet (Giai đoạn 2).

---

## Nguyên tắc riêng tư (bắt buộc, không thoả hiệp)

- Mặc định `android:allowBackup="false"` + loại trừ mọi domain trong `data_extraction_rules.xml` → **không** Auto Backup tài liệu lên Google Drive của người dùng.
- Không có máy chủ riêng, không đăng nhập, không analytics, không quảng cáo trong MVP.
- **Không** ghi log tên tài liệu, nội dung OCR, hay đường dẫn file. Thông điệp lỗi không chứa tên tài liệu.
- Xoá tài liệu là soft delete 30 ngày rồi mới xoá file thật.
- Câu marketing đúng: **"Tài liệu được xử lý trên thiết bị, không tải lên máy chủ."** Không hứa "offline 100%" vì ML Kit Document Scanner có thể cần Google Play services + mạng ở lần dùng đầu.

---

## Lộ trình

| Giai đoạn | Nội dung |
|---|---|
| 1 ✅ | Nền tảng: Compose + Nav + ViewModel + Room + DataStore, quản lý tài liệu, thùng rác, tìm kiếm không dấu |
| 2 | Quét thật bằng ML Kit Document Scanner (nhiều trang), copy kết quả vào app storage, xuất/chia sẻ PDF |
| 3 | OCR tiếng Việt on-device (`text-recognition-latin` bundled), sửa/copy/chia sẻ văn bản |
| 4 | Trải nghiệm & hiệu năng (ảnh thu nhỏ, xuất PDF nhanh hơn, nâng cấp schema lần đầu) |
| 5 | Phát hành: icon, screenshots, privacy policy, Data Safety, closed testing ≥14 ngày |

Chi tiết từng bước và tiêu chí dừng: [`MVP_SPEC.md`](MVP_SPEC.md) mục 4 và 6.
