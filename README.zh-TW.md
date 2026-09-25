<h1 align="center">CyIME</h1>

<p align="center">
  <a href="README.md">English</a> · <a href="README.zh-CN.md">简体中文</a>
</p>

**CyIME** 是 Cyletix 維護的 Android 輸入法，基於 [Rime](https://rime.im/) 引擎，提供中文（五筆 / 拼音 / 九宮格 / 雙拼）、日文假名、英文、手寫與語音輸入。

## 專案定位

- 對外身分：應用名稱 **CyIME**，維護者 **Cyletix**，安裝包識別碼 `com.cyletix.cyime`，APK 命名為 `CyIME-<版本>-<abi>.apk`。
- 原始碼衍生自上游 [Xime](https://github.com/ximeiorg/Xime)（作者 Kor1 / kingzcheung 及貢獻者），繼續遵循 [GNU GPL v3](LICENSE)。上游與第三方版權聲明保留在 [LICENSE](LICENSE)、`app/src/main/assets/NOTICE.txt` 與 [TRADEMARKS.md](TRADEMARKS.md)。
- 為了持續追蹤上游、減少合併衝突，原始碼 namespace（`com.kingzcheung.xime`）、外掛 ID、`xime.yaml` / `xime.custom.yaml`、擴充商店與 index 協定維持不變，僅安裝身分（applicationId）獨立。取捨與影響見 [命名與身分記錄](docs/cyime-identity.md)。

## 截圖

<!-- 截圖待補：將截圖放入 docs/Screenshot/ 後在此插入，例如
<table align="center">
  <tr>
    <td><img src="docs/Screenshot/xxx.jpg" width="180"><br><p align="center">說明</p></td>
  </tr>
</table>
-->

## 功能特點

- **多種輸入方案** — 內建霧凇拼音詞庫，提供五筆、拼音與混輸方案，支援自訂（雙拼、筆畫、九鍵 / 14 鍵等），可從方案市場下載或無線匯入
- **Rime 引擎** — 使用成熟穩定的 Rime（librime）引擎
- **豐富鍵盤版面** — QWERTY 全鍵盤、T9 九宮格拼音、九宮格筆畫、手寫、數字（含計算機）、符號與 Emoji
- **懸浮鍵盤** — 懸浮卡片樣式，支援拖曳移動與半透明圓角
- **語音輸入** — 本機離線串流辨識，也可透過外掛接入線上 ASR
- **AI 聯想** — 基於 Transformer 的聯想詞預測（可選）
- **Material 3 介面** — 淺色 / 深色主題與多種配色
- **鍵盤與工具列自訂** — 鍵盤高度 / 位置調整、工具列按鈕自訂、按鍵音效與震動強度
- **滑動手勢** — 游標移動、刪除、符號等滑動手勢
- **剪貼簿管理** — 剪貼簿歷史、快速傳送與置頂，可透過外掛與遠端雙向同步
- **實體鍵盤支援** — 連接實體 / 藍牙鍵盤時顯示浮動候選列
- **外掛擴充** — 透過擴充商店安裝 Lua 外掛（表情、剪貼簿同步、線上語音、WebDAV 備份等）

## 系統需求

- Android 9.0 (API 28) 及以上

## 安裝

### 從 Releases 下載

1. 在 [Releases](https://github.com/Cyletix/CyIME/releases) 下載對應架構的 `CyIME-<版本>-<abi>.apk`：
   - `arm64-v8a`：絕大多數現代手機
   - `armeabi-v7a`：舊款 32 位元手機
   - `x86_64` / `x86`：模擬器
   - `universal`：包含全部架構，體積較大
2. 安裝後在系統設定中啟用 **CyIME**，並設為目前的輸入法。

> **從舊版（`com.kingzcheung.xime`）移轉**：安裝身分已獨立為 `com.cyletix.cyime`，系統視為新應用，可與舊版並存；舊版的資料與設定不會自動繼承，需要重新設定。若 GitHub 下載不穩定，可自行建置（見下）。

### 外掛（可選）

Lua 外掛（`.xipk`）可在主應用程式「設定 > 擴充商店」中安裝與啟用：顏文字、表情包、線上語音辨識（FunAsr / 火山等）、WebDAV 剪貼簿同步與備份等。外掛 ID 與市場協定沿用上游。

## 建置

```bash
# 複製專案（包含子模組）
git clone --recursive https://github.com/Cyletix/CyIME.git

# 已複製過則初始化子模組
git submodule update --init --recursive

# 除錯包 / 發行包
./gradlew assembleDebug
./gradlew assembleRelease
```

## 技術堆疊

Kotlin · Jetpack Compose · Material 3 · Rime (librime) · JNI (Native C++)

## 貢獻

見 [CONTRIBUTING.md](CONTRIBUTING.md)：先提 Issue、最小修改、commit 需 GPG 簽章。

## 致謝

- 上游 [Xime](https://github.com/ximeiorg/Xime)：本分支的起點
- [Rime](https://rime.im/) · [Trime](https://github.com/osfans/trime) · [fcitx5-android](https://github.com/fcitx5-android/fcitx5-android) · [onnxruntime](https://github.com/microsoft/onnxruntime)

## 授權

[GPLv3](LICENSE)。上游程式碼版權歸其作者所有，本分支的修改由 Cyletix 維護。"Xime" 名稱、Logo 及其他品牌資產**不屬於** GPLv3 授權範圍，詳見 [TRADEMARKS.md](TRADEMARKS.md)。
