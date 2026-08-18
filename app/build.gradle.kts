@file:Suppress("UnstableApiUsage")


plugins {
    id("com.android.application")
}

abstract class ConanInstallTask @Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask() {

    @get:Internal
    abstract val projectRootDir: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val conanFile: RegularFileProperty

    @get:Input abstract val arch: Property<String>
    @get:Input abstract val apiLevel: Property<Int>
    @get:Input abstract val buildType: Property<String>
    @get:Input abstract val ndkPath: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val conanfileDir = conanFile.get().asFile.parentFile

        val rootDir = this.projectRootDir.get().asFile
        val venvDir = rootDir.resolve(".venv")

        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val binDirName = if (isWindows) "Scripts" else "bin"
        val venvBinDir = venvDir.resolve(binDirName)

        val conanExecutable = if (venvBinDir.exists()) {
            val exeName = if (isWindows) "conan.exe" else "conan"
            val localConan = venvBinDir.resolve(exeName)
            if (localConan.exists()) localConan.absolutePath else "conan"
        } else {
            "conan"
        }

        val args = listOf(
            "install", conanfileDir.absolutePath,
            // You can modify this line for your remotes like artifactory
            "-r", "skylabs", "-r", "conancenter",
            "-pr", "android",
            "-s", "arch=${arch.get()}",
            "-s", "os.api_level=${apiLevel.get()}",
            "-s", "build_type=${buildType.get()}",
            //!!! Depends on this file configuration, don't forget to update !!!
            "-s", "compiler.version=21",
            //!!! Depends on this file configuration, don't forget to update !!!
            "-s", "compiler.libcxx=c++_static",
            "-c", "tools.android:ndk_path=${ndkPath.get()}",
            "-c", "tools.cmake.cmake_layout:build_folder_vars=['settings.arch']",
            "--build", "missing",
        )

        logger.lifecycle(">> Using Conan executable: $conanExecutable")
        logger.lifecycle(">> conan ${args.joinToString(" ")}")

        execOperations.exec {
            commandLine(conanExecutable)
            args(args)
            errorOutput = System.out
            workingDir = conanfileDir

            if (venvBinDir.exists()) {
                val currentPath = System.getenv("PATH") ?: ""
                environment("PATH", "${venvBinDir.absolutePath}${File.pathSeparator}$currentPath")
                environment("VIRTUAL_ENV", venvDir.absolutePath)
            }
        }
    }
}

val repoRoot = layout.projectDirectory.dir("../../")

android {
    namespace = "org.grinlexstudios.skylabs"

    compileSdk = 37
    ndkVersion = "30.0.15729638"

    defaultConfig {
        applicationId = "org.grinlexstudios.skylabs"

        minSdk = 33
        targetSdk = 37

        versionCode = 2026
        versionName = "2026.0.0"

        externalNativeBuild {
            cmake {
                val toolchainPath = repoRoot.file("cmake/ConanAndroidToolchain.cmake").asFile.absolutePath
                arguments += listOf("-DCMAKE_TOOLCHAIN_FILE=$toolchainPath")
                abiFilters += listOf("arm64-v8a")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = repoRoot.file("CMakeLists.txt").asFile
            version = "4.4.0+"
        }
    }

    sourceSets.getByName("main") {
        java.directories += "src/main/java"
        assets.directories += "src/main/assets"
        assets.directories += repoRoot.dir("assets").asFile.absolutePath
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

androidComponents {
    tasks.register("prepareKotlinBuildScriptModel") {
        description = "Android Studio pre-sync."
    }

    onVariants { variant ->
        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        val configName = if (variant.buildType == "release") "RelWithDebInfo" else "Debug"
        val abis = variant.externalNativeBuild?.abiFilters?.get() ?: listOf("arm64-v8a")

        val conanTaskProviders = abis.map { abi ->
            tasks.register<ConanInstallTask>("conanInstall${configName}[${abi}]") {
                description = "Conan install for $abi"

                val conanfileTxt = repoRoot.file("conanfile.txt").asFile
                val conanfilePy = repoRoot.file("conanfile.py").asFile
                
                val conanArch = when (abi) {
                    "arm64-v8a" -> "armv8"
                    "armeabi-v7a" -> "armv7"
                    else -> abi
                }

                projectRootDir.set(repoRoot)
                conanFile.set(if (conanfilePy.exists()) conanfilePy else conanfileTxt)

                arch.set(conanArch)
                apiLevel.set(android.defaultConfig.minSdk!!)
                buildType.set(configName)
                ndkPath.set(androidComponents.sdkComponents.ndkDirectory.map { it.asFile.absolutePath })

                outputDir.set(repoRoot.dir("build/$conanArch/$configName"))
            }
        }

        tasks.matching { it.name.startsWith("configureCMake$configName") }.configureEach {
            dependsOn(conanTaskProviders)
        }

        tasks.findByName("prepareKotlinBuildScriptModel")?.dependsOn(conanTaskProviders)

        tasks.matching { it.name == "merge${variantName}Assets" }.configureEach {
            dependsOn(tasks.matching { it.name.startsWith("buildCMake$configName") })
        }
    }
}
