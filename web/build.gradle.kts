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
