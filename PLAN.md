# Kế hoạch xây dựng app Scan tài liệu Android (Kotlin, ngân sách ~0)

> Mục tiêu: app quét tài liệu **nhanh – tiện – dễ dùng**, khác biệt bằng **chạy hoàn toàn offline (riêng tư)**, **OCR tiếng Việt tốt**, và **miễn phí thực sự**. Xuất bản lên Google Play.
>
> 🔴 **Đọc trước (20/10/2026):** trụ cột *"OCR tiếng Việt tốt"* **chưa đạt** — engine đang dùng không hỗ trợ tiếng Việt có dấu. Xem Giai đoạn 3 bên dưới và `MVP_SPEC.md` §2.5. Mọi tuyên bố marketing dựa trên trụ cột này phải hoãn lại cho tới khi thay engine xong.

---

## 1. Định vị sản phẩm (trước khi code)

| Hạng mục | Quyết định |
|---|---|
| Tên gợi ý | DocScan / ScanVi / Quét Tài Liệu (tránh trùng Play — kiểm tra trước) |
| Khác biệt cốt lõi | 100% on-device, không account, không upload, không watermark |
| OCR tiếng Việt | On-device, miễn phí (điểm mà CamScanner/Adobe bắt người dùng trả hoặc gửi lên cloud) |
| Miễn phí | Core (scan + OCR + PDF + tìm kiếm) free vĩnh viễn; tính tiền phần mở rộng |
| Nền tảng | Kotlin + Jetpack Compose, minSdk 24 (Android 7.0), targetSdk mới nhất Play yêu cầu |
| Đối tượng đầu | Sinh viên, nhân viên văn phòng, kế toán, giáo viên VN |

**Định nghĩa "top đầu" = KPI đo được:**
- Mở app → chụp → lưu xong: **< 5 giây** thao tác, **< 1.5s** xử lý ảnh trên máy tầm trung
- Cold start < 1.5s, không ANR, crash-free rate > 99.5%
- Rating ≥ 4.3, D1 retention ≥ 40%
- APK tải về < 40MB (AAB, arm64)

---

## 2. Kiến trúc

### 2.1. Kiến trúc tổng thể

```
┌─────────────────────────── :app ───────────────────────────┐
│  UI (Compose, Material3)                                    │
│  ViewModel (StateFlow, single source of truth)             │
├────────────────────────── :core:* ─────────────────────────┤
│  :core-camera      → CameraX, capture, auto-capture        │
│  :core-imageproc   → OpenCV: detect, crop, filter, enhance │
│  :core-ocr         → ML Kit Text Recognition (latin/VN)    │
│  :core-pdf         → PdfDocument + pdfbox (text layer)     │
│  :core-data        → Room + DataStore + FileStore          │
│  :core-designsystem→ theme, composables dùng chung         │
│  :core-common      → utils, dispatchers, Result wrappers   │
├────────────────────────────────────────────────────────────┤
│  Domain layer (pure Kotlin)                                 │
│  ScanDocumentUseCase, ExportPdfUseCase, OcrDocumentUseCase  │
└────────────────────────────────────────────────────────────┘
```

- **Offline-first, không backend.** Dữ liệu = Room (metadata) + file ảnh/PDF trong `filesDir` (scoped storage, không cần quyền lưu trữ ngoài trên API 29+).
- DI: **Hilt**. Async: **Coroutines + Flow**. Ảnh nặng chạy qua **WorkManager** (OCR hàng loạt, export PDF) để không giết app khi background.
- Pattern: **MVVM + UseCase** (đủ gọn cho team 1 người; tránh MVI boilerplate).

### 2.2. Quy trình 1 lần scan (luồng lõi)

```
CameraX frame → downscale 1600px → OpenCV detect 4 góc giấy (adaptiveThreshold
+ findContours + approxPolyDP) → gợi ý crop → user xác nhận (hoặc auto-capture
khi 4 góc ổn định N frame) → perspective transform → filter (Original/Làm trắng/
Grayscale/B&W/Đảo màu) → sharpen + denoise → lưu JPG (quality 85, EXIF strip)
→ (tuỳ chọn) OCR → (tuỳ chọn) xuất PDF (nhiều trang, có text layer tìm kiếm)
```

### 2.3. Thư viện cốt lõi

| Lớp | Thư viện | Lý do |
|---|---|---|
| UI | Jetpack Compose + Material 3 | Nhanh để viết, hiện đại, ít boilerplate |
| Camera | **CameraX** (camera2, lifecycle, view 1.4+) | API ổn định, auto-capture tiện |
| Detect & filter | **OpenCV Android SDK** (org.opencv:opencv) | Edge detection + perspective transform tốt nhất on-device, free Apache-2.0 |
| OCR (bundled — nằm trong APK, chạy ngoại tuyến), hỗ trợ tiếng Việt | ~~**ML Kit Text Recognition v2** (`com.google.mlkit:text-recognition`)~~ 🔴 **KHÔNG DÙNG ĐƯỢC** | Dòng này từng ghi *"Hỗ trợ dấu tiếng Việt"* — **SAI**. Kiểm tra 20/10/2026: Text Recognition v2 chỉ có 5 hệ chữ (Latin, Chinese, Devanagari, Japanese, Korean), **không có tiếng Việt**; model thật trong APK là `Latn_ctc` và nó bỏ dấu. Cần **thay engine** — xem `MVP_SPEC.md` §2.5. (`text-recognition-latin` cũng không tồn tại — kiểm tra 404 ngày 19/10/2026.) |
| PDF | `androidx.pdf` (PdfRenderer mới) + **pdfbox-android** (add text layer ẩn) | Xuất PDF có thể tìm kiếm được chữ VN |
| Room / DataStore | androidx.room, preferences-datastore | Metadata docs, settings |
| DI / Async | Hilt, Coroutines, Flow, WorkManager | Chuẩn Android |
| Rating / Feedback | In-app review API + link Google Form | Hộp thư phản hồi 0 đồng |
| Phân tích | **Firebase Analytics + Crashlytics (free tier)** hoặc Matomo self-host | Cần data để tối ưu; free tier đủ |

> Lưu ý: ML Kit model latin ~3-5MB, tự download on-device (Play Services) — đảm bảo chạy **offline sau khi cài**. Nếu muốn "zero Google dependency", thay bằng **Tesseract 5 + `vie.traineddata`** (nhẹ hơn nhưng chậm & kém chính xác hơn → khuyến nghị ML Kit cho MVP).

---

## 3. Lộ trình MVP theo bước (tuần, giả định 1 người + AI coding)

### Giai đoạn 0 — Nền tảng (Tuần 1)
1. Tạo repo Git + GitHub Actions (CI: build debug APK, chạy unit test, lint).
2. Dựng project: Compose, Hilt, modular `:core:*`, detekt/ktlint.
3. Đăng Play Console ($25 một lần), tạo app listing nháp.
4. **Deliverable:** APK debug cài được, CI xanh.

### Giai đoạn 1 — Scan cốt lõi (Tuần 2–3) ★ trái tim app
1. Màn hình CameraX: khung viền giấy, hướng dẫn đặt tài liệu.
2. OpenCV: detect 4 góc → vẽ overlay → vuốt/điều chỉnh góc tay.
3. Chụp → perspective transform → 5 filter cơ bản → lưu ảnh.
4. **Deliverable:** scan 1 trang ra file JPG đẹp. *Test thủ công với 20 loại tài liệu thật (giấy trắng, hóa đơn, bảng biểu, ảnh chụp nghiêng).*

### Giai đoạn 2 — Quản lý & PDF (Tuần 4–5)
1. Room: Document (nhiều Page), thumbnail, tên, ngày, tag.
2. Màn hình thư viện: grid, tìm kiếm local, xoá (undo 5s), chia sẻ JPG.
3. Export PDF nhiều trang, sắp xếp lại trang, chia sẻ/print.
4. **Deliverable:** dùng được hàng ngày cho việc cá nhân (dogfooding).

> **Đã xong (2a + 2b):** mục 3 đã hoàn thành phần export/chia sẻ PDF khổ A4 và sắp xếp lại trang (làm ngay ở màn hình xem lại sau khi quét). Còn lại của mục 1–2 (thumbnail, grid, chia sẻ JPG) chưa làm — nằm trong Giai đoạn 4 (trải nghiệm).

### Giai đoạn 3 — OCR tiếng Việt (Tuần 6) ★ marketing hook
1. OCR 1 trang → hiển thị text, copy, tìm kiếm trong doc.
2. OCR nền bằng WorkManager, tiến trình trong notification.
3. Làm sạch text: gộp dòng, bỏ khoảng trắng thừa, chuẩn hoá dấu.
4. Xuất PDF có text layer ẩn (Ctrl+F tìm được).
5. **Deliverable:** quét 1 trang A4 → copy được đoạn văn VN chính xác.

> 🔴 **Trạng thái (20/10/2026) — ĐỌC KỸ, ĐÂY LÀ ĐIỂM HỤT LỚN NHẤT CỦA KẾ HOẠCH.**
>
> Mục 1, 4 và một phần 3 **đã code xong** (lưu chữ theo dòng + toạ độ chuẩn hoá vào Room v2, vẽ lớp chữ ẩn vào PDF, nút "Nhận dạng chữ", tự nhận dạng trước khi xuất PDF). Mục 2 (OCR nền bằng WorkManager) **chưa làm** — OCR hiện chạy trong lúc màn hình đang mở.
>
> **Nhưng mục 5 — "copy được đoạn văn VN chính xác" — KHÔNG ĐẠT, và không thể đạt bằng engine đang dùng.** ML Kit Text Recognition v2 chỉ hỗ trợ 5 hệ chữ (Latin, Chinese, Devanagari, Japanese, Korean); **không có tiếng Việt**. Mô hình đóng gói thật trong APK là `Latn_ctc` — đã kiểm chứng bằng cách đọc trực tiếp file trong APK release. Nó bỏ dấu hoặc trả chuỗi rỗng.
>
> Dòng ở §2.3 của tài liệu này từng ghi *"Hỗ trợ dấu tiếng Việt"* — **dòng đó SAI và đã được đính chính**. Đây là lỗi do chọn thư viện trước khi kiểm tra tài liệu chính thức của Google.
>
> **Việc phải làm (Giai đoạn 3b):** thay engine OCR. Xem `MVP_SPEC.md` §2.5 để so sánh Tesseract + `vie.traineddata` (offline, miễn phí, ưu tiên) và PaddleOCR/ONNX (chất lượng cao hơn), cùng Cloud Vision (tùy chọn có cảnh báo, cần người dùng tự bật). Cổng `OcrEngine` đã tách sẵn nên **không phải sửa** Room, ViewModel hay PDF exporter.

### Giai đoạn 3b — Thay engine OCR (Tuần 6–7, làm trước khi phát hành)
1. Cài Tesseract + `vie.traineddata` sau cổng `OcrEngine` hiện có (chỉ sửa `platform/ocr/`).
2. Thêm cột `ocrEngineVersion` + migration v2→v3 để biết trang nào cần re-OCR.
3. Dựng bộ ≥20 ảnh tài liệu in tiếng Việt thật, đo tỉ lệ dòng đọc đúng (mục tiêu ≥90%).
4. Chỉ sau khi đạt mục 3 mới bật "OCR tiếng Việt" trong mô tả Play.
5. **Deliverable:** PDF xuất ra, copy trên máy tính ra **đúng dấu tiếng Việt**.

### Giai đoạn 4 — Trải nghiệm & hiệu năng (Tuần 7) ★ "nhanh & dễ dùng"
1. Auto-capture: giữ máy yên 0.5s → tự chụp.
2. Baseline Profile + Macrobenchmark (R8 full mode, profile installer).
3. Tối ưu: decode ảnh theo bounds, object pool bitmap, huỷ job khi rời màn hình.
4. Onboarding 3 màn, empty states, dark mode, accessibility (content descriptions).
5. **Deliverable:** benchmark ghi lại thời gian; cold start < 1.5s trên Snapdragon 6xx.

> **Đã làm trước một phần (20/10/2026):** mục 3 **đã xong** cho ảnh (`AndroidPageImageLoader` giải mã theo `inSampleSize` theo kích thước đích, kèm **bộ nhớ đệm LRU**), và `PageImageLoader.load(relativePath, maxDimension)` chỉ có hai mức được dùng: 320 px cho ảnh thu nhỏ trong danh sách, 2048 px cho trình xem toàn màn hình. Nhờ vậy cuộn danh sách không giải mã lại ảnh, và zoom không làm lại từ đầu. Thumbnail **không** ghi ra đĩa (không có thư mục `thumbs/`) — cache trong RAM rẻ hơn và tự dọn.
>
> Còn lại của mục 4: giao diện "nhìn chưa đẹp lắm" là phản hồi thật của chủ dự án, **chưa được đặt phạm vi cụ thể**. Cần chốt danh sách việc trước khi làm.
>
> Auto-capture (mục 1) có thể **không làm được** trong MVP: giao diện chụp nằm trong ML Kit Document Scanner, không can thiệp được vào luồng chụp của nó.

### Giai đoạn 4b — Tự cập nhật (đã xong, 20/10/2026)
1. Đọc bản phát hành mới nhất từ GitHub Releases (`/releases/latest`), so phiên bản bằng số.
2. Thông báo trong Cài đặt, có công tắc tắt kiểm tra tự động.
3. Tải APK vào `cacheDir/updates/` rồi **mở trình cài đặt của hệ thống** (app không tự cài).
4. Lỗi mạng → thông báo "không kiểm tra được", **tuyệt đối không** nói dối là "đã mới nhất".

> **Hai điều kiện vận hành, đã kiểm chứng bằng HTTP:** repo phải **public** (private → 404), và bản phát hành **không được tick pre-release** (`/releases/latest` bỏ qua pre-release → 404). Xem `ARCHITECTURE.md` §5c.
>
> **Khi lên Google Play, cơ chế này PHẢI bị gỡ** (Play cấm tự tải và cài APK): xoá quyền `REQUEST_INSTALL_PACKAGES`, xoá `platform/update/` và `ui/update/`, thay bằng Play In-App Updates.

### Giai đoạn 5 — Phát hành (Tuần 8)
1. Unit test cho UseCase + image pipeline (so sánh pixel trên ảnh fixture).
2. Screenshot test (Roborazzi/Paparazzi) cho các màn hình chính.
3. Chạy closed testing 14 ngày theo chính sách Play (bắt buộc với dev mới, ≥12 tester).
4. Xin 5–10 review đầu tiên (bạn bè, nhóm Facebook "sinh viên", group kế toán).
5. **Deliverable:** bản production, listing tiếng Việt + Anh, screenshots đẹp.

> **Đã làm trước (20/10/2026):** mục 1 một phần — 193 unit test / 0 lỗi / 21 suite, đã chạy thật. Nhưng **chưa có test instrumented nào**; đây là lỗ hổng lớn nhất còn lại, vì mọi thứ phụ thuộc giao diện hệ thống (trình quét ML Kit, trình cài đặt APK, SAF) hiện chỉ kiểm được bằng tay.
> **Trước khi phát hành bắt buộc:** dùng bản release **đã ký** (hiện APK release chưa ký, chỉ APK debug là cài được), và kiểm tra tên "ScanViet" trên Play + thương hiệu.

### Sau MVP (Backlog, chỉ làm khi có data)
- Scan sổ dài / quét hàng loạt từ gallery, chữ ký số, điền form PDF.
- Widget "chụp nhanh", chia sẻ từ app khác (intent filter).
- Đám mây **opt-in** (sync qua tài khoản Google của user, không server của mình) — chỉ khi người dùng đòi.

---

## 4. Kiểm thử

> **Trạng thái thật (20/10/2026):** đã chạy **193 unit test / 0 lỗi / 21 suite** (`./gradlew :app:testDebugUnitTest`), cộng `assembleDebug` + `assembleRelease` xanh. **Chưa có test instrumented và chưa có screenshot test.** Bảng dưới là *công cụ dự định*, không phải đã dùng hết.

| Loại | Công cụ | Tập trung |
|---|---|---|
| Unit test | JUnit4 + Robolectric (đang dùng thật) | UseCase, ViewModel, Room migration, repository, file storage |
| Image pipeline | JUnit + ảnh fixture trong `androidTest` | Detect đúng 4 góc, filter không lỗi màu, crop đúng tỉ lệ |
| UI/Screenshot | Roborazzi | 20 màn hình, light/dark |
| Performance | Macrobenchmark + Baseline Profile | Cold start, thời gian scan 1 trang |
| Thực địa | Dogfooding + 10 tester thật | Máy yếu (RAM 3GB), ảnh thiếu sáng, giấy vàng, ảnh nghiêng mạnh |
| Phân bổ | Firebase Test Lab free tier? → không free; thay bằng 3–5 máy ảo AVD + máy thật | Độ phủ màn hình/Android version |

**Ba điều bộ test hiện tại KHÔNG chứng minh được** (ghi rõ để không ai tưởng nhầm là đã kiểm thử):

1. **Chất lượng nhận dạng chữ tiếng Việt** — `RecognizeDocumentTextUseCaseTest` dùng engine **giả**; nó chứng minh luồng nghiệp vụ, không chứng minh chất lượng.
2. **Chất lượng file PDF** — Robolectric không chạy nổi `android.graphics.pdf.PdfDocument` (`RobolectricPdfLimitationTest` ghi lại bằng chứng). Kể cả **lớp chữ ẩn có copy được bằng Ctrl+F hay không** cũng chỉ biết được khi mở file trên máy tính.
3. **Mọi thứ qua giao diện hệ thống** — trình quét ML Kit (là Activity của Play services), trình cài đặt APK, SAF, quyền cài từ nguồn không xác định, và toàn bộ bố cục/giao diện.

Danh sách kiểm tra tay đầy đủ (28 mục): `MVP_SPEC.md` §6.3.
| Release gate | CI: lint + test + benchmark threshold; Play Vitals theo dõi sau phát hành | ANR < 0.47%, crash < 1.09% (ngưỡng Play) |

---

## 5. Kiếm tiền thực tế (ngân sách ~0, app free là khác biệt)

**Nguyên tắc: không bao giờ chặn core scan/OCR sau paywall — đó là USP.** Thu tiền ở tính năng "nâng cao".

| Hạng mục | Chi phí/Doanh thu |
|---|---|
| Play Console | $25 một lần |
| OpenCV, ML Kit, Compose, pdfbox | $0 (Apache-2.0 / Google free) |
| Domain, landing page | $0–12/năm (GitHub Pages free) |

**Kênh doanh thu (khuyến nghị theo thứ tự):**

1. **AdMob (free tier)** — banner ở màn hình thư viện + interstitial *tùy chọn* sau khi export PDF (không bao giờ hiện giữa lúc chụp). Người dùng có thể "gỡ quảng cáo" bằng 1 lần mua.
   - KPI để ước thu: CTR ~0.5–1%, eCPM VN ~ $0.5–2 → khoảng **$1–3 / 1.000 DAU / ngày**. Với 10k DAU ≈ $30–90/ngày. Thực tế tháng đầu: vài đô → chấp nhận được.
2. **IAP "Gỡ quảng cáo" (non-consumable)** — 49.000đ (VN) / $1.99. Tỷ lệ mua thường 1–3% DAU hoạt động/tháng.
3. **Pro (tuần 8+ nếu có tín hiệu)** — 99.000đ/năm: OCR hàng loạt (>5 trang), nhãn tuỳ chỉnh, template chữ ký, export ZIP, mật khẩu PDF. Tách bạch rõ tính năng, không giấu core.
4. **Không bán data, không account bắt buộc** — giữ đúng chữ "riêng tư"; có thể đăng Manifesto riêng tư lên Play listing như một điểm bán hàng.

**Ước thực tế năm 1 (giả sử làm tốt ASO + lan truyền nhóm):** 5–20k DAU → **$50–300/tháng** (ads + IAP). Đủ để tái đầu tư; kỳ vọng "nghìn đô" chỉ đến khi đạt 50k+ DAU hoặc có kênh Pro convert tốt.

**Phân phối 0 đồng:**
- ASO: từ khoá "scan tài liệu", "quét tài liệu", "ocr tiếng việt", "scan pdf" — đặt ở title, short description, 5 dòng đầu long description.
- Video demo 15s trên TikTok/Facebook Reels ("quét hóa đơn trong 3 giây, không cần mạng").
- Đăng group Facebook sinh viên/kế toán, Reddit r/androidapps, Product Hunt (ngày phát hành).
- Nhờ 10 người dùng đầu để lại review 5★ sau lần scan thành công thứ 3 (trigger in-app review đúng lúc).

---

## 6. Rủi ro & phương án dự phòng

| Rủi ro | Xử lý |
|---|---|
| OpenCV nặng (~độ lớn APK) | Chỉ include arm64-v8a; dùng app bundle (Play auto-tối ưu), hoặc tách `:core-imageproc` thành dynamic feature |
| **🔴 ML Kit v2 KHÔNG nhận dạng được tiếng Việt có dấu — rủi ro đã THÀNH HIỆN THỰC (20/10/2026)** | Phải **thay engine** (Giai đoạn 3b). Không sửa được bằng cấu hình. Trong lúc chờ: **không** quảng cáo "OCR tiếng Việt", **không** đưa vào từ khoá Play. Xem `MVP_SPEC.md` §2.5 |
| OCR VN sai trên ảnh tối/nghiêng | Preprocess tốt hơn (deskew + auto-contrast + denoise) — đo bằng bộ 200 ảnh fixture. **Lưu ý thứ tự: sửa engine trước, tinh chỉnh ảnh sau** — tinh chỉnh ảnh không cứu được engine không hỗ trợ hệ chữ |
| **Tự cập nhật im lặng không báo** | Đã kiểm chứng thực nghiệm: repo **private** → 404; bản **pre-release** → `/releases/latest` trả 404. Bản phát hành phải là **bản chính thức, không tick pre-release**, trên repo **public**. Lỗi mạng được xử lý thành "không kiểm tra được", **không** nói dối là "đã mới nhất" |
| **Tự cập nhật bị Play cấm** | Khi phát hành qua Play **phải** gỡ `REQUEST_INSTALL_PACKAGES` và `platform/update/`; thay bằng Play In-App Updates. Nguồn cập nhật đã nằm sau cổng `UpdateSource` nên đổi kênh chỉ là đổi cài đặt |
| Chậm trên máy yếu | Giảm resolution cho bước detect (1600px), chạy pipeline ở background, benchmark ngưỡng rõ ràng |
| Bị Play reject (chính sách camera/permission) | Xin permission đúng lúc (runtime), Privacy Policy miễn phí via GitHub Pages, Data safety form khai trung thực "không thu thập dữ liệu" |
| Dev account mới bị yêu cầu closed testing 12 tester/14 ngày | Chuẩn bị sẵn 12 tester thật từ hôm đầu; dùng Google Groups để phân phối |
| Burnout / chậm tiến độ | Giữ scope MVP sắt đá: không cloud, không tính năng thừa, mọi thứ khác vào backlog |
| **Không có test instrumented nào** | Mọi thứ phụ thuộc giao diện hệ thống (trình quét ML Kit, trình cài đặt APK, SAF, quyền cài từ nguồn không xác định) hiện **chỉ kiểm được bằng tay**. Cần bổ sung khi có thiết bị/emulator — AGENTS.md yêu cầu |
| **APK release chưa ký** | Hiện chỉ APK **debug** cài được. Trước khi phát hành phải tạo keystore và ký bản release; mất keystore là **không thể** cập nhật app trên Play nữa |

---

## 7. Checklist khởi động (ngày đầu)

- [ ] `flutter`/`react native` bị loại — đã chốt Kotlin native (đúng: performance + CameraX/OpenCV tốt nhất)
- [ ] Tạo repo, scaffold modular project, CI xanh
- [ ] Viết spec 1 trang cho màn hình Capture (luồng, trạng thái, copy tiếng Việt)
- [ ] Chuẩn bị 30 ảnh tài liệu mẫu làm fixture test
- [ ] Đăng Play Console, tạo listing nháp tên app

**Bắt đầu từ Giai đoạn 0 — báo tao khi nào scaffold xanh để t review tiếp Giai đoạn 1 (camera + OpenCV detect).**
