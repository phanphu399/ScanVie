# ScanViet — Kiến trúc dữ liệu & module (định hướng bảo trì lâu dài)

> Nguyên tắc gốc: **MVP không có backend.** File tài liệu nằm trong bộ nhớ riêng của app; metadata + text OCR trong Room; cài đặt trong DataStore; xuất/chia sẻ qua Storage Access Framework.
> Nhưng mọi quyết định ở đây được chọn sao cho **sau này thêm backup rồi thêm cloud sync mà không phải viết lại dữ liệu**.

---

## 1. Phân biệt 3 loại dữ liệu (đồng ý với đề xuất, có chỉnh)

| Loại | Nơi lưu | Ghi chú quan trọng |
|---|---|---|
| File tài liệu (PDF, JPG) | `filesDir/documents/<docUuid>/` | Riêng tư; app khác không đọc được; xóa khi gỡ app |
| Metadata + text OCR | Room | Metadata **không** chứa nội dung PDF, chỉ trỏ tới file |
| Cài đặt người dùng | DataStore | Không có hồ sơ/đăng nhập ở MVP |

**Chỉnh so với đề xuất ban đầu:**

- **Không dùng tên file theo tên tài liệu** (`doc_001.pdf`) và **không đổi tên file khi người dùng đổi tên tài liệu.** Tên hiển thị chỉ nằm trong Room. Lý do: đổi tên = 1 UPDATE trong Room (tức thì, không rủi ro hỏng file), tên không trùng nhau, ký tự tiếng Việt/emoji không phá đường dẫn.
- **Khóa chính là UUID**, không phải số tự tăng. Lý do: khi cần sync/merge nhiều thiết bị sau này, số tự tăng sẽ đụng nhau.
- **Lưu đường dẫn TƯƠNG ĐỐI** (`documents/<uuid>/page_0001.jpg`), không lưu đường dẫn tuyệt đối. Lý do: đường dẫn tuyệt đối (`/data/user/0/.../files/...`) có thể khác nhau giữa các bản cài/máy → backup khôi phục sang máy mới là mất liên kết ngay. Resolve bằng `context.filesDir` lúc chạy.

---

## 2. Cây thư mục thực tế trên máy (Giai đoạn 1 + 2a)

```
filesDir/
└── documents/
    └── <docUuid>/
        ├── page_0001.jpg      (ảnh JPEG do trình quét trả về; không strip EXIF ở v1)
        └── page_0002.jpg

cacheDir/
└── scan_scratch/              (vùng cho file tạm. Hiện luồng quét KHÔNG dùng nó:
                                ảnh được đọc trực tiếp từ URI của trình quét và ghi
                                thẳng vào documents/<uuid>/page_XXXX.jpg. Sẽ dùng cho
                                bản PDF tạm khi chia sẻ — Giai đoạn 2b.)
```

**Chưa có** (đúng phạm vi, không phải thiếu sót): `thumbs/` (không dùng thư viện tải ảnh), bản sao PDF cố định trong app (xuất PDF là *lưu bản sao* ra nơi người dùng chọn bằng SAF, không lưu thêm một bản vào app để không gấp đôi dung lượng), và `doc.pdf` trong thư mục tài liệu.

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
documents(
  id            TEXT PRIMARY KEY,      -- UUID
  title         TEXT NOT NULL,         -- tên hiển thị, do người dùng đặt
  createdAt     INTEGER NOT NULL,
  updatedAt     INTEGER NOT NULL,      -- sẵn cho sync sau này
  deletedAt     INTEGER,               -- soft delete = thùng rác 30 ngày + tombstone cho sync
  pageCount     INTEGER NOT NULL,
  ocrStatus     TEXT NOT NULL,         -- NONE | RUNNING | DONE | FAILED
  ocrLanguage   TEXT,                  -- "vi"
  ocrEngineVersion TEXT,               -- biết text này do engine nào sinh ra → re-OCR khi nâng cấp
  sizeBytes     INTEGER,
  remoteRev     INTEGER                -- để trống ở MVP, dành cho sync sau
)

pages(
  id TEXT PRIMARY KEY, documentId TEXT NOT NULL, pageIndex INTEGER NOT NULL,
  imageFile TEXT NOT NULL,             -- đường dẫn TƯƠNG ĐỐI
  width INTEGER, height INTEGER, rotationDeg INTEGER
)

ocr_pages(
  documentId TEXT NOT NULL, pageIndex INTEGER NOT NULL,
  text TEXT NOT NULL,                  -- bản gốc để hiển thị/copy
  textSearch TEXT NOT NULL,            -- bản đã bỏ dấu, chỉ dùng để tìm kiếm
  PRIMARY KEY(documentId, pageIndex)
)

documents_fts                        -- FTS4 trên title + textSearch
```

Chi tiết đáng chú ý:

> **Trạng thái đã code (Giai đoạn 1) — đọc trước khi tin vào schema ở trên.**
> Schema trên là **thiết kế đích**. Bản v1 đã chạy (`app/schemas/vn.scanviet.app.data.local.ScanVietDatabase/1.json`) chỉ có **2 bảng `documents` và `pages`**. Chưa có:
> - `ocr_pages` và `documents_fts` → thêm ở Giai đoạn 3 cùng lúc với OCR (không có text thì bảng rỗng vô nghĩa).
> - `remoteRev`, `ocrLanguage`, `ocrEngineVersion` → thêm khi thật sự cần (sync / nâng cấp engine).
> - Cột `titleSearch TEXT NOT NULL` (bản bỏ dấu của `title`) **đã có** và **có index** — đây là thứ phục vụ tìm kiếm ở v1.
> - Tìm kiếm v1 dùng `LIKE '%query%' ESCAPE '\'` trên `titleSearch` (có thoát `%` và `_`). FTS chỉ đáng làm khi tìm cả **nội dung OCR** (Giai đoạn 3) — với vài chục tài liệu thì `LIKE` nhanh và ít lỗi hơn.
> Mọi thay đổi schema từ nay phải là migration thật, vì `1.json` đã được commit làm mốc.

- **Tìm kiếm tiếng Việt:** FTS của SQLite tokenizer `unicode61` không xử lý tốt dấu tiếng Việt. Cách làm đúng và rẻ: chuẩn hoá cả query và nội dung bằng `java.text.Normalizer` (NFD → bỏ combining marks, `đ/Đ` → `d`) rồi index cột `textSearch`. Người dùng gõ "hoa don" vẫn tìm ra "Hóa đơn"; vẫn giữ `text` gốc để hiển thị.
- **`ocr_pages` tách riêng khỏi `pages`:** cho phép chạy lại OCR một trang (khi nâng cấp engine), và cho phép xóa text mà không đụng ảnh.
- **`exportSchema = true` + viết migration thật từ đầu.** Tuyệt đối không dùng `fallbackToDestructiveMigration()` trong bản release — nó xoá sạch metadata (mà file thì còn) → app mất toàn bộ danh sách tài liệu của người dùng.
- **Thùng rác (soft delete) là tính năng của MVP**, không phải "nice-to-have": người dùng xoá nhầm hợp đồng là mất luôn. `deletedAt != null` → ẩn khỏi danh sách, dọn file sau 30 ngày.

---

## 4. DataStore — chìa khoá cài đặt

```
theme            = system | light | dark
language         = vi | en
autoOcr          = Boolean            (OCR ngay sau khi scan hay để người dùng bấm)
imageQuality     = Int  (60–95)
keepOriginal     = Boolean            (giữ ảnh gốc chưa filter)
defaultExportUri = String?            (Tree URI, đã takePersistableUriPermission)
onboardingDone   = Boolean
reviewPromptAt   = Long?              (đếm lần export thành công thứ 3 → in-app review)
backupLastAt     = Long?
```

---

## 5. Xuất / chia sẻ file (SAF)

> **Trạng thái: chưa code (Giai đoạn 2b).** Giai đoạn 2a chỉ xong phần quét + lưu vào vùng riêng của app.

- **Xuất PDF ra chỗ người dùng chọn:** `ActivityResultContracts.CreateDocument("application/pdf")` → ghi bản sao. File đã xuất nằm ngoài vùng app nên **không** bị xoá khi gỡ app (đúng như bảng của bạn).
- **Chia sẻ nhanh:** `FileProvider` + `ACTION_SEND` (`content://` URI) — chỉ chia sẻ bản copy/single-doc, không expose toàn bộ thư mục.
- **Thư mục mặc định (tuỳ chọn):** `OpenDocumentTree` + `takePersistableUriPermission` để lần sau export 1 chạm.
- Không xin `READ/WRITE_EXTERNAL_STORAGE` ở bất kỳ đâu → ít rủi ro bị Play soi quyền.

Vị trí hợp đồng xuất PDF (quan trọng cho quy tắc phụ thuộc): hợp đồng **thuần Kotlin** — nhận `List<File>` ảnh trang và `OutputStream` đầu ra — nên nằm ở `domain/export/`; cài đặt thật dùng `android.graphics.pdf.PdfDocument` nằm ở `platform/pdf/`. Nhờ vậy use case xuất PDF kiểm thử được trong JVM thuần, và việc đổi sang thư viện PDF khác (nếu `PdfDocument` không đủ) chỉ sửa `platform/pdf/`.

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
  - `:platform:scanner` (ML Kit Document Scanner), `:platform:ocr` (`OcrEngine` + impl ML Kit), `:platform:pdf`, `:platform:ads` (tạo khi thật sự gắn quảng cáo — **không** tạo module rỗng).
- **`:feature:*`** — chỉ Compose + ViewModel + use case của chính nó. Không truy cập Room trực tiếp, không depend feature khác. Mỗi feature expose 1 entry composable + route constant; `:app` là nơi ráp `NavHost`.
- **`:app`** — chỉ wiring: `Application`, `MainActivity`, `NavHost`, DI gốc.

### Danh sách module chốt

```
:app
:core:common          Result, DispatcherProvider, VnTextNormalizer, file utils
:core:model           model thuần Kotlin
:core:designsystem    theme, component dùng chung
:core:data            Room + DataStore + FileStore + Repository + Backup
:core:testing         fake, fixture, helper test (chỉ dùng ở testImplementation)
:platform:scanner     bọc ML Kit Document Scanner
:platform:ocr         OcrEngine + ML Kit impl
:platform:pdf         xuất PDF (sau: text layer ẩn)
:feature:library      trang chủ + danh sách + tìm kiếm + chi tiết/đổi tên/xoá/thùng rác
:feature:scan         chụp + xem trước + sắp xếp/xoá trang
:feature:ocr          OCR + sửa text + copy/share
:feature:settings     cài đặt + xuất/nhập backup
```

`preview/` nằm trong `:feature:scan` (cùng một luồng, cùng state) — tách ra chỉ tổ phải bắc cầu state qua navigation.
`:platform:ads` tạo muộn, khi thêm quảng cáo.

### Quy tắc đặt "cổng" (interface) — đã áp dụng ngay từ Giai đoạn 2a

Không phải cổng nào cũng được đặt ở chỗ giống nhau. Tiêu chí: **cổng có diễn đạt được bằng loại Java thuần hay không.**

| Cổng | Đặt ở | Vì sao |
|---|---|---|
| `DocumentRepository` | `domain/repository/` | thuần Kotlin, không cần Android |
| `DocumentFileStorage` | `domain/storage/` | chỉ dùng `java.io.File`; use case ghi trang cần nó mà không được biết `Context` |
| `PageImageReader` | `domain/scan/` | nhận `String` (URI dạng chuỗi); cài đặt thật (`ContentResolver`) nằm ở `data/scan/` |
| `PdfExporter` (Giai đoạn 2b) | `domain/export/` | nhận `List<File>` + `OutputStream`; `PdfDocument` thật nằm ở `platform/pdf/` |
| `DocumentScannerEngine` | `platform/scanner/` | **bắt buộc** phải nhận `Activity`/`IntentSender` — đó là Activity của Play services, không thể diễn đạt bằng domain |

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
| 2. Scan & PDF | `:platform:scanner`, `:platform:pdf`, `:feature:scan` (+ `:core:*` tách khỏi `:app`) |
| 3. OCR | `:platform:ocr`, `:feature:ocr` |
| 4. Quản lý tài liệu | `:feature:library`, `:feature:settings` |
| 5. Phát hành | `:core:testing` + (nếu gắn ads) `:platform:ads` |

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
| `ocrEngineVersion` | Máy khác engine → biết trang nào cần re-OCR |
| `formatVersion` trong backup | Định dạng dữ liệu có thể tiến hoá an toàn |

Khi nào mới làm cloud: chỉ khi có người dùng thật hỏi "tôi đổi máy thì sao" **và** tính năng backup `.svbk` không đủ. Lúc đó vẫn không bắt đăng nhập để scan — sync là tuỳ chọn.

---

## 10. Kiểm thử cho phần lưu trữ (không bỏ qua)

- `FileStore` (Robolectric, dùng thư mục tạm): save/move/delete, đổi tên không đụng tên file, doc bị thiếu file → báo lỗi đúng.
- Room: mỗi migration một test với `MigrationTestHelper`; test FTS tìm được cả khi **không dấu** và **có dấu**.
- `BackupManager`: round-trip (export → xoá sạch → import → so sánh từng trường), cộng 7 case ở mục 6.
- UI test nhẹ: xoá tài liệu → hoàn tác trong thùng rác → khôi phục được.
