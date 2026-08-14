# Moonlight KATO v0.1 測試矩陣

狀態標記：`PASS`、`FAIL`、`BLOCKED`、`N/A`。

## 自動化測試

| 項目 | 預期結果 | 狀態 |
|---|---|---|
| Win 單鍵 | DOWN／UP，BASE 含 META 時零事件 | PASS |
| Win+E／Win+R | owned META 正確包住主鍵 | PASS |
| Alt+Tab | owned ALT 正確包住 Tab | PASS |
| Ctrl+Shift+Esc | owned modifier 依相反順序釋放 | PASS |
| BASE 含相同 modifier | 不重送、不提前釋放實體 modifier | PASS |
| 未連線／translator=0 | 零輸入事件 | PASS |
| sender 失敗 | `finally` 盡力反向清除 owned modifier | PASS |
| 連續 100 次 | 不交錯、不殘留 synthetic state | PASS |
| `releaseAll()` 冪等性 | 重複呼叫不增加事件 | PASS |
| JUnit | 14 tests／0 failures／0 errors／0 skipped | PASS |
| Lint | 工作成功；187 個既有 warning，KATO 新增檔案 0 個 issue | PASS |
| Debug／Release | nonRoot 兩種 APK 均成功建置 | PASS |
| Root flavor 回歸 | Root Debug 成功建置，versionName 維持 `12.1` | PASS |

## Fold／Windows 11 實機驗收

| 區域 | 驗收項目 | 狀態／證據 |
|---|---|---|
| 並存 | 官方、KATO Debug、KATO Release 同時安裝 | 等待裝置 |
| 輸入 | Win、Esc、Tab、四向鍵與四個快捷鍵 | 等待裝置 |
| IME | 三指與工具列按鈕切換 Samsung Keyboard | 等待裝置 |
| 中文輸入 | 標準／分割鍵盤中英文輸入 | 等待裝置 |
| Fold | 內外螢幕、折疊、展開、橫直向後仍可見 | 等待裝置 |
| 觸控隔離 | 拖曳／點擊不產生遠端滑鼠事件 | 等待裝置 |
| OSC 多點 | 一指手把、另一指工具列互不取消 | 等待裝置 |
| 輸入回歸 | 觸控板、直接觸控、實體鍵鼠、手把 | 等待裝置 |
| 串流回歸 | 60／120 FPS 無功能回歸 | 等待裝置 |
| 生命週期 | PiP、背景、鎖屏、中止、斷網不 crash | 等待裝置 |
| 卡鍵 | 正常中止後 Ctrl／Alt／Shift／Win 全部釋放 | 等待裝置 |
| 易用性 | 48dp、明暗背景、放大字級不裁切 | 等待裝置 |

## 人工關卡

實機部分只需要使用者在 Fold 上開啟 USB 偵錯、接受 ADB RSA 授權，以及首次使用 KATO App 輸入 Sunshine 配對 PIN。浮動 Samsung Keyboard 模式不列入 v0.1 保證。
