# Moonlight KATO v0.6 測試矩陣

狀態標記：`PASS`、`FAIL`、`BLOCKED`、`待使用者驗收`、`N/A`。自動化測試結果與 Fold 人工驗收分開計算；既有版本的正常使用回報不得直接視為 v0.6 應用程式名稱自動解析度與方向切換的實機 PASS。

## 自動化測試

| 項目 | 預期結果 | 狀態 |
|---|---|---|
| Win 單鍵 | DOWN／UP，BASE 含 META 時零事件 | PASS |
| Win+E／Win+R／放大鏡 | owned META 正確包住主鍵，所有 key map 經 translator 取得 | PASS |
| Alt+Tab | owned ALT 正確包住 Tab | PASS |
| Ctrl+Shift+Esc | owned modifier 依相反順序釋放 | PASS |
| Ctrl／Shift 鎖定 | 點按啟用後套用至後續按鍵，明確關閉時才釋放 | PASS |
| 多個鎖定修飾鍵 | Ctrl、Shift 依相反順序釋放 | PASS |
| 鎖定狀態下執行快捷鍵 | 不提前釋放相同的已鎖定 modifier | PASS |
| BASE 含相同 modifier | 不重送、不提前釋放實體 modifier | PASS |
| 左右實體 Ctrl ownership | 右 Ctrl 不得接管 synthetic 左 Ctrl 的釋放責任 | PASS |
| Caps | 傳送單次 Caps Lock DOWN／UP，由 Windows 維持大小寫狀態 | 由共用單鍵分派路徑涵蓋；待實機確認 LED／狀態呈現 |
| F1～F12 | VK `0x70`～`0x7B` 經 translator 產生 keyMap `0x8070`～`0x807B`，各傳送單次 DOWN／UP | PASS |
| F1～F12 與鎖定鍵組合 | 已鎖定 Ctrl／Shift 套用至功能鍵，功能鍵放開後不提前釋放 latch | PASS |
| 未連線／translator=0 | 零輸入事件 | PASS |
| sender 失敗 | `finally` 盡力反向清除 owned modifier | PASS |
| 中斷／失敗清理 | 清除本機鎖定狀態；可送達時反向釋放 synthetic modifier | PASS |
| 連續及併發呼叫 | 100 次連續 chord 與併發請求均保持完整序列，不交錯、不殘留 state | PASS |
| 解析度分類 | nonRoot 將 2184x1228／2496x1404 視為固定非 Native；root 維持 Native；2504x2256 仍為 Native | PASS |
| 解析度資源隔離 | nonRoot 為 8 組同序名稱／值；root 維持官方 6 組且不含 KATO 說明字串 | PASS |
| 預設位元率 | 2184x1228@60 為 28,000 kbps；2496x1404@60 為 38,000 kbps | PASS |
| 自訂值格式 | `custom:WxH` 與 raw `WxH` 可正規化、往返解析；無效 prefix／overflow 安全拒絕 | PASS |
| 自訂值驗證 | 256–8192、寬高偶數、總像素不超過 7680×4320；橫向／直向／方形皆可 | PASS |
| 自訂 Native 分類 | `custom:2504x2256` 與其他自訂尺寸均視為非 Native；相同 bare 尺寸仍可維持 Native | PASS |
| 自訂位元率 | 2184×1228、2496×1404、2504×2256、2520×1080、8K 的 30／60／120 FPS 計算符合既有公式 | PASS |
| Root prefix 隔離 | root 遇到 `custom:` 時安全回復 1280×720，不崩潰、不啟用 KATO 自訂行為 | PASS |
| 名稱自動解析度擷取 | 支援 `×`/`x`/`*`（如 `1968×2184`），忽略 `16:9`/`2K`/`4K`、奇數與超限值，取最後一組有效尺寸 | PASS |
| 名稱自動解析度位元率與隔離 | 預設位元率隨尺寸重算、手動位元率保留；`root` 或停用開關時零改動 | PASS |
| JUnit | nonRoot、root 各 51 tests／0 failures／0 errors／0 skipped | PASS |
| Lint | nonRoot 0 errors／188 warnings；root 0 errors／189 warnings | PASS |
| Debug／Release | `assembleNonRootDebug`、`assembleNonRootRelease` | PASS |
| Root flavor 回歸 | `assembleRootDebug`，package `com.limelight.root.debug`、versionName `12.1` | PASS |

## Fold／Windows 11 實機驗收

| 區域 | 驗收項目 | 狀態／證據 |
|---|---|---|
| 安裝與身分 | KATO Release v0.6 覆蓋安裝；package、versionName、versionCode 正確 | PASS；`SM-F9760`／`12.1-kato.0.6`／`314006` |
| 資料保留 | 覆蓋安裝不得改變 `firstInstallTime`，且不得清除 App data | PASS；時間維持 `2026-08-15 01:52:17` |
| 冷啟動 smoke | `monkey` 啟動主畫面，無 `AndroidRuntime:E` crash | PASS；PID `8869` |
| 名稱自動解析度串流 | 點選 `1968×2184` 直向與 `2184×1968` 橫向項目時自動套用解析度與方向並顯示提示 | 待使用者驗收 |
| 自訂解析度入口 | 基本設定顯示繁中「自訂解析度」；root 不顯示 | nonRoot PASS；root runtime 靜態／編譯驗證 PASS，root 實機 N/A |
| 對話框 | 寬高欄位可捲動、長字與鍵盤不裁切；取消零寫入；錯誤時不關閉 | 基本顯示與目前值 `2520 × 1080` 預填 PASS；完整錯誤互動待使用者驗收 |
| 驗證提示 | 空白、非整數、奇數、超出範圍與超過 8K 總像素均顯示正確繁中提示 | 待使用者驗收 |
| 保存與重啟 | 儲存後清單選中「自訂（W × H）」；離開設定或重啟 App 後仍保留 | 待使用者驗收 |
| 自訂串流 | 以 2520×1080、2504×2256 或其他主機已支援尺寸完整重連，Sunshine／效能統計顯示相同值 | 待使用者驗收 |
| 並存 | 官方、KATO Debug、KATO Release 同時安裝 | v0.5 未重新完整盤點，待使用者驗收 |
| 基本輸入 | Win、Ctrl、Shift、Caps、Esc、Tab、四向鍵 | 既有版本使用者回報正常；v0.5 回歸待驗收 |
| 修飾鍵鎖定 | Ctrl／Shift 啟用狀態、組合輸入、關閉與生命週期釋放 | 既有版本使用者回報正常；v0.5 回歸待驗收 |
| 分組快捷鍵 | Windows、視窗、編輯、系統、功能鍵五組選單及所有 chord | v0.5 回歸待驗收 |
| F1～F12 | F1～F12 單次點按符合 Windows 11／目標應用程式預期 | v0.5 回歸待驗收 |
| 功能鍵組合 | Ctrl+F1～F12、Shift+F1～F12 的 latch 組合與釋放 | v0.5 回歸待驗收 |
| Windows 放大鏡 | 放大、縮小、關閉皆符合 Windows 11 預期 | 既有版本使用者回報正常；v0.5 回歸待驗收 |
| IME | 三指與工具列按鈕切換 Samsung Keyboard | 既有版本使用者回報正常；v0.5 回歸待驗收 |
| 中文輸入 | 標準／分割鍵盤中英文輸入 | 待使用者驗收 |
| Fold | 內外螢幕、折疊、展開、橫直向後仍可見 | 待使用者驗收 |
| 觸控隔離 | 拖曳／點擊不產生遠端滑鼠事件 | 待使用者驗收 |
| OSC 多點 | 一指手把、另一指工具列互不取消 | 待使用者驗收 |
| 輸入回歸 | 觸控板、直接觸控、實體鍵鼠、手把 | 待使用者驗收 |
| 串流回歸 | 60／120 FPS 無功能回歸 | 待使用者驗收 |
| 生命週期 | PiP、背景、鎖屏、中止、斷網不 crash | 待使用者驗收 |
| 卡鍵 | 正常中止或隱藏工具列後 Ctrl／Alt／Shift／Win 全部釋放 | 待使用者驗收 |
| 易用性 | 48dp、明暗背景、放大字級不裁切 | 待使用者驗收 |

## Pinch-to-zoom 說明

Moonlight Android 目前沒有 client 端 pinch-to-zoom：兩指操作在現有 touch context 中用於 mouse scroll，而原生多點觸控 forwarding 程式碼目前被註解。因此關閉觸控板模式也不會讓雙指手勢縮放串流畫面，此項在 v0.5 屬於 `N/A`，不是待修正的工具列回歸。

若目的是放大 Windows 內容，請驗收「Windows」分組中的放大鏡放大、縮小與關閉快捷鍵。真正的串流畫面縮放需另行實作 Surface scale／crop 與反向座標映射，不納入 v0.5。

## v0.1～v0.4 實機歷史

使用者已在 Fold／Windows 11 實際測試 v0.1，並回報當時功能運作正常。v0.2 後續亦完成安裝，使用者回報目前功能正常，並建立本機 tag `kato-v0.2.0`。v0.3 已完成覆蓋安裝與身分 readback；v0.4 已完成建置與靜態驗證。這些歷史紀錄不直接證明 v0.5 自訂解析度 UI 與新工作階段協商已通過。

## 人工關卡

v0.5 已沿用相同 release package 與 keystore，並以 `adb install -r` 成功覆蓋既有 KATO Release；版本、首次安裝時間、首頁電腦項目、冷啟動與自訂對話框均完成 readback／smoke。保存重啟、所有錯誤提示與實際串流協商仍由使用者驗收。浮動 Samsung Keyboard 模式不列入 v0.5 保證。
