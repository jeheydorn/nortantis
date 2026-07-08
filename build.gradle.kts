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
    )
}

tasks.register<JavaExec>("runApi") {
    description = "Run the Map API server"
    group = "application"
    mainClass.set("nortantis.api.MapApiServer")
    classpath = sourceSets.main.get().runtimeClasspath
    jvmArgs = listOf(
        "--enable-native-access=ALL-UNNAMED",
        "-Djava.awt.headless=true",
        "-Xmx4g",
    )
    // Pass NORTANTIS_API_TOKEN env var through to the server process
    environment("NORTANTIS_API_TOKEN", System.getenv("NORTANTIS_API_TOKEN") ?: "")
    // Pass command line args, e.g., ./gradlew runApi --args="9090"
    if (project.hasProperty("apiPort")) {
        args = listOf(project.property("apiPort").toString())
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
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
    // `:benchmark --tests "nortantis.FindClosestCenterBenchmark"` match nothing.
    filter {
        includeTestsMatching("*Benchmark")
        // Don't fail the build when this task is asked (e.g. by IntelliJ as a candidate test task) to run a non-benchmark test it doesn't
        // include. Without this, IntelliJ runs of regular tests fail with "No matching tests found ... in task :benchmark" before the
        // :test task gets a chance to run them.
        isFailOnNoMatchingTests = false
    }

    // Enable the benchmark gate (@EnabledIfSystemProperty(named = "runBenchmarks", matches = "true")).
    systemProperty("runBenchmarks", "true")

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
// running from a JAR or on Android can enumerate assets without JAR introspection.
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
