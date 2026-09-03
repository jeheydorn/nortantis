plugins {
    java
    application
    id("com.diffplug.spotless") version "6.23.3"
    eclipse
}

repositories {
    mavenCentral()
}

dependencies {
    // Apache Commons IO
    implementation("commons-io:commons-io:2.14.0")

    // Apache Commons Lang
    implementation("org.apache.commons:commons-lang3:3.18.0")

    // Apache Commons Math
    implementation("org.apache.commons:commons-math3:3.2")

    // Imgscalr (Java Image Scaling Library)
    implementation("org.imgscalr:imgscalr-lib:4.2")

    // JSON.simple
    implementation("com.googlecode.json-simple:json-simple:1.1.1")

    implementation("com.github.wendykierp:JTransforms:3.2")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("com.formdev:flatlaf:3.6.2")
}

application {
    mainClass.set("nortantis.swing.MainWindow")
    applicationDefaultJvmArgs = listOf(
        "--enable-native-access=ALL-UNNAMED",
        // Assertions are on for development runs only. The installers build the jar and launch it through jpackage (or, for Arch, a
        // wrapper script), so they never see these arguments - they pass their own via --java-options.
        "-ea",
    )
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    // Compile against Java 17 (rather than the runtime's 21) to keep a possible mobile / libGDX
    // port viable - such toolchains target older Java levels. No such drawing layer exists today; this
    // just preserves the option. Using options.release (rather than source/targetCompatibility) also
    // blocks calls to JDK APIs newer than 17 at compile time, so any accidental newer-API usage fails
    // here in the build instead of somewhere harder to diagnose later. This only constrains what we
    // compile against - it does not affect the runtime JVM (run on 21+ for full JIT/GC performance) or
    // graphics/driver capability.
    options.release.set(17)
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes("Main-Class" to "nortantis.swing.MainWindow")
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }
    from(sourceSets.main.get().output) {
        from("assets") {
            into("assets")
        }
    }
    archiveFileName.set("Nortantis.jar")
}

tasks.test {
    jvmArgs = listOf(
        "-ea", "--enable-native-access=ALL-UNNAMED", "-Dfile.encoding=UTF-8", "-Dsun.java2d.d3d=false", "-Xmx3g",
        "--add-opens", "java.base/java.nio=ALL-UNNAMED",
    )
    useJUnitPlatform()
    // Benchmarks are gated by @EnabledIfSystemProperty(named = "runBenchmarks", matches = "true"), so they are skipped during normal
    // test runs. Forward the flag from the Gradle invocation so `./gradlew test -DrunBenchmarks=true` can opt in. Default off.
    systemProperty("runBenchmarks", System.getProperty("runBenchmarks", "false"))
    // Forward the blur algorithm override, so `./gradlew test -Dnortantis.blurAlgorithm=fft` can render the tests with a
    // different blur to compare them. Left unset, ImageHelper picks its own default.
    System.getProperty("nortantis.blurAlgorithm")?.let { systemProperty("nortantis.blurAlgorithm", it) }
    // Lets a benchmark be pointed at a map outside the repository.
    System.getProperty("incrementalBenchmarkSettings")?.let { systemProperty("incrementalBenchmarkSettings", it) }
}

// Benchmark task with JFR profiling
// Usage: ./gradlew benchmark
// Output: build/profile.jfr (open in JDK Mission Control or IntelliJ)
tasks.register<Test>("benchmark") {
    description = "Run benchmarks with JFR profiling"
    group = "verification"

    // Use the test source set
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    useJUnitPlatform()
    forkEvery = 1

    // Run benchmark classes (any class named *Benchmark) under JFR profiling. With no --tests this runs all of them; pass
    // --tests "nortantis.SomeBenchmark" to profile just one (e.g. from IntelliJ). The include must be this broad because Gradle's --tests
    // can only narrow a task's includes, not override them - a narrower include here (e.g. only AwtMapCreatorBenchmark) would make
    // `:benchmark --tests "nortantis.ImageHelperBenchmark"` match nothing.
    filter {
        includeTestsMatching("*Benchmark")
        // Don't fail the build when this task is asked (e.g. by IntelliJ as a candidate test task) to run a non-benchmark test it doesn't
        // include. Without this, IntelliJ runs of regular tests fail with "No matching tests found ... in task :benchmark" before the
        // :test task gets a chance to run them.
        isFailOnNoMatchingTests = false
    }

    // Enable the benchmark gate (@EnabledIfSystemProperty(named = "runBenchmarks", matches = "true")).
    systemProperty("runBenchmarks", "true")
    // Same overrides the test task forwards, so a benchmark can be profiled against a chosen map and blur.
    System.getProperty("nortantis.blurAlgorithm")?.let { systemProperty("nortantis.blurAlgorithm", it) }
    System.getProperty("incrementalBenchmarkSettings")?.let { systemProperty("incrementalBenchmarkSettings", it) }
    System.getProperty("incrementalBenchmarkOceanShading")?.let { systemProperty("incrementalBenchmarkOceanShading", it) }

    jvmArgs = listOf(
        "-ea",
        "--enable-native-access=ALL-UNNAMED",
        "-Dfile.encoding=UTF-8",
        "-Dsun.java2d.d3d=false",
        "-Xmx4g",
        "--add-opens", "java.base/java.nio=ALL-UNNAMED",
        // JFR profiling - records to build/profile.jfr
        "-XX:StartFlightRecording=filename=build/profile.jfr,settings=profile",
    )

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }

    doFirst {
        println("Starting benchmark with JFR profiling...")
        println("JFR output will be saved to: build/profile.jfr")
    }

    doLast {
        println("\nBenchmark complete!")
        println("Open build/profile.jfr in JDK Mission Control or IntelliJ to analyze.")
    }
}

// Accepts the images written by failing image-comparison tests as the new expected images, for use after the diff images have been
// reviewed and the new rendering is the wanted one.
// Usage: ./gradlew acceptFailedImages
tasks.register("acceptFailedImages") {
    description = "Copy failed test images over their expected images, for baselines that changed intentionally"
    // Deliberately not in the "verification" group, and with no task dependencies, so that nothing treats this as a test task and
    // compiles or runs the tests before it. It only copies files that are already on disk.
    group = "unit test images"

    // Failed-image folders paired with the expected-image folder they are compared against. A folder without an expected counterpart
    // does not belong here: "failed sub-maps" is omitted because those tests assert on data rather than pixels and write their maps
    // there for viewing even when they pass.
    val folderPairs = listOf(
        "failed maps" to "expected maps",
        "failed image helper tests" to "expected image helper tests",
        "failed skia tests" to "expected skia tests",
        "failed maps skia" to "expected maps skia",
    )
    val unitTestFilesFolder = file("unit test files")

    doLast {
        fun describeAge(millis: Long): String {
            val minutes = millis / (60L * 1000L)
            if (minutes < 90L) {
                return "$minutes minute(s) ago"
            }
            val hours = minutes / 60L
            if (hours < 48L) {
                return "$hours hour(s) ago"
            }
            return "${hours / 24L} day(s) ago"
        }

        val now = System.currentTimeMillis()
        // Failed image paired with the expected image it would overwrite.
        val toAccept = mutableListOf<Pair<File, File>>()
        var diagnosticImageCount = 0

        for ((failedFolderName, expectedFolderName) in folderPairs) {
            val failedFolder = File(unitTestFilesFolder, failedFolderName)
            val expectedFolder = File(unitTestFilesFolder, expectedFolderName)
            if (!failedFolder.isDirectory || !expectedFolder.isDirectory) {
                continue
            }

            val failedImages = failedFolder.listFiles { f: File -> f.isFile && f.name.endsWith(".png") }?.sortedBy { it.name } ?: emptyList()
            for (failedImage in failedImages) {
                val expectedImage = File(expectedFolder, failedImage.name)
                // A failed image with no expected image of the same name is a diagnostic that a test wrote to be looked at (a diff
                // image, an incremental-draw snippet, and the like) rather than a baseline that can be accepted.
                if (!expectedImage.isFile) {
                    diagnosticImageCount++
                    continue
                }
                toAccept.add(failedImage to expectedImage)
            }
        }

        if (toAccept.isEmpty()) {
            println("No failed images to accept.")
            if (diagnosticImageCount > 0) {
                println("$diagnosticImageCount diagnostic image(s) have no expected image and were left alone.")
            }
            return@doLast
        }

        for ((failedImage, expectedImage) in toAccept) {
            failedImage.copyTo(expectedImage, overwrite = true)
            println("Accepted ${expectedImage.parentFile.name}/${expectedImage.name} (${describeAge(now - failedImage.lastModified())})")
        }

        println("\nAccepted ${toAccept.size} image(s) as expected.")
        if (diagnosticImageCount > 0) {
            println("$diagnosticImageCount diagnostic image(s) have no expected image and were left alone.")
        }
    }
}

sourceSets {
    main {
        java {
            setSrcDirs(listOf("src"))
            include("**/*.java")
        }
        resources {
            setSrcDirs(listOf("src"))
            include("**/*.properties", "**/manifest.txt")
        }
    }
    test {
        java {
            setSrcDirs(listOf("test"))
            include("**/*.java")
        }
    }
}

// Generate an asset manifest listing all files under assets/ so that code
// running from a JAR or a context without JAR introspection (like Android) can enumerate assets.
tasks.register("generateAssetManifest") {
    val assetsDir = file("assets")
    val outputDir = file("${layout.buildDirectory.get()}/generated-resources/assets")
    val manifestFile = File(outputDir, "manifest.txt")

    inputs.dir(assetsDir)
    outputs.file(manifestFile)

    doLast {
        outputDir.mkdirs()
        val lines = mutableListOf<String>()
        assetsDir.walkTopDown().forEach { f ->
            if (f == assetsDir) return@forEach
            val relative = assetsDir.toPath().relativize(f.toPath()).toString().replace('\\', '/')
            val prefix = "assets/$relative"
            if (f.isDirectory) {
                lines.add("$prefix/\tD")
            } else {
                lines.add("$prefix\tF")
            }
        }
        lines.sort()
        manifestFile.writeText(lines.joinToString("\n") + "\n")
    }
}

sourceSets.main.get().resources.srcDirs("${layout.buildDirectory.get()}/generated-resources")
tasks.processResources { dependsOn("generateAssetManifest") }

spotless {
    java {
        eclipse().configFile("eclipse-formatter-config.xml")
        cleanthat()
    }
}
