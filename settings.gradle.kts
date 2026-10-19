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
