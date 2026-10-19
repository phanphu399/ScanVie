# ScanViet — Đặc tả MVP (chốt phạm vi)

> Nguyên tắc: **không làm bản sao CamScanner**. V1 làm tốt đúng 3 việc:
> 1. Scan giấy tờ → PDF rõ, nhiều trang, dễ chia sẻ.
> 2. OCR tiếng Việt **ngay trên máy**, không gửi tài liệu lên server.
> 3. Miễn phí, dễ dùng, quảng cáo không cản trở việc scan.
>
> Tên "ScanViet" chỉ là tên làm việc. Trước khi phát hành: kiểm tra tên trên Play, kiểm tra thương hiệu/logo, chọn namespace chính thức (**không dùng `com.example`** — nên chọn kiểu `vn.scanviet.app` và kiểm tra khả năng đăng ký sớm).

---

## 1. Phạm vi MVP

### Màn hình 1 — Trang chủ
- Nút "Quét tài liệu" lớn, dễ thấy.
- Danh sách tài liệu đã lưu (gần đây nhất trước).
- Tìm kiếm theo tên.
- Mở lại, đổi tên, xóa tài liệu.

### Màn hình 2 — Scan và xử lý trang
- Chụp một hoặc nhiều trang.
- Cắt mép, xoay, chỉnh màu.
- Xem trước, thêm/xóa trang.
- Sắp xếp thứ tự trang trước khi xuất.

### Màn hình 3 — OCR và xuất file
- Nhận dạng chữ tiếng Việt trên máy.
- Hiển thị nội dung, cho phép sửa tay.
- Sao chép / chia sẻ văn bản.
- Xuất PDF và lưu tài liệu.

### KHÔNG làm trong v1 (backlog, lý do: tăng thời gian/chi phí/độ khó kiểm thử khi chưa chứng minh giá trị scan cốt lõi)
- Đăng nhập, tài khoản, đồng bộ đám mây.
- AI tóm tắt / chatbot / sửa văn bằng model trả phí.
- Hứa "Word giữ nguyên bố cục".
- Trích xuất bảng → Excel.
- Backend riêng.
- PDF có text layer ẩn (tìm kiếm được bằng Ctrl+F) → để v1.1 sau khi OCR ổn định.
- Quảng cáo → chỉ thêm **sau** closed testing cho kết quả tốt.

---

## 2. Quyết định kỹ thuật đã chốt

### 2.1. Chọn đúng "bản đồ offline" (quan trọng cho marketing trung thực)

| Thành phần | Cách hoạt động | Cần mạng? |
|---|---|---|
| ML Kit Document Scanner | UI + model tải qua **Google Play services**; lần đầu có thể cần mạng | Lần đầu |
| OCR (Text Recognition v2) | Dùng artifact **bundled** `com.google.mlkit:text-recognition` — mô hình nằm trong APK | **Không** (kể cả lần đầu) |
| Lưu/đọc tài liệu | Room + file nội bộ app | Không |
| Quảng cáo (sau này) | AdMob | Cần mạng — nhưng scan/OCR/xuất vẫn chạy bình thường khi offline |

→ Câu marketing đúng: **"Tài liệu được xử lý trên thiết bị, không cần tải lên máy chủ."** Không quảng cáo "hoàn toàn offline 100%" vì Document Scanner có thể tải resource lần đầu qua Play services.

### 2.2. Quyết định 8 điểm (đọc trước khi code)

1. **Dùng luôn UI có sẵn của ML Kit Document Scanner** cho màn hình 2 (nó đã có cắt mép, xoay, filter, nhiều trang, sắp xếp trang). Bù lại: giao diện ít tùy chỉnh được và chỉ chạy được trên máy có Google Play services. Custom crop screen bằng Compose + OpenCV chỉ làm **sau** MVP nếu cần thương hiệu hóa.
2. **Kết quả scanner là URI tạm thời** (lưu trong MediaStore tạm, chỉ app mình truy cập được và có thể hết hạn). Sau khi scan xong phải **copy ngay vào `filesDir` của app**, rồi mới ghi dòng Room. Không bao giờ giữ URI của scanner làm nguồn sự thật.
3. **Nguồn sự thật của file = thư mục nội bộ app** (`filesDir/`); Room chỉ lưu metadata + đường dẫn. Storage Access Framework chỉ dùng cho nút **"Lưu bản sao ra…"** (export) và Share sheet. Nhờ vậy **không cần xin quyền bộ nhớ ngoài**.
4. **OCR chạy bằng artifact bundled** (`com.google.mlkit:text-recognition`) thay vì artifact qua Play services (`com.google.android.gms:play-services-mlkit-text-recognition`) — đảm bảo OCR chạy offline ngay lần mở đầu.
   > ⚠️ Đính chính (kiểm tra ngày 19/10/2026): tài liệu trước đây ghi `com.google.mlkit:text-recognition-latin`. **Artifact đó không tồn tại** (Google Maven trả 404). Bản bundled cho chữ Latin — gồm tiếng Việt có dấu — là `com.google.mlkit:text-recognition`, bản stable mới nhất `16.0.1`.
5. **AdMob = 100% nằm ngoài MVP.** Thêm quảng cáo sau khi có verdict closed testing, vì AdMob cần privacy policy + duyệt, và banner/interstitial tuyệt đối **không** đặt trong luồng chụp.
6. **Min SDK 23, target SDK = mức Play yêu cầu tại thời điểm nộp.**
7. **Một module `:app` ở Giai đoạn 1** (đã chốt lại theo `AGENTS.md` §2), ranh giới package trùng tên module tương lai; tách `:core:*`, `:platform:*`, `:feature:*` theo từng giai đoạn sau. Không dựng sẵn 13 module ngày đầu, không tạo `build-logic` khi chưa có ≥4 module, nhưng **không** để logic nằm lẫn trong UI hay `MainActivity`. Chi tiết: `ARCHITECTURE.md` mục 8.
8. **PDF v1 = ảnh-based PDF do Document Scanner trả về.** Text layer ẩn (Ctrl+F được) để v1.1.

### 2.3. Rủi ro đã nhận diện

| Rủi ro | Xử lý |
|---|---|
| Máy không có Google Play services (Huawei/Honor cũ) → Document Scanner không chạy | v1: hiện thông báo thân thiện + hướng dẫn thay thế (dùng ảnh từ gallery). Backlog: fallback OpenCV. |
| URI scanner hết hạn / mất quyền giữa chừng | Copy ngay vào app storage (điểm 2 ở trên); có try/catch + thông báo lỗi rõ ràng. |
| OCR sai trên ảnh mờ/nghiêng/chữ viết tay | Preprocess bằng filter có sẵn của scanner; ghi nhận giới hạn trong mô tả Play; bộ ảnh fixture test. |
| OCR không cho biết độ tin cậy từng dòng (ML Kit v2 không expose confidence) | Chỉ hiển thị trạng thái "không đọc được chữ nào" khi result rỗng; không bịa độ tin cậy. |
| Namespace `com.example.*` | Bắt buộc chọn namespace chính thức có thể đăng ký được. |

### 2.4. Dependency (versions: lấy bản stable mới nhất tại thời điểm code, kiểm tra docs chính thức)

```kotlin
// UI
implementation(platform("androidx.compose:compose-bom:<latest>"))
implementation("androidx.compose.material3:material3")
implementation("androidx.navigation:navigation-compose:<latest>")
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:<latest>")

// Data
implementation("androidx.room:room-runtime:<latest>")
implementation("androidx.room:room-ktx:<latest>")
kapt/implementation("androidx.room:room-compiler:<latest>") // hoặc ksp
implementation("androidx.datastore:datastore-preferences:<latest>")

// Scan (qua Google Play services, lần đầu có thể tải resource)
// Đã kiểm chứng trên Google Maven: 16.0.0 là bản stable (29/09/2025), không còn -beta.
implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")

// OCR (bundled — nằm trong APK, offline hoàn toàn)
// LƯU Ý: KHÔNG có artifact tên "text-recognition-latin" (đã kiểm tra 404).
// "text-recognition" chính là mô hình Latin, hỗ trợ tiếng Việt có dấu. Bản stable: 16.0.1.
implementation("com.google.mlkit:text-recognition:16.0.1")

// Chạy nền (OCR nhiều trang)
implementation("androidx.work:work-runtime-ktx:<latest>")
```

---

## 3. Cấu trúc project

### 3.1. Trạng thái đã code (Giai đoạn 1 + 2a) — MỘT module `:app`

Chưa chia module. Toàn bộ nằm trong `:app` với ranh giới package trùng tên module tương lai (chi tiết + lý do: `ARCHITECTURE.md` mục 8):

```
app/src/main/java/vn/scanviet/app/
├── ScanVietApplication.kt      @HiltAndroidApp + dọn dẹp lúc mở app
├── MainActivity.kt             entry duy nhất, chỉ setContent
├── core/                       → :core:common   (DispatcherProvider, text, format, time, file/PdfFileNames, di)
├── domain/                     → :core:domain
│   ├── model/, DocumentRules, exception
│   ├── repository/             DocumentRepository (cổng)
│   ├── storage/                DocumentFileStorage (cổng — Giai đoạn 2a chuyển từ data/)
│   ├── scan/                   PageImageReader + ScannedImage (cổng — Giai đoạn 2a)
│   ├── export/                 PdfExporter + PdfPageSource + DocumentOutputTarget/PdfOutputTarget (cổng — 2b)
│   └── usecase/                CreateDocument, RenameDocument, ImportScannedDocument, ExportDocumentAsPdf
├── data/                       → :core:data
│   ├── local/                  Room (entity, dao, database, converter)
│   ├── file/                   LocalDocumentFileStorage (cài đặt cổng storage)
│   ├── scan/                   ContentUriPageImageReader (cài đặt cổng scan)
│   ├── export/                 ContentUriDocumentOutputTarget (cài đặt cổng ghi — 2b)
│   ├── settings/, repository/, maintenance/, di/
│   └── dev/                    SampleDocumentFactory — CHỈ bản debug, R8 loại ở release
├── platform/                   → :platform:*
│   ├── scanner/                DocumentScannerEngine (cổng Android) + MlKitDocumentScannerEngine + di/
│   └── pdf/                    AndroidPdfExporter (PdfDocument) + di/ (Giai đoạn 2b)
├── ui/                         → :feature:*     (theme, common, components, library, scan, settings, root)
└── navigation/                 → :app           (nơi DUY NHẤT biết mọi màn hình)
```

Kiểm tra được bằng mắt: `domain/` không có import nào của `androidx.compose.*`, `androidx.room.*`, `android.*` hay `vn.scanviet.app.data.*`.

Ba cổng dùng kiểu gần-Android (`DocumentFileStorage` dùng `java.io.File`; `PageImageReader` dùng `String` URI; `PdfExporter` dùng `java.io.File` + `OutputStream`) là chủ ý: chúng vẫn là hợp đồng thuần Kotlin/JVM nên use case kiểm thử được bằng cài đặt giả. Thứ **không thể** diễn đạt bằng domain — `DocumentScannerEngine` cần `Activity` và `IntentSender` — được đẩy ra `platform/scanner/` để domain sạch.

### 3.2. Thiết kế đích (khi tách module)

Đã chuyển sang kế hoạch module hoá có lộ trình — xem **`ARCHITECTURE.md` mục 8** (danh sách module, quy tắc phụ thuộc, `build-logic` + version catalog, lộ trình tách module theo giai đoạn).

Tóm tắt: `:app` chỉ wiring → `:feature:*` (presentation + use case) → `:core:*` (model, designsystem, data) + `:platform:*` (scanner, ocr, pdf, ads). `:core:data` là nơi **duy nhất** chạm Room/DataStore/FileStore. Feature không phụ thuộc feature.

Quy ước bất di bất dịch: wrapper scanner chỉ scan; OCR engine chỉ nhận ảnh trả văn bản; repository tập trung dữ liệu; **không** để logic trong `MainActivity`.

---

## 4. Lộ trình (ước lượng, không phải deadline)

| Giai đoạn | Nội dung | Ước lượng |
|---|---|---|
| 1. Nền tảng | Dựng project Compose + Nav + ViewModel, trang chủ + list rỗng, chạy trên máy thật, Git | 2–4 ngày |
| 2. Scan & PDF | Tích hợp Document Scanner (nhiều trang), copy kết quả vào app storage, xem trước, đặt tên, lưu, chia sẻ, xử lý huỷ/thiếu file | 3–6 ngày |
| 3. OCR tiếng Việt | Đọc từng ảnh trang, ghép text theo trang, sửa tay, copy/share, báo lỗi khi không đọc được | 3–6 ngày |
| 4. Quản lý tài liệu | Room metadata, đổi tên/tìm kiếm/xóa/mở lại, xử lý file mất/quyền hết | 3–5 ngày |
| 5. Kiểm thử & phát hành | Test offline/quyền/crash, đo OCR trên tài liệu thật, tối ưu dung lượng PDF, icon + screenshots + privacy policy, closed testing | 4–7 ngày |

**Tổng: ~3–6 tuần** (học + làm đều đặn, có AI hỗ trợ). Mỗi giai đoạn = 1 merge/commit riêng, build được thì mới sang bước sau.

---

## 5. Quy trình dùng AI để code

Làm theo từng nhiệm vụ nhỏ; **không** yêu cầu sinh cả app một lần:

1. Yêu cầu AI giải thích thiết kế + liệt kê file cần sửa.
2. AI triển khai **đúng một** chức năng.
3. Build ngay → chạy trên thiết bị → sửa lỗi → commit Git.
4. Không nhận câu "code đúng rồi" — chỉ tin kết quả build, test và hành vi thật.

Prompt mẫu (nền tảng — Giai đoạn 1):

```
Bạn là Android developer kinh nghiệm Kotlin + Jetpack Compose.
Dự án: ScanViet — app scan tài liệu, OCR tiếng Việt, lưu cục bộ.
Tôi là người mới Android, muốn hiểu code, không chỉ nhận code.
Nhiệm vụ: dựng nền tảng — CHƯA scan, OCR, quảng cáo, backend.
Yêu cầu: Kotlin native; Compose + Material 3; MVVM; Navigation Compose;
UI tiếng Việt tối giản; trang chủ có nút "Quét tài liệu" + danh sách rỗng;
tách UI/state/navigation; không thêm thư viện thừa; build được trên
Android Studio/AGP mà dự án đang dùng.
Đầu tiên: đề xuất cấu trúc file + giải thích ngắn. Sau đó triển khai
từng file, nêu rõ đường dẫn, dependency cần thêm, cách kiểm thử.
```

---

## 6. Kiểm thử

### 6.1. Đã có test tự động (140 test, 0 lỗi — chạy `./gradlew testDebugUnitTest`)

| Nhóm | Test | Nội dung |
|---|---|---|
| Tiện ích | `FormattersTest` (9), `VnTextNormalizerTest` (8) | định dạng ngày/dung lượng; tìm kiếm không dấu |
| Tên file PDF | `PdfFileNamesTest` (9) | bỏ dấu tiếng Việt (`đ` → `d`, không xoá); **loại `\/` và `..`** (ranh giới bảo mật tên file); loại ký tự hệ điều hành cấm; cắt ngắn tên dài; tên rỗng/toàn ký tự lạ → tên dự phòng dùng được |
| Lưu trữ file | `LocalDocumentFileStorageTest` (15) | đường dẫn tương đối, ghi `.tmp` rồi đổi tên, chặn thoát khỏi vùng app, xoá thư mục; **file nháp nằm trong `cacheDir` không cùng chỗ tài liệu**; **tên file nháp chứa dấu `/` hay `..` bị từ chối**; dọn nháp xoá được file nháp |
| Ghi PDF ra đích | `ContentUriDocumentOutputTargetTest` (5) | ghi file thật đúng nội dung; **ghi đè nội dung ngắn hơn phải cắt đuôi file cũ** (nếu không PDF sẽ hỏng); tạo thư mục cha; URI/junk sai → `IOException` rõ ràng |
| Luồng quét — nhập ảnh | `ImportScannedDocumentUseCaseTest` (9) | 2 trang → đủ 2 file + 2 bản ghi; giữ đúng thứ tự; nội dung file khớp bytes; **lỗi ghi Room → xoá sạch thư mục**; 1 trang đọc lỗi → lỗi riêng + dọn file; lỗi đọc ảnh **không mang theo URI**; từ chối tên rỗng/quá dài **trước khi** ghi file |
| Luồng xuất PDF — nghiệp vụ | `ExportDocumentAsPdfUseCaseTest` (11) | đúng số trang và đúng file; **sắp theo `pageIndex` ngay cả khi database trả về lộn thứ tự**; truyền góc xoay; thiếu ảnh → `PageImageMissingException` **kèm số trang**, không xuất PDF thiếu trang; lỗi thiếu ảnh **không mang đường dẫn**; tài liệu không tồn tại/rỗng → từ chối **trước khi mở stream**; **stream đóng đúng một lần, kể cả khi exporter lỗi**; mở đích ghi đúng một lần cho tài liệu nhiều trang |
| Luồng xuất PDF — màn hình | `DocumentDetailViewModelTest` (10) | lưu bản sao → ghi PDF + báo thành công; truyền đúng URI người dùng chọn; chia sẻ → **file PDF thật tồn tại trong vùng nháp**; tên file theo tên tài liệu (bỏ dấu); xoá yêu cầu chia sẻ sau khi xử lý (xoay màn hình không mở lại); thiếu ảnh → thông báo **kèm số trang** và **không** phát yêu cầu chia sẻ; lỗi ghi/lỗi mở đích → `isExporting` trở về `false` (nút không kẹt) |
| Luồng quét — màn hình | `ScanViewModelTest` (17) | chuẩn bị quét → phát `IntentSender`; xoá yêu cầu sau khi dùng; máy không hỗ trợ → thông báo riêng; huỷ → về trạng thái chờ; nhận kết quả → danh sách trang + tên mặc định; chuyển trang lên/xuống, bỏ qua chỉ số ngoài danh sách; xoá trang; xoá hết trang → không cho lưu; lưu thành công → file thật + bản ghi + `Saved`; lưu lỗi → giữ nguyên trang, có thông báo |
| Thư viện | `LibraryViewModelTest` (13), `DocumentStorageMaintenanceTest` (7) | trạng thái rỗng/tìm kiếm/đổi tên/xoá; dọn thùng rác quá hạn, thư mục mồ côi |
| Giới hạn của bộ test | `RobolectricPdfLimitationTest` (1) | ghi lại **bằng chứng** rằng Robolectric không chạy được `PdfDocument` (`startPage` → `IllegalStateException: document is closed!`), để không ai tưởng phần sinh PDF đã được kiểm thử |

**Điều test tự động KHÔNG chứng minh được:** chính trình quét của ML Kit (nó là Activity của Play services), việc cấp quyền camera, việc tải công cụ quét lần đầu, **chất lượng file PDF do `android.graphics.pdf.PdfDocument` sinh ra**, và mọi thứ về bố cục/giao diện. Xem 6.3.

### 6.2. Dữ liệu thật cho OCR (Giai đoạn 3)

OCR là tính năng tạo khác biệt → chuẩn bị **bộ dữ liệu thật ngay từ đầu**.

| Nhóm tài liệu | Kiểm tra |
|---|---|
| Văn bản in tiếng Việt | Dấu tiếng Việt, xuống dòng, dấu câu |
| Nhiều trang | Thứ tự trang, không thiếu trang |
| Hóa đơn | Chữ nhỏ, số tiền, ký hiệu |
| Ảnh nghiêng / thiếu sáng | Cắt mép + chất lượng OCR |
| Tài liệu có bảng | Thứ tự đọc dòng/cột |
| Chữ viết tay | Ghi nhận giới hạn (không hứa) |
| File lớn | Bộ nhớ, thời gian xử lý, nguy cơ crash |

**Tiêu chí dừng (exit criteria) cho Giai đoạn 3:** trên ≥20 ảnh tài liệu in tiếng Việt thật, ≥90% dòng chữ đọc đúng nguyên vẹn; ảnh mờ/chữ viết tay chỉ cần ghi nhận "kết quả kém" chứ không crash.

### 6.3. Việc PHẢI kiểm tra bằng tay trên máy thật (chưa làm được ở môi trường hiện tại)

Không có thiết bị/emulator trong môi trường phát triển, nên các mục sau **chưa từng chạy**:

1. Quét 1 trang, 5 trang, 20 trang → lưu → mở lại thấy đủ trang, không thiếu trang.
2. Huỷ giữa lúc quét → quay về màn hình quét, **không** có tài liệu rác.
3. Quay màn hình trong lúc đang quét → trình quét **không** tự bật lại.
4. Máy không có Google Play services / Play services cũ → hiện thông báo "Thiết bị này chưa quét được".
5. Lần quét đầu tiên ở chế độ máy bay → xác nhận việc tải công cụ quét cần mạng (đây là cơ sở cho câu marketing).
6. Sau khi tải xong, **bật máy bay** rồi quét lại → phải chạy được.
7. Đóng app giữa lúc đang lưu (bấm Lưu rồi tắt app) → mở lại: không có tài liệu nửa vời, không có thư mục rác.
8. Giao diện: tiếng Việt dài có bị cắt không, chế độ tối, màn hình nhỏ.

**Xuất và chia sẻ PDF (Giai đoạn 2b) — cũng chưa từng chạy:**

9. Mở một tài liệu nhiều trang → "Lưu bản PDF" → chọn thư mục Tải về → mở file bằng trình đọc PDF của máy: **đủ trang, đúng thứ tự, không bị cắt mép chữ**.
10. Kiểm tra khổ trang: tài liệu A4 in ra vừa tờ; ảnh chụp ngang tạo ra trang A4 **ngang**, không bị bóp méo.
11. "Chia sẻ PDF" → chọn Zalo/Gmail → file nhận được mở được ở đầu bên kia.
12. Chia sẻ vào ứng dụng chỉ nhận ảnh hoặc không nhận PDF → app **không** crash (hộp thoại chia sẻ không nên hiện ứng dụng đó, nhưng nếu có thì phải hiện thông báo).
13. Xuất lại vào **cùng một file** đã có nội dung dài hơn → file kết quả phải mở được (kiểm tra chế độ ghi đè `"wt"`; nếu để lại đuôi file cũ thì PDF sẽ hỏng).
14. Xoá file ảnh của một trang (qua trình quản lý file / adb) rồi bấm xuất → phải báo **"Trang N bị thiếu file ảnh"**, **không** tạo ra PDF thiếu trang.
15. Máy ít dung lượng: xuất tài liệu 20 trang → không bị kill giữa chừng, không hết RAM.
16. Chọn nơi lưu là Google Drive → xác nhận file thật sự được tải lên (SAF qua provider là con đường dễ lộ lỗi cấp quyền nhất).

Lý do các mục 9–16 phải làm bằng tay: Robolectric **không chạy được** `android.graphics.pdf.PdfDocument` (xem `RobolectricPdfLimitationTest`), nên chất lượng file PDF do `platform/pdf/AndroidPdfExporter` sinh ra không thể xác nhận bằng unit test. Ngược lại, mục 13 và 14 có test tự động bảo vệ phần logic; việc kiểm tay là để xác nhận bản thân file PDF đúng.

Quy trình mỗi lần test: chạy offline hoàn toàn (máy chế độ máy bay) → xác nhận scan/OCR/xuất PDF vẫn chạy → ghi scorecard thủ công theo nhóm tài liệu. Sau này thêm mini-harness: thả ảnh fixture vào `androidTest/assets`, so khớp text kỳ vọng.

---

## 7. Checklist trước khi phát hành

- [ ] Namespace chính thức (không `com.example`), tên app kiểm tra chưa trùng trên Play
- [ ] Privacy Policy (GitHub Pages free) + Data Safety form trung thực ("xử lý trên thiết bị, không thu thập")
- [ ] Icon + 4–8 screenshots tiếng Việt + short description chứa từ khoá: scan tài liệu, quét tài liệu, OCR tiếng Việt, PDF
- [ ] Closed testing ≥14 ngày với ≥12 tester (bắt buộc với dev mới)
- [ ] In-app review trigger sau lần export PDF thành công thứ 3
- [ ] Crashlytics (free) nếu kịp — cần privacy policy trước
