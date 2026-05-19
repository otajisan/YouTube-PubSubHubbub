# YouTube PubSubHubbub Subscriber

YouTube チャンネルの新着動画を PubSubHubbub (WebSub) 経由で受信する、ローカル起動可能な Spring Boot サンプル。

- Kotlin 2.1 + Spring Boot 3.4 + Spring MVC + Thymeleaf
- SQLite による永続化
- WireMock + JUnit5 によるテスト

実装プランは Issue #1 を参照。

## 仕組み

```
[Browser] ─ HTTP ─▶ [Spring Boot App] ─ HTTP ─▶ [PubSubHubbub Hub] ◀─ YouTube
                          │  ▲                       │
                          │  └──── GET/POST callback ┘
                          ▼
                       SQLite (./data/app.db)
```

1. 画面からチャンネル ID を登録すると、アプリは Hub (`https://pubsubhubbub.appspot.com/subscribe`) に `subscribe` リクエストを送る。
2. Hub は登録時に指定したコールバック URL に GET (検証) を投げてくるので、`hub.challenge` をエコーバックして購読を確立する。
3. チャンネルに新着動画 / 更新が発生すると、Hub が Atom XML を POST してくる。`X-Hub-Signature` を HMAC で検証してから DB に保存。
4. リースは最大 10 日。スケジューラが期限 1 日前に再 subscribe する。

## 必要環境

- JDK 21
- Gradle 8.12+ (Wrapper 同梱)
- [ngrok](https://ngrok.com/) など、ローカルポートをインターネットに露出できるツール

## クイックスタート (Hub 連携なし)

UI と DB だけ動かして触りたい場合:

```bash
./gradlew bootRun
```

- http://localhost:8080/ — チャンネル登録 / 一覧
- http://localhost:8080/notifications — 受信通知一覧
- http://localhost:8080/actuator/health — ヘルスチェック

チャンネル ID は `UC` で始まる 24 文字 (例: `UCBR8-60-B28hp2BmDPdntcQ`)。
Hub への到達ができない環境では、ステータスは `FAILED` になります (通知も来ません)。

## 実 Hub と接続する手順 (ngrok)

1. アプリを起動:
   ```bash
   ./gradlew bootRun
   ```
2. 別タームで ngrok でポート 8080 を公開:
   ```bash
   ngrok http 8080
   ```
   `Forwarding https://xxxxxxxx.ngrok-free.app -> http://localhost:8080` の URL をメモる。
3. アプリを ngrok URL で再起動:
   ```bash
   CALLBACK_BASE_URL=https://xxxxxxxx.ngrok-free.app ./gradlew bootRun
   ```
4. http://localhost:8080/ でチャンネル ID を登録すると、Hub にリクエストが飛び、数秒〜数分で `PENDING` → `ACTIVE` に変わる。
5. 該当チャンネルに新着が発生すると、http://localhost:8080/notifications に行が積まれる。

> **注意**: `CALLBACK_BASE_URL` は Hub から到達可能な HTTPS でなければなりません。ngrok 無料プランの URL は再起動のたびに変わる仕様に注意。

## 設定値

`src/main/resources/application.yml`

| キー | デフォルト | 説明 |
|---|---|---|
| `app.callback.base-url` | `http://localhost:8080` | Hub にコールバック URL として通知する FQDN。`CALLBACK_BASE_URL` で上書き可。 |
| `app.hub.url` | `https://pubsubhubbub.appspot.com/subscribe` | Hub のエンドポイント。 |
| `app.hub.default-lease-seconds` | `864000` (10 日) | subscribe 時に要求するリース期間。 |
| `spring.datasource.url` | `jdbc:sqlite:./data/app.db` | SQLite ファイルパス。 |

## データ永続化

SQLite ファイルは `./data/app.db`。スキーマは Hibernate `ddl-auto: update` で初回起動時に自動生成される。

- `subscriptions`: 登録チャンネル、購読状態、`callback_token`、`hub_secret`、リース情報
- `notifications`: 受信した動画通知 (`video_id`, `title`, `link`, `published_at`, `updated_at`, `raw_xml`)

DB を初期化したい場合は `rm -f data/app.db` で良い。

## テスト

```bash
./gradlew test
```

- `AtomFeedParserTest` — Atom XML パース
- `SubscriptionRepositoryTest` — JPA エンティティの永続化
- `SubscriptionHubFlowTest` — WireMock で subscribe フォーマット検証
- `PubSubCallbackControllerTest` — `GET /callback/{token}` 検証 / `POST /callback/{token}` 通知受信 + HMAC
- `SubscriptionRenewalJobTest` — リース更新ジョブ

## ディレクトリ構成

```
src/main/kotlin/jp/fout/ytpubsubhubbub/
├── YtPubsubhubbubApplication.kt
├── config/                      # AppProperties
├── application/
│   ├── web/                     # ChannelViewController, NotificationViewController
│   └── callback/                # PubSubCallbackController
├── domain/
│   ├── subscription/            # Entity / Repository / Service / Status
│   └── notification/            # Entity / Repository / Service
├── infrastructure/
│   ├── hub/                     # PubSubHubbubClient, HmacVerifier
│   └── youtube/                 # AtomFeedParser
└── scheduler/                   # SubscriptionRenewalJob
```

## トラブルシュート

| 症状 | 原因 / 対処 |
|---|---|
| 登録後すぐに `FAILED` になる | `CALLBACK_BASE_URL` が `http://localhost:...` のまま、または到達不能。ngrok URL を確認。 |
| 検証 GET は来るが challenge が一致しないと言われる | リバースプロキシで URL がリライトされている可能性。ngrok 経由で素直に届けること。 |
| `POST /callback` は受けているが通知が保存されない | `X-Hub-Signature` の HMAC が一致していない。`hub_secret` がアプリ起動間で揺れていないかを確認 (DB は永続化される)。 |
| `data/app.db` が見当たらない | 初回起動時にカレントワーキングディレクトリ直下の `data/` に作成される。`pwd` を確認。 |

## ライセンス

未指定 (サンプル用途)。
