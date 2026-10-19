// Compiles Nortantis to JavaScript with TeaVM, so maps can be generated in a web browser.
//   ./gradlew :web:generateJavaScript   writes build/generated/teavm/js/nortantis.js
plugins {
    java
    id("org.teavm") version "0.16.0"
}

repositories {
    mavenCentral()
}

// TeaVM has no sun.misc.Unsafe, which JTransforms' array library refers to (it only uses it for arrays too large for Java arrays). This
// compiles a stub of it, which must be patched into the module that normally holds sun.misc.
val compileUnsafeStub by tasks.registering(JavaCompile::class) {
    source = fileTree("stubs")
    classpath = files()
    destinationDirectory.set(layout.buildDirectory.dir("unsafe-stub"))
    options.compilerArgs.addAll(listOf("--patch-module", "jdk.unsupported=${file("stubs")}"))
}

dependencies {
    implementation(project(":"))
    implementation(teavm.libs.jso)
    // For the build-time class that tells TeaVM which resources to include.
    compileOnly("org.teavm:teavm-core:0.16.0")
    implementation(files(compileUnsafeStub.flatMap { it.destinationDirectory }))
}

// The asset manifest for the browser, which lists the files the page downloads and the generator can see. It leaves out the files a demo
// that can't edit text doesn't need: every font but the default one for new maps, and the large SSA name books.
val webAssetManifestFolder = layout.buildDirectory.dir("generated-resources")
val generateWebAssetManifest by tasks.registering {
    val fullManifest = rootProject.layout.buildDirectory.file("generated-resources/assets/manifest.txt")
    val webManifest = webAssetManifestFolder.map { it.file("assets/manifest.txt") }
    dependsOn(":generateAssetManifest")
    inputs.file(fullManifest)
    outputs.file(webManifest)
    doLast {
        val fontsFolder = "assets/installed art pack/fonts/"
        val keptFontFolder = fontsFolder + "Z003/"
        val lines = fullManifest.get().asFile.readLines().filter { line ->
            val path = line.substringBefore('\t')
            val isOtherFont = path.startsWith(fontsFolder) && path != fontsFolder && !path.startsWith(keptFontFolder)
            line.isNotEmpty() && !isOtherFont && !path.startsWith("assets/books/SSA ")
        }
        webManifest.get().asFile.writeText(lines.joinToString("\n") + "\n")
    }
}
sourceSets.main.get().resources.srcDir(webAssetManifestFolder)
tasks.processResources { dependsOn(generateWebAssetManifest) }

tasks.withType<JavaCompile>().matching { it.name != "compileUnsafeStub" }.configureEach {
    options.encoding = "UTF-8"
    options.release.set(17)
}

teavm {
    all {
        mainClass = "nortantis.web.WebMain"
    }
    js {
        targetFileName = "nortantis.js"
        obfuscated = false
    }
}

// This module replaces some of the Java classes in TeaVM's class library (see src/main/java/org/teavm/classlib), so its classes must come
// before TeaVM's on the class path TeaVM compiles from.
tasks.withType<org.teavm.gradle.tasks.TeaVMTask>().configureEach {
    val original = classpath.from.toList()
    classpath.setFrom(sourceSets.main.get().output, original)
}
