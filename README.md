# ScanViet

Ứng dụng quét tài liệu cho Android, viết bằng Kotlin + Jetpack Compose. Mục tiêu: **quét giấy → PDF nhiều trang rõ nét → OCR tiếng Việt ngay trên máy**, miễn phí, không cần đăng nhập, không có máy chủ của ứng dụng.

> Tài liệu thiết kế: [`PLAN.md`](PLAN.md) (định vị, KPI, lộ trình) · [`MVP_SPEC.md`](MVP_SPEC.md) (phạm vi đã chốt) · [`ARCHITECTURE.md`](ARCHITECTURE.md) (lưu trữ, schema, module) · [`AGENTS.md`](AGENTS.md) (quy tắc kỹ thuật bắt buộc).

---

## Trạng thái hiện tại: xong Giai đoạn 1 + Giai đoạn 2a (quét) + Giai đoạn 2b (xuất/chia sẻ PDF)

**Đã chạy được:**

- Danh sách tài liệu thật từ Room, sắp xếp theo lần cập nhật mới nhất.
- Tìm kiếm tiếng Việt **không cần gõ dấu** ("hoa don" ra "Hóa đơn điện").
- Chi tiết tài liệu: số trang, ngày tạo/sửa, dung lượng thật trên đĩa, cảnh báo khi thiếu file ảnh.
- Đổi tên tài liệu (chỉ sửa metadata, **không** đổi tên file trên đĩa).
- Xoá mềm → **Thùng rác** → khôi phục, hoặc xoá vĩnh viễn (xoá cả file).
- Cài đặt: chủ đề Sáng/Tối/Theo hệ thống, công tắc OCR tự động, giữ ảnh gốc, xem dung lượng đang dùng.
- Dọn dẹp lúc mở app: xoá tài liệu trong thùng rác quá 30 ngày, xoá thư mục mồ côi, chuyển tài liệu mất hết file ảnh vào thùng rác.
- Chủ đề Sáng/Tối, giao diện tiếng Việt, Material 3.
- **Quét tài liệu thật** (Giai đoạn 2a): mở giao diện quét của ML Kit Document Scanner (nhận diện mép giấy, cắt phối cảnh, chỉnh màu, nhiều trang), rồi xem lại danh sách trang — chuyển trang lên/xuống, xoá trang, đặt tên — và lưu. Ảnh được **sao chép ngay** vào `filesDir/documents/<uuid>/page_XXXX.jpg` trước khi ghi Room; nếu ghi Room lỗi thì thư mục vừa tạo bị xoá sạch.
- Tối đa **20 trang** cho một lần quét. Huỷ giữa chừng hoặc máy không có Google Play services đều có thông báo riêng, không màn hình trắng.
- **Xuất và chia sẻ PDF** (Giai đoạn 2b): tạo PDF nhiều trang khổ **A4** (mỗi trang một tờ), rồi hoặc **lưu bản sao** vào nơi bạn chọn qua Storage Access Framework (Drive, thư mục Tải về…), hoặc **chia sẻ** qua Zalo/Gmail/Drive bằng hộp thoại chia sẻ của hệ thống. Tên file gợi ý lấy từ tên tài liệu, đã bỏ dấu và loại ký tự cấm trong tên file. Trang thiếu file ảnh làm hỏng cả lần xuất kèm báo đúng số trang — cố ý vậy, để không bao giờ có "PDF thành công nhưng thiếu trang".
- Ảnh được vẽ **vừa khít, giữ đúng tỉ lệ, không phóng to** (phóng to chỉ làm mờ). Ảnh ngang tự đổi khổ trang thành A4 ngang. Chỉ **một trang nằm trong RAM** tại một thời điểm.

**Chưa có (đúng phạm vi, không phải lỗi):** OCR tiếng Việt (Giai đoạn 3), ảnh thu nhỏ trong danh sách, lớp văn bản chọn được trong PDF (đi kèm OCR).

> ⚠️ **Chưa kiểm chứng trên thiết bị thật.** Toàn bộ luồng quét — giao diện của ML Kit, việc cấp quyền camera, việc tải công cụ quét ở lần đầu — **chưa từng chạy trên máy thật hay emulator** vì môi trường phát triển không có thiết bị. Phần kiểm thử được là phần **sau** khi trình quét trả kết quả (sao chép ảnh, ghi Room, dọn rác khi lỗi): đã có test tự động. Xem `MVP_SPEC.md` để biết việc gì còn phải kiểm tra bằng tay.

> ⚠️ **Phần xuất PDF cũng chưa mở được bằng mắt trên máy thật.** Lý do cụ thể: Robolectric **không chạy được** `android.graphics.pdf.PdfDocument` (đã đo: `startPage` ném `IllegalStateException: document is closed!` ngay ở lần gọi đầu; thư viện Robolectric 4.14.1 không có shadow cho lớp này). Vì vậy chất lượng file PDF do `AndroidPdfExporter` sinh ra chỉ có thể xác nhận trên thiết bị. Phần logic đáng kiểm thử của việc xuất — thứ tự trang theo `pageIndex`, lỗi thiếu ảnh kèm số trang, đóng stream kể cả khi lỗi, từ chối tài liệu rỗng/không tồn tại, chế độ ghi đè `"wt"`, tên file an toàn — **đã được kiểm thử tự động** (xem bảng test). Giới hạn này được ghi lại bằng một test riêng (`RobolectricPdfLimitationTest`) để khi Robolectric hỗ trợ thì có tín hiệu viết test thật.

> Trong bản **debug** có một công cụ thử nghiệm ở màn hình Quét: nút tạo tài liệu 2 trang mẫu (ảnh JPEG tự sinh) để thử danh sách/đổi tên/xoá/thùng rác trên máy **không có Google Play services** (khi đó không thể tạo tài liệu bằng cách quét). Công cụ này bị R8 loại khỏi bản release — đã kiểm chứng bằng cách tìm chuỗi trong file `.dex` của APK release (xem mục "Kiểm chứng R8" bên dưới).

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

**Kết quả đã chạy thật (20/10/2026):** **140 unit test, 0 lỗi**. Chi tiết: `PdfFileNamesTest` 9, `FormattersTest` 9, `VnTextNormalizerTest` 8, `ContentUriDocumentOutputTargetTest` 5, `LocalDocumentFileStorageTest` 15, `DocumentStorageMaintenanceTest` 7, `OfflineDocumentRepositoryTest` 16, `CreateDocumentUseCaseTest` 7, `ExportDocumentAsPdfUseCaseTest` 11, `ImportScannedDocumentUseCaseTest` 9, `RenameDocumentUseCaseTest` 3, `RobolectricPdfLimitationTest` 1, `DocumentDetailViewModelTest` 10, `LibraryViewModelTest` 13, `ScanViewModelTest` 17.

Test dùng **Robolectric** (Room in-memory + `filesDir`/`cacheDir` giả) nên chạy được trên máy không có emulator. Chưa có test instrumented — sẽ bổ sung khi có thiết bị/emulator.

`ImportScannedDocumentUseCaseTest`, `DocumentDetailViewModelTest` và `ScanViewModelTest` là test **tích hợp có chủ đích**: dùng file thật trên `filesDir`/`cacheDir` giả của Robolectric, chỉ thay trình quét, repository, bộ sinh PDF và đích ghi. Nhờ vậy chúng trả lời được câu hỏi "bấm Lưu thì có thật sự sinh ra file + bản ghi đúng không", "khi ghi database lỗi thì có còn file rác không", và "bấm Chia sẻ thì file PDF có thật sự tồn tại trên đĩa không". Cái **không** kiểm thử được: chính trình quét của ML Kit (là Activity của Play services) và chính `android.graphics.pdf.PdfDocument` (Robolectric không hỗ trợ — xem cảnh báo ở đầu file).

---

## Kiểm chứng R8 (công cụ thử nghiệm không lọt vào bản release)

Bản release bật `isMinifyEnabled` + `isShrinkResources`. Công cụ tạo tài liệu mẫu nằm trong nhánh `if (BuildConfig.DEBUG)`, nên R8 phải loại bỏ nó. Cách kiểm chứng: tìm **chuỗi ký tự** trong file `.dex` của APK (không tìm tên class — R8 đổi tên class nên tên class không còn ý nghĩa; ngược lại `mapping.txt` liệt kê cả class đã bị xoá nên cũng không dùng làm bằng chứng).

Đã chạy ngày 20/10/2026 (`app-debug.apk` và `app-release-unsigned.apk`):

| Chuỗi cần tìm | Bản debug | Bản release |
|---|---|---|
| `TÀI LIỆU MẪU SCANVIET` (tiêu đề trên ảnh mẫu) | có | **không** |
| `Tài liệu mẫu ` (tiền tố tên tài liệu mẫu) | có | **không** |
| `SampleDocumentFactory` | có | **không** |
| `dev_create_sample` (tên resource) | có | **không** |
| Đối chứng: `page_%04d.jpg`, `scan_scratch`, `GmsDocumentScanningDelegateActivity` | có | có |
| Đối chứng: chuỗi không tồn tại | không | không |

Các dòng đối chứng chứng minh cách tìm này không cho kết quả dương tính giả, nên kết luận "release không còn công cụ thử nghiệm" là đáng tin. Kích thước APK: debug 12,34 MB, release 2,14 MB.

Kiểm chứng thêm cho phần FileProvider chia sẻ PDF, đọc trực tiếp từ APK (không suy đoán từ mã nguồn):

| Thứ cần đúng | Cách kiểm | Kết quả |
|---|---|---|
| Authority khớp giữa manifest và code | `aapt2 dump xmltree --file AndroidManifest.xml` | `authorities="vn.scanviet.app.fileprovider"` — khớp `"$packageName.fileprovider"` |
| Ứng dụng khác không tự đọc được | như trên | `exported=false`, `grantUriPermissions=true` |
| Vùng chia sẻ chỉ mở thư mục nháp | tên entry trong APK | `res/xml/file_paths.xml` có mặt; nội dung chỉ khai báo `cache/scan_scratch/` |
| Không xin thêm quyền | báo cáo hợp nhất manifest | chỉ có `INTERNET` + `ACCESS_NETWORK_STATE` (đi kèm ML Kit), **không** `CAMERA`, **không** quyền bộ nhớ |

---

## Cấu trúc project

Một module `:app` duy nhất (theo `AGENTS.md`), nhưng ranh giới package đặt **trùng tên module tương lai** để việc tách module sau này chỉ là đổi thư mục:

```
app/src/main/java/vn/scanviet/app/
├── core/         → :core:common    DispatcherProvider, VnTextNormalizer, Formatters, TimeProvider,
│                                   file/PdfFileNames
├── domain/       → :core:domain    model, DocumentRules, use case, exception,
│                                   :core:domain/repository   (DocumentRepository)
│                                   :core:domain/storage      (DocumentFileStorage - cổng)
│                                   :core:domain/scan         (PageImageReader - cổng)
│                                   :core:domain/export       (PdfExporter + DocumentOutputTarget - cổng)
├── data/         → :core:data      Room (entity/dao/database), DataStore, repository impl,
│                                   file/LocalDocumentFileStorage, scan/, export/,
│                                   di/, dev/ (chỉ debug)
├── platform/     → :platform:*     scanner/ (DocumentScannerEngine + ML Kit impl + DI)
│                                   pdf/ (PdfExporter thật + DI)
├── ui/           → :feature:*      theme, components, common, library, scan, settings
└── navigation/   → :app            ScanVietRoutes + ScanVietNavHost
```

Quy tắc phụ thuộc (kiểm tra được bằng mắt qua import):

- `domain/` **không** import Compose, Room, Android UI, hay `data/`. Các cổng như `DocumentFileStorage`, `PageImageReader` nằm ở đây vì use case cần chúng mà không được biết Android.
- `ui/` không truy cập `data/` trực tiếp — chỉ qua interface trong `domain/`. Riêng thứ thuộc Android SDK không thể diễn đạt bằng domain (`DocumentScannerEngine` cần `Activity`/`IntentSender`) thì nằm ở `platform/`; `ui/` chỉ chuyển tiếp `IntentSender`/`Intent`.
- `platform/` biết SDK cụ thể (ML Kit, `android.graphics.pdf`) nhưng **không** chứa quy tắc nghiệp vụ. Cùng một lý do như `platform/scanner`: `PdfDocument` là API Android, không diễn đạt được bằng domain, nên cổng `PdfExporter` nằm ở `domain/export/` và cài đặt thật nằm ở `platform/pdf/`.
- `navigation/` là nơi **duy nhất** biết tất cả màn hình; các màn hình chỉ nhận callback.

### Luồng quét (Giai đoạn 2a)

```
ScanScreen ──(bấm "Bắt đầu quét")──> ScanViewModel.onStartScan(activity)
   ▲                                        │
   │  StateFlow<IntentSender?>                ▼
   │                              DocumentScannerEngine.createStartIntent()
   │  (mở giao diện quét của ML Kit)          │  (ML Kit Document Scanner, Play services)
   │                                        ▼
   └──(ActivityResult: Intent?)──> ScanViewModel.onScanResult(intent)
                                            │  parseResult -> ScannedDocument(pageUris)
                                            ▼
                                  ScanUiState.Reviewing  (xem lại, sắp xếp, xoá, đặt tên)
                                            │  (bấm "Lưu")
                                            ▼
                            ImportScannedDocumentUseCase
                              1. kiểm tra tên + số trang (chưa ghi gì)
                              2. tạo filesDir/documents/<uuid>/
                              3. từng URI: PageImageReader.read -> writePageImage
                              4. CreateDocumentUseCase (ghi Room)
                              5. lỗi ở bất kỳ bước nào -> xoá thư mục vừa tạo
```

Điểm cốt lõi: URI do trình quét trả về là **file tạm trong vùng của Play services**, có thể biến mất bất cứ lúc nào — nên phải sao chép **ngay** vào vùng riêng của app rồi mới làm gì khác.

### Luồng xuất PDF (Giai đoạn 2b)

```
DocumentDetailScreen
   ├──(bấm "Lưu bản PDF")──> SAF CreateDocument("application/pdf") -> Uri người dùng chọn
   │                            └──> DocumentDetailViewModel.onSaveCopy(uri)
   └──(bấm "Chia sẻ PDF")───> DocumentDetailViewModel.onSharePdf()
                                └── tạo file trong cacheDir/scan_scratch/
                                        │
                    ExportDocumentAsPdfUseCase
                      1. tài liệu có tồn tại? (không -> lỗi TRƯỚC khi mở stream)
                      2. có trang nào không? (không -> lỗi)
                      3. sắp trang theo pageIndex
                      4. mỗi trang: file ảnh có thật trên đĩa? (thiếu -> lỗi kèm số trang)
                      5. DocumentOutputTarget.open(target).use { PdfExporter.export(it, ...) }
                                                │
                                    AndroidPdfExporter (platform/pdf)
                                      - A4 595x842 pt, khổ ngang nếu ảnh ngang
                                      - vẽ vừa khít, giữ tỉ lệ, nền trắng
                                      - mỗi trang: decode -> vẽ -> recycle()
```

Chia sẻ dùng `FileProvider` với authority `vn.scanviet.app.fileprovider`, chỉ mở đúng thư mục `cache/scan_scratch/` (khai báo ở `res/xml/file_paths.xml`) và cấp quyền đọc tạm thời cho Intent — không dùng `file://`, không mở cả `filesDir`.

### Lưu trữ

| Loại dữ liệu | Nơi lưu | Ghi chú |
|---|---|---|
| Ảnh trang scan | `filesDir/documents/<uuid>/page_0001.jpg` | đường dẫn **tương đối** trong database |
| Metadata tài liệu | Room `scanviet.db` | `exportSchema = true`, schema v1 đã commit ở `app/schemas/` |
| Cài đặt | DataStore Preferences | chủ đề, OCR tự động, giữ ảnh gốc |
| Vùng nháp khi scan | `cacheDir/scan_scratch/` | dọn sạch khi mở app |
| PDF tạm để chia sẻ | `cacheDir/scan_scratch/<ten-tai-lieu>.pdf` | không lưu thêm bản trong app; do Android được phép dọn khi hết chỗ |
| PDF người dùng lưu | nơi người dùng chọn qua SAF | nằm **ngoài** vùng của app, app không quản lý |

Không xin quyền bộ nhớ ngoài và **không** xin `CAMERA`. Cả quét (ảnh do Play services trả về), lưu PDF và chia sẻ đều không cần quyền bộ nhớ: lưu dùng SAF, chia sẻ dùng `FileProvider`.

### Quyền thực tế trong APK (đã kiểm chứng)

Ứng dụng không tự khai báo quyền nào, nhưng thư viện hợp nhất thêm vào:

| Quyền | Nguồn | Ghi chú |
|---|---|---|
| `android.permission.INTERNET` | `com.google.android.datatransport:transport-backend-cct` (phụ thuộc bắc cầu của ML Kit) | Kênh truyền số liệu của Google; cũng là thứ cho phép lần quét đầu tải công cụ quét |
| `android.permission.ACCESS_NETWORK_STATE` | cùng nguồn | |
| `vn.scanviet.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | AndroidX | Quyền nội bộ, `protectionLevel=signature` |

`CAMERA` **không** cần: giao diện quét nằm trong tiến trình của Google Play services (activity `com.google.mlkit.vision.documentscanner.internal.GmsDocumentScanningDelegateActivity`, do SDK khai báo, `exported=false`) — chính Play services xin quyền camera. Đã kiểm chứng bằng cách đọc `AndroidManifest.xml` trong `play-services-mlkit-document-scanner-16.0.0.aar`: AAR đó không khai báo quyền nào.

Nguồn của từng quyền tra được ở `app/build/outputs/logs/manifest-merger-debug-report.txt`.
Không có mã nào trong `vn.scanviet.app` gọi HTTP; ảnh/PDF/văn bản OCR không được gửi đi đâu.

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
| 2a ✅ | Quét thật bằng ML Kit Document Scanner (nhiều trang), xem lại/sắp xếp/xoá trang, copy ảnh vào app storage, dọn rác khi lỗi |
| 2b ✅ | Xuất PDF nhiều trang + chia sẻ/lưu bản sao qua SAF |
| 3 | OCR tiếng Việt on-device (`com.google.mlkit:text-recognition` bundled), sửa/copy/chia sẻ văn bản |
| 4 | Trải nghiệm & hiệu năng (ảnh thu nhỏ, xuất PDF nhanh hơn, nâng cấp schema lần đầu) |
| 5 | Phát hành: icon, screenshots, privacy policy, Data Safety, closed testing ≥14 ngày |

Chi tiết từng bước và tiêu chí dừng: [`MVP_SPEC.md`](MVP_SPEC.md) mục 4 và 6.
