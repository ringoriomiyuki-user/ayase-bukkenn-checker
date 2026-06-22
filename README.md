# ayase-bukkenn-checker

UR賃貸住宅の空室を自動でチェックし、空室が出たら [ntfy](https://ntfy.sh/) でスマホに通知するツール。

## 監視対象

| 物件 | danchi | 頻度 | 目的 |
|------|--------|------|------|
| 東綾瀬パークタウン | 700 | 30分ごと | 本命 |
| 大谷田団地 | 281 | 1日1回 | テスト用(空室が出やすく動作確認になる) |

## しくみ

URの部屋一覧API (`detail_bukken_room`) をPOSTで叩き、レスポンスで判定する。

- レスポンスが `null` → 空室なし → 何もしない
- それ以外(部屋データあり) → 空室あり → ntfyに通知

GitHub Actions の cron で定期実行するので、サーバーは不要・無料。

## セットアップ

### 1. ntfyの準備

1. スマホに **ntfy** アプリをインストール (iOS / Android / ブラウザ)
2. アプリで好きなトピックを購読する。トピック名は **推測されにくい文字列**にすること
   (トピック名を知っていれば誰でも購読・送信できる仕様のため)
   - 例: `ayase-bukkenn-checker`

### 2. GitHubにSecretsとしてトピック名（topic名）を登録

リポジトリの **Settings → Secrets and variables → Actions → New repository secret**

| Name | Value |
|------|-------|
| `NTFY_TOPIC` | あなたのntfyトピック名 (例: `ayase-bukkenn-checker`) |

### 3. 完了

以後、スケジュールに従って自動でチェックが走る。
各ワークフローは **Actionsタブから手動実行(Run workflow)** でも動作確認できる。

## ローカルでの実行(任意)

```bash
javac src/VacancyChecker.java -d out
NTFY_TOPIC=あなたのトピック名 \
  java -cp out VacancyChecker 700 "東綾瀬パークタウン" \
  https://www.ur-net.go.jp/chintai/kanto/tokyo/20_7000.html
```

## 注意

- URのcronはGitHub混雑時に数分〜数十分遅延することがある。
- 空室が出続けている間は実行のたびに通知が来る(変化検知は未実装)。
