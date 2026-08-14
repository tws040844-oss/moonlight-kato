# Moonlight KATO 變更記錄

## 0.2.0（實機驗收中）

- 版本更新為 Release `12.1-kato.0.2`、Debug `12.1-kato.0.2-dev`，versionCode `314002`；package ID 維持不變。
- 主工具列新增 Ctrl、Shift 與 Caps。Ctrl／Shift 可點按鎖定，再套用到後續 Samsung Keyboard、工具列或實體輸入；Caps 送出單次 Caps Lock 事件，交由 Windows 控制英文大小寫狀態。
- 重新設計快捷鍵選單，依「Windows、視窗、編輯、系統」分組：
  - Windows：Win+E、Win+R、Win+D、Win+Tab、Windows 放大鏡放大／縮小／關閉。
  - 視窗：Alt+Tab、Alt+Shift+Tab。
  - 編輯：Ctrl+A、Ctrl+C、Ctrl+X、Ctrl+V、Ctrl+Z、Ctrl+Y。
  - 系統：Ctrl+Shift+Esc（工作管理員）、本次串流隱藏。
- 強化 synthetic modifier ownership：快捷鍵不會提前放開已鎖定的 Ctrl／Shift，並區分左右實體 Ctrl／Shift，避免右側實體鍵錯誤接管 synthetic 左側鍵的釋放責任。
- 在隱藏工具列、觸控取消、失焦、暫停與結束串流等生命週期事件清理 KATO 鎖定狀態，降低遠端卡鍵風險。
- 單元測試擴充為 24 tests，涵蓋鎖定 modifier、實體鍵 ownership、Windows 放大鏡、KEY_UP 失敗重試、斷線清理、連續及併發序列化。
- 最終 Release APK 已完成靜態驗證：7,990,614 bytes，SHA-256 `0825436FF0E54DEDD763779F7EA923ED90EE3E2A1B8D2A50FD1FB09E9AB0A556`，v1／v2 簽章通過。
- 說明 Moonlight Android 現階段不支援 client 端 pinch-to-zoom：兩指手勢由既有路徑作為 mouse scroll，原生多點觸控 forwarding 程式碼目前被註解。v0.2 提供 Windows 放大鏡快捷鍵作為內容放大替代方案，未修改串流 Surface 或座標映射。

v0.2 尚未安裝至 Fold／Windows 11 實機，不標示人工驗收 PASS。v0.1 已由使用者實際測試並確認當時功能正常。

## 0.1.0

- 增加可拖曳並停靠左右側的 PC 特殊鍵浮鈕與收合工具列。
- 增加 Samsung Keyboard、Win、Esc、Tab、四向鍵。
- 增加 Win+E、Win+R、上一個視窗、工作管理員固定快捷鍵。
- 增加 synthetic modifier ownership 與生命週期清理，避免提前放開實體修飾鍵。
- 在失焦、PiP、解除輸入擷取與正常中止時，盡力釋放工具列擁有的 synthetic modifier；異常斷線僅清本機狀態。
- 修正連線建立中離開 Activity 與晚到 callback 的輸入 readiness 競態。
- 增加 KATO 獨立 package、版本、名稱與 release 簽章設定。
- 增加 PC 特殊鍵工具列偏好設定，預設啟用。

v0.1 僅支援單次點按，不含長按、Sticky modifier、自訂巨集或 Samsung Keyboard 顯示狀態偵測。
