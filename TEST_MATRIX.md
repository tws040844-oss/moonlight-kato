# Moonlight KATO v0.2 測試矩陣

狀態標記：`PASS`、`FAIL`、`BLOCKED`、`尚未驗收`、`N/A`。自動化測試結果與 Fold 人工驗收分開計算；v0.1 的正常使用結果不得直接繼承為 v0.2 PASS。

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
| 未連線／translator=0 | 零輸入事件 | PASS |
| sender 失敗 | `finally` 盡力反向清除 owned modifier | PASS |
| 中斷／失敗清理 | 清除本機鎖定狀態；可送達時反向釋放 synthetic modifier | PASS |
| 連續及併發呼叫 | 100 次連續 chord 與併發請求均保持完整序列，不交錯、不殘留 state | PASS |
| JUnit | 24 tests／0 failures／0 errors／0 skipped | PASS |
| Lint | `lintNonRootDebug`：0 errors／187 warnings | PASS |
| Debug／Release | `assembleNonRootDebug`、`assembleNonRootRelease` | PASS |
| Root flavor 回歸 | `assembleRootDebug`，package `com.limelight.root.debug`、versionName `12.1` | PASS |

## Fold／Windows 11 實機驗收

| 區域 | 驗收項目 | 狀態／證據 |
|---|---|---|
| 並存 | 官方、KATO Debug、KATO Release 同時安裝 | 尚未安裝／驗收 |
| 基本輸入 | Win、Ctrl、Shift、Caps、Esc、Tab、四向鍵 | 尚未安裝／驗收 |
| 修飾鍵鎖定 | Ctrl／Shift 啟用狀態、組合輸入、關閉與生命週期釋放 | 尚未安裝／驗收 |
| 分組快捷鍵 | Windows、視窗、編輯、系統四組選單及所有 chord | 尚未安裝／驗收 |
| Windows 放大鏡 | 放大、縮小、關閉皆符合 Windows 11 預期 | 尚未安裝／驗收 |
| IME | 三指與工具列按鈕切換 Samsung Keyboard | 尚未安裝／驗收 |
| 中文輸入 | 標準／分割鍵盤中英文輸入 | 尚未安裝／驗收 |
| Fold | 內外螢幕、折疊、展開、橫直向後仍可見 | 尚未安裝／驗收 |
| 觸控隔離 | 拖曳／點擊不產生遠端滑鼠事件 | 尚未安裝／驗收 |
| OSC 多點 | 一指手把、另一指工具列互不取消 | 尚未安裝／驗收 |
| 輸入回歸 | 觸控板、直接觸控、實體鍵鼠、手把 | 尚未安裝／驗收 |
| 串流回歸 | 60／120 FPS 無功能回歸 | 尚未安裝／驗收 |
| 生命週期 | PiP、背景、鎖屏、中止、斷網不 crash | 尚未安裝／驗收 |
| 卡鍵 | 正常中止或隱藏工具列後 Ctrl／Alt／Shift／Win 全部釋放 | 尚未安裝／驗收 |
| 易用性 | 48dp、明暗背景、放大字級不裁切 | 尚未安裝／驗收 |

## Pinch-to-zoom 說明

Moonlight Android 目前沒有 client 端 pinch-to-zoom：兩指操作在現有 touch context 中用於 mouse scroll，而原生多點觸控 forwarding 程式碼目前被註解。因此關閉觸控板模式也不會讓雙指手勢縮放串流畫面，此項在 v0.2 屬於 `N/A`，不是待修正的工具列回歸。

若目的是放大 Windows 內容，請驗收「Windows」分組中的放大鏡放大、縮小與關閉快捷鍵。真正的串流畫面縮放需另行實作 Surface scale／crop 與反向座標映射，不納入 v0.2。

## v0.1 實機歷史

使用者已在 Fold／Windows 11 實際測試 v0.1，並回報當時功能運作正常。此紀錄只證明 v0.1；v0.2 因新增鎖定修飾鍵與重設選單，必須重新完成上表人工驗收後才能標示 PASS。

## 人工關卡

實機部分只需要使用者在 Fold 上開啟 USB 偵錯、接受 ADB RSA 授權；若沿用相同 release package 與 keystore 覆蓋安裝，既有 App data 通常可保留，仍以實際 package readback 與 Sunshine 連線結果為準。浮動 Samsung Keyboard 模式不列入 v0.2 保證。
