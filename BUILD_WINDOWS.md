# Moonlight KATO Windows 建置指南

## 固定工具鏈

- Android Studio `2026.1.3.7`
- Eclipse Temurin JDK `17.0.19.10`
- Android SDK Platform 34
- Android SDK Build Tools `34.0.0`
- Android SDK Platform Tools／ADB
- Android SDK Command-line Tools
- Android NDK `27.0.12077973`
- Gradle Wrapper `8.7`
- Android Gradle Plugin `8.5.1`

## 本機前置作業

```powershell
winget install --id EclipseAdoptium.Temurin.17.JDK --version 17.0.19.10 --exact
winget install --id Google.AndroidStudio --version 2026.1.3.7 --exact
git submodule update --init --recursive
```

`local.properties` 必須指向本機 Android SDK 與固定 NDK。此檔只屬於本機，不得提交：

```properties
sdk.dir=C\:\\Users\\User\\AppData\\Local\\Android\\Sdk
ndk.dir=C\:\\Users\\User\\AppData\\Local\\Android\\Sdk\\ndk\\27.0.12077973
```

## Release 簽章

KATO release 使用下列 repository 外部檔案：

- `C:\Users\User\.android\moonlight-kato-release.jks`
- `C:\Users\User\.android\moonlight-kato-signing.properties`

properties 格式如下；實際密碼不得放入 Git、文件或終端輸出：

```properties
storeFile=C:\\Users\\User\\.android\\moonlight-kato-release.jks
storePassword=<secret>
keyAlias=moonlight-kato
keyPassword=<secret>
```

同一 application ID 的後續更新必須沿用同一把 release keystore。遺失 keystore 將無法覆蓋安裝既有 release App。

## 建置與測試

在 repository 根目錄執行：

```powershell
.\gradlew.bat :app:testNonRootDebugUnitTest
.\gradlew.bat :app:lintNonRootDebug
.\gradlew.bat :app:assembleNonRootDebug
.\gradlew.bat :app:assembleNonRootRelease
```

## 本次建置收據

執行日期：2026-08-15（Asia/Taipei）

| 項目 | 實測結果 |
|---|---|
| JDK | Temurin `17.0.19+10` |
| Gradle | `8.7` |
| Android Studio | `2026.1.3.7`，`studio64.exe` Google Authenticode 有效 |
| SDK／Build Tools | Platform 34／`34.0.0` |
| Platform Tools | ADB `37.0.1-15733141` |
| Command-line Tools | `22.0` |
| NDK | `27.0.12077973` |
| 上游乾淨基準 | `:app:assembleNonRootDebug`：PASS |
| KATO unit test | `:app:testNonRootDebugUnitTest`：PASS，14 tests／0 failures |
| KATO lint | `:app:lintNonRootDebug`：PASS；187 個既有 warning，KATO 新增檔案 0 個 issue |
| KATO Debug | `:app:assembleNonRootDebug`：PASS |
| KATO Release | `:app:assembleNonRootRelease`：PASS |
| Root 相容性 | `:app:assembleRootDebug`：PASS；versionName 維持 `12.1` |

winget 的 machine 安裝流程曾被 Windows UAC 安全桌面阻擋，無法由自動化代為確認；因此改用相同官方版本的使用者範圍 ZIP。JDK、Android Studio、SDK／NDK 均位於 `C:\Users\User\AppData\Local\`。此差異不影響固定版本或建置產物。

工具鏈下載暫存約 1.86 GB，位於 `C:\Users\User\AppData\Local\Temp\MoonlightKatoToolchain`；建置與 APK 已不再依賴它，可由使用者確認後手動刪除。

Gradle 會提示 `ndk.dir` 已 deprecated，以及新版 SDK metadata XML 與 AGP 8.5.1 的相容性警告；兩者均未使任何指定工作失敗。

## Release APK readback

- 檔案：`dist\Moonlight-KATO-12.1-kato.0.1.apk`
- 大小：7,975,189 bytes
- SHA-256：`B19E03CB3CC62139796D3090B9BFF0F98E2AC5F594D52E27E90BA792BDD4CD75`
- Package：`tw.kato.moonlight.unofficial`
- Label：`Moonlight KATO`
- versionName：`12.1-kato.0.1`
- versionCode：`314001`
- minSdk／targetSdk：21／34
- ABI：`arm64-v8a`、`armeabi-v7a`、`x86`、`x86_64`
- APK Signature Scheme：v1、v2 驗證通過
- 簽署憑證 SHA-256：`d39be840d3ca9501310b561becf21f4f0459ebb617fc93915e1656f1410ca85f`
- 簽署金鑰：RSA 3072-bit

Debug 靜態 readback：package `tw.kato.moonlight.debug`、label `Moonlight KATO Dev`、versionName `12.1-kato.0.1-dev`、versionCode `314001`。

Root Debug 相容性 readback：package `com.limelight.root.debug`、label `Moonlight (Root Debug)`、versionName `12.1`、versionCode `314`。KATO 工具列 listener、顯示條件及偏好項目均在 root flavor 停用。

實際安裝後的 `adb shell dumpsys package` readback 需在 Fold 完成 RSA 授權後補做。
