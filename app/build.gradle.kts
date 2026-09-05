import java.io.File
import java.net.URI
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.sergey.reader"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sergey.reader"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "0.6.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

// DjVuLibre is pinned to a fork that ships Android 16 / 16 KB-page compatible
// native libraries.  Instead of asking JitPack to rebuild the native project,
// download the already-published AAR from that exact Git commit.  The Gradle
// file dependency is builtBy this task, so ordinary assemble/test commands fetch
// it automatically on the first build.
val djvuAarUrl =
    "https://raw.githubusercontent.com/Kazzenkatt/android-djvulibre/5b9bd591befc528268cf00c28e8fb81bc75d664b/build/outputs/aar/android-djvulibre-release.aar"
val djvuAarGitBlobSha1 = "bde3f2e2cbe693343e5180b69e80b5580b40ecd4"
val djvuAarFile = layout.projectDirectory.file("libs/android-djvulibre-release.aar")

fun gitBlobSha1(file: File): String {
    val digest = MessageDigest.getInstance("SHA-1")
    digest.update("blob ${file.length()}\u0000".toByteArray(Charsets.UTF_8))
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read > 0) digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

val prepareDjvuAar = tasks.register("prepareDjvuAar") {
    outputs.file(djvuAarFile)
    doLast {
        val target = djvuAarFile.asFile
        fun valid(): Boolean = target.isFile && runCatching { gitBlobSha1(target) == djvuAarGitBlobSha1 }.getOrDefault(false)

        if (!valid()) {
            target.parentFile.mkdirs()
            val temp = File(target.parentFile, target.name + ".part")
            temp.delete()
            URI(djvuAarUrl).toURL().openStream().buffered().use { input ->
                temp.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            if (gitBlobSha1(temp) != djvuAarGitBlobSha1) {
                temp.delete()
                error("DjVuLibre AAR checksum mismatch; refusing to use an unexpected binary")
            }
            if (target.exists()) target.delete()
            check(temp.renameTo(target)) { "Unable to place DjVuLibre AAR at ${target.absolutePath}" }
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    implementation("androidx.datastore:datastore-preferences:1.2.0")
    implementation("androidx.documentfile:documentfile:1.1.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Extended document/archive formats
    implementation("com.github.junrar:junrar:8.1.1")
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("org.tukaani:xz:1.10") // LZMA/LZMA2 backend used by most CB7 archives
    implementation("com.github.chimenchen:jchmlib:v0.5.4")
    implementation(files(djvuAarFile).builtBy(prepareDjvuAar))

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
