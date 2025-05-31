# KR-Platform Bot

Google カレンダーと連携して予定を Twitter に自動投稿する Spring Boot アプリケーション

## 機能

- Google カレンダーから今日・明日の予定を取得
- 予定を Twitter に投稿
- JWT 認証による API セキュリティ

## セットアップ

### 1. 必要な環境

- Java 21
- Gradle 8.x

### 2. API キーの設定

1. `.env.example` を `.env` にコピー
```bash
cp .env.example .env
```

2. `.env` ファイルに以下の情報を設定：
```
# Twitter API設定
TWITTER_CONSUMER_KEY=your_twitter_consumer_key
TWITTER_CONSUMER_SECRET=your_twitter_consumer_secret
TWITTER_ACCESS_TOKEN=your_twitter_access_token
TWITTER_ACCESS_TOKEN_SECRET=your_twitter_access_token_secret
TWITTER_BEARER_TOKEN=your_twitter_bearer_token
```

3. Google Calendar API の認証情報
- Google Cloud Console で認証情報（OAuth 2.0）を作成
- `credentials.json` をプロジェクトルートの `src/main/resources/` に配置

### 3. アプリケーションの起動

```bash
# 環境変数を読み込んで起動
source .env
./gradlew bootRun
```

または、環境変数を直接指定：
```bash
TWITTER_CONSUMER_KEY=xxx TWITTER_CONSUMER_SECRET=xxx ./gradlew bootRun
```

## 使用方法

### Google カレンダー認証
1. ブラウザで `http://localhost:8888/auth/google` にアクセス
2. Google アカウントでログインして権限を許可

### API エンドポイント

- `GET /test-today` - 今日の予定を取得
- `GET /test-tomorrow` - 明日の予定を取得
- `POST /tweet` - カスタムツイートを投稿
- `POST /tweet-schedule` - 今日の予定をツイート

## 注意事項

- Twitter API の無料プランでは月 1,500 ツイートまでの制限があります
- 15分間のレート制限にも注意してください
- `credentials.json` と `.env` ファイルは絶対に Git にコミットしないでください

## トラブルシューティング

### 429 Too Many Requests エラー
- Twitter API のレート制限に達しています
- 15分〜数時間待ってから再試行してください

### 401 Unauthorized エラー
- Google 認証の有効期限が切れています
- `/auth/google` から再認証してください