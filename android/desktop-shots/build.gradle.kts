// Screenshot harness: compiles the Android app's stateless Compose UI against Compose Desktop (Skia),
// which runs on linux-aarch64 where Robolectric/Paparazzi native graphics are unavailable.
// Android-only files (Activity, ViewModels, DataStore, WebView, R.font) are excluded and stubbed in src/main/kotlin.
plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
}
kotlin { jvmToolchain(17) }
sourceSets {
    main {
        kotlin {
            srcDir("../app/src/main/java")
            exclude(
                "com/harkquant/terminal/MainActivity.kt", "com/harkquant/terminal/HqApp.kt",
                "com/harkquant/terminal/data/Prefs.kt",
                "com/harkquant/terminal/ui/ViewModels.kt", "com/harkquant/terminal/ui/AppRoot.kt",
                "com/harkquant/terminal/ui/screens/Routes.kt", "com/harkquant/terminal/ui/screens/TvWidget.kt",
                "com/harkquant/terminal/ui/theme/Fonts.kt", "com/harkquant/terminal/ui/components/Insets.kt",
            )
        }
    }
}
dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")
}
tasks.register<JavaExec>("shots") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.harkquant.terminal.ShotsKt")
    workingDir = projectDir
    jvmArgs("-Xmx900m", "-Djava.awt.headless=true")
    args = listOf("../screenshots", "../app/src/test/resources/samples", "../app/src/main/res/font") + listOfNotNull(project.findProperty("only") as String?)
}
