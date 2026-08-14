# Moonlight KATO 變更記錄

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
