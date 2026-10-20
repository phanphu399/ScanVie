# ScanViet — Kiến trúc dữ liệu & module (định hướng bảo trì lâu dài)

> Nguyên tắc gốc: **MVP không có backend.** File tài liệu nằm trong bộ nhớ riêng của app; metadata + text OCR trong Room; cài đặt trong DataStore; xuất/chia sẻ qua Storage Access Framework.
> Nhưng mọi quyết định ở đây được chọn sao cho **sau này thêm backup rồi thêm cloud sync mà không phải viết lại dữ liệu**.

---

## 1. Phân biệt 3 loại dữ liệu (đồng ý với đề xuất, có chỉnh)

> **Trạng thái (20/10/2026):** §2–§5, §5b, §5c đã **code và có test**. §6 (backup), §9 (cloud sync) vẫn là **thiết kế**, chưa code. Mục nào đã code đều được ghi rõ ngay đầu mục.

| Loại | Nơi lưu | Ghi chú quan trọng |
|---|---|---|
| File tài liệu (PDF, JPG) | `filesDir/documents/<docUuid>/` | Riêng tư; app khác không đọc được; xóa khi gỡ app |
| Metadata + text OCR | Room | Metadata **không** chứa nội dung PDF, chỉ trỏ tới file |
| Cài đặt người dùng | DataStore | Không có hồ sơ/đăng nhập ở MVP |
| APK cập nhật | `cacheDir/updates/` | **Chỉ là cache** — không phải dữ liệu người dùng, hệ thống được phép xoá |

**Chỉnh so với đề xuất ban đầu:**

- **Không dùng tên file theo tên tài liệu** (`doc_001.pdf`) và **không đổi tên file khi người dùng đổi tên tài liệu.** Tên hiển thị chỉ nằm trong Room. Lý do: đổi tên = 1 UPDATE trong Room (tức thì, không rủi ro hỏng file), tên không trùng nhau, ký tự tiếng Việt/emoji không phá đường dẫn.
- **Khóa chính là UUID**, không phải số tự tăng. Lý do: khi cần sync/merge nhiều thiết bị sau này, số tự tăng sẽ đụng nhau.
- **Lưu đường dẫn TƯƠNG ĐỐI** (`documents/<uuid>/page_0001.jpg`), không lưu đường dẫn tuyệt đối. Lý do: đường dẫn tuyệt đối (`/data/user/0/.../files/...`) có thể khác nhau giữa các bản cài/máy → backup khôi phục sang máy mới là mất liên kết ngay. Resolve bằng `context.filesDir` lúc chạy.

---

## 2. Cây thư mục thực tế trên máy (Giai đoạn 1 → 4)

```
filesDir/
└── documents/
    └── <docUuid>/
        ├── page_0001.jpg      (ảnh JPEG do trình quét trả về; không strip EXIF ở v1)
        └── page_0002.jpg

cacheDir/
├── scan_scratch/              (vùng cho file tạm)
│   └── <ten-tai-lieu>.pdf     (PDF tạm sinh ra để chia sẻ — Giai đoạn 2b)
└── updates/                   (Giai đoạn 4)
    └── scanviet-update.apk    (tên CỐ ĐỊNH, ghi đè mỗi lần tải)
```

Luồng quét **không** dùng `scan_scratch/`: ảnh được đọc trực tiếp từ URI của trình quét và ghi thẳng vào `documents/<uuid>/page_XXXX.jpg`. Thư mục nháp chỉ dùng cho PDF tạm khi chia sẻ, và bị dọn trong `cleanScratchSpace()` lúc mở app. Toàn bộ thư mục nháp bị dọn khi mở app nên **không** có nguy cơ tích tụ dung lượng.

`updates/` cố ý dùng **tên file cố định** thay vì tên theo phiên bản: mục đích là để mỗi lần tải **ghi đè** bản cũ, không bao giờ tích tụ nhiều APK trong cache. Vì nằm trong `cacheDir`, hệ thống được phép xoá nó bất cứ lúc nào — app coi đó là chuyện bình thường và tải lại khi người dùng bấm nút lần nữa. **Hai** thư mục `cache/scan_scratch/` và `cache/updates/` cũng chính là hai mục duy nhất được `FileProvider` mở ra (xem §5).

**Chưa có** (đúng phạm vi, không phải thiếu sót): `thumbs/` (ảnh thu nhỏ được giải mã theo nhu cầu và giữ trong **bộ nhớ đệm LRU**, không ghi ra đĩa), bản sao PDF cố định trong app (xuất PDF là *lưu bản sao* ra nơi người dùng chọn bằng SAF, không lưu thêm một bản vào app để không gấp đôi dung lượng), và `doc.pdf` trong thư mục tài liệu.

Vì sao PDF chia sẻ **không** nằm trong `filesDir/documents/<uuid>/`: nội dung đó là bản sao dùng một lần. Để trong `cacheDir` thì Android được phép tự dọn khi máy hết chỗ, và người dùng không thấy dung lượng app phình lên sau mỗi lần chia sẻ.

Quy tắc bất di bất dịch của luồng ghi dữ liệu — gọi là **"ghi file trước, ghi Room sau, lỗi thì dọn"**:

1. **Trước khi ghi bất cứ thứ gì**: kiểm tra tên và số trang. (Nếu không, bạn tạo rác rồi mới phải dọn.)
2. Tạo `filesDir/documents/<uuid>/`.
3. Với mỗi trang: đọc ảnh từ URI của trình quét → ghi `page_XXXX.jpg` (ghi `.tmp` rồi đổi tên để không bao giờ có file dở dang).
4. Khi **mọi** ảnh đã ghi xong → ghi bản ghi vào Room.
5. Bất kỳ bước nào lỗi → **xoá sạch** thư mục vừa tạo rồi mới ném lỗi ra ngoài.

Vì sao bước sao chép là bắt buộc: URI do ML Kit Document Scanner trả về là **file tạm trong vùng của Play services**, không thuộc ứng dụng; chúng có thể biến mất bất cứ lúc nào. Vì vậy "dòng" bản gốc vào vùng riêng của app là điều kiện để app không mất trang khi trình quét dọn cache.

Vì sao chỉ giữ một ảnh tại một thời điểm trong RAM: `ContentUriPageImageReader` đọc trọn một trang (~1–3 MB) rồi ghi đi, nên 20 trang không làm tăng mức dùng bộ nhớ.

---

## 3. Schema Room (thiết kế sẵn cho backup + sync sau này)

```sql
-- ===== v1 (đã commit ở app/schemas/.../1.json) =====
documents(
  id            TEXT PRIMARY KEY,      -- UUID
  title         TEXT NOT NULL,         -- tên hiển thị, do người dùng đặt
  titleSearch   TEXT NOT NULL,         -- bản bỏ dấu, có index
  createdAt     INTEGER NOT NULL,
  updatedAt     INTEGER NOT NULL,      -- sẵn cho sync sau này
  deletedAt     INTEGER,               -- soft delete = thùng rác 30 ngày + tombstone cho sync
  pageCount     INTEGER NOT NULL,
  ocrStatus     TEXT NOT NULL,         -- NONE | RUNNING | DONE | FAILED
  sizeBytes     INTEGER
)

pages(
  id TEXT PRIMARY KEY, documentId TEXT NOT NULL, pageIndex INTEGER NOT NULL,
  imageFile TEXT NOT NULL,             -- đường dẫn TƯƠNG ĐỐI
  width INTEGER, height INTEGER, rotationDeg INTEGER
)

-- ===== v2 thêm (MIGRATION_1_2, đã commit ở .../2.json) =====
ocr_pages(
  documentId TEXT NOT NULL, pageIndex INTEGER NOT NULL,
  text TEXT NOT NULL,
  textSearch TEXT NOT NULL,            -- bản bỏ dấu, chỉ dùng để tìm kiếm
  imageWidth INTEGER NOT NULL,         -- để quy đổi toạ độ 0..1 ra điểm PDF
  imageHeight INTEGER NOT NULL,
  PRIMARY KEY(documentId, pageIndex)
)

ocr_lines(
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  documentId TEXT NOT NULL, pageIndex INTEGER NOT NULL,
  lineIndex INTEGER NOT NULL,
  text TEXT NOT NULL,
  left REAL NOT NULL, top REAL NOT NULL,
  right REAL NOT NULL, bottom REAL NOT NULL   -- toạ độ CHUẨN HOÁ 0..1
)

documents_fts                        -- FTS4 trên title + textSearch (chưa làm; xem ghi chú)
```

Chi tiết đáng chú ý:

### 3.1. Trạng thái đã code — v1 và v2 đều đã chạy thật

| Mốc | File | Nội dung |
|---|---|---|
| v1 | `app/schemas/vn.scanviet.app.data.local.ScanVietDatabase/1.json` | `documents` + `pages` |
| v2 | `app/schemas/vn.scanviet.app.data.local.ScanVietDatabase/2.json` | thêm `ocr_pages` + `ocr_lines` |

**Mọi thay đổi schema từ nay phải là migration thật**, vì cả `1.json` và `2.json` đã được commit làm mốc. Tuyệt đối không `fallbackToDestructiveMigration()` trong bản release — nó xoá sạch metadata (mà file thì còn) → app mất toàn bộ danh sách tài liệu của người dùng.

### 3.2. Vì sao `ocr_lines` là bảng riêng chứ không nhồi JSON vào `ocr_pages`

Thiết kế ban đầu ở trên chỉ có `ocr_pages.text`. Bản v2 thêm bảng `ocr_lines` với toạ độ từng dòng, vì **lớp chữ ẩn trong PDF cần toạ độ** để đặt chữ đúng chỗ. Hai lựa chọn đã cân:

| Cách | Ưu | Nhược |
|---|---|---|
| Nhồi JSON toạ độ vào một cột `ocr_pages.linesJson` | 1 bảng, ghi/đọc một phát | Không truy vấn được theo dòng; sửa một dòng phải ghi lại cả trang; JSON hỏng thì mất cả trang |
| **Bảng `ocr_lines` (đã chọn)** | Truy vấn/đếm được; ghi một dòng lỗi không phá cả trang; schema tự mô tả | Nhiều dòng hơn (một trang A4 ~ 40–60 dòng) |

Toạ độ lưu **chuẩn hoá 0..1** so với ảnh gốc, kèm `imageWidth`/`imageHeight` của trang. Nhờ vậy khi đổi khổ trang PDF (A4 dọc ↔ A4 ngang) hoặc đổi độ phân giải ảnh, lớp chữ **vẫn thẳng hàng** mà không phải nhận dạng lại. Đây là lý do `ocr_pages` cần hai cột kích thước dù chúng "có vẻ" đã nằm ở bảng `pages`.

### 3.3. Các quyết định khác

- **Tìm kiếm tiếng Việt:** FTS của SQLite tokenizer `unicode61` không xử lý tốt dấu tiếng Việt. Cách làm đúng và rẻ: chuẩn hoá cả query và nội dung bằng `java.text.Normalizer` (NFD → bỏ combining marks, `đ/Đ` → `d`) rồi index cột `titleSearch`. Người dùng gõ "hoa don" vẫn tìm ra "Hóa đơn"; vẫn giữ `text` gốc để hiển thị.
- **`documents_fts` chưa làm.** Với vài chục tài liệu, `LIKE '%query%' ESCAPE '\'` trên `titleSearch` nhanh và ít lỗi hơn. FTS chỉ đáng làm khi cần tìm **trong nội dung chữ đã nhận dạng** — mà tính năng đó còn chờ engine OCR đạt (`MVP_SPEC.md` §2.5). Làm FTS trước khi có chữ đúng chất lượng là tối ưu sớm.
- **`ocrEngineVersion` chưa thêm.** Sẽ thêm cùng lúc với việc thay engine OCR, vì lúc đó mới thật sự cần biết "trang này do engine nào đọc" để re-OCR. Thêm cột bây giờ là cột chết.
- **`ocr_pages` tách riêng khỏi `pages`:** cho phép chạy lại OCR một trang (khi nâng cấp engine), và cho phép xóa text mà không đụng ảnh.
- **Thùng rác (soft delete) là tính năng của MVP**, không phải "nice-to-have": người dùng xoá nhầm hợp đồng là mất luôn. `deletedAt != null` → ẩn khỏi danh sách, dọn file sau 30 ngày.
- **Test bảo vệ migration:** `ScanVietDatabaseMigrationTest` dựng lại schema v1 **bằng SQL thô** với đúng identity hash trong `1.json`, rồi mở bằng Room v2 và kiểm tra dữ liệu cũ còn nguyên. Test này đã được chứng minh **thật sự bắt lỗi** bằng cách cố tình đổi tên bảng `ocr_lines` → cả 3 test FAIL.

---

## 4. DataStore — chìa khoá cài đặt

```
-- Đã code (Giai đoạn 1 → B)
theme            = system | light | dark
autoOcr          = Boolean            (OCR ngay sau khi scan hay để người dùng bấm)
keepOriginal     = Boolean            (giữ ảnh gốc chưa filter)
checkForUpdates  = Boolean            (mặc định TRUE — tự kiểm tra bản cập nhật khi mở Cài đặt)
ocr_engine       = tesseract | ml_kit (mặc định tesseract — engine duy nhất đọc được tiếng Việt có dấu)
ocr_model        = best | fast        (mặc định best; chỉ áp dụng khi ocr_engine = tesseract)

-- Chưa làm (đúng phạm vi)
language         = vi | en            (v1 chỉ có tiếng Việt)
imageQuality     = Int  (60–95)
defaultExportUri = String?            (Tree URI, đã takePersistableUriPermission)
onboardingDone   = Boolean
reviewPromptAt   = Long?              (đếm lần export thành công thứ 3 → in-app review)
backupLastAt     = Long?
```

---

## 5. Xuất / chia sẻ file (SAF)

> **Trạng thái: đã code (Giai đoạn 2b).** Xem `platform/pdf/AndroidPdfExporter`, `data/export/ContentUriDocumentOutputTarget`, `domain/usecase/ExportDocumentAsPdfUseCase`.

- **Xuất PDF ra chỗ người dùng chọn:** `ActivityResultContracts.CreateDocument("application/pdf")` → ghi bản sao. File đã xuất nằm ngoài vùng app nên **không** bị xoá khi gỡ app (đúng như bảng của bạn).
- **Chia sẻ nhanh:** `FileProvider` + `ACTION_SEND` (`content://` URI). Authority là `${applicationId}.fileprovider`; `res/xml/file_paths.xml` **chỉ** mở **hai** `cache-path`: `scan_scratch/` (PDF chia sẻ) và `updates/` (APK cập nhật) — không expose `filesDir` và không expose cả cây thư mục. Đã kiểm chứng bằng `aapt2 dump xmltree` trên APK thật, không suy đoán từ mã nguồn.
- **Ghi đè phải cắt bớt:** mở bằng chế độ `"wt"`, không phải `"w"`. `"w"` để lại đuôi của file cũ khi nội dung mới ngắn hơn → PDF hỏng. Đã có test riêng cho hành vi này.
- **Thư mục mặc định (tuỳ chọn):** `OpenDocumentTree` + `takePersistableUriPermission` để lần sau export 1 chạm — **chưa làm**, sẽ cân nhắc sau.
- Không xin `READ/WRITE_EXTERNAL_STORAGE` ở bất kỳ đâu → ít rủi ro bị Play soi quyền.

Vị trí hợp đồng xuất PDF (quan trọng cho quy tắc phụ thuộc): cổng **thuần Kotlin** nằm ở `domain/export/`:

- `PdfExporter.export(OutputStream, List<PdfPageSource>)` — `PdfPageSource` chỉ chứa `java.io.File` + góc xoay + **danh sách `PdfTextLine`** (chữ và hộp đã chuẩn hoá).
- `DocumentOutputTarget.open(PdfOutputTarget)` — tách riêng khỏi `PdfExporter` vì hai việc khác nhau: *mở đâu* (Android, cần `Context`/`ContentResolver`) và *ghi gì* (thuần JVM). Gộp lại sẽ kéo `ContentResolver` vào use case và làm mất khả năng kiểm thử trên JVM.

Cài đặt thật: `platform/pdf/AndroidPdfExporter` (dùng `android.graphics.pdf.PdfDocument`) và `data/export/ContentUriDocumentOutputTarget`. Đổi sang thư viện PDF khác chỉ cần sửa `platform/pdf/`.

**Lớp chữ ẩn — ba chi tiết dễ sai, đều đã xử lý:**

1. **`Paint.alpha = 1`, không phải `0`.** Đặt alpha bằng 0 tưởng là hợp lý ("chữ vô hình") nhưng `PdfDocument` **bỏ qua** lệnh vẽ có alpha 0 → lớp chữ rỗng, copy không ra gì. Đây là loại lỗi không báo, chỉ im lặng sai.
2. **`textScaleX` kéo giãn theo hộp.** Chữ nhận dạng được có bề rộng khác chữ vẽ ra; không kéo giãn thì vệt bôi đen lệch khỏi dòng, người dùng bôi đen ra chữ của dòng khác.
3. **Ghép theo `pageIndex`, không theo thứ tự mảng.** Nếu database trả về lộn thứ tự, lớp chữ sẽ gắn sai trang. Đã có test cho đúng điều này.

**Giới hạn đã đo được:** `android.graphics.pdf.PdfDocument` **không chạy được** trên Robolectric (xem `RobolectricPdfLimitationTest`), nên chất lượng file PDF do exporter sinh ra — kể cả **lớp chữ ẩn có thật sự copy được bằng Ctrl+F hay không** — chỉ xác nhận được trên thiết bị thật. Phần logic của use case thì đã kiểm thử tự động đầy đủ.

---

## 5b. Nhận dạng chữ (OCR) — cổng, lưu trữ, và trạng thái thật

> **Trạng thái: engine đúng đã tích hợp (Giai đoạn B) — Tesseract + `vie.traineddata` là mặc định. Chất lượng chữ tiếng Việt trên ảnh thật: CHƯA ĐO ĐƯỢC** — xem `MVP_SPEC.md` §2.5 và §6.2.

Cổng thuần Kotlin ở `domain/ocr/`:

```
OcrEngine.recognize(relativePath: String): OcrText       <- cổng cơ bản
TesseractOcrEnginePort : OcrEngine                       <- + forModel(dir, quality)
MlKitOcrEnginePort     : OcrEngine                       <- không cần cấu hình
IdentifiedOcrEngine    : có `kind: OcrEngineKind`
OcrEngineProvider.forKind(kind, quality): OcrEngine       <- chọn engine theo cài đặt
TessdataProvisioner.ensureInstalled(quality): File        <- cổng cài model
OcrText = List<OcrBlock>, mỗi block có text + hộp bao + danh sách dòng
```

`domain/` **không** biết ML Kit hay `TessBaseAPI`: engine nhận **đường dẫn tương đối** (nhất quán với `imageFile` trong Room) và trả dữ liệu thuần Kotlin, không trả `Bitmap` hay kiểu của SDK nào. Cài đặt thật ở `platform/ocr/MlKitOcrEngine` và `platform/ocr/TesseractOcrEngine`.

**Vì sao có nhiều cổng thay vì một:** hai engine cần **cấu hình khác nhau**. Tesseract phải được trỏ tới file model và chất lượng model; ML Kit không cần gì (model nằm trong APK). Nếu gộp thành một interface duy nhất có `forModel`, ML Kit sẽ phải nhận tham số nó không dùng, và `FakeOcrEngine` trong test phải giả lập cả việc giải nén file. `OcrEngineProvider` là chỗ **duy nhất** biết sự khác biệt đó.

**Vì sao `TessdataProvisioner` là cổng riêng:** việc giải nén 12 MB từ `assets/` ra đĩa là **I/O file**, không phải nhận dạng chữ. Gộp chung vào `OcrEngine` sẽ khiến một interface về nhận dạng phải gánh thêm trách nhiệm cài đặt, và làm test luồng nhận dạng phải giả lập việc giải nén.

**Vì sao đây là thiết kế quan trọng:** cài đặt thật ở Giai đoạn B đã chứng minh điều này — thêm Tesseract chỉ cần thêm file trong `platform/ocr/` và sửa `OcrModule`, **không sửa một dòng nào** trong Room, ViewModel, PDF exporter, hay luồng quét. Đó chính là lợi ích cụ thể của Clean Architecture trong dự án này, không phải lý thuyết.

**Vì sao `NormalizedOcrLine` nằm ở `domain/` chứ không ở `platform/`:** phép chuyển hộp bao pixel → toạ độ tỉ lệ quyết định lớp chữ ẩn trong PDF có vẽ được hay không, nhưng bản thân nó không liên quan tới Tesseract. Đặt ở `domain/` nghĩa là nó **kiểm thử được trên JVM thuần**. Điều này đã trả giá trị ngay: một phiên bản đầu trả `bottom = 0f` cho mọi dòng, làm **toàn bộ chữ biến mất khỏi PDF** mà không có lỗi nào — 12 test ở `NormalizedOcrLineTest` giờ chặn lỗi đó, và đã kiểm chứng bằng cách cố tình tái tạo lỗi (6/12 test gãy).

**Vì sao lưu chữ theo dòng với toạ độ chuẩn hoá** (chứ không lưu cả trang thành một khối text):

- Lớp chữ ẩn trong PDF cần **hộp của từng dòng** để đặt chữ đúng chỗ. Chỉ có text thuần thì không vẽ được.
- Toạ độ **0..1 so với ảnh gốc** nên không phụ thuộc độ phân giải ảnh hay khổ trang PDF.
- Một trang A4 sinh khoảng 40–60 dòng; với 20 trang là ~1200 dòng — không đáng kể với SQLite.

**Trang đã nhận dạng mà không có chữ vẫn được ghi**, để phân biệt "trang trắng" với "trang chưa xử lý". Nếu không ghi, mỗi lần xuất PDF app lại chạy lại nhận dạng trang trắng đó — chậm mà vô ích.

> 🟡 **Cảnh báo kỹ thuật quan trọng nhất của phần này:** model ML Kit nằm trong APK là `Latn_ctc` (đã kiểm chứng bằng cách đọc entry `assets/mlkit-google-ocr-models/.../Latn_ctc/optical/conv_model.fb` trong APK release). Nó **bỏ dấu** hoặc trả chuỗi rỗng với tiếng Việt, và **không sửa được bằng cấu hình**. Vì vậy mặc định đã chuyển sang **Tesseract + `vie.traineddata`** (Giai đoạn B, §2.5), và ML Kit chỉ còn là lựa chọn người dùng phải **chủ động bật**, kèm cảnh báo ngay trên màn hình Cài đặt.
>
> `RecognizeDocumentTextUseCaseTest` dùng **engine giả**: nó chứng minh **luồng nghiệp vụ** (bỏ qua trang đã xử lý, một trang lỗi không phá cả tài liệu, ghi đúng toạ độ chuẩn hoá, chọn engine theo cài đặt) và **không** chứng minh chất lượng nhận dạng. `AssetTessdataProvisionerTest` dùng **file model thật** qua `AssetManager` của Robolectric, nên chứng minh được việc giải nén. Nhưng **chất lượng chữ đọc ra trên ảnh chụp thực tế thì chưa có phép đo nào** — xem `MVP_SPEC.md` §6.2.

---

## 5c. Tự cập nhật (Giai đoạn 4) — và vì sao nó phải chết khi lên Play

```
domain/update/
  UpdateSource            ← cổng: fetchLatestRelease(): AppRelease?
  VersionComparator       ← so phiên bản bằng SỐ, không bằng chuỗi
domain/usecase/
  CheckForUpdateUseCase   ← hiện thực thuần Kotlin, không @Inject (nhận currentVersion qua constructor)
platform/update/
  GitHubReleaseSource     ← đọc api.github.com bằng HttpURLConnection của JDK
  ApkInstaller            ← tải APK + mở trình cài đặt hệ thống
```

**Vì sao dùng `HttpURLConnection` của JDK thay vì OkHttp:** cả tính năng chỉ có hai request GET đơn giản, không cần connection pool, interceptor, hay cache. Thêm OkHttp (kèm Okio) chỉ để gọi hai URL là đánh đổi không đáng, và làm APK to thêm. Nếu sau này số request tăng hoặc cần cấu hình phức tạp, thay bằng OkHttp chỉ cần sửa `GitHubReleaseSource`.

**Vì sao `VersionComparator` không so chuỗi:** `"0.10.0" > "0.9.0"` đúng theo nghĩa phiên bản nhưng **sai** khi so chuỗi (`'1' < '9'`). So chuỗi sẽ khiến app coi bản 0.10.0 là cũ hơn 0.9.0 và **không bao giờ** báo cập nhật — lỗi im lặng, người dùng chỉ phát hiện khi đã lâu không nhận được bản sửa lỗi. Lớp này **bỏ tiền tố `v`/`V`** ở chính nó, không chỉ ở nơi đọc dữ liệu: nhãn `v1.2.0` là quy ước rất phổ biến và có thể đến từ nguồn khác trong tương lai. (Lỗi này đã bị test bắt được trong lần chạy thật — xem `README.md`.)

**Vì sao lỗi mạng không được coi là "đã là bản mới nhất":** `CheckForUpdateUseCase` ném `UpdateCheckFailedException` khi nguồn không đọc được. Gộp lỗi mạng vào kết quả `UpToDate` là nói dối người dùng và che mất sự cố — đúng loại lỗi mà cả lớp này tồn tại để chống. Giao diện hiển thị "Không kiểm tra được bản cập nhật", không phải "Bạn đang dùng bản mới nhất".

**Hai điều kiện vận hành, đã kiểm chứng thực nghiệm bằng HTTP:**

| Endpoint | Điều kiện | Kết quả đo |
|---|---|---|
| `/repos/phanphu399/ScanVie` | repo **private** | HTTP **404** |
| `/repos/phanphu399/ScanVie` | repo **public** | HTTP **200** |
| `/releases/latest` | chỉ có bản **pre-release** | HTTP **404** |
| `/releases` | như trên | HTTP **200**, liệt kê được `v0.2-beta` |

→ Bản phát hành muốn app **nhìn thấy** phải là bản **chính thức, KHÔNG tick pre-release**. Đây là cái bẫy đã được ghi thẳng vào mã nguồn (`GitHubReleaseSource`) và checklist phát hành (`MVP_SPEC.md`).

**🔴 Phải chết khi lên Google Play.** Chính sách Play **cấm** ứng dụng tự tải và cài APK. Khi phát hành qua Play:
- Xoá quyền `REQUEST_INSTALL_PACKAGES` khỏi manifest.
- Bỏ `ApkInstaller` và `ui/update/`.
- Thay bằng **Play In-App Updates** (`app-update` của Play Core) hoặc đơn giản là để Play tự lo.
- `CheckForUpdateUseCase` có thể **giữ lại** cho kênh phân phối ngoài Play (APK trực tiếp, bản beta nội bộ), nhưng phải tách kênh rõ ràng — nguồn cập nhật đã nằm sau cổng `UpdateSource` nên việc này chỉ là đổi cài đặt, không phải viết lại.

---

## 6. Sao lưu / khôi phục (thiết kế ngay, code ở v1.1)

Định dạng: **một file zip `.svbk`** người dùng tự chọn nơi lưu (Drive, SD, máy tính).

```
scanviet_backup_2026-10-17.svbk
├── manifest.json
│     { "formatVersion": 1, "schemaVersion": 3, "appVersionName": "1.1.0",
│       "exportedAt": 1760000000000, "documentCount": 12, "checksum": "sha256:..." }
├── data/
│     ├── documents.json      (đổ từ Room, KHÔNG copy file .db)
│     ├── pages.json
│     └── ocr_pages.json
└── files/
      └── <docUuid>/page_0001.jpg ...
```

Vì sao export JSON thay vì copy `scanviet.db`: file `.db` gắn chặt với schema; JSON + `schemaVersion` cho phép app đời sau đọc bản backup cũ và tự nâng cấp. Đây là điểm quyết định giữa "backup dùng được 6 tháng" và "backup dùng được 3 năm".

**Quy trình restore (transactional, không đoán):**
1. Đọc `manifest.json`; nếu `formatVersion` cao hơn app hỗ trợ → báo "cần cập nhật app", dừng.
2. Giải nén ra `cacheDir/restore_tmp`.
3. Đối chiếu: mỗi `page.imageFile` phải có file thật → thiếu thì báo rõ "3 trang bị thiếu, vẫn khôi phục 9 trang còn lại".
4. Insert Room trong **một transaction**; chỉ khi commit thành công mới move file vào `filesDir`.
5. Trùng `id`: hỏi người dùng "Bỏ qua / Giữ cả hai (đổi tên)".

**Test bắt buộc cho backup (dễ sai nhất trong cả app):** backup rỗng · 1 doc 1 trang · doc 5 trang có OCR · zip thiếu file · manifest version mới hơn app · restore lên máy đã có doc trùng id · hủy giữa lúc restore (không được để lại DB rác).

---

## 7. `allowBackup` — cái bẫy phá lời hứa riêng tư

Mặc định Android **tự động backup dữ liệu app lên Google Drive của người dùng** (Auto Backup) — tức là file scan có thể bị đẩy lên máy chủ Google mà người dùng không biết, trong khi app quảng cáo "không gửi tài liệu lên máy chủ". Xử lý:

**Quyết định đã áp dụng ở Giai đoạn 1 (không phải "hoặc" nữa):**

- `android:allowBackup="false"` trong `AndroidManifest.xml`.
- Vẫn khai báo `android:dataExtractionRules="@xml/data_extraction_rules"` với **mọi domain bị loại trừ** (`<exclude domain="root|file|database|sharedpref|external|device_file|device_database|device_sharedpref|device_root" />`).
- **Không** tạo `backup_rules.xml` (`android:fullBackupContent`) vì `allowBackup="false"` đã tắt Auto Backup trên mọi phiên bản Android; thêm file đó chỉ là cấu hình chết.
- Vì sao vẫn giữ `dataExtractionRules`: một số hãng/OEM và công cụ chuyển máy có thể bỏ qua `allowBackup`; khai báo tường minh là lớp bảo vệ thứ hai, và cũng là bằng chứng rõ ràng cho Data Safety form trên Play.
- **Hệ quả đã chấp nhận:** người dùng đổi máy sẽ mất danh sách tài liệu (trừ khi tự export). Đổi lại là lời hứa riêng tư đúng 100%. Việc sao lưu do người dùng chủ động làm qua tính năng `.svbk` ở mục 6 → đúng lời hứa, đúng kỳ vọng.

---

## 8. Kiến trúc module

> **Quyết định đã áp dụng ở Giai đoạn 1: MỘT module `:app` duy nhất.**
>
> Cả mục này mô tả **thiết kế đích**, không phải cấu trúc hiện tại. Lý do chọn 1 module cho MVP:
> - `AGENTS.md` §2 yêu cầu rõ: "Start with one Gradle app module and well-separated packages. Do not introduce additional modules... without a documented reason."
> - Chia 13 module + `build-logic` cho ~70 file là chi phí thuần tuý lúc này: chưa có ai review, chưa có CI matrix, chưa có lợi ích build song song (build hiện tại 2–3 phút).
> - Việc tách module sau này là **cơ học**: move package + thêm `build.gradle.kts`. Ranh giới package trong `:app` được đặt **trùng tên với module tương lai** để việc tách chỉ còn là đổi thư mục:
>
> ```
> vn.scanviet.app.core        → :core:common      (DispatcherProvider, text, format, time, di)
> vn.scanviet.app.domain      → :core:model       (model, repository interface, use case, rules)
> vn.scanviet.app.data        → :core:data        (Room, DataStore, FileStorage, repository impl)
> vn.scanviet.app.ui.*        → :feature:*        (library / scan / settings / theme=:core:designsystem)
> vn.scanviet.app.navigation  → :app              (chỉ nơi này biết mọi màn hình)
> ```
>
> Quy tắc phụ thuộc bên dưới **vẫn được tuân thủ ngay trong 1 module**: `domain` không import `androidx.compose.*`, không import Room, không import `data`. Kiểm tra được bằng mắt qua danh sách import — và CI có thể thêm luật này sau.

**Đồng ý với hướng module hoá, nhưng KHÔNG chia theo kiểu "mỗi feature có presentation/domain/data riêng".** Lý do: như vậy mỗi feature lại tự mở Room/DataStore → 5 nơi cùng truy cập database, sửa schema phải sửa 5 module, đúng cái "đống file phụ thuộc lẫn nhau" mà bạn muốn tránh.

### Quy tắc phụ thuộc (bất di bất dịch)

```
:app  →  :feature:*  →  :core:*  +  :platform:*
                          ▲
      không bao giờ đi ngược lại; :feature không phụ thuộc :feature khác
```

- **`:core:model`** — Kotlin thuần, không có Android SDK: `Document`, `Page`, `OcrResult`. Test chạy mili-giây.
- **`:core:data`** — nơi DUY NHẤT biết Room/DataStore/FileStore. Chứa `DocumentRepository`, `SettingsRepository`, `FileStore`, `BackupManager`. Feature chỉ nhìn thấy interface.
- **`:platform:*`** — bọc SDK bên thứ ba (ML Kit, AdMob). Đổi vendor hoặc tách engine chỉ sửa ở đây.
  - `:platform:scanner` (ML Kit Document Scanner), `:platform:ocr` (`OcrEngine` + impl ML Kit **và Tesseract** + `TessdataProvisioner`), `:platform:pdf`, `:platform:update` (`UpdateSource` GitHub + `ApkInstaller`), `:platform:ads` (tạo khi thật sự gắn quảng cáo — **không** tạo module rỗng).
- **`:feature:*`** — chỉ Compose + ViewModel + use case của chính nó. Không truy cập Room trực tiếp, không depend feature khác. Mỗi feature expose 1 entry composable + route constant; `:app` là nơi ráp `NavHost`.
- **`:app`** — chỉ wiring: `Application`, `MainActivity`, `NavHost`, DI gốc.

### Danh sách module chốt

```
:app
:core:common          Result, DispatcherProvider, VnTextNormalizer, Formatters, file utils
:core:model           model thuần Kotlin
:core:designsystem    theme, component dùng chung
:core:data            Room + DataStore + FileStore + Repository + Backup + image loader
:core:testing         fake, fixture, helper test (chỉ dùng ở testImplementation)
:platform:scanner     bọc ML Kit Document Scanner
:platform:ocr         OcrEngine + impl (Tesseract mặc định, ML Kit cho tiếng Anh)
:platform:pdf         xuất PDF + lớp chữ ẩn
:platform:update      UpdateSource (GitHub Releases) + ApkInstaller
:feature:library      trang chủ + danh sách + tìm kiếm + chi tiết/đổi tên/xoá/thùng rác
:feature:scan         chụp + xem trước + sắp xếp/xoá trang
:feature:viewer       trình xem toàn màn hình phóng to được
:feature:ocr          OCR + sửa text + copy/share
:feature:settings     cài đặt + kiểm tra cập nhật + xuất/nhập backup
```

`preview/` nằm trong `:feature:scan` (cùng một luồng, cùng state) — tách ra chỉ tổ phải bắc cầu state qua navigation.
`:platform:ads` tạo muộn, khi thêm quảng cáo.

> ⚠️ **`:platform:update` phải bị xoá khi phát hành qua Google Play** (Play cấm tự cài APK — xem §5c). Đây là module duy nhất trong danh sách có "hạn sử dụng".

### Quy tắc đặt "cổng" (interface) — đã áp dụng ngay từ Giai đoạn 2a

Không phải cổng nào cũng được đặt ở chỗ giống nhau. Tiêu chí: **cổng có diễn đạt được bằng loại Java thuần hay không.**

| Cổng | Đặt ở | Vì sao |
|---|---|---|
| `DocumentRepository` | `domain/repository/` | thuần Kotlin, không cần Android |
| `DocumentFileStorage` | `domain/storage/` | chỉ dùng `java.io.File`; use case ghi trang cần nó mà không được biết `Context` |
| `PageImageReader` | `domain/scan/` | nhận `String` (URI dạng chuỗi); cài đặt thật (`ContentResolver`) nằm ở `data/scan/` |
| `PdfExporter` ✅ | `domain/export/` | nhận `java.io.File` + `OutputStream`; `PdfDocument` thật nằm ở `platform/pdf/` |
| `DocumentOutputTarget` ✅ | `domain/export/` | nhận `PdfOutputTarget` (sealed: SAF URI hoặc file nháp); `ContentResolver` thật nằm ở `data/export/` |
| `OcrEngine` ✅ | `domain/ocr/` | nhận đường dẫn tương đối, trả `OcrText` thuần Kotlin. **Nếu để cổng này nhận `Bitmap`, `domain/` sẽ dính Android và không kiểm thử được trên JVM.** Trả `DecodedImage` (chỉ `width`/`height`) cho `PageImageLoader` cũng vì lý do này |
| `UpdateSource` ✅ | `domain/update/` | trả `AppRelease` (dữ liệu thuần); `HttpURLConnection` thật nằm ở `platform/update/` |
| `DocumentScannerEngine` | `platform/scanner/` | **bắt buộc** phải nhận `Activity`/`IntentSender` — đó là Activity của Play services, không thể diễn đạt bằng domain |
| `ApkInstaller` | `platform/update/` | **bắt buộc** phải nhận `Context` + `FileProvider` + `Intent` — không thể diễn đạt bằng domain |

Hệ quả: `domain/` không import `android.*` (chỉ `java.io`/`java.util`); `ui/` không import `data/` — trừ khi đi qua cổng trong `domain/`. Thứ Android không diễn đạt được thì đẩy ra `platform/` và để `ui/` làm công việc chuyển tiếp `IntentSender`/`Intent`.

Gói `platform/scanner/` **đã được tạo trong `:app`** ở Giai đoạn 2a — tức là bước "tạo `:platform:scanner`" ở bảng lộ trình dưới đây đã đi trước một nửa: phần interface + DI + cài đặt ML Kit đã xong, việc tách Gradle module sau này chỉ còn là move package + thêm `build.gradle.kts`.

### Giữ multi-module không biến thành gánh nặng

- `gradle/libs.versions.toml` — version catalog, một chỗ khai báo phiên bản.
- **`build-logic/` (composite build) + convention plugin**: `scanviet.android.library`, `scanviet.android.feature`, `scanviet.android.compose`, `scanviet.android.hilt`. Nhờ vậy mỗi `build.gradle.kts` chỉ còn ~10 dòng; nếu không có cái này thì 13 module sẽ là 13 file cấu hình copy-paste → ác mộng bảo trì.
- CI (GitHub Actions): build debug + unit test + ktlint/detekt. Không có CI thì multi-module không đem lại lợi ích gì.

### Lộ trình tách module (không dựng hết một lượt)

| Giai đoạn | Module tạo mới |
|---|---|
| 1. Nền tảng ✅ **đã làm: chỉ `:app`** | (không tách module; ranh giới package trùng tên module tương lai) |
| 2. Scan & PDF ✅ **đã code, chưa tách** | `:platform:scanner`, `:platform:pdf`, `:feature:scan` (+ `:core:*` tách khỏi `:app`) |
| 3. OCR + xem trang ✅ **đã code, chưa tách** | `:platform:ocr`, `:feature:ocr`, `:feature:viewer` |
| 4. Quản lý tài liệu + tự cập nhật ✅ **đã code, chưa tách** | `:feature:library`, `:feature:settings`, `:platform:update` |
| 5. Phát hành | `:core:testing` + (nếu gắn ads) `:platform:ads`; **xoá `:platform:update` nếu lên Play** |

`build-logic/` (convention plugin) chỉ đáng tạo **trước khi** tách từ 4 module trở lên, không phải ngay bây giờ.

Tách feature ra module sau khi đã viết xong là việc cơ học (move package, thêm `build.gradle.kts`, gỡ Hilt module), nên **không cần dựng sẵn 13 module ngay ngày đầu**.

---

## 9. Đường nâng cấp lên cloud (không phải viết lại dữ liệu)

Những gì đã chuẩn bị sẵn ở trên khiến việc thêm sync sau này là **thêm chứ không phải sửa**:

| Chuẩn bị | Vì sao cần khi có sync |
|---|---|
| PK = UUID | Tránh đụng khoá giữa nhiều thiết bị |
| `updatedAt` | Giải quyết xung đột (last-write-wins hoặc merge) |
| `deletedAt` (tombstone) | Không cần "hỏi server xem doc có bị xoá không" |
| `remoteRev` (để trống) | Cột chống ghi đè; thêm sau vẫn được nhờ migration |
| Đường dẫn tương đối | File di chuyển/đồng bộ không phá DB |
| `ocrEngineVersion` | Máy khác engine → biết trang nào cần re-OCR. **Vẫn chưa thêm cột** — Giai đoạn B đã làm cho engine **đổi được lúc chạy**, nên cột này trở nên cần thiết hơn trước. Hiện tại đổi engine **không** tự nhận dạng lại tài liệu cũ: chữ cũ vẫn giữ nguyên (và có thể đang thiếu dấu nếu được sinh bởi ML Kit). Đây là **hạn chế đã biết**, cần xử lý ở giai đoạn sau |
| `formatVersion` trong backup | Định dạng dữ liệu có thể tiến hoá an toàn |

Khi nào mới làm cloud: chỉ khi có người dùng thật hỏi "tôi đổi máy thì sao" **và** tính năng backup `.svbk` không đủ. Lúc đó vẫn không bắt đăng nhập để scan — sync là tuỳ chọn.

---

## 10. Kiểm thử cho phần lưu trữ (không bỏ qua)

**Đã có (234 test / 0 lỗi / 0 bỏ qua / 24 suite — xem bảng đầy đủ ở `MVP_SPEC.md` §6.1):**

- `FileStore` (Robolectric, dùng thư mục tạm): save/move/delete, đổi tên không đụng tên file, doc bị thiếu file → báo lỗi đúng. → `LocalDocumentFileStorageTest` (15)
- Room migration: `ScanVietDatabaseMigrationTest` (3) dựng schema v1 bằng **SQL thô** với đúng identity hash trong `1.json` rồi mở bằng v2. **Không dùng `room-testing`** — để tránh thêm dependency chỉ cho một test, và quan trọng hơn: dựng schema bằng SQL thô kiểm tra đúng thứ cần kiểm tra (dữ liệu cũ đọc được bằng schema mới), không kiểm tra lại định nghĩa do Room sinh ra. Test đã được chứng minh **thật sự bắt lỗi** (đổi tên bảng `ocr_lines` → 3/3 FAIL).
- Repository trên Room thật + `filesDir` giả: `OfflineDocumentRepositoryTest` (16).
- `ExportDocumentAsPdfUseCaseTest` (14): sắp trang theo `pageIndex`, thiếu ảnh → lỗi kèm số trang, stream đóng đúng một lần kể cả khi exporter lỗi, **lớp chữ ẩn gắn đúng trang**, trang chưa nhận dạng → xuất với lớp rỗng chứ không lỗi.
- `RecognizeDocumentTextUseCaseTest` (17): **engine giả** — chứng minh luồng nghiệp vụ và **chọn engine theo cài đặt**, không chứng minh chất lượng nhận dạng (xem §5b).
- `NormalizedOcrLineTest` (12): hàm thuần JVM, khoá hành vi toạ độ mà lớp chữ ẩn trong PDF phụ thuộc. **Đã kiểm chứng bằng cách cố tình tái tạo lỗi `bottom = 0f` → 6/12 test gãy.**
- `AssetTessdataProvisionerTest` (9): dùng **file model thật** qua `AssetManager` của Robolectric — kiểm tra tên thư mục/tên file mà Tesseract tìm, vị trí `filesDir`, thay model khi đổi chất lượng, chỉ giữ một model.
- `DataStoreSettingsRepositoryTest` (6): **DataStore thật** — khoá và định dạng giá trị của cài đặt engine; giá trị lạ → rơi về mặc định.
- `GitHubReleaseSourceTest` (11), `CheckForUpdateUseCaseTest` (7), `VersionComparatorTest` (13), `UpdateUiStateTest` (5) — phần tự cập nhật.

**Chưa có (đúng phạm vi):**

- `BackupManager`: round-trip (export → xoá sạch → import → so sánh từng trường), cộng 7 case ở mục 6. Backup chưa được code.
- Test FTS: chưa có FTS (`documents_fts` chưa làm — xem §3.3).
- UI test nhẹ (xoá → hoàn tác → khôi phục): chưa làm. Hiện có unit test ở tầng ViewModel (`LibraryViewModelTest` 13) bảo vệ logic thùng rác, nhưng chưa có test bấm tay trên Compose.
- Test instrumented: **chưa có test nào**, và AGENTS.md yêu cầu bổ sung khi có thiết bị/emulator. Đây là lỗ hổng kiểm thử lớn nhất còn lại — mọi thứ phụ thuộc giao diện hệ thống (trình quét ML Kit, trình cài đặt APK, SAF) hiện chỉ được kiểm bằng tay.
