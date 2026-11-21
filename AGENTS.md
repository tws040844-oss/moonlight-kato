# AGENTS.md — `moonlight-kato` 專案規範與開發紅線

本文件供後續維護與 AI 協作代理遵循，彙整 Moonlight Android KATO 客製版之架構邊界、功能契約、簽章安全與驗證流程。

---

## 1. 核心範圍與 Flavor 隔離紅線

- **僅限 Android `nonRoot` flavor 啟用**：所有 KATO 客製功能（PC 特殊鍵工具列、F1–F12 功能鍵、Fold 8 Ultra 固定解析度、自訂解析度編輯器、依 Sunshine 項目名稱自動套用解析度與橫直向）皆僅在 `nonRoot` 啟用。
- **`root` flavor 維持官方隔離**：`root` flavor 必須從設定頁移除所有 KATO 專屬偏好項目，並拒絕 `custom:` 解析度與應用程式名稱自動解析度覆寫；`versionName` 維持官方版本字串。
- **禁止修改的底層範圍**：不修改 Sunshine 協定、VDD 驅動、Moonlight native C/C++ code、`namespace 'com.limelight'`、Java package 結構或既有串流 Surface 觸控／座標映射（pinch-to-zoom 替代方案為 Windows 放大鏡快捷鍵）。

---

## 2. 功能契約彙整（v0.1 – v0.6）

1. **PC 特殊鍵工具列（v0.1–v0.3）**：
   - 可拖曳停靠左右側之浮動按鈕與工具列，支援 Samsung Keyboard、Win、Esc、Tab、四向鍵、可鎖定（latch）之 Ctrl / Shift、Caps Lock、分組快捷鍵（Windows／視窗／編輯／系統）與 F1–F12。
   - 必須嚴格維護 synthetic modifier ownership（區分左右實體修飾鍵、生命週期失焦／隱藏／斷線自動釋放），避免遠端卡鍵。
2. **Fold 解析度與自訂編輯器（v0.4–v0.5）**：
   - `nonRoot` 解析度清單包含 `2184x1228`（效率 16:9）與 `2496x1404`（Fold8 Ultra 精確 16:9）；此兩者與所有 `custom:WxH` 值皆視為非 Native，不會誤觸發 Native 專用的方向鎖定、inset 忽略或顯示模式配對。
   - `CustomResolution` 驗證範圍為每邊 `256–8192` 偶數、總像素不超過 `7680 × 4320`，支援橫向、直向與方形；無效值不得寫入設定，損壞偏好值必須安全回退 `1280x720`。
3. **依 Sunshine 應用程式名稱自動套用解析度與橫直向（v0.6）**：
   - 設定項 `checkbox_auto_app_resolution`（「自動套用應用程式名稱解析度」）在 `nonRoot` 預設開啟，`root` 移除。
   - 啟動 `Game` 時透過 `AppResolutionParser` 解析 `EXTRA_APP_NAME`（支援全形 `×`、半形 `x` / `*`），取最後一組通過 `CustomResolution.validate` 的尺寸；找不到時（例如 `Desktop｜原生雙螢幕`）自動回退使用者原本設定。
   - **不可覆寫偏好設定保存值**：僅在當次串流暫時套用（`customResolution = true`、`autoAppliedAppResolution = true`），並顯示短暫提示「`已自動套用解析度 W × H`」。
   - **智慧位元率連動**：若串流位元率等於原解析度的系統預設值，自動依新尺寸與 FPS 重算；若使用者已手動自訂位元率則保留。
   - **自動方向配合**：`Game.setPreferredOrientationForCurrentDisplay()` 於開啟自動套用時，依尺寸請求 `SCREEN_ORIENTATION_USER_PORTRAIT`（高 > 寬）或 `SCREEN_ORIENTATION_USER_LANDSCAPE`（寬 > 高）；若已啟用螢幕虛擬手把（OSC）則維持橫向。

---

## 3. App 身分、Release 簽章與隱私紅線

- **Package ID 與簽章一致性**：
  - `nonRootRelease`：`tw.kato.moonlight.unofficial`；`nonRootDebug`：`tw.kato.moonlight.debug`。
  - 後續版本更新必須沿用同一外部 keystore（`%USERPROFILE%\.android\moonlight-kato-release.jks` 與 `moonlight-kato-signing.properties`），確保手機可直接 `adb install -r` 覆蓋升級而不流失 Sunshine 配對資料。
- **嚴禁提交進 Git 的檔案**：
  - 簽章金鑰（`*.jks`、`*-signing.properties`）、密碼、`local.properties`、編譯產物 `*.apk`、`evidence/` 本機實機截圖與 UI dump。
  - 產出之 Release APK 應複製到 `dist\Moonlight-KATO-<version>.apk` 並附上 `.sha256` 校驗檔，透過 GitHub Release 發布。

---

## 4. 固定建置與完整驗證流程

- **工具鏈基準**：JDK 17（Temurin `17.0.19`）、Android SDK Platform 34、Build Tools `34.0.0`、NDK `27.0.12077973`、Gradle `8.7` / AGP `8.5.1`（詳見 `BUILD_WINDOWS.md`）。
- **每次程式變更後必跑指令**：
  ```powershell
  .\gradlew.bat :app:testNonRootDebugUnitTest :app:testRootDebugUnitTest :app:lintNonRootDebug :app:lintRootDebug :app:assembleNonRootDebug :app:assembleNonRootRelease :app:assembleRootDebug
  ```
- **簽章與實機覆蓋驗證**：
  1. 使用 `apksigner.bat verify --verbose --print-certs` 驗證 v1/v2 簽章通過與憑證 SHA-256 一致。
  2. 透過 `adb install -r` 安裝後，以 `dumpsys package tw.kato.moonlight.unofficial` 驗證 `versionName`、`versionCode` 更新且 `firstInstallTime` 不變，並做冷啟動無 crash 檢查。
  3. 同步更新 `CHANGELOG_KATO.md`、`BASELINE.md`、`BUILD_WINDOWS.md` 與 `TEST_MATRIX.md` 實測收據，且只記錄實際觀測到之數據。
