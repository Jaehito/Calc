plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.calc.expense"
    compileSdk = 35

    // 고정 debug 키스토어. CI 러너마다 새 키를 만들면 서명이 바뀌어 «다른 앱»이 되고,
    // 안드로이드가 재설치를 요구해 설정이 매번 초기화된다. 커밋된 키로 서명해 유지한다.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "com.calc.expense"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
        compose = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    // 주 1회 돌아보기 알림 예약. 재부팅에도 예약이 살아남는다.
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // 홈 화면만 Compose 다. 설정·빠른 입력은 XML 그대로 둔다 —
    // 잘 도는 화면을 다시 만들 이유가 없다.
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // 챌린지 탭 — 익명 인증 + Firestore. BOM 이 버전을 함께 맞춘다.
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")

    testImplementation("junit:junit:4.13.2")
    // 유닛 테스트의 android.jar 은 org.json 이 던지도록 스텁돼 있다.
    // NotionRows / MonthTotals 를 테스트하려면 실제 구현이 필요하다.
    testImplementation("org.json:json:20240303")
}
