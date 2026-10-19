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
| OCR (Text Recognition v2) | Dùng artifact **bundled** `com.google.mlkit:text-recognition-latin` — mô hình nằm trong APK | **Không** (kể cả lần đầu) |
| Lưu/đọc tài liệu | Room + file nội bộ app | Không |
| Quảng cáo (sau này) | AdMob | Cần mạng — nhưng scan/OCR/xuất vẫn chạy bình thường khi offline |

→ Câu marketing đúng: **"Tài liệu được xử lý trên thiết bị, không cần tải lên máy chủ."** Không quảng cáo "hoàn toàn offline 100%" vì Document Scanner có thể tải resource lần đầu qua Play services.

### 2.2. Quyết định 8 điểm (đọc trước khi code)

1. **Dùng luôn UI có sẵn của ML Kit Document Scanner** cho màn hình 2 (nó đã có cắt mép, xoay, filter, nhiều trang, sắp xếp trang). Bù lại: giao diện ít tùy chỉnh được và chỉ chạy được trên máy có Google Play services. Custom crop screen bằng Compose + OpenCV chỉ làm **sau** MVP nếu cần thương hiệu hóa.
2. **Kết quả scanner là URI tạm thời** (lưu trong MediaStore tạm, chỉ app mình truy cập được và có thể hết hạn). Sau khi scan xong phải **copy ngay vào `filesDir` của app**, rồi mới ghi dòng Room. Không bao giờ giữ URI của scanner làm nguồn sự thật.
3. **Nguồn sự thật của file = thư mục nội bộ app** (`filesDir/`); Room chỉ lưu metadata + đường dẫn. Storage Access Framework chỉ dùng cho nút **"Lưu bản sao ra…"** (export) và Share sheet. Nhờ vậy **không cần xin quyền bộ nhớ ngoài**.
4. **OCR chạy bằng artifact bundled** (`com.google.mlkit:text-recognition-latin`) thay vì artifact qua Play services — đảm bảo OCR chạy offline ngay lần mở đầu.
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
implementation("com.google.android.gms:play-services-mlkit-document-scanner:<latest>")

// OCR (bundled — nằm trong APK, offline hoàn toàn)
implementation("com.google.mlkit:text-recognition-latin:<latest>")

// Chạy nền (OCR nhiều trang)
implementation("androidx.work:work-runtime-ktx:<latest>")
```

---

## 3. Cấu trúc project

### 3.1. Trạng thái đã code (Giai đoạn 1) — MỘT module `:app`

Giai đoạn 1 **không** chia module. Toàn bộ nằm trong `:app` với ranh giới package trùng tên module tương lai (chi tiết + lý do: `ARCHITECTURE.md` mục 8):

```
app/src/main/java/vn/scanviet/app/
├── ScanVietApplication.kt      @HiltAndroidApp + dọn dẹp lúc mở app
├── MainActivity.kt             entry duy nhất, chỉ setContent
├── core/                       → :core:common   (DispatcherProvider, text, format, time, di)
├── domain/                     → :core:model    (model, DocumentRules, repository interface, use case)
├── data/                       → :core:data     (Room, DataStore, FileStorage, repository impl, di)
├── ui/                         → :feature:*     (theme, common, components, library, scan, settings, root)
└── navigation/                 → :app           (nơi DUY NHẤT biết mọi màn hình)
```

Kiểm tra được bằng mắt: `domain/` không có import nào của `androidx.compose.*`, `androidx.room.*` hay `vn.scanviet.app.data.*`.

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

Quy trình mỗi lần test: chạy offline hoàn toàn (máy chế độ máy bay) → xác nhận scan/OCR/xuất PDF vẫn chạy → ghi scorecard thủ công theo nhóm tài liệu. Sau này thêm mini-harness: thả ảnh fixture vào `androidTest/assets`, so khớp text kỳ vọng.

---

## 7. Checklist trước khi phát hành

- [ ] Namespace chính thức (không `com.example`), tên app kiểm tra chưa trùng trên Play
- [ ] Privacy Policy (GitHub Pages free) + Data Safety form trung thực ("xử lý trên thiết bị, không thu thập")
- [ ] Icon + 4–8 screenshots tiếng Việt + short description chứa từ khoá: scan tài liệu, quét tài liệu, OCR tiếng Việt, PDF
- [ ] Closed testing ≥14 ngày với ≥12 tester (bắt buộc với dev mới)
- [ ] In-app review trigger sau lần export PDF thành công thứ 3
- [ ] Crashlytics (free) nếu kịp — cần privacy policy trước
