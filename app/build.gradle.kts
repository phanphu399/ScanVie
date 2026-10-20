import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "vn.scanviet.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "vn.scanviet.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 3
        versionName = "0.5.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Nơi phát hành bản cập nhật.
        //
        // Vì sao để trong build config chứ không viết thẳng trong code: khi chuyển sang phát
        // hành qua Google Play, chỉ cần đổi hai giá trị này (hoặc đổi hẳn nguồn) mà không phải
        // sửa logic nghiệp vụ. Cũng tránh việc mỗi môi trường build lại phải sửa nhiều file.
        //
        // PHẢI KHỚP với `git remote -v`. Kiểm chứng ngày 20/10/2026:
        //   origin  https://github.com/phanphu399/ScanVie.git
        // Nếu đổi tên repo trên GitHub, cập nhật cả hai giá trị này, nếu không ứng dụng sẽ gọi
        // một URL không tồn tại và cơ chế tự cập nhật hỏng trong im lặng.
        buildConfigField("String", "RELEASE_OWNER", "\"phanphu399\"")
        buildConfigField("String", "RELEASE_REPOSITORY", "\"ScanVie\"")

        ndk {
            // Chỉ đóng gói thư viện native cho hai kiến trúc ARM.
            //
            // Vì sao: ML Kit nhận dạng chữ mang theo thư viện native
            // `libmlkit_google_ocr_pipeline.so` nặng ~11 MB MỖI kiến trúc, và mô hình ngôn ngữ
            // (~25 MB) đi kèm. Đóng gói cả bốn kiến trúc làm APK phình từ ~12 MB lên ~54 MB.
            //
            // `x86` và `x86_64` chỉ tồn tại trên trình giả lập và một số máy tính bảng cũ.
            // Điện thoại Android thật gần như 100% là ARM. Bỏ hai kiến trúc đó tiết kiệm
            // khoảng 22 MB mà không ảnh hưởng người dùng thật.
            //
            // Đánh đổi đã biết: ứng dụng KHÔNG cài được trên trình giả lập x86.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Xuất schema Room ra app/schemas để mỗi thay đổi schema đều có file lịch sử
// => bắt buộc phải viết migration thay vì xoá dữ liệu người dùng.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)

    // Giai đoạn 2: giao diện quét và xử lý ảnh (nhận diện mép giấy, cắt phối cảnh,
    // chỉnh màu, nhiều trang). Việc sao chép ảnh vào vùng riêng của app do tầng data lo,
    // không do SDK này lo.
    implementation(libs.mlkit.document.scanner)

    // Giai đoạn 3: nhận dạng chữ ngay trên thiết bị, chạy được ngoại tuyến (mô hình nằm
    // trong APK). KHÔNG gửi ảnh hay văn bản lên máy chủ nào.
    //
    // ⚠️ ĐÍNH CHÍNH: artifact này KHÔNG hỗ trợ tiếng Việt có dấu. Text Recognition v2 chỉ
    // có 5 hệ chữ (Latin, Chinese, Devanagari, Japanese, Korean); mô hình đóng gói thật là
    // `Latn_ctc`, nó bỏ dấu hoặc trả chuỗi rỗng với văn bản tiếng Việt.
    // Xem MVP_SPEC.md §2.5.
    //
    // Từ Giai đoạn B, engine này KHÔNG còn là mặc định cho tiếng Việt. Nó vẫn được giữ
    // vì mạnh hơn Tesseract trên tài liệu TIẾNG ANH in rõ (mô hình nơ-ron so với LSTM
    // cổ điển). Người dùng chọn engine trong Cài đặt; mặc định là Tesseract.
    implementation(libs.mlkit.text.recognition)

    // Giai đoạn B: Tesseract 5 — engine nhận dạng chữ TIẾNG VIỆT.
    //
    // Vì sao cần thư viện này: ML Kit Text Recognition v2 không có tiếng Việt trong 5 hệ
    // chữ được hỗ trợ, nên chữ có dấu bị bỏ dấu hoặc mất hẳn.
    //
    // Lưu ý về nguồn: artifact này nằm trên **JitPack**, không có trên Maven Central
    // (đã kiểm chứng 404 ngày 20/10/2026). Repository JitPack được khai báo trong
    // settings.gradle.kts, giới hạn đúng group `cz.adaptech.tesseract4android`.
    //
    // File model `vie.traineddata` (~12 MB) KHÔNG tải lúc chạy. Nó được đóng gói sẵn
    // trong `assets/tessdata/` và giải nén ra vùng riêng của app ở lần dùng đầu tiên.
    // Nhờ vậy nhận dạng chữ chạy được ngoại tuyến ngay sau khi cài, đúng lời hứa của app.
    implementation(libs.tesseract4android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
    // `org.json` nằm trong Android framework nhưng không có trên JVM. Chỉ test cần.
    testImplementation(libs.json)
androidTestImplementation(libs.androidx.test.ext.junit)
androidTestImplementation(libs.androidx.test.espresso.core)
androidTestImplementation(platform(libs.compose.bom))
androidTestImplementation(libs.compose.ui.test.junit4)
debugImplementation(libs.compose.ui.test.manifest)
}
