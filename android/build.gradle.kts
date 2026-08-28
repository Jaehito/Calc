plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    // Kotlin 2.0 부터 Compose 컴파일러는 별도 플러그인이다 (composeOptions 는 더 안 쓴다).
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    // Firebase(챌린지 탭) — google-services.json 을 읽어 앱을 프로젝트에 연결한다.
    id("com.google.gms.google-services") version "4.4.2" apply false
}
