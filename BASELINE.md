# Moonlight KATO 基準紀錄

## 原始碼基準

- 上游 repository：`https://github.com/moonlight-stream/moonlight-android.git`
- 上游 commit：`f10085f552b367cf7203007693d91c322a0a2936`
- 本機分支：`feature/pc-key-toolbar`
- GitHub Fork／遠端推送：無
- `moonlight-common-c`：`8af4562af672dd6b9ed28553ead172984fd9a683`
- `enet`：`d3a323fc8b9559786ee059205db74e8027c756f7`

## 範圍界線

KATO v0.1 功能只在 Android `nonRoot` client 啟用。不修改 Sunshine、Moonlight native code、串流協定、root flavor 的身分與既有輸入路徑、`namespace 'com.limelight'` 或 Java package。共用 `main` source set 中的 UI class 與資源仍會參與 root 編譯，但 root runtime 會移除偏好項目、拒絕掛接 listener，並讓工具列永久隱藏；`rootDebug` 已成功建置且版本維持 `12.1`。

## App 身分

| Variant | Package | App 名稱 | versionName | versionCode |
|---|---|---|---|---:|
| `nonRootDebug` | `tw.kato.moonlight.debug` | Moonlight KATO Dev | `12.1-kato.0.1-dev` | 314001 |
| `nonRootRelease` | `tw.kato.moonlight.unofficial` | Moonlight KATO | `12.1-kato.0.1` | 314001 |

新 application ID 使用獨立 App data；首次連線必須重新與 Sunshine 配對並重新設定串流選項。

## 建置收據

2026-08-15 以獨立 detached worktree 在未修改的 `f10085f` 執行 `:app:assembleNonRootDebug`，結果為 `BUILD SUCCESSFUL`（45 tasks）。工具鏈版本與 KATO 驗證結果記錄在 [BUILD_WINDOWS.md](BUILD_WINDOWS.md)。
