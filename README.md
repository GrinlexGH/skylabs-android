### 📱 Android Build

The Android pipeline is **fully automated via Gradle** - no manual Conan step required (you can change conan setup manually from [`build.gradle.kts`](android/app/build.gradle.kts) if you need).

```bash
cd android
./gradlew assembleDebug
```

> [!TIP]
> Gradle triggers conan internally during the `configureCMake` phase. It uses android-specific conan profile (you can copy it from my conan config repo).
