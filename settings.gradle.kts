pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        /*
         * JitPack: BẮT BUỘC cho Tesseract4Android.
         *
         * Vì sao cần repository riêng: `cz.adaptech.tesseract4android` KHÔNG có trên Maven
         * Central. Đã kiểm chứng ngày 20/10/2026 — `search.maven.org` với `g:cz.adaptech`
         * trả về 0 kết quả, và `repo1.maven.org/maven2/cz/adaptech/` trả HTTP 404, trong
         * khi cùng artifact đó trên JitPack trả HTTP 200 (AAR 12,25 MB).
         *
         * Đánh đổi đã biết khi dùng JitPack:
         *  - JitPack build artifact theo yêu cầu và có thể chậm ở lần tải đầu.
         *  - JitPack không cam kết giữ artifact vĩnh viễn như Maven Central. Để bảo vệ
         *    khỏi rủi ro này, dự án đã tự đóng gói file model `vie.traineddata` trong
         *    `app/src/main/assets/` thay vì tải lúc chạy — nghĩa là nếu JitPack có sự cố,
         *    chúng ta vẫn biết chính xác phiên bản và có thể chuyển sang `mavenLocal()`
         *    bằng cách build AAR từ mã nguồn (README của thư viện có hướng dẫn).
         */
        maven {
            url = uri("https://jitpack.io")
            content {
                // Chỉ hỏi JitPack cho đúng nhóm này. Không giới hạn thì mọi dependency
                // (kể cả androidx) đều có thể bị Gradle hỏi sang JitPack trước, làm chậm
                // build và tạo nguy cơ lấy nhầm artifact từ nguồn không mong muốn.
                includeGroup("cz.adaptech.tesseract4android")
            }
        }
    }
}

rootProject.name = "ScanViet"

/**
 * Giai đoạn 1 dùng MỘT Gradle module duy nhất (theo AGENTS.md mục 2).
 * Ranh giới module tương lai được giữ bằng package:
 *   core/ domain/ data/ scanner/ ocr/ pdf/ ui/ navigation/
 * => khi cần tách module, việc tách là cơ học (move package + build.gradle.kts).
 */
include(":app")
