# ScanViet — Đặc tả MVP (chốt phạm vi)

> Nguyên tắc: **không làm bản sao CamScanner**. V1 làm tốt đúng 3 việc:
> 1. Scan giấy tờ → PDF rõ, nhiều trang, dễ chia sẻ.
> 2. OCR tiếng Việt **ngay trên máy**, không gửi tài liệu lên server. 🟡 **Đã tích hợp engine đúng (Tesseract, §2.5) nhưng CHƯA đo chất lượng trên ảnh thật** — xem §6.2. Không được ghi "OCR tiếng Việt" trong mô tả Play cho tới khi đo xong.
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
- ~~PDF có text layer ẩn (tìm kiếm được bằng Ctrl+F)~~ → **đã làm ở Giai đoạn 3** (xem §6.3 mục 20 để kiểm bằng tay trên máy tính).
- Quảng cáo → chỉ thêm **sau** closed testing cho kết quả tốt.

---

## 2. Quyết định kỹ thuật đã chốt

### 2.1. Chọn đúng "bản đồ offline" (quan trọng cho marketing trung thực)

| Thành phần | Cách hoạt động | Cần mạng? |
|---|---|---|
| ML Kit Document Scanner | UI + model tải qua **Google Play services**; lần đầu có thể cần mạng | Lần đầu |
| OCR (Text Recognition v2) | Mô hình nằm trong APK (bundled) | **Không** — nhưng xem §2.5: không hỗ trợ tiếng Việt có dấu |
| Lưu/đọc tài liệu | Room + file nội bộ app | Không |
| Kiểm tra cập nhật | Đọc JSON công khai từ GitHub Releases, có thể tắt trong Cài đặt | Có (chỉ khi kiểm tra) |
| Quảng cáo (sau này) | AdMob | Cần mạng — nhưng scan/OCR/xuất vẫn chạy bình thường khi offline |

→ Câu marketing đúng: **"Tài liệu được xử lý trên thiết bị, không cần tải lên máy chủ."** Không quảng cáo "hoàn toàn offline 100%" vì Document Scanner có thể tải resource lần đầu qua Play services.
→ **Không** quảng cáo "OCR tiếng Việt" cho tới khi thay engine OCR (§2.5).

### 2.5. Engine OCR — trạng thái thật và các phương án

ML Kit Text Recognition v2 **không nhận dạng được tiếng Việt có dấu**. Đây là kết luận sau khi đọc tài liệu Google chính thức và kiểm tra model trong APK đã đóng gói, không phải phỏng đoán.

| Engine | Tiếng Việt có dấu | Offline | Trạng thái | Chi phí / rủi ro |
|---|---|---|---|---|
| **Tesseract + `vie.traineddata`** | ✅ Có | ✅ | ✅ **Đã tích hợp (Giai đoạn B, mặc định)** | Miễn phí, Apache 2.0. Độ chính xác thấp hơn trên ảnh chụp nghiêng/thiếu sáng. |
| ML Kit Text Recognition v2 | ❌ **Không** | ✅ | ✅ Đã tích hợp (chỉ cho tiếng Anh) | Miễn phí. Mạnh hơn Tesseract trên tài liệu **tiếng Anh in rõ** (mô hình nơ-ron), nhưng **bỏ dấu tiếng Việt**. |
| ML Kit Document Scanner | — | — | — | **Không có chức năng OCR.** Không phải phương án. |
| **PaddleOCR / ONNX Runtime Mobile** | ✅ Khá tốt | ✅ | ⏳ Chưa làm | Miễn phí. Mô hình lớn, cần cầu nối native. Chất lượng cao hơn Tesseract. |
| Google Cloud Vision | ✅ Rất tốt | ❌ | ⏳ Chưa làm | Phải gửi ảnh lên máy chủ Google → cần người dùng tự bật + cảnh báo rõ. Cần API key, không được nhúng trong APK. |

Hướng đã chốt với chủ dự án: **hybrid** — Tesseract mặc định (offline), PaddleOCR là engine thứ hai để so sánh chất lượng thật, Cloud Vision là tùy chọn nâng cao do người dùng tự bật và đã xác nhận cảnh báo.

Cổng `OcrEngine` đã tách sẵn, nên **thay engine không đụng tới Room, ViewModel, hay PDF exporter**. Đây chính là lợi ích của Clean Architecture đã chọn. Trên thực tế Giai đoạn B đã chứng minh điều này: thêm Tesseract chỉ cần thêm một lớp ở `platform/ocr/` và sửa `OcrModule`, **không sửa một dòng nào** trong Room, PDF exporter, hay luồng quét.

#### 2.5.1. Chi tiết Tesseract (đã tích hợp ngày 20/10/2026)

**Nguồn artifact — đã kiểm chứng, không suy đoán:**

| Mục | Giá trị | Cách kiểm chứng |
|---|---|---|
| Thư viện | `cz.adaptech.tesseract4android:tesseract4android:4.9.0` | HTTP 200, AAR 12,25 MB |
| Repository | **JitPack** (KHÔNG có trên Maven Central) | `search.maven.org` với `g:cz.adaptech` → 0 kết quả; `repo1.maven.org/maven2/cz/adaptech/` → 404 |
| Giấy phép | Apache 2.0 (gồm Tesseract 5.5.1 + Leptonica 1.85.0) | README của thư viện |
| Yêu cầu | Android 5.0 (API 21)+ | Ứng dụng minSdk 23 → thoả |

**File model — tải về và đối chiếu với GitHub:**

| Model | Dung lượng | Xuất xứ | Kiểm chứng |
|---|---|---|---|
| `vie.traineddata` (best) | 12.435.550 byte | `tesseract-ocr/tessdata_best` | ✅ Khớp **byte-for-byte** với GitHub API |
| `vie.traineddata` (fast) | 531.275 byte | `tesseract-ocr/tessdata_fast` | ✅ Khớp **byte-for-byte** với GitHub API |

SHA256 của file đã tải:

```
vie_best.traineddata  b6b49293d95d0b6dbd8780174627e82c75be957b6f4ed9862155540d6b00bb45
vie_fast.traineddata  79df64caf7bcfb2a27df5042ecb6121e196eada34da774956995747636d5bfa1
```

> Dự án Tesseract **không phát hành file checksum** cho hai file này (đã kiểm chứng: `vie.traineddata.md5` trả 404). Bằng chứng thay thế là **kích thước byte khớp tuyệt đối** với giá trị GitHub API công bố, và git blob SHA của nội dung.

**Hai model, người dùng chọn:** cả hai đều được đóng gói trong APK (bắt buộc, để chạy offline ngay sau khi cài). Trên đĩa chỉ có **một** model tại một thời điểm — đổi chất lượng sẽ xoá bản cũ (xem `AssetTessdataProvisioner`). Đánh đổi dung lượng APK: **+12,4 MB** (đã đo: debug 31,15 → 55,34 MB; release 20,59 → 44,81 MB).

**Lấy toạ độ để vẽ lớp chữ ẩn:** Tesseract chỉ trả toạ độ qua `ResultIterator`, không qua `getUTF8Text()`. Dùng mức **`RIL_TEXTLINE`** — Tesseract đã phân tích bố cục trang để quyết định từ nào thuộc dòng nào, nên đọc thẳng ở mức dòng đúng hơn là tự ghép từ bằng cách so chồng lấn hộp bao. Phép chuyển pixel → toạ độ chuẩn hoá được tách ra `domain/ocr/NormalizedOcrLine.kt` để **kiểm thử được trên JVM thuần**; 12 test ở đó khoá chặt hành vi, và đã kiểm chứng rằng cố tình tái tạo lỗi cũ (`bottom = 0f`) làm **6/12 test gãy**.

### 2.2. Quyết định 8 điểm (đọc trước khi code)

1. **Dùng luôn UI có sẵn của ML Kit Document Scanner** cho màn hình 2 (nó đã có cắt mép, xoay, filter, nhiều trang, sắp xếp trang). Bù lại: giao diện ít tùy chỉnh được và chỉ chạy được trên máy có Google Play services. Custom crop screen bằng Compose + OpenCV chỉ làm **sau** MVP nếu cần thương hiệu hóa.
2. **Kết quả scanner là URI tạm thời** (lưu trong MediaStore tạm, chỉ app mình truy cập được và có thể hết hạn). Sau khi scan xong phải **copy ngay vào `filesDir` của app**, rồi mới ghi dòng Room. Không bao giờ giữ URI của scanner làm nguồn sự thật.
3. **Nguồn sự thật của file = thư mục nội bộ app** (`filesDir/`); Room chỉ lưu metadata + đường dẫn. Storage Access Framework chỉ dùng cho nút **"Lưu bản sao ra…"** (export) và Share sheet. Nhờ vậy **không cần xin quyền bộ nhớ ngoài**.
4. **OCR chạy bằng artifact bundled** (`com.google.mlkit:text-recognition`) thay vì artifact qua Play services (`com.google.android.gms:play-services-mlkit-text-recognition`) — đảm bảo mô hình nằm sẵn trong APK, không phải chờ tải về.
   > ⚠️ Đính chính (kiểm tra ngày 19/10/2026): tài liệu trước đây ghi `com.google.mlkit:text-recognition-latin`. **Artifact đó không tồn tại** (Google Maven trả 404).
   >
   > 🔴 **ĐÍNH CHÍNH LẦN 2 — QUAN TRỌNG (20/10/2026):** tài liệu này từng ghi artifact bundled "gồm tiếng Việt có dấu". **Điều đó SAI.** Kiểm tra tài liệu Google chính thức và chính APK đã đóng gói:
   > - Text Recognition v2 chỉ hỗ trợ **5 hệ chữ**: Latin, Chinese, Devanagari, Japanese, Korean. **Không có tiếng Việt.**
   > - Model đóng gói thật trong APK là `Latn_ctc` (`assets/mlkit-google-ocr-models/.../Latn_ctc/optical/conv_model.fb`), và nó **bỏ dấu** hoặc trả chuỗi rỗng với văn bản tiếng Việt.
   > - **API Document Scanner KHÔNG có chức năng OCR.** Nó chỉ trả ảnh JPEG và PDF dạng ảnh. Không dùng nó để thay thế.
   >
   > **Hệ quả:** tính năng "nhận dạng chữ tiếng Việt" **chưa đạt yêu cầu** và không được quảng cáo cho tới khi thay engine (xem §2.5). Lớp chữ ẩn trong PDF vẫn được vẽ đúng kỹ thuật, nhưng nội dung chữ do engine hiện tại sinh ra không đáng tin.
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
| **ML Kit v2 không hỗ trợ tiếng Việt có dấu** (đã xác nhận 20/10/2026) | 🔴 **Rủi ro đã thành hiện thực.** Phải thay engine (§2.5) trước khi quảng cáo tính năng. Không sửa được bằng cấu hình. |
| OCR không cho biết độ tin cậy từng dòng (ML Kit v2 không expose confidence) | Chỉ hiển thị trạng thái "không đọc được chữ nào" khi result rỗng; không bịa độ tin cậy. |
| Tự cập nhật qua GitHub Releases im lặng không báo (repo private trả 404; bản pre-release bị `/releases/latest` bỏ qua) | Đã kiểm chứng: `/releases/latest` trả 404 với repo private và với bản pre-release. Bản phát hành muốn app nhìn thấy **phải là bản chính thức, không tick pre-release**. Lỗi được xử lý thành thông báo "không kiểm tra được", không nói dối là "đã mới nhất". |
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
| 3. OCR tiếng Việt | Đọc từng ảnh trang, ghép text theo trang, sửa tay, copy/share, báo lỗi khi không đọc được. **Trạng thái: khung kỹ thuật xong, engine chưa đạt — xem §2.5.** | 3–6 ngày |
| 4. Quản lý tài liệu | Room metadata, đổi tên/tìm kiếm/xóa/mở lại, xử lý file mất/quyền hết. **Trạng thái: xong (Giai đoạn 3 của mã nguồn).** | 3–5 ngày |
| 4b. Thay engine OCR | Tesseract + `vie.traineddata` (offline) và/hoặc PaddleOCR; Cloud Vision là tùy chọn có cảnh báo. Cổng `OcrEngine` đã tách sẵn nên không đụng Room/ViewModel/PDF. | 5–10 ngày |
| 5. Cập nhật & phát hành | Cơ chế tự cập nhật qua GitHub Releases (**Trạng thái: xong**), tự động cập nhật qua Play sau này, icon + screenshots + privacy policy, closed testing | 4–7 ngày |
| 6. Kiểm thử & phát hành | Test offline/quyền/crash, đo OCR trên tài liệu thật, tối ưu dung lượng PDF, closed testing | 4–7 ngày |

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

### 6.1. Đã có test tự động (234 test / 0 lỗi / 0 bỏ qua / 24 suite — chạy `./gradlew :app:testDebugUnitTest`)

| Nhóm | Test | Nội dung |
|---|---|---|
| Tiện ích | `FormattersTest` (9), `VnTextNormalizerTest` (8) | định dạng ngày/dung lượng; tìm kiếm không dấu |
| Tên file PDF | `PdfFileNamesTest` (9) | bỏ dấu tiếng Việt (`đ` → `d`, không xoá); **loại `\/` và `..`** (ranh giới bảo mật tên file); loại ký tự hệ điều hành cấm; cắt ngắn tên dài; tên rỗng/toàn ký tự lạ → tên dự phòng dùng được |
| Lưu trữ file | `LocalDocumentFileStorageTest` (15) | đường dẫn tương đối, ghi `.tmp` rồi đổi tên, chặn thoát khỏi vùng app, xoá thư mục; **file nháp nằm trong `cacheDir` không cùng chỗ tài liệu**; **tên file nháp chứa dấu `/` hay `..` bị từ chối**; dọn nháp xoá được file nháp |
| Ghi PDF ra đích | `ContentUriDocumentOutputTargetTest` (5) | ghi file thật đúng nội dung; **ghi đè nội dung ngắn hơn phải cắt đuôi file cũ** (nếu không PDF sẽ hỏng); tạo thư mục cha; URI/junk sai → `IOException` rõ ràng |
| Luồng quét — nhập ảnh | `ImportScannedDocumentUseCaseTest` (9) | 2 trang → đủ 2 file + 2 bản ghi; giữ đúng thứ tự; nội dung file khớp bytes; **lỗi ghi Room → xoá sạch thư mục**; 1 trang đọc lỗi → lỗi riêng + dọn file; lỗi đọc ảnh **không mang theo URI**; từ chối tên rỗng/quá dài **trước khi** ghi file |
| Luồng xuất PDF — nghiệp vụ | `ExportDocumentAsPdfUseCaseTest` (11) | đúng số trang và đúng file; **sắp theo `pageIndex` ngay cả khi database trả về lộn thứ tự**; truyền góc xoay; thiếu ảnh → `PageImageMissingException` **kèm số trang**, không xuất PDF thiếu trang; lỗi thiếu ảnh **không mang đường dẫn**; tài liệu không tồn tại/rỗng → từ chối **trước khi mở stream**; **stream đóng đúng một lần, kể cả khi exporter lỗi**; mở đích ghi đúng một lần cho tài liệu nhiều trang |
| Luồng xuất PDF — màn hình | `DocumentDetailViewModelTest` (10) | lưu bản sao → ghi PDF + báo thành công; truyền đúng URI người dùng chọn; chia sẻ → **file PDF thật tồn tại trong vùng nháp**; tên file theo tên tài liệu (bỏ dấu); xoá yêu cầu chia sẻ sau khi xử lý (xoay màn hình không mở lại); thiếu ảnh → thông báo **kèm số trang** và **không** phát yêu cầu chia sẻ; lỗi ghi/lỗi mở đích → `isExporting` trở về `false` (nút không kẹt) |
| Luồng quét — màn hình | `ScanViewModelTest` (25) | chuẩn bị quét → phát `IntentSender`; xoá yêu cầu sau khi dùng; máy không hỗ trợ → thông báo riêng; huỷ → về trạng thái chờ; nhận kết quả → danh sách trang + tên mặc định; chuyển trang lên/xuống, bỏ qua chỉ số ngoài danh sách; xoá trang; xoá hết trang → không cho lưu; lưu thành công → file thật + bản ghi + `Saved`; lưu lỗi → giữ nguyên trang, có thông báo. **Tự động nhận dạng sau khi lưu:** bật → có nhận dạng; **tắt → KHÔNG nhận dạng** (đây là hành vi mà công tắc hứa); mặc định là bật; **lỗi nhận dạng → tài liệu VẪN được lưu** và trạng thái vẫn là `Saved` (không quay về màn hình xem lại, tránh tạo tài liệu trùng); lỗi → có thông báo, thành công → im lặng; nhiều trang → mỗi trang đúng một lần |
| Thư viện | `LibraryViewModelTest` (13), `DocumentStorageMaintenanceTest` (7) | trạng thái rỗng/tìm kiếm/đổi tên/xoá; dọn thùng rác quá hạn, thư mục mồ côi |
| Nhận dạng chữ — nghiệp vụ | `RecognizeDocumentTextUseCaseTest` (17) | bỏ qua trang đã nhận dạng (không chạy lại tốn thời gian); một trang lỗi không làm hỏng cả tài liệu; lưu toạ độ chuẩn hoá; tài liệu không tồn tại → lỗi rõ ràng. **Chọn engine theo cài đặt tại thời điểm chạy:** mặc định Tesseract; chọn ML Kit → không cài model Tesseract; **thiếu model → ném `TessdataInstallException`, KHÔNG báo thành công rỗng**; **thiếu model → không đánh dấu tài liệu là `RUNNING`** (giao diện không kẹt). **Lưu ý: test này dùng engine giả, nên nó chứng minh luồng nghiệp vụ, KHÔNG chứng minh chất lượng nhận dạng chữ tiếng Việt.** |
| Nhận dạng chữ — toạ độ | `NormalizedOcrLineTest` (12) | chuẩn hoá pixel → `0..1` dùng đúng `x+w`/`y+h` (nhầm thành `w`/`h` sẽ vẽ chữ lệch về góc trang); **mọi hộp bao hợp lệ đều cho `hasUsableBox == true`** (điều kiện để lớp chữ ẩn trong PDF được vẽ); chữ rỗng/hộp bao 0x0/âm/sai định dạng/kích thước ảnh không hợp lệ đều bị loại; hộp bao nhô ra ngoài ảnh được kẹp về `0..1`; hộp bao nằm hoàn toàn ngoài ảnh bị loại. **Đã kiểm chứng test thật sự bắt lỗi: cố tình trả `bottom = 0f` (tái tạo lỗi cũ) → 6/12 test FAIL** |
| Nhận dạng chữ — cài model | `AssetTessdataProvisionerTest` (9) | giải nén **file model THẬT** qua `AssetManager` của Robolectric; đúng thư mục `tessdata/` và đúng tên `vie.traineddata` mà Tesseract tìm; nằm trong `filesDir` **chứ không phải `cacheDir`**; `FAST` nhỏ và `BEST` lớn đúng như công bố; đổi chất lượng → model được thay; **chỉ giữ MỘT model trên đĩa** và không còn file `.tmp`; gọi lại nhiều lần không tạo thêm file |
| Cài đặt — lưu engine | `DataStoreSettingsRepositoryTest` (6) | dùng **DataStore thật**: mặc định Tesseract + BEST; chọn ML Kit/FAST đọc lại đúng; đổi engine **không ghi đè** các cài đặt khác; **giá trị lạ trong file cài đặt → rơi về mặc định, không ném lỗi**; lưu bằng `storageValue` ổn định chứ không phải tên hằng số enum |
| Nâng cấp cơ sở dữ liệu | `ScanVietDatabaseMigrationTest` (3) | dựng schema v1 bằng SQL thô với đúng identity hash từ `app/schemas/.../1.json` rồi mở bằng Room v2: dữ liệu cũ còn nguyên, bảng OCR mới tồn tại, ghi/đọc được. **Đã chứng minh test thật sự bắt lỗi** bằng cách cố tình đổi tên bảng `ocr_lines` → cả 3 test FAIL |
| So sánh phiên bản | `VersionComparatorTest` (13) | `0.10.0` mới hơn `0.9.0` (so chuỗi thì ngược lại — đây là lý do lớp này tồn tại); bỏ tiền tố `v`/`V`; bỏ hậu tố `-beta`; phần thiếu coi là 0; chuỗi không có số → phiên bản 0 |
| Cập nhật — nghiệp vụ | `CheckForUpdateUseCaseTest` (7) | bản mới hơn → `Available`; **cùng phiên bản → `UpToDate`**; nguồn không có release → `UpToDate`; **lỗi mạng ném `UpdateCheckFailedException` chứ KHÔNG nói dối "đã là bản mới nhất"** |
| Cập nhật — đọc GitHub | `GitHubReleaseSourceTest` (12) | đọc đúng `tag_name`/`size`/`body`; **bỏ tiền tố `v`**; gọi đúng URL và header `Accept`; **ưu tiên APK `arm64`**; bỏ qua file không phải `.apk`; thiếu `tag_name`/URL tải/danh sách rỗng → lỗi rõ ràng; JSON hỏng và lỗi mạng đều thành `UpdateCheckFailedException` |
| Cập nhật — trạng thái tải | `UpdateUiStateTest` (5) | phần trăm đúng; không biết tổng dung lượng → `null` (không bịa số); **không vượt quá 100%** khi máy chủ báo thiếu `Content-Length` |
| Giới hạn của bộ test | `RobolectricPdfLimitationTest` (1) | ghi lại **bằng chứng** rằng Robolectric không chạy được `PdfDocument` (`startPage` → `IllegalStateException: document is closed!`), để không ai tưởng phần sinh PDF đã được kiểm thử |

**Điều test tự động KHÔNG chứng minh được:** chính trình quét của ML Kit (nó là Activity của Play services), việc cấp quyền camera, việc tải công cụ quét lần đầu, **chất lượng file PDF do `android.graphics.pdf.PdfDocument` sinh ra** (kể cả **lớp chữ ẩn có thật sự copy được bằng Ctrl+F hay không** — Robolectric không chạy nổi `PdfDocument`), **chất lượng nhận dạng chữ tiếng Việt** (engine giả trong test không chứng minh được điều này), việc mở trình cài đặt APK của hệ thống, và mọi thứ về bố cục/giao diện. Xem 6.3.

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

> 🟡 **Tiêu chí này CHƯA ĐO ĐƯỢC.** Engine đúng đã được tích hợp (Tesseract + `vie.traineddata`, §2.5), và đây là lần đầu tiên ứng dụng **có khả năng** đọc tiếng Việt có dấu. Nhưng **chưa có phép đo nào trên ảnh thật** — bộ test tự động dùng engine giả, không chứng minh được chất lượng nhận dạng (§6.1).
>
> **Điều đã biết chắc:** cấu hình sai thì đã tránh được — mặc định là Tesseract, và ML Kit (engine không đọc được tiếng Việt) phải do người dùng chủ động chọn, kèm cảnh báo ngay trên màn hình Cài đặt.
>
> **Điều CHƯA biết:** tỉ lệ dòng đọc đúng thực tế là bao nhiêu. Có thể là 85%, có thể là 95%. **Không được đoán con số này.** **Không được ghi "OCR tiếng Việt" trong mô tả Play cho tới khi có số đo thật.**
>
> Việc cần làm tiếp: chuẩn bị ≥20 ảnh tài liệu in tiếng Việt thật, chạy cả hai engine (Tesseract `best`, Tesseract `fast`) trên cùng bộ ảnh, đếm tỉ lệ dòng đúng, rồi ghi số liệu thật vào đây.

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

**Xem trang và lớp chữ ẩn (Giai đoạn 3) — chưa từng chạy:**

17. Mở tài liệu → **xem ảnh thu nhỏ** trong danh sách thư viện: có hiện đúng trang đầu không, cuộn danh sách dài có giật không.
18. Mở **trình xem toàn màn hình** → chụm hai ngón để phóng to: ảnh phải nét (không vỡ hạt), vuốt ngang đổi trang, đóng ra không giữ trạng thái zoom của trang cũ.
19. Xoay màn hình khi đang phóng to → không mất vị trí, không crash.
20. 🔴 **Quan trọng nhất:** xuất PDF → mở file trên **máy tính** bằng Chrome/Adobe Reader:
    - Bôi đen được chữ và dán ra Notepad → **chữ copy được**.
    - Ctrl+F tìm một từ có trong tài liệu → **tìm thấy**.
    - Chữ copy ra **có đúng dấu tiếng Việt không**. Đây là phép thử engine OCR. Với mặc định hiện tại (Tesseract) kỳ vọng là **có dấu**; nếu vẫn mất dấu thì phải **ghi nhận trung thực** và điều tra, không được sửa bằng cách tắt tính năng. Nếu người dùng chọn ML Kit thì mất dấu là **hành vi đã biết và đã cảnh báo** ngay trên màn hình Cài đặt.
21. Bấm "Nhận dạng chữ" trên tài liệu đã nhận dạng rồi → phải **bỏ qua**, không chạy lại.
22. Ngắt mạng rồi bấm "Nhận dạng chữ" → phải chạy được (model nằm trong APK).

**Chọn engine nhận dạng chữ (Giai đoạn B) — chưa từng chạy:**

23. Mở Cài đặt → mục **Nhận dạng chữ** phải hiện, với mặc định là **Tesseract** và **Chính xác nhất**.
24. Lần đầu bấm "Nhận dạng chữ" với Tesseract `best` → có độ trễ nhận thấy được (đang giải nén 12 MB ra đĩa). Đây là hành vi đúng, **không phải treo**.
25. Sau lần đầu, bấm lại trên tài liệu khác → **không còn độ trễ giải nén** (model đã nằm trên đĩa).
26. Đổi sang `Nhanh (~0,5 MB)` → bấm nhận dạng → kiểm tra dung lượng ứng dụng trong Cài đặt hệ thống **giảm khoảng 12 MB** (model cũ bị xoá).
27. Đổi sang **ML Kit** → phần chọn chất lượng phải **mờ đi và không bấm được**, kèm lý do.
28. Chọn ML Kit rồi nhận dạng một tài liệu **tiếng Việt** → chữ sẽ **mất dấu**. Đây là hành vi đã biết, và màn hình Cài đặt phải đã cảnh báo trước. **Ghi nhận trung thực, không sửa.**
29. Đổi engine trong lúc đang ở màn hình chi tiết → quay lại, bấm nhận dạng → phải dùng engine **mới**, không cần tắt mở ứng dụng.
30. Xoá toàn bộ dữ liệu ứng dụng (hoặc cài lại) rồi mở Cài đặt → phải quay về mặc định Tesseract + `best`, và nhận dạng lại phải hoạt động (model được giải nén lại).
31. **Tự động nhận dạng chữ (đã sửa ở Giai đoạn B — trước đó công tắc này không có tác dụng gì):** bật "Tự động nhận dạng chữ sau khi quét" → quét một tài liệu → bấm Lưu → vào thư viện, mở tài liệu → **phải đã có chữ** mà không cần bấm "Nhận dạng chữ".
32. Tắt công tắc đó → quét tài liệu khác → bấm Lưu → mở tài liệu → **phải CHƯA có chữ** (trạng thái "Chưa nhận dạng chữ"), chứng minh công tắc thật sự có tác dụng.
33. Ngắt mạng rồi quét và lưu với công tắc bật → **tài liệu vẫn được lưu** (chỉ là không có chữ nếu engine cần tài nguyên mạng — riêng Tesseract thì không cần). Điều quan trọng: **không được mất tài liệu**.

**Cập nhật ứng dụng (Giai đoạn 4) — chưa từng chạy:**

31. Cài bản cũ (0.2 beta) trên máy → cài bản mới → mở app → Cài đặt: phải thấy "Đang dùng bản mới nhất" (khi chưa có bản phát hành nào MỚI HƠN bản đang cài).
32. Phát hành một bản **chính thức** (KHÔNG tick pre-release) với `versionName` cao hơn → mở app → phải thấy "Có bản cập nhật X.Y.Z" + dung lượng + nút "Tải và cài đặt".
33. Bấm "Tải và cài đặt" lần đầu trên Android 8+ → hệ thống mở màn hình xin quyền "Cài đặt ứng dụng không rõ nguồn gốc" → bật quyền → quay lại → bấm lại → trình cài đặt của hệ thống mở ra.
34. Ngắt mạng rồi bấm "Kiểm tra cập nhật" → phải hiện **"Không kiểm tra được bản cập nhật"**, TUYỆT ĐỐI không hiện "Bạn đang dùng bản mới nhất".
35. Tắt "Tự kiểm tra bản cập nhật" trong Cài đặt → mở lại Cài đặt → không thấy vòng xoay kiểm tra chạy.
36. Bấm "Tải và cài đặt" rồi bấm lần nữa thật nhanh → chỉ được có **một** tiến trình tải, không sinh file APK hỏng.

Lý do các mục 9–16 phải làm bằng tay: Robolectric **không chạy được** `android.graphics.pdf.PdfDocument` (xem `RobolectricPdfLimitationTest`), nên chất lượng file PDF do `platform/pdf/AndroidPdfExporter` sinh ra không thể xác nhận bằng unit test. Ngược lại, mục 13 và 14 có test tự động bảo vệ phần logic; việc kiểm tay là để xác nhận bản thân file PDF đúng.

Lý do các mục 23–30 phải làm bằng tay: chúng liên quan tới **dung lượng đĩa thật, độ trễ giải nén thật, và trạng thái cài đặt của hệ điều hành** — những thứ unit test không quan sát được. Test tự động chỉ chứng minh được *luồng logic* cài model (xem `AssetTessdataProvisionerTest`), không chứng minh được rằng người dùng thật sự thấy nó hoạt động.

Lý do các mục 31–36 phải làm bằng tay: chúng phụ thuộc **máy chủ GitHub và chính sách của hệ điều hành** (quyền cài đặt từ nguồn không xác định, trình cài đặt hệ thống). Unit test chỉ chứng minh phần logic đọc JSON, so sánh phiên bản và tính phần trăm — không chứng minh được luồng cài đặt thật.

Quy trình mỗi lần test: chạy offline hoàn toàn (máy chế độ máy bay) → xác nhận scan/OCR/xuất PDF vẫn chạy → ghi scorecard thủ công theo nhóm tài liệu. Sau này thêm mini-harness: thả ảnh fixture vào `androidTest/assets`, so khớp text kỳ vọng.

---

## 7. Checklist trước khi phát hành

- [ ] Namespace chính thức (không `com.example`), tên app kiểm tra chưa trùng trên Play
- [ ] Privacy Policy (GitHub Pages free) + Data Safety form trung thực ("xử lý trên thiết bị, không thu thập")
- [ ] Icon + 4–8 screenshots tiếng Việt + short description chứa từ khoá: scan tài liệu, quét tài liệu, PDF
      - ⚠️ **KHÔNG** đưa "OCR tiếng Việt" vào từ khoá/mô tả cho tới khi **đo được chất lượng thật** trên ≥20 ảnh tài liệu tiếng Việt (§6.2). Engine đúng đã có (Tesseract, §2.5), nhưng chưa có số đo nào. Quảng cáo một tính năng chưa được kiểm chứng sẽ bị gỡ khỏi Play và mất niềm tin người dùng.
- [ ] Xuất bản chính thức **không tick pre-release** (bắt buộc, nếu không cơ chế tự cập nhật sẽ bỏ qua bản đó — đã kiểm chứng `/releases/latest` trả 404 với bản pre-release)
- [ ] Closed testing ≥14 ngày với ≥12 tester (bắt buộc với dev mới)
- [ ] In-app review trigger sau lần export PDF thành công thứ 3
- [ ] Crashlytics (free) nếu kịp — cần privacy policy trước
