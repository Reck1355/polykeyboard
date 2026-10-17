import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Packaging tier. `full` is everything; `slim` is the same keyboard without on-device dictation and
// without the Spanish data, which takes the download from 221 MiB to roughly 31 MiB. Absent
// property means full, so a forgotten flag can only ever ship the complete build.
val polykeyboardTier: String = (findProperty("polykeyboardTier") as String?)?.lowercase() ?: "full"

android {
    namespace = "com.slide.app"
    compileSdk = 37

    defaultConfig {
        ndk {
            // The keyboard is built for the phone it is for, so it carries only the instruction sets that
            // phone or an older one can actually run. Measured on the shipped build: the x86 and x86_64
            // copies of every native library were 17.5 MB of a 251 MiB download, all of it so the keyboard
            // could also run in an emulator, which is not where typing happens. arm64-v8a is the phone;
            // armeabi-v7a is kept for a device older than it and costs 2.3 MB, which is not worth the doubt.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
        buildConfigField("boolean", "HAS_DICTATION", if (polykeyboardTier == "full") "true" else "false")
        resValue("string", "app_name", if (polykeyboardTier == "full") "Polykeyboard" else "Polykeyboard Slim")

        // Our own identity, not the fork parent's. This is the package Android uses as
        // the app's identity on the device: it decides which installs update which. It is
        // deliberately NOT com.slide, so a release-signed Polykeyboard also installs
        // side by side with any Slide build rather than fighting it for one slot.
        applicationId = "com.reck1355.polykeyboard"
        minSdk = 26
        targetSdk = 37
        versionCode = 32
        versionName = "0.1.12"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Installs beside a release-signed Slide on test devices instead of failing
            // INSTALL_FAILED_UPDATE_INCOMPATIBLE against it.
            applicationIdSuffix = ".debug"
        }
        release {
            // Release signing is deliberately outside Gradle. CI builds this unsigned artifact in
            // an unprivileged job; a separate approval-gated job signs it without checking out or
            // executing repository code while the keystore is available.
            isMinifyEnabled = true
            isShrinkResources = true
            ndk.debugSymbolLevel = "SYMBOL_TABLE"
            // The tagged release workflow attests the source revision externally. AGP's embedded
            // VCS record differs between a Git checkout and an otherwise identical source export
            // (`NO_SUPPORTED_VCS_FOUND`), defeating the independent byte-for-byte rebuild.
            vcsInfo {
                include = false
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    androidResources {
        // Library noCompress declarations do not propagate into the final application APK.
        // Whisper maps its model directly, while swipe inference copies already-packed models to
        // mmap-friendly storage; the application therefore owns both packaging rules.
        noCompress += listOf("bin", "pte")

        // What the slim tier leaves out, by name and for a measured reason: the English speech model
        // is 181 MiB, which is the whole download, the ggml/whisper/slide_asr libraries exist only to
        // run it, and the Spanish bundle is the one language whose data is not shared with English.
        // The library modules cannot express this themselves: their source-set accessors do not
        // survive AGP's own decorators, so the rule lives here, on the artifact that ships.
        if (polykeyboardTier != "full") {
            // A bare name matches any path component, so `pinyin` takes the whole Mandarin directory:
            // 6 MiB of tables for a language this build does not offer. The unit tests read those
            // files from the source tree, never from the APK, so they still cover the engine.
            ignoreAssetsPattern =
                "ggml-small.en-q5_1.bin:lexicon_es.bin:bigrams_es.bin:trigrams_es.bin:pinyin"
        }
    }

    packaging {
        jniLibs {
            // ARM64 Whisper's runtime-selected backends and fbjni both bring the NDK's ABI-stable
            // shared C++ runtime. Package one process-wide copy so Gradle does not reject the
            // otherwise valid duplicate transitive input.
            pickFirsts += "**/libc++_shared.so"
            // Speech backends and only speech backends: the swipe encoder's runtime stays, because
            // gesture typing is not optional in either build.
            if (polykeyboardTier != "full") {
                excludes += listOf("**/libggml*.so", "**/libwhisper.so", "**/libslide_asr.so")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        // The slim tier renames the app through resValue rather than by editing the manifest.
        resValues = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":engine"))
    implementation(project(":ime"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.core)
}

tasks.register("writeReleaseRuntimeArtifacts") {
    group = "verification"
    description = "Writes the exact external artifacts selected for the app release runtime"
    notCompatibleWithConfigurationCache(
        "Reads resolved artifact identities and repository paths during execution",
    )

    val runtimeClasspath = configurations.named("releaseRuntimeClasspath")
    val externalArtifacts = runtimeClasspath.get().incoming.artifactView {
        componentFilter { component -> component !is ProjectComponentIdentifier }
    }.artifacts
    val outputFile = layout.buildDirectory.file("reports/release-runtime-artifacts.tsv")
    inputs.files(externalArtifacts.artifactFiles).withPropertyName("releaseRuntimeClasspath")
    outputs.file(outputFile)

    doLast {
        val repositoryRoot = rootProject.projectDir.toPath().toAbsolutePath().normalize()

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        }

        fun requireSafeField(value: String): String {
            require('\t' !in value && '\r' !in value && '\n' !in value) {
                "Runtime artifact field contains a TSV control character: $value"
            }
            return value
        }

        val rows = externalArtifacts.artifacts.mapNotNull { artifact ->
            val component = artifact.id.componentIdentifier
            val file = artifact.file
            when (component) {
                is ProjectComponentIdentifier -> null
                is ModuleComponentIdentifier -> {
                    require(file.isFile) { "Resolved runtime artifact is not a file: $file" }
                    listOf(
                        "maven",
                        component.group,
                        component.module,
                        component.version,
                        file.absolutePath,
                        sha256(file),
                    ).joinToString("\t", transform = ::requireSafeField)
                }
                else -> {
                    require(file.isFile) { "Resolved local runtime artifact is not a file: $file" }
                    val absolute = file.toPath().toAbsolutePath().normalize()
                    require(absolute.startsWith(repositoryRoot)) {
                        "Local runtime artifact must be inside the repository: $file"
                    }
                    listOf(
                        "file",
                        "-",
                        "-",
                        "-",
                        repositoryRoot.relativize(absolute).toString().replace(File.separatorChar, '/'),
                        sha256(file),
                    ).joinToString("\t", transform = ::requireSafeField)
                }
            }
        }.distinct().sorted()
        val destination = outputFile.get().asFile
        destination.parentFile.mkdirs()
        destination.writeText(
            (listOf("kind\tgroup\tname\tversion\tpath\tsha256") + rows).joinToString(
                separator = "\n",
                postfix = "\n",
            ),
            Charsets.UTF_8,
        )
        logger.lifecycle("Wrote ${rows.size} resolved release runtime artifacts to $destination")
    }
}
