plugins { id("com.android.application") version "9.1.1" }

android {
    namespace = "dev.cctv.v380.oracle"
    compileSdk = 37
    ndkVersion = "29.0.14206865"
    defaultConfig {
        applicationId = "dev.cctv.v380.oracle"
        minSdk = 26
        targetSdk = 35
        ndk { abiFilters += "arm64-v8a" }
    }
    sourceSets.getByName("main").jniLibs.directories.add(layout.buildDirectory.dir("oracle-libs").get().asFile.absolutePath)
    packaging { jniLibs { useLegacyPackaging = true } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

// Standalone development tool. No dependency from :app, no downloaded/vendor binaries in git.
val prepareOracle by tasks.registering(Exec::class) {
    val apk = providers.gradleProperty("vendorApk")
    inputs.file("prepare.py")
    inputs.file("src/main/cpp/oracle.cpp")
    if (apk.isPresent) inputs.file(apk.get())
    outputs.dir(layout.buildDirectory.dir("oracle-libs"))
    outputs.file(layout.buildDirectory.file("vendor-hashes.json"))
    doFirst {
        require(apk.isPresent) { "Pass -PvendorApk=/path/to/v380-6-4-90.apk" }
        commandLine("python3", "prepare.py", apk.get(), androidComponents.sdkComponents.sdkDirectory.get().asFile.absolutePath)
    }
}
tasks.named("preBuild") { dependsOn(prepareOracle) }
