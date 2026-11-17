# BUILD AND SIGNING GUIDE
## Reel Monster — Reproducible Build & Release Engineering Reference

**Project:** Reel Monster  
**Document Version:** 1.0.0  
**Target Toolchain:** Gradle 9.5.0 · Android Gradle Plugin 9.0.1 · Kotlin 2.3.20 · Java 21  

---

## 1. Development Prerequisites

To reproduce the signed release build of Reel Monster, the build environment must meet the following requirements:

- **Operating System:** macOS (Sonoma/Sequoia), Linux (Ubuntu 22.04+), or Windows 11
- **Java Development Kit:** JDK 21 (Eclipse Temurin 21.0.6 or OpenJDK 21)
- **Android SDK:**
  - `compileSdk` = 36
  - `minSdk` = 26
  - `targetSdk` = 36
  - Android SDK Build-Tools: `36.0.0`
- **Gradle:** 9.5.0 (managed automatically via `./gradlew`)

Ensure your environment variables are configured:
```bash
export JAVA_HOME="/path/to/jdk-21"
export ANDROID_HOME="$HOME/Library/Android/sdk"   # macOS
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
```

---

## 2. Release Keystore Architecture

Reel Monster enforces a strict separation between application source code and cryptographic signing credentials. **Keystores, private keys, and passwords must never be committed to Git.**

### Keystore Generation Command
The official release keystore was generated using the standard Java `keytool` utility:
```bash
keytool -genkeypair -v \
  -keystore ~/reel-monster-release.keystore \
  -alias reelmonster \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -dname "CN=Reel Monster, OU=Engineering, O=Reel Monster, L=Bangalore, ST=Karnataka, C=IN"
```

### Keystore Metadata
- **Keystore Path:** `~/reel-monster-release.keystore`
- **Alias:** `reelmonster`
- **Key Algorithm:** RSA 2048-bit
- **Validity:** 10,000 days (~27 years)
- **File Permissions:** `chmod 600 ~/reel-monster-release.keystore`

---

## 3. Secure Gradle Credentials Setup

To sign release builds, store your signing credentials in your user-level Gradle configuration file (`~/.gradle/gradle.properties`). This file lives outside the repository directory:

```properties
# ~/.gradle/gradle.properties
reelMonsterStoreFile=/Users/rohansiddhpura/reel-monster-release.keystore
reelMonsterStorePassword=<YOUR_KEYSTORE_PASSWORD>
reelMonsterKeyAlias=reelmonster
reelMonsterKeyPassword=<YOUR_KEY_PASSWORD>
```

Secure the properties file:
```bash
chmod 600 ~/.gradle/gradle.properties
```

### Gradle Build Script Integration (`app/build.gradle.kts`)
The Gradle script detects these properties dynamically. If they are absent, the build falls back to an unsigned release build:
```kotlin
signingConfigs {
    if (project.findProperty("reelMonsterStoreFile") != null) {
        create("release") {
            storeFile = file(project.findProperty("reelMonsterStoreFile") as String)
            storePassword = project.findProperty("reelMonsterStorePassword") as String
            keyAlias = project.findProperty("reelMonsterKeyAlias") as String
            keyPassword = project.findProperty("reelMonsterKeyPassword") as String
        }
    }
}

buildTypes {
    release {
        isMinifyEnabled = false
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        if (project.findProperty("reelMonsterStoreFile") != null) {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

---

## 4. Executing the Release Build

### Step 1: Run Full Regression Unit Tests
Always verify that all 340 test cases pass prior to packaging:
```bash
./gradlew testDebugUnitTest --rerun-tasks
```
*(Must complete with 340 tests passed, 0 failures).*

### Step 2: Assemble Signed Release APK
Run the assemble task:
```bash
./gradlew assembleRelease
```

The signed release APK will be generated at:
```text
/Users/rohansiddhpura/.cache/reelmonster-build/app/outputs/apk/release/app-release.apk
```

---

## 5. Artifact Verification & Inspection

### A. Copy to Project Release Directory
```bash
cp /Users/rohansiddhpura/.cache/reelmonster-build/app/outputs/apk/release/app-release.apk \
   release/reel-monster-v1.0.0.apk
```

### B. Verify Cryptographic Signature (`apksigner`)
```bash
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --verbose --print-certs release/reel-monster-v1.0.0.apk
```
*Expected Output:*
- `Verifies`
- `Verified using v2 scheme (APK Signature Scheme v2): true`
- `Signer #1 certificate DN: CN=Reel Monster, OU=Engineering, O=Reel Monster...`

### C. Verify Zero Network Permissions (`aapt2`)
```bash
$ANDROID_HOME/build-tools/36.0.0/aapt2 dump badging release/reel-monster-v1.0.0.apk | grep -i "permission"
```
*Confirm:* `android.permission.INTERNET` is **completely absent**.

### D. Generate Checksum
```bash
shasum -a 256 release/reel-monster-v1.0.0.apk > release/reel-monster-v1.0.0.sha256
cat release/reel-monster-v1.0.0.sha256
```

---

## 6. Build Maintenance & Clean-up

To avoid daemon hangs or stale compilation locks:
```bash
# Stop any running daemons
./gradlew --stop

# Perform a clean build if needed
./gradlew clean
```
Note: Gradle build outputs are cached at `/Users/rohansiddhpura/.cache/reelmonster-build/app` to protect external drive endurance and ensure fast build times.
