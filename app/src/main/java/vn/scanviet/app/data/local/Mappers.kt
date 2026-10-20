package vn.scanviet.app.data.local

import vn.scanviet.app.data.local.entity.DocumentEntity
import vn.scanviet.app.data.local.entity.OcrLineEntity
import vn.scanviet.app.data.local.entity.OcrPageEntity
import vn.scanviet.app.data.local.entity.PageEntity
import vn.scanviet.app.domain.model.Document
import vn.scanviet.app.domain.model.DocumentPage
import vn.scanviet.app.domain.model.OcrStatus
import vn.scanviet.app.domain.model.OcrText
import vn.scanviet.app.domain.model.StoredOcrLine

internal fun DocumentEntity.toDomain(): Document = Document(
    id = id,
    title = title,
    createdAtMillis = createdAt,
    updatedAtMillis = updatedAt,
    deletedAtMillis = deletedAt,
    pageCount = pageCount,
    ocrStatus = ocrStatus.toOcrStatus(),
    sizeBytes = sizeBytes,
)

internal fun PageEntity.toDomain(): DocumentPage = DocumentPage(
    id = id,
    documentId = documentId,
    pageIndex = pageIndex,
    imageFile = imageFile,
    width = width,
    height = height,
    rotationDegrees = rotationDegrees,
)

/**
 * Giá trị lạ trong database (do dữ liệu cũ hoặc khôi phục lỗi) được coi là NONE
 * thay vì làm ứng dụng crash.
 */
internal fun String.toOcrStatus(): OcrStatus =
    OcrStatus.entries.firstOrNull { it.name == this } ?: OcrStatus.NONE

/** Id trang có tính xác định, nhờ vậy chèn lại cùng một trang là thao tác idempotent. */
internal fun pageIdOf(documentId: String, pageIndex: Int): String = "${documentId}_p$pageIndex"

internal fun OcrPageEntity.toDomain(lines: List<OcrLineEntity>): OcrText = OcrText(
    documentId = documentId,
    pageIndex = pageIndex,
    lines = lines
        .filter { it.pageIndex == pageIndex }
        .sortedBy { it.lineIndex }
        .map { line ->
            StoredOcrLine(
                text = line.text,
                left = line.left,
                top = line.top,
                right = line.right,
                bottom = line.bottom,
            )
        },
    imageWidth = imageWidth,
    imageHeight = imageHeight,
)
