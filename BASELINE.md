# Moonlight KATO 基準紀錄

## 原始碼基準

- 上游 repository：`https://github.com/moonlight-stream/moonlight-android.git`
- 上游 commit：`f10085f552b367cf7203007693d91c322a0a2936`
- 本機分支：`feature/pc-key-toolbar`
- GitHub Fork／遠端推送：無
- `moonlight-common-c`：`8af4562af672dd6b9ed28553ead172984fd9a683`
- `enet`：`d3a323fc8b9559786ee059205db74e8027c756f7`

## 範圍界線

KATO v0.5 功能只在 Android `nonRoot` client 啟用。不修改 Sunshine、VDD、Moonlight native code、串流協定、root flavor 的身分與既有輸入路徑、`namespace 'com.limelight'` 或 Java package。共用 `main` source set 中的 UI class 與資源仍會參與 root 編譯，但 root runtime 會移除 KATO 自訂解析度與工具列偏好項目，並拒絕 `custom:` 編碼值；root flavor 的 versionName 維持 `12.1`。

v0.5 在 KATO nonRoot 的「基本設定」新增「自訂解析度」編輯器。使用者可分別輸入寬度與高度，儲存後以 `custom:<width>x<height>` 記錄於解析度選單，例如 `custom:2504x2256`。不限長寬比或橫直方向，但寬、高各自必須介於 256–8192 像素、均為偶數，且總像素數不得超過 `7680 × 4320`（33,177,600 像素）。儲存時會依尺寸與 FPS 重算預設位元率；無效或損壞的 `custom:` 偏好值不會使 App 崩潰，而會回復為預設 `1280x720`。

自訂解析度會明確視為非 Native，即使尺寸剛好與 Android 螢幕相同，`Game` 仍不會套用 Native 解析度專用的方向鎖定、inset 忽略與顯示模式配對邏輯。實際串流成功仍取決於 Sunshine 主機顯示器或 VDD 是否支援相同尺寸；儲存後必須完整中止並重新連線才會在新工作階段協商。

v0.4 在 KATO nonRoot 的解析度清單新增兩個固定選項：`2184x1228`（高效率，近 16:9，較省頻寬）與 `2496x1404`（Fold8 Ultra 精確 16:9，較清晰）。英文介面亦有對應說明。root flavor 維持官方原有六個固定選項；裝置回報的 `2504x2256` 仍屬動態 Native 解析度。變更解析度後必須完整中止並重新建立串流，新的尺寸才會在連線協商時生效。

v0.3 在 v0.2 的分組快捷鍵選單新增第五組「功能鍵」，提供 F1～F12 單次點按，並可與已鎖定的 Ctrl／Shift 組合。F1～F12 仍經 `KeyboardTranslator` 轉換：Windows VK 為 `0x70`～`0x7B`，送出的 keyMap 為 `0x8070`～`0x807B`，未修改 native input 或串流協定。

v0.2 已由使用者在 Fold／Windows 11 串流環境回報目前功能正常，並建立本機 tag `kato-v0.2.0`。v0.3 已以 `adb install -r` 覆蓋安裝至 `SM-F9760`，package 與版本 readback 正確、`firstInstallTime` 不變且未執行資料清除。v0.4 已完成建置與靜態驗證。v0.5 已完成自動化驗證與產物 readback；當前 ADB 清單無裝置，因此不宣稱已安裝，編輯器 UI、Fold 顯示與實際串流協商仍待實機驗收。

## App 身分

| Variant | Package | App 名稱 | versionName | versionCode |
|---|---|---|---|---:|
| `nonRootDebug` | `tw.kato.moonlight.debug` | Moonlight KATO Dev | `12.1-kato.0.5-dev` | 314005 |
| `nonRootRelease` | `tw.kato.moonlight.unofficial` | Moonlight KATO | `12.1-kato.0.5` | 314005 |

新 application ID 使用獨立 App data；首次連線必須重新與 Sunshine 配對並重新設定串流選項。

v0.4 歷史版本為 Debug `12.1-kato.0.4-dev`／Release `12.1-kato.0.4`，versionCode `314004`。v0.3 歷史版本為 Debug `12.1-kato.0.3-dev`／Release `12.1-kato.0.3`，versionCode `314003`。v0.2 歷史版本為 Debug `12.1-kato.0.2-dev`／Release `12.1-kato.0.2`，versionCode `314002`；其本機 tag 為 `kato-v0.2.0`。v0.1 歷史版本為 Debug `12.1-kato.0.1-dev`／Release `12.1-kato.0.1`，versionCode `314001`；其本機基準 tag 為 `kato-v0.1.0`。

## 觸控縮放界線

目前 Moonlight Android 沒有 client 端的 pinch-to-zoom 實作。觸控板模式關閉後，第二根手指仍被既有輸入路徑解讀為滑鼠滾輪；將原生多點觸控轉送到遠端的程式碼目前也被註解。因此雙指縮放無法改變串流畫面的顯示比例，這不是 Fold 或 Samsung Keyboard 設定異常。

v0.2～v0.5 不修改串流 Surface、座標映射或 native input；需要放大 Windows 內容時，可使用工具列「Windows」分組內的 Windows 放大鏡放大、縮小與關閉快捷鍵作為替代方案。

## 建置收據

2026-08-15 以獨立 detached worktree 在未修改的 `f10085f` 執行 `:app:assembleNonRootDebug`，結果為 `BUILD SUCCESSFUL`（45 tasks）。工具鏈版本與 KATO 驗證結果記錄在 [BUILD_WINDOWS.md](BUILD_WINDOWS.md)。
