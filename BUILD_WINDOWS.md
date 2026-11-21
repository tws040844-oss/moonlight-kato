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
.\gradlew.bat :app:testRootDebugUnitTest
.\gradlew.bat :app:lintNonRootDebug
.\gradlew.bat :app:lintRootDebug
.\gradlew.bat :app:assembleNonRootDebug
.\gradlew.bat :app:assembleNonRootRelease
.\gradlew.bat :app:assembleRootDebug
```

## v0.6 應用程式名稱自動解析度

- KATO nonRoot 在「基本設定」新增「自動套用應用程式名稱解析度」（`checkbox_auto_app_resolution`，預設啟用）；root flavor 會移除該偏好項目且停用該功能。
- 搭配 Sunshine 項目名稱內的尺寸標註（如 `Desktop｜Fold8U 展開直向 (效率 1968×2184)`），開啟串流時自動套用對應寬高，並依尺寸比例自動切換手機橫向／直向，不需每次先到設定頁改解析度。
- 當次套用不會覆蓋偏好設定內保存的預設解析度；若項目名稱未包含解析度（如 `Desktop｜原生雙螢幕`），自動沿用設定頁選項。
- 若當前位元率為系統預設值，會依新解析度與 FPS 自動計算；若已手動調整過位元率，則保留手動設定值。

## v0.6 產物狀態

| 項目 | 目前狀態 |
|---|---|
| Debug 身分 | `tw.kato.moonlight.debug`／`12.1-kato.0.6-dev`／versionCode `314006` |
| Release 身分 | `tw.kato.moonlight.unofficial`／`12.1-kato.0.6`／versionCode `314006` |
| Unit test | PASS；nonRoot、root 各 51 tests／0 failures／0 errors／0 skipped |
| Lint | PASS；nonRoot 0 errors／188 warnings，root 0 errors／189 warnings |
| Debug／Release／Root Debug | 三者皆建置 PASS；Root versionName 維持 `12.1` |
| Release 目標檔名 | `dist\Moonlight-KATO-12.1-kato.0.6.apk` |
| Release APK 大小 | 8,009,205 bytes |
| Release SHA-256 | `2D11CC6F5E8C8A36F1CA6C6D9D1DBB4548A1128C5380268E77C7BEAFC03083D0` |
| Release 簽章 readback | v1／v2 PASS；憑證 SHA-256 `d39be840d3ca9501310b561becf21f4f0459ebb617fc93915e1656f1410ca85f`；RSA 3072-bit |
| Fold 安裝 readback | `adb install -r` PASS；`SM-F9760`（`RFGL73HJWQY`）／`12.1-kato.0.6`／`314006`；`firstInstallTime` 維持 `2026-08-15 01:52:17` |
| 冷啟動 smoke | PASS；PID `8869`、AndroidRuntime 無錯誤 |
| Sunshine 實機串流協商 | 待使用者點選橫／直向項目後驗收 |

## v0.5 自訂解析度

- KATO nonRoot 在「基本設定」提供「自訂解析度」編輯器，可分別輸入寬度與高度。
- 自訂值在偏好設定中以 `custom:<width>x<height>` 編碼，例如 `custom:2184x1228`；UI 解析度清單會顯示為「自訂（2184 × 1228）」。
- 寬、高各自必須介於 256–8192 像素、均為偶數，總像素數不得超過 `7680 × 4320`（33,177,600 像素）。允許任意長寬比、橫向、直向與正方形；不需符合 16:9。
- 空白、非整數、超出範圍、奇數尺寸或超過 8K UHD 總像素上限時，編輯器會顯示繁中／英文驗證錯誤並不儲存。
- 若現有 `custom:` 偏好值無效或損壞，`PreferenceConfiguration` 會回復為預設 `1280x720`，避免解析錯誤導致 App 崩潰。
- 儲存會同步更新自訂原始值、解析度選單值與依當前 FPS 推算的預設位元率；儲存後必須中止並重新連線。
- 自訂值在 `Game` 中一律視為非 Native，不觸發 Native 專用的方向鎖定、inset 忽略與顯示模式配對。
- 這只是 Android client 設定；Sunshine 主機顯示器或 VDD 仍必須支援一樣的尺寸，否則無法保證協商成功。
- root flavor 會從設定畫面移除編輯器，且不接受 `custom:` 值；官方 root 行為維持隔離。

## v0.5 產物狀態

| 項目 | 目前狀態 |
|---|---|
| Debug 身分 | `tw.kato.moonlight.debug`／`12.1-kato.0.5-dev`／versionCode `314005` |
| Release 身分 | `tw.kato.moonlight.unofficial`／`12.1-kato.0.5`／versionCode `314005` |
| Unit test | PASS；nonRoot、root 各 46 tests／0 failures／0 errors／0 skipped |
| Lint | PASS；nonRoot 0 errors／187 warnings，root 0 errors／188 warnings |
| Debug／Release／Root Debug | 三者皆建置 PASS；Root versionName 維持 `12.1` |
| Release 目標檔名 | `dist\Moonlight-KATO-12.1-kato.0.5.apk` |
| Release APK 大小 | 8,007,881 bytes |
| Release SHA-256 | `3F60C2F8D146A934E68DAB7BF7EBF83E5CF201BC12988D92E20C15E129BB95F7` |
| Release 簽章 readback | v1／v2 PASS；憑證 SHA-256 `d39be840d3ca9501310b561becf21f4f0459ebb617fc93915e1656f1410ca85f`；RSA 3072-bit |
| APK 內嵌 revision | `f6974088ff24035cfb158f56ba54d0784934a902` |
| Fold 安裝 readback | `adb install -r` PASS；`SM-F9760`／`12.1-kato.0.5`／`314005`；`firstInstallTime` 維持 `2026-08-15 01:52:17`；既有電腦項目仍可見 |
| 實機 UI smoke | App 冷啟動 PASS、無 Fatal；繁中「自訂解析度」列與對話框可見，寬高預填目前 `2520 × 1080` |
| 儲存／串流協商 | 待使用者選擇尺寸並完整重連後驗收 |

## v0.4 解析度變更

- KATO nonRoot 的固定清單新增 `2184x1228` 與 `2496x1404`，位置在 1080p 與 1440p 之間。
- 繁中顯示為「2184 × 1228（高效率，近 16:9，較省頻寬）」與「2496 × 1404（Fold8 Ultra 精確 16:9，較清晰）」；英文資源提供對應 fallback。
- 解析度資源使用 `app/src/nonRoot/res` 覆寫，root flavor 仍保留官方六個固定選項。
- `2184x1228` 與 `2496x1404` 只在 nonRoot 被判定為固定、非 Native 選項；`2504x2256` 等裝置回報尺寸仍維持動態 Native 行為。
- 既有位元率內插不需修改；60 FPS 預設分別為 28,000 kbps 與 38,000 kbps。
- 切換選項後需中止並重新連線，解析度會在新的串流工作階段協商。

## v0.4 產物狀態

| 項目 | 目前狀態 |
|---|---|
| Debug 身分 | `tw.kato.moonlight.debug`／`12.1-kato.0.4-dev`／versionCode `314004` |
| Release 身分 | `tw.kato.moonlight.unofficial`／`12.1-kato.0.4`／versionCode `314004` |
| Unit test | PASS；nonRoot、root 各 31 tests／0 failures／0 errors／0 skipped |
| Lint | PASS；nonRoot 0 errors／187 warnings，root 0 errors／188 warnings |
| Debug／Release／Root Debug | 三者皆建置 PASS；Root versionName 維持 `12.1` |
| Release 目標檔名 | `dist\Moonlight-KATO-12.1-kato.0.4.apk` |
| Release APK 大小 | 7,997,847 bytes |
| Release SHA-256 | `07360EC7AEE1CE5A83C5F80D91C61E267DA1DD3ADB8D4CBF694AA929827A9BE3` |
| Release 簽章 readback | v1／v2 PASS；憑證 SHA-256 `d39be840d3ca9501310b561becf21f4f0459ebb617fc93915e1656f1410ca85f`；RSA 3072-bit |
| APK 內嵌 revision | `afdb8f88a276e4f46a52ddeccf2c79d27526a1fa` |
| 實機安裝／長字串顯示 | 待 ADB 裝置重新連線後覆蓋安裝與目視驗收 |

## v0.3 輸入與介面歷史變更

- Ctrl 與 Shift 改為可點按切換的鎖定修飾鍵；啟用狀態會在工具列上顯示，生命週期清理時會釋放 KATO 擁有的 synthetic key state。
- Caps 以單次 Caps Lock 按鍵事件切換 Windows 英文大小寫狀態。
- 快捷鍵依「Windows、視窗、編輯、系統、功能鍵」分組，避免主工具列過長。
- 「Windows」分組加入 Windows 放大鏡的放大、縮小與關閉快捷鍵。
- 「功能鍵」分組新增 F1～F12 單次點按，並可與 Ctrl／Shift 鎖定狀態組合。VK `0x70`～`0x7B` 均由 translator 轉為 keyMap `0x8070`～`0x807B`，不硬編送出協定值。
- `PcKeyDispatcherTest` 現有 27 項測試，最新報告為 27 tests／0 failures／0 errors／0 skipped。

Moonlight Android 目前沒有 client 端 pinch-to-zoom。即使關閉觸控板模式，第二根手指仍由既有輸入路徑轉為滑鼠滾輪；原生多點觸控轉送程式碼目前被註解。v0.2～v0.5 未改動串流 Surface、座標映射或 native input，Windows 放大鏡快捷鍵是安全替代方案。

## v0.3 歷史產物狀態

| 項目 | 目前狀態 |
|---|---|
| Debug 身分 | `tw.kato.moonlight.debug`／`12.1-kato.0.3-dev`／versionCode `314003` |
| Release 身分 | `tw.kato.moonlight.unofficial`／`12.1-kato.0.3`／versionCode `314003` |
| Unit test | PASS，27 tests／0 failures／0 errors／0 skipped |
| Lint | PASS，0 errors／187 warnings |
| Debug／Release／Root Debug | 三者皆建置 PASS；Root versionName 維持 `12.1` |
| Release 目標檔名 | `dist\Moonlight-KATO-12.1-kato.0.3.apk` |
| Release APK 大小 | 7,997,033 bytes |
| Release SHA-256 | `6BA45D62A1A30F9DC12E0770DEF6D655ED0178B0AEF038B3C3611B8C018EBB28` |
| Release 簽章 readback | v1／v2 PASS；憑證 SHA-256 `d39be840d3ca9501310b561becf21f4f0459ebb617fc93915e1656f1410ca85f`；RSA 3072-bit |
| Fold 安裝 readback | 已以 `adb install -r` 覆蓋安裝至 `SM-F9760`；package、versionName、versionCode 正確且 `firstInstallTime` 不變；未清除 App data，Sunshine 配對待使用者開啟確認 |
| F1～F12 實機驗收 | 待使用者驗收 |

## v0.2 歷史建置收據

| 項目 | 歷史結果 |
|---|---|
| Debug／Release 身分 | `12.1-kato.0.2-dev`／`12.1-kato.0.2`，versionCode `314002` |
| Unit test | PASS，24 tests／0 failures／0 errors／0 skipped |
| Lint | PASS，0 errors／187 warnings |
| Debug／Release／Root Debug | 三者皆建置 PASS |
| Release APK | 7,990,614 bytes；SHA-256 `0825436FF0E54DEDD763779F7EA923ED90EE3E2A1B8D2A50FD1FB09E9AB0A556` |
| Release 簽章 | v1／v2 PASS；相同 RSA 3072-bit KATO 憑證 |
| 實機結果 | 已安裝至 Fold；使用者回報目前功能正常 |
| 本機 tag | `kato-v0.2.0` |

## v0.1 歷史建置收據

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

## v0.1 Release APK 歷史 readback

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

上述大小、雜湊與 readback 全部屬於 v0.1，不得視為較新版本的產物資訊。v0.1 已由使用者完成實際使用測試並確認核心功能正常；v0.2 後續亦已安裝並由使用者回報功能正常。v0.3 已完成覆蓋安裝與身分 readback，F1～F12 功能仍待使用者驗收。
