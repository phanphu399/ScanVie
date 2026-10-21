# ScanViet

Ứng dụng quét tài liệu cho Android, viết bằng Kotlin + Jetpack Compose. Mục tiêu: **quét giấy → PDF nhiều trang rõ nét → OCR tiếng Việt ngay trên máy**, miễn phí, không cần đăng nhập, không có máy chủ của ứng dụng.

> Tài liệu thiết kế: [`PLAN.md`](PLAN.md) (định vị, KPI, lộ trình) · [`MVP_SPEC.md`](MVP_SPEC.md) (phạm vi đã chốt) · [`ARCHITECTURE.md`](ARCHITECTURE.md) (lưu trữ, schema, module) · [`AGENTS.md`](AGENTS.md) (quy tắc kỹ thuật bắt buộc).

> 🟡 **Đọc trước nếu bạn quan tâm OCR tiếng Việt:** engine **Tesseract + `vie.traineddata`** đã được tích hợp và là **mặc định**, đây là engine đầu tiên ứng dụng có thể đọc tiếng Việt **có dấu**. ML Kit Text Recognition v2 vẫn còn nhưng **không đọc được tiếng Việt có dấu** — nó phải do người dùng chủ động chọn, kèm cảnh báo ngay trên màn hình Cài đặt.
>
> **Điều chưa biết:** chất lượng nhận dạng **thật** trên ảnh chụp thực tế là bao nhiêu. Chưa có phép đo nào trên ảnh thật. Xem `MVP_SPEC.md` §2.5 và §6.2. **Không quảng cáo "OCR tiếng Việt" cho tới khi có số đo.**

---

## Trạng thái hiện tại: xong Giai đoạn 1 + 2a (quét) + 2b (xuất/chia sẻ PDF) + 3 (thư viện/xem trang/lớp chữ) + 4 (tự cập nhật)

**Đã chạy được:**

- Danh sách tài liệu thật từ Room, sắp xếp theo lần cập nhật mới nhất.
- Tìm kiếm tiếng Việt **không cần gõ dấu** ("hoa don" ra "Hóa đơn điện").
- **Ảnh thu nhỏ của trang đầu ngay trong danh sách** — tải theo lô, có bộ nhớ đệm LRU, không giải mã lại ảnh khi cuộn.
- Chi tiết tài liệu: số trang, ngày tạo/sửa, dung lượng thật trên đĩa, cảnh báo khi thiếu file ảnh.
- **Trình xem toàn màn hình**: chụm hai ngón để phóng to (1×–6×) không cần giải mã lại ảnh, vuốt ngang đổi trang, trạng thái zoom tách riêng theo từng trang.
- Đổi tên tài liệu (chỉ sửa metadata, **không** đổi tên file trên đĩa).
- Xoá mềm → **Thùng rác** → khôi phục, hoặc xoá vĩnh viễn (xoá cả file).
- Cài đặt: chủ đề Sáng/Tối/Theo hệ thống, công tắc OCR tự động, giữ ảnh gốc, **tự kiểm tra cập nhật**, xem dung lượng đang dùng.
- Dọn dẹp lúc mở app: xoá tài liệu trong thùng rác quá 30 ngày, xoá thư mục mồ côi, chuyển tài liệu mất hết file ảnh vào thùng rác.
- Chủ đề Sáng/Tối, giao diện tiếng Việt, Material 3.
- **Quét tài liệu thật** (Giai đoạn 2a): mở giao diện quét của ML Kit Document Scanner (nhận diện mép giấy, cắt phối cảnh, chỉnh màu, nhiều trang), rồi xem lại danh sách trang — chuyển trang lên/xuống, xoá trang, đặt tên — và lưu. Ảnh được **sao chép ngay** vào `filesDir/documents/<uuid>/page_XXXX.jpg` trước khi ghi Room; nếu ghi Room lỗi thì thư mục vừa tạo bị xoá sạch.
- Tối đa **20 trang** cho một lần quét. Huỷ giữa chừng hoặc máy không có Google Play services đều có thông báo riêng, không màn hình trắng.
- **Xuất và chia sẻ PDF** (Giai đoạn 2b): tạo PDF nhiều trang khổ **A4** (mỗi trang một tờ), rồi hoặc **lưu bản sao** vào nơi bạn chọn qua Storage Access Framework (Drive, thư mục Tải về…), hoặc **chia sẻ** qua Zalo/Gmail/Drive bằng hộp thoại chia sẻ của hệ thống. Tên file gợi ý lấy từ tên tài liệu, đã bỏ dấu và loại ký tự cấm trong tên file. Trang thiếu file ảnh làm hỏng cả lần xuất kèm báo đúng số trang — cố ý vậy, để không bao giờ có "PDF thành công nhưng thiếu trang".
- Ảnh được vẽ **vừa khít, giữ đúng tỉ lệ, không phóng to** (phóng to chỉ làm mờ). Ảnh ngang tự đổi khổ trang thành A4 ngang. Chỉ **một trang nằm trong RAM** tại một thời điểm.
- **Lớp chữ ẩn trong PDF** (Giai đoạn 3): mỗi trang có thể mang thêm lớp chữ **vô hình** phủ lên ảnh, để mở PDF trên máy tính có thể bôi đen copy hoặc Ctrl+F. Chữ được lưu dạng **toạ độ tỉ lệ 0–1** so với ảnh gốc, nên lớp chữ vẫn thẳng hàng dù khổ trang đổi. Xem cảnh báo về chất lượng chữ bên dưới.
- **Nhận dạng chữ** (Giai đoạn 3): nút "Nhận dạng chữ" trong màn chi tiết. Khi xuất PDF mà tài liệu chưa nhận dạng, ứng dụng **tự chạy nhận dạng trước**. Kết quả được lưu lại nên **không bao giờ chạy lại** cùng một trang. Trang đã nhận dạng mà không có chữ nào vẫn được ghi nhận (phân biệt "trang trắng" với "trang chưa xử lý").
- **Tự kiểm tra và cập nhật** (Giai đoạn 4): app đọc bản phát hành mới nhất từ GitHub Releases và báo trong Cài đặt; bấm "Tải và cài đặt" thì tải APK rồi **mở trình cài đặt của hệ thống** (app không tự cài). Có công tắc tắt kiểm tra tự động. Kết quả kiểm tra lưu vào DataStore. Xem cảnh báo vận hành bên dưới.

**Chưa có (đúng phạm vi, không phải lỗi):** số đo chất lượng OCR tiếng Việt trên ảnh thật (xem cảnh báo ở trên), nhận dạng chữ qua đám mây (Cloud Vision), tự cập nhật qua Google Play.

> 🟡 **Lớp chữ ẩn đã được vẽ, và engine giờ đọc được tiếng Việt có dấu — nhưng chất lượng thật chưa được đo.** Phần kỹ thuật (vẽ chữ vô hình đúng vị trí, lưu toạ độ tỉ lệ, ghép đúng trang) đã xong và có test. Toạ độ lấy ở mức dòng từ `ResultIterator` của Tesseract, và phép chuyển pixel → tỉ lệ được kiểm thử riêng (12 test, đã chứng minh bắt được lỗi cũ bằng cách cố tình tái tạo nó). Điều **chưa** biết là chữ đọc ra đúng bao nhiêu phần trăm trên ảnh chụp thực tế. Xem `MVP_SPEC.md` §6.2.

> ⚠️ **Tự cập nhật qua GitHub Releases — hai điều kiện bắt buộc, đã kiểm chứng thực nghiệm:**
> 1. **Repo phải public.** Với repo private, GitHub trả **404** cho người không có quyền. (Đã đo: `/repos/phanphu399/ScanVie` trả 404 khi còn private, 200 sau khi public.)
> 2. **Bản phát hành KHÔNG được đánh dấu pre-release.** Endpoint `/releases/latest` bỏ qua pre-release. (Đã đo: repo hiện chỉ có `v0.2-beta` là pre-release → `/releases/latest` trả **404**, còn `/releases` trả 200 và liệt kê được nó.)
>
> Nếu vi phạm một trong hai điều trên, app sẽ hiện đúng thông báo **"Không kiểm tra được bản cập nhật"** — cố ý không nói dối là "đã là bản mới nhất". Khi lên Google Play, cơ chế này phải được thay bằng luồng của Play và bỏ quyền `REQUEST_INSTALL_PACKAGES`.

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

---

## Khoá ký — đọc trước khi phát hành

Ứng dụng dùng **hai khoá**, cả hai đều **không** nằm trong Git (`*.keystore` bị `.gitignore` chặn):

| Khoá | File | Dùng cho | SHA-256 |
|---|---|---|---|
| Khoá phát hành chung | `scanviet-dev.keystore` | APK gắn lên GitHub Releases | `B2:24:FE:B1:15:3E:E4:49:AE:52:40:AA:4C:C8:B1:A6:AF:F9:79:CB:8E:BB:9A:CF:25:39:66:C3:83:CA:64:C1` |
| Khoá release | `scanviet-release.keystore` | Bản ký chính thức (tương lai, Google Play) | `E3:8D:F8:13:99:04:97:ED:9A:16:7E:AB:2C:6E:02:27:FB:BD:A3:3E:75:6B:78:47:79:FB:C1:89:C2:90:3A:35` |

**Vì sao không dùng khoá debug mặc định của máy (`~/.android/debug.keystore`):** khoá đó thuộc về **máy**, không thuộc dự án. Cài lại Windows, đổi máy, hoặc gỡ Android Studio là mất. Khi mất, Android **chặn cài đặt** bản mới lên bản đang cài (chữ ký khác nhau), nên mọi người dùng phải gỡ ứng dụng rồi cài lại — **mất toàn bộ tài liệu đã quét**. `scanviet-dev.keystore` được sao lưu trong chính thư mục dự án nên đi theo dự án.

> ⚠️ **Phải sao lưu cả hai file khoá ra ngoài máy này** (trình quản lý mật khẩu, ổ cứng ngoài). Mất `scanviet-dev.keystore` = không phát hành được bản cập nhật cài đè nữa. Mất `scanviet-release.keystore` = **vĩnh viễn** không cập nhật được ứng dụng đã lên Google Play.

Hành vi khi thiếu file khoá (đã kiểm chứng thật, không suy đoán):

- **Thiếu `scanviet-dev.keystore`** → Gradle dùng khoá debug mặc định của máy, vẫn ký và build bình thường. Bản này **không** cài đè được lên bản đã phát hành.
- **Thiếu `scanviet-release.keystore`** → bản release không được ký (`app-release-unsigned.apk`), vẫn build được để R8 kiểm tra.
- Gán một `signingConfig` **thiếu `storeFile`** → build **dừng** với lỗi *"SigningConfig "development" is missing required property storeFile"*.
- Đặt `signingConfig = null` khi máy không có khoá debug nào → Gradle sinh `app-debug-unsigned.apk` (**không ký**, không cài được). Đây là kiểu hỏng âm thầm nên tránh.


**Kết quả đã chạy thật (20/10/2026):** **234 unit test / 0 lỗi / 0 bỏ qua / 24 suite**.

| Suite | Số test | Ghi chú |
|---|---|---|
| `RecognizeDocumentTextUseCaseTest` | 17 | **chọn engine theo cài đặt**, luồng nhận dạng (engine giả) |
| `ScanViewModelTest` | 25 | quét + lưu + **tự động nhận dạng sau khi lưu** |
| `OfflineDocumentRepositoryTest` | 16 | Room + file thật |
| `LocalDocumentFileStorageTest` | 15 | ghi `.tmp` rồi đổi tên |
| `ExportDocumentAsPdfUseCaseTest` | 14 | gồm **lớp chữ ẩn gắn đúng trang** |
| `VersionComparatorTest` | 13 | **so sánh phiên bản** |
| `LibraryViewModelTest` | 13 | danh sách/tìm kiếm/xoá |
| `NormalizedOcrLineTest` | 12 | **toạ độ Tesseract → tỉ lệ** (khoá lỗi chữ mất khỏi PDF) |
| `GitHubReleaseSourceTest` | 12 | **đọc JSON GitHub Releases** |
| `AssetTessdataProvisionerTest` | 9 | **giải nén model thật** qua `AssetManager` |
| `PdfFileNamesTest` | 9 | tên file PDF an toàn |
| `FormattersTest` | 9 | định dạng ngày/dung lượng |
| `VnTextNormalizerTest` | 8 | tìm kiếm không dấu |
| `CheckForUpdateUseCaseTest` | 7 | **nghiệp vụ cập nhật** |
| `DocumentStorageMaintenanceTest` | 7 | dọn thùng rác/mồ côi |
| `DataStoreSettingsRepositoryTest` | 6 | **lưu engine bằng DataStore thật** |
| `ContentUriDocumentOutputTargetTest` | 5 | ghi đè đúng |
| `UpdateUiStateTest` | 5 | **phần trăm tải** |
| `ScanVietDatabaseMigrationTest` | 3 | **nâng cấp Room v1→v2** |
| Còn lại | — | `DocumentDetailViewModelTest` (18), `CreateDocumentUseCaseTest`, `RenameDocumentUseCaseTest`, `ImportScannedDocumentUseCaseTest`, `RobolectricPdfLimitationTest` |

Đối chiếu với lần chạy 193 test trước Giai đoạn B: **+41 test**, tập trung vào phần mới: chọn engine theo cài đặt, toạ độ dòng từ Tesseract, giải nén model, lưu cài đặt, và tự động nhận dạng sau khi lưu.

**Lần chạy 284 test (bản 0.6.1):** thêm **12 test** so với 272, tập trung vào bốn lỗi ở mục dưới — phân biệt lý do thất bại, trang rỗng không tính là thành công, và không tự chạy lại OCR khi đã thất bại.

**Ba lỗi thật đã bắt được trong các lần chạy này:**1. `NormalizedOcrLineTest` — kiểm chứng bằng cách **cố tình tái tạo lỗi cũ** (`bottom = 0f`) thì **6/12 test gãy**. Nếu lỗi đó lọt ra bản phát hành, **toàn bộ chữ biến mất khỏi PDF** mà không có lỗi nào được báo.
2. `DataStoreSettingsRepositoryTest` — 4/6 test gãy vì **lỗi ở chính test** (hai `DataStore` cùng mở một file). Đây là lỗi thiết kế test thật, đã sửa lại cho đúng ràng buộc một-DataStore của ứng dụng. Điều này cũng chứng minh bộ test chạy trên DataStore **thật**, không phải đồ giả.
3. **Công tắc "Tự động nhận dạng chữ sau khi quét" là một điều khiển không hoạt động.** Nó được lưu vào cài đặt và hiển thị đúng, nhưng **không được đọc ở bất kỳ đâu** — bật hay tắt đều không thay đổi gì. Phát hiện khi rà lại toàn bộ luồng quét. Đã sửa: lưu xong thì nhận dạng chữ nếu công tắc bật, và **lỗi nhận dạng không làm mất tài liệu vừa lưu**. 8 test mới bảo vệ hành vi này; đã kiểm chứng bằng cách **cố tình bỏ phần gọi nhận dạng → 4 test gãy**.

Một lỗi thật đã bắt được trong lần chạy này: test `VersionComparatorTest` phát hiện `VersionComparator` **im lặng coi `"v0.5.0"` là phiên bản 0**. Đã sửa **mã nguồn** (bỏ tiền tố `v`/`V` trong `parse`) chứ không sửa test cho qua — vì hậu quả của lỗi đó là ứng dụng **không bao giờ** báo có bản cập nhật, mà cũng không hề báo lỗi.

### Bốn lỗi thật trong luồng nhận dạng chữ (bản 0.6.1)

Người dùng báo: danh sách hiện nhãn đỏ **"Lỗi nhận dạng"**, mở tài liệu thì phần chữ **hoàn toàn trống**. Rà lại toàn bộ luồng tìm được bốn lỗi thật, mỗi lỗi đều có test khoá lại:

1. **Trang "đã chạy nhưng rỗng" bị tính là thành công.** Điều kiện kiểm tra là `results.isEmpty()` — tức "không có TRANG nào". Nhưng khi engine chạy xong mà ảnh không có chữ, `results` chứa một `OcrText` với `lines` rỗng, nên `results` **không** rỗng. Hệ quả: tài liệu được đánh dấu `DONE` dù không có một chữ nào, người dùng thấy "Đã có chữ", mở ra thì trống, và PDF xuất ra thiếu lớp chữ **mà không có cảnh báo nào**. Đã sửa thành kiểm tra **số DÒNG chữ**, không phải số trang.

2. **Lỗi kiểu bị che trong `TesseractOcrEngine`.** Bản cũ viết `engine.resultIterator?.let { ... }`. Khi iterator là `null`, khối `let` không chạy, `withLock` trả về `Unit`, và vì `withContext` suy ra kiểu chung `Any?`, hàm `recognize` **lặng lẽ trả về `Unit`** thay vì `OcrPageResult?`. Bên gọi chỉ kiểm tra `null`, nên `Unit` đi lọt qua như một kết quả "thành công" không có dòng chữ nào. Đã sửa thành `?: return@withLock null` để lỗi không bị che.

3. **Mọi nguyên nhân thất bại đều thành một câu chung "Lỗi nhận dạng".** Người dùng không phân biệt được "ảnh mờ, quét lại đi" với "máy hết dung lượng" — hai hành động hoàn toàn khác nhau. Đã thêm `OcrFailureReason` (`NO_TEXT_FOUND` / `PAGE_UNREADABLE` / `ENGINE_UNAVAILABLE`) và ba thông báo riêng.

4. **Mở lại tài liệu đã `FAILED` thì tự chạy lại OCR vô hạn.** `autoRecognize` chỉ kiểm tra "đã có chữ chưa", mà tài liệu lỗi thì không có chữ — nên **mỗi lần mở** lại chạy lại toàn bộ, chắc chắn thất bại y hệt, đốt hàng chục giây với model BEST. Đã thêm cờ `ocrFailed` chặn vòng lặp đó, kèm nút **"Thử nhận dạng lại"** trong màn hình chi tiết để người dùng chủ động chạy sau khi đã sửa nguyên nhân.

> **Lỗi công cụ đáng ghi nhớ:** khai báo một `enum class` top-level **cùng file** với một `sealed class` và các class con làm trình phân tích FIR của Kotlin (mà `lint` dùng) gãy với `RAW_FIR to TYPES` khi phân tích `androidTest`. Triệu chứng rất dễ chẩn đoán nhầm thành "lỗi mã nguồn của tôi": `:app:lintDebug` một mình thì PASS, chỉ `:app:lintAnalyzeDebugAndroidTest` gãy, và thông báo lỗi chỉ đổ vào stack của compiler. **Cách chẩn đoán quyết định:** `git stash` rồi chạy lại — lỗi biến mất nghĩa là do thay đổi của mình, không phải lỗi có sẵn. Sửa bằng cách tách enum ra file riêng (`domain/OcrFailureReason.kt`), vừa hết lỗi vừa gọn hơn.

**Kết quả sau khi sửa:** **284 unit test / 0 lỗi / 0 bỏ qua / 27 suite** (tăng 12 test), `assembleDebug` + `assembleRelease` + `lintDebug` + `compileDebugAndroidTestKotlin` đều BUILD SUCCESSFUL.

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

Các dòng đối chứng chứng minh cách tìm này không cho kết quả dương tính giả, nên kết luận "release không còn công cụ thử nghiệm" là đáng tin. Kích thước APK: debug 12,34 MB, release 2,14 MB *(số của Giai đoạn 2b)*.

**Cập nhật 20/10/2026 (sau Giai đoạn B — Tesseract):** debug **55,34 MB**, release **44,81 MB**. APK to lên là hệ quả trực tiếp của ba quyết định có chủ đích: (a) gói **hai ABI ARM** (`arm64-v8a` + `armeabi-v7a`) thay vì tất cả — đánh đổi là **không cài được lên emulator x86/x86_64**; (b) thêm **mô hình OCR đóng gói sẵn** cùng native lib của ML Kit; và (c) **hai file model tiếng Việt của Tesseract** (`vie_best` 11,86 MB + `vie_fast` 0,51 MB) cùng native lib Tesseract/Leptonica. Đã kiểm chứng phần tự cập nhật **sống sót qua R8** bằng cách tìm chuỗi trong `.dex`, kèm hai chuỗi đối chứng âm. Cũng đã kiểm chứng bằng `jar tf` rằng cả hai file model và cả hai ABI của `libtesseract.so`/`libleptonica.so` **thật sự có trong APK release**, không phải chỉ có trong thư mục nguồn.

| Chuỗi cần tìm | Kết quả | Ý nghĩa |
|---|---|---|
| `api.github.com/repos` | có | mã nguồn tự cập nhật không bị R8 xoá |
| `ScanVie` | có | tên repo đúng (khớp `git remote`) |
| `scanviet-update.apk` | có | tên file APK tải về |
| `application/vnd.android.package-archive` | có | MIME mở trình cài đặt |
| `CAMSCANNER_NEGATIVE_CONTROL` (đối chứng âm) | không | cách tìm không cho dương tính giả |
| `khong-bao-gio-ton-tai-xyz` (đối chứng âm) | không | như trên |

Kiểm chứng thêm cho phần FileProvider chia sẻ PDF, đọc trực tiếp từ APK (không suy đoán từ mã nguồn):

| Thứ cần đúng | Cách kiểm | Kết quả |
|---|---|---|
| Authority khớp giữa manifest và code | `aapt2 dump xmltree --file AndroidManifest.xml` | `authorities="vn.scanviet.app.fileprovider"` — khớp `"$packageName.fileprovider"` |
| Ứng dụng khác không tự đọc được | như trên | `exported=false`, `grantUriPermissions=true` |
| Vùng chia sẻ chỉ mở thư mục nháp | `aapt2 dump xmltree --file res/xml/file_paths.xml` | đúng **hai** mục, cả hai đều là `cache-path`: `scan_scratch/` (PDF chia sẻ) và `updates/` (APK cập nhật). **Không** mở `filesDir` — tài liệu của người dùng vẫn kín |
| Quyền đã khai báo | `aapt2 dump permissions` | `REQUEST_INSTALL_PACKAGES` (cho luồng tự cập nhật), `INTERNET` + `ACCESS_NETWORK_STATE` (đi kèm ML Kit), permission nội bộ của AndroidX. **Không** `CAMERA`, **không** quyền bộ nhớ |

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
│                                   :core:domain/ocr          (OcrEngine, OcrEngineKind, OcrEngineProvider,
│                                                               TessdataProvisioner, NormalizedOcrLine - cổng + logic thuần)
│                                   :core:domain/update       (UpdateSource + VersionComparator - cổng)
│                                   :core:domain/image        (PageImageLoader - cổng, trả DecodedImage)
├── data/         → :core:data      Room (entity/dao/database + Migration 1→2), DataStore,
│                                   repository impl, file/LocalDocumentFileStorage, scan/,
│                                   export/, image/ (AndroidPageImageLoader + LRU cache),
│                                   di/, dev/ (chỉ debug)
├── platform/     → :platform:*     scanner/ (DocumentScannerEngine + ML Kit impl + DI)
│                                   ocr/     (MlKitOcrEngine, TesseractOcrEngine, AssetTessdataProvisioner + DI)
│                                   pdf/     (PdfExporter thật + DI)
│                                   update/  (GitHubReleaseSource + ApkInstaller + DI)
├── ui/           → :feature:*      theme, components, common, library, viewer, scan,
│                                   settings, update
└── navigation/   → :app            ScanVietRoutes + ScanVietNavHost
```

Quy tắc phụ thuộc (kiểm tra được bằng mắt qua import):

- `domain/` **không** import Compose, Room, Android UI, hay `data/`. Các cổng như `DocumentFileStorage`, `PageImageReader` nằm ở đây vì use case cần chúng mà không được biết Android.
- `ui/` không truy cập `data/` trực tiếp — chỉ qua interface trong `domain/`. Riêng thứ thuộc Android SDK không thể diễn đạt bằng domain (`DocumentScannerEngine` cần `Activity`/`IntentSender`) thì nằm ở `platform/`; `ui/` chỉ chuyển tiếp `IntentSender`/`Intent`.
- `platform/` biết SDK cụ thể (ML Kit, `android.graphics.pdf`) nhưng **không** chứa quy tắc nghiệp vụ. Cùng một lý do như `platform/scanner`: `PdfDocument` là API Android, không diễn đạt được bằng domain, nên cổng `PdfExporter` nằm ở `domain/export/` và cài đặt thật nằm ở `platform/pdf/`.
- `navigation/` là nơi **duy nhất** biết tất cả màn hình; các màn hình chỉ nhận callback.
- `domain/` **không** biết Android: `PageImageLoader` trả `DecodedImage` (chỉ có `width`/`height`) chứ không trả `Bitmap`; `OcrEngine` nhận đường dẫn tương đối và trả `OcrText`; `UpdateSource`/`VersionComparator` dùng kiểu JDK thuần. Nhờ vậy thay engine OCR hay thay nguồn cập nhật **không đụng** tới Room, ViewModel hay PDF exporter.

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
                                             │  (chỉ khi lưu THÀNH CÔNG)
                                             ▼
                                   ScanUiState.Saved(documentId)
                                             │  nếu "Tự động nhận dạng" đang bật
                                             ▼
                                  RecognizeDocumentTextUseCase
                                    - chạy SAU khi tài liệu đã lưu xong
                                    - lỗi nhận dạng KHÔNG làm mất tài liệu
                                    - lỗi nhận dạng KHÔNG đưa về màn hình xem lại
                                      (nếu đưa về, người dùng bấm "Lưu" lần nữa
                                       -> tạo tài liệu TRÙNG)
```

**Vì sao nhận dạng chạy sau khi lưu:** nhận dạng chữ là bước *thêm giá trị*, không phải điều kiện để lưu thành công. Nếu nó thất bại, người dùng **vẫn có tài liệu** — mất chữ còn hơn mất cả tài liệu vừa quét công phu.

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
| Metadata tài liệu | Room `scanviet.db` | `exportSchema = true`, schema **v1 và v2** đã commit ở `app/schemas/` |
| Chữ nhận dạng được | Room `ocr_pages` + `ocr_lines` (schema v2) | Toạ độ **tỉ lệ 0–1** so với ảnh gốc, nên lớp chữ không lệch khi khổ trang PDF đổi. Nâng cấp bằng `MIGRATION_1_2` tường minh — **không** `fallbackToDestructiveMigration`, dữ liệu cũ giữ nguyên |
| Cài đặt | DataStore Preferences | chủ đề, OCR tự động, giữ ảnh gốc, **tự kiểm tra cập nhật**, **engine nhận dạng chữ** (`ocr_engine`), **chất lượng model** (`ocr_model`) |
| Vùng nháp khi scan | `cacheDir/scan_scratch/` | dọn sạch khi mở app |
| PDF tạm để chia sẻ | `cacheDir/scan_scratch/<ten-tai-lieu>.pdf` | không lưu thêm bản trong app; do Android được phép dọn khi hết chỗ |
| APK cập nhật tải về | `cacheDir/updates/scanviet-update.apk` | tên **cố định**, ghi đè mỗi lần tải; cache nên bị hệ thống xoá được bất cứ lúc nào — app tải lại khi cần |
| PDF người dùng lưu | nơi người dùng chọn qua SAF | nằm **ngoài** vùng của app, app không quản lý |

Không xin quyền bộ nhớ ngoài và **không** xin `CAMERA`. Cả quét (ảnh do Play services trả về), lưu PDF và chia sẻ đều không cần quyền bộ nhớ: lưu dùng SAF, chia sẻ dùng `FileProvider`.

### Quyền thực tế trong APK (đã kiểm chứng)

Ứng dụng tự khai báo **đúng một** quyền, cộng thêm các quyền do thư viện hợp nhất vào:

| Quyền | Nguồn | Ghi chú |
|---|---|---|
| `android.permission.REQUEST_INSTALL_PACKAGES` | **app tự khai báo** | Chỉ để mở trình cài đặt của hệ thống cho APK cập nhật. Hệ thống **vẫn** hỏi người dùng bật "cài từ nguồn không xác định" — app không tự cài. **Phải bỏ khi phát hành qua Google Play**, vì Play cấm ứng dụng tự tải và cài APK |
| `android.permission.INTERNET` | `com.google.android.datatransport:transport-backend-cct` (phụ thuộc bắc cầu của ML Kit) | Kênh truyền số liệu của Google; cũng là thứ cho phép lần quét đầu tải công cụ quét |
| `android.permission.ACCESS_NETWORK_STATE` | cùng nguồn | |
| `vn.scanviet.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | AndroidX | Quyền nội bộ, `protectionLevel=signature` |

`CAMERA` **không** cần: giao diện quét nằm trong tiến trình của Google Play services (activity `com.google.mlkit.vision.documentscanner.internal.GmsDocumentScanningDelegateActivity`, do SDK khai báo, `exported=false`) — chính Play services xin quyền camera. Đã kiểm chứng bằng cách đọc `AndroidManifest.xml` trong `play-services-mlkit-document-scanner-16.0.0.aar`: AAR đó không khai báo quyền nào.

Nguồn của từng quyền tra được ở `app/build/outputs/logs/manifest-merger-debug-report.txt`.

**Mã nguồn trong `vn.scanviet.app` gọi HTTP đúng hai lần, cả hai đều là `GET` chỉ đọc và đều do người dùng chủ động:**

| # | Nơi | URL | Gửi đi cái gì |
|---|---|---|---|
| 1 | `GitHubReleaseSource.fetchLatestRelease()` | `api.github.com/repos/phanphu399/ScanVie/releases/latest` | **Không gửi nội dung tài liệu.** Chỉ có header `Accept` và `User-Agent`. URL công khai, không có tham số nhận dạng người dùng |
| 2 | `ApkInstaller.downloadApk()` | URL file APK trong bản phát hành | Không có. Chỉ chạy khi người dùng **bấm** "Tải và cài đặt" |

Cả hai đều **tắt được**: công tắc "Tự kiểm tra bản cập nhật" trong Cài đặt chặn lần gọi #1; lần gọi #2 chỉ xảy ra khi người dùng bấm nút. Ảnh, PDF và văn bản OCR **không bao giờ** rời khỏi thiết bị.

### Luồng nhận dạng chữ và lớp chữ ẩn (Giai đoạn 3, mở rộng ở Giai đoạn B)

```
DocumentDetailViewModel.onRecognizeText()  /  (tự chạy trước khi xuất PDF)
   │
   ▼
RecognizeDocumentTextUseCase
   1. tài liệu có tồn tại không  (không -> lỗi TRƯỚC khi bật trạng thái RUNNING)
   2. lấy danh sách trang của tài liệu
   3. bỏ qua trang ĐÃ nhận dạng (không chạy lại — tốn thời gian vô ích)
   4. ĐỌC CÀI ĐẶT để chọn engine (không cố định lúc khởi tạo)
         │
         ▼
      OcrEngineProvider.forKind(kind, quality)
         ├── TESSERACT -> TessdataProvisioner.ensureInstalled(quality)   (giải nén model)
         │                   └── TesseractOcrEngine.forModel(...)
         └── ML_KIT    -> MlKitOcrEngine                                 (model có sẵn trong APK)
   5. mỗi trang còn lại: engine.recognize(file, rotation) -> OcrPageResult
   6. ghi Room: 1 dòng ocr_pages (kèm imageWidth/imageHeight) + n dòng ocr_lines
                 toạ độ chuẩn hoá 0..1
   7. một trang lỗi KHÔNG làm hỏng cả tài liệu; trang không có chữ vẫn được ghi
      (phân biệt "trang trắng" với "trang chưa xử lý")
```

**Vì sao engine được chọn lúc chạy chứ không lúc khởi tạo:** người dùng đổi engine trong Cài đặt rồi bấm nhận dạng lại thì phải dùng engine mới **ngay**, không cần tắt mở ứng dụng. Đổi lại, mỗi lần nhận dạng phải đọc cài đặt — chi phí không đáng kể so với việc nhận dạng chính nó.

**Vì sao lấy engine TRƯỚC khi bật `RUNNING`:** nếu model chưa cài được (hết chỗ, asset thiếu), lỗi được ném ra khi tài liệu **chưa** bị đánh dấu `RUNNING`. Nếu thứ tự bị đảo, giao diện sẽ kẹt ở trạng thái "đang nhận dạng" vĩnh viễn. Có test riêng cho điều này.

#### Bốn chi tiết kỹ thuật của `TesseractOcrEngine`

| Quyết định | Lý do |
|---|---|
| **Toạ độ ở mức DÒNG (`RIL_TEXTLINE`)** | `getUTF8Text()` không trả hộp bao, mà thiếu hộp bao thì lớp chữ ẩn trong PDF không vẽ được. Tesseract đã phân tích bố cục trang để quyết định từ nào thuộc dòng nào, nên đọc thẳng ở mức dòng đúng hơn là tự ghép từ bằng cách so chồng lấn hộp bao |
| **`TessBaseAPI` được TÁI SỬ DỤNG** | Khởi tạo đọc model 12 MB từ đĩa và dựng cấu trúc nội bộ, tốn hàng trăm ms. Với tài liệu 20 trang, tạo mới mỗi trang cộng thêm hàng chục giây vô ích. Instance chỉ dựng lại khi **đổi model** |
| **Mọi lời gọi bọc trong `Mutex`** | `TessBaseAPI` KHÔNG an toàn đa luồng. Dùng một instance chung và tuần tự hoá (thay vì một instance mỗi luồng) để không giữ thêm 12 MB model trong RAM cho mỗi luồng |
| **Giải mã ở 2500px cạnh dài, `RGB_565`** | Tesseract dùng đặc trưng hình học của nét chữ nên cần chi tiết hơn ML Kit; ~210 DPI cho trang A4 đủ giữ dấu (dấu mũ, dấu hỏi, dấu nặng đều là nét nhỏ). `RGB_565` giảm nửa bộ nhớ ảnh mà không kém chính xác (Tesseract tự nhị phân hoá) |

Phép chuyển hộp bao pixel → toạ độ tỉ lệ được tách ra `domain/ocr/NormalizedOcrLine.kt` — hàm thuần, kiểm thử được trên JVM, không cần thư viện native. Xem `NormalizedOcrLineTest` (12 test) ở bảng trên.

Khi xuất PDF, `ExportDocumentAsPdfUseCase` đọc chữ đã lưu và chuyển thành lớp chữ **vô hình** vẽ đè lên ảnh:

```
AndroidPdfExporter.drawInvisibleTextLayer()
   - Paint.alpha = 1   (KHÔNG dùng alpha = 0: PdfDocument sẽ BỎ QUA lệnh vẽ)
   - textSize = chiều cao hộp / 1.3
   - textScaleX kéo giãn vừa đúng bề rộng hộp, kẹp trong 0.05..20
   - ghép theo pageIndex, nên trang nào thiếu chữ thì chỉ trang đó có lớp rỗng
```

Vì sao chữ phải vô hình mà không phải chữ thật: PDF giữ **ảnh gốc** làm nội dung nhìn thấy, lớp chữ chỉ phục vụ Ctrl+F và copy. Nếu vẽ chữ đè lên ảnh thì bản in ra sẽ bị chồng hai lớp chữ.

### Luồng tự cập nhật (Giai đoạn 4)

```
SettingsScreen
   ├── LaunchedEffect (nếu bật "Tự kiểm tra cập nhật") ──> UpdateViewModel.checkForUpdate()
   └──(bấm "Kiểm tra cập nhật")─────────────────────────> UpdateViewModel.checkForUpdate()
                                                              │
                                          CheckForUpdateUseCase
                                           1. UpdateSource.fetchLatestRelease()   (GET, chỉ đọc)
                                           2. VersionComparator.isNewer(remote, BuildConfig.VERSION_NAME)
                                              ├── có bản mới hơn -> UpdateUiState.Available
                                              ├── bằng/không có  -> UpToDate
                                              └── lỗi mạng/JSON  -> UpdateCheckFailedException
                                                                    (TUYỆT ĐỐI không nói "đã mới nhất")
                                          (bấm "Tải và cài đặt")
                                                              │
                                                     ApkInstaller
                                                       1. tải cacheDir/updates/scanviet-update.apk (tên cố định)
                                                       2. canRequestPackageInstalls()?
                                                            ├── chưa -> mở Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES
                                                            └── rồi -> Intent.ACTION_VIEW qua FileProvider (content://)
```

Ba điểm cố ý làm chặt, đều đã có test:

1. **Lỗi mạng không bị nói dối thành "đã là bản mới nhất".** `CheckForUpdateUseCaseTest` kiểm tra đúng điều này — đây là loại lỗi khiến người dùng mãi không nhận được bản sửa.
2. **App không tự cài APK.** Nó chỉ mở trình cài đặt của hệ thống; quyền cài vẫn do người dùng quyết định.
3. **Một tiến trình tải tại một thời điểm.** Cờ `isDownloading` được bật **đồng bộ** trước `launch`, chặn bấm đúp làm hỏng file APK.

---

## Nguyên tắc riêng tư (bắt buộc, không thoả hiệp)

- Mặc định `android:allowBackup="false"` + loại trừ mọi domain trong `data_extraction_rules.xml` → **không** Auto Backup tài liệu lên Google Drive của người dùng.
- Không có máy chủ riêng, không đăng nhập, không analytics, không quảng cáo trong MVP.
- **Không** ghi log tên tài liệu, nội dung OCR, hay đường dẫn file. Thông điệp lỗi không chứa tên tài liệu.
- Xoá tài liệu là soft delete 30 ngày rồi mới xoá file thật.
- Câu marketing đúng: **"Tài liệu được xử lý trên thiết bị, không tải lên máy chủ."** Không hứa "offline 100%" vì ML Kit Document Scanner có thể cần Google Play services + mạng ở lần dùng đầu.
- **Ngoại lệ duy nhất đã biết:** hai lần gọi HTTP chỉ-đọc ở bảng trên. Cả hai **không** mang nội dung tài liệu, và cả hai đều tắt được. Khi bổ sung engine OCR đám mây (tùy chọn, xem `MVP_SPEC.md` §2.5) thì **phải** sửa mục này, thêm màn hình xác nhận rõ ràng trong app, và khai báo lại Data Safety trên Play.

---

## Lộ trình

| Giai đoạn | Nội dung |
|---|---|
| 1 ✅ | Nền tảng: Compose + Nav + ViewModel + Room + DataStore, quản lý tài liệu, thùng rác, tìm kiếm không dấu |
| 2a ✅ | Quét thật bằng ML Kit Document Scanner (nhiều trang), xem lại/sắp xếp/xoá trang, copy ảnh vào app storage, dọn rác khi lỗi |
| 2b ✅ | Xuất PDF nhiều trang + chia sẻ/lưu bản sao qua SAF |
| 3 ✅ | Ảnh thu nhỏ trong danh sách, trình xem toàn màn hình phóng to được, khung nhận dạng chữ + lớp chữ ẩn trong PDF, nâng Room lên schema v2 |
| 4 ✅ | Tự kiểm tra và cập nhật qua GitHub Releases, mở trình cài đặt hệ thống |
| 4b 🔴 | **Thay engine OCR** — ML Kit v2 không nhận dạng được tiếng Việt có dấu (`MVP_SPEC.md` §2.5). Cổng `OcrEngine` đã tách sẵn nên không đụng Room/ViewModel/PDF |
| 5 | Trải nghiệm & hiệu năng, dọn giao diện |
| 6 | Phát hành: icon, screenshots, privacy policy, Data Safety, closed testing ≥14 ngày |

Chi tiết từng bước và tiêu chí dừng: [`MVP_SPEC.md`](MVP_SPEC.md) mục 4 và 6.
