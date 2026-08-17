# Mobile Moodle

汎用 Moodle サイトへ Android 端末から直接接続するクライアントです。独自の中継サーバーは使用しません。

## 接続モード

- Moodle モバイル Web サービスが有効なサイトでは、コース、教材、課題、成績、予定、通知、メッセージをネイティブ画面で利用できます。
- モバイル Web サービスが無効なサイトでも、ログイン後の HTML を端末内で解析し、コース、教材、読み取り専用の課題、成績、予定、通知を Jetpack Compose の独自 UI で表示します。メッセージは HTML から取得した認証済みセッションで Moodle 標準 AJAX へ直接接続します。
- ページ、ファイル、フォルダは可能な限りアプリ内で構造化表示します。URL、クイズ、フォーラム、独自プラグインなど安全に構造化できない画面だけ外部ブラウザで開きます。
- サイト URL は初回接続時に利用者が入力します。特定組織の URL や認証情報はアプリに組み込まれていません。

HTML モードは Moodle 3.9〜5.x の標準的な Boost／Classic 構造と、CSS クラス名に依存しすぎない構造フォールバックを対象にしています。サイト独自テーマで必要な要素を取得できない場合、取得済みキャッシュを維持したままエラーを表示します。HTML モードの課題提出は行いませんが、サイトが対応していれば Moodle 標準 AJAX によるメッセージ送信と既読変更を利用できます。

## メッセージと同期

- 既存の個人・グループ会話の閲覧と返信、ユーザー検索、新規個人メッセージに対応します。
- 会話と本文はアカウント単位で分離して保存し、オフライン時は読み取り専用で表示します。失敗した本文は下書きとして保持し、自動送信は行いません。
- 新着は WorkManager で 15 分ごとに確認します。独自サーバーを使わないため、リアルタイムプッシュではありません。
- Android 通知は既定で送信者名だけを表示し、本文プレビューは設定から明示的に有効化できます。

## ビルド

```bash
cd android
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Android Studio から `android` ディレクトリを開くこともできます。必要環境は JDK 17 以上、Android SDK 37、minSdk 24 です。

## ローカル実サイトテスト

`android/local-test.properties.example` を `android/local-test.properties` にコピーし、手元のテスト専用 Moodle アカウントを設定します。実ファイルは Git で無視され、テストはログインと閲覧だけを行います。

ローカル値が追跡対象へ混入していないことは次のコマンドで確認できます。

```bash
MOODLE_TEST_SITE='...' \
MOODLE_TEST_USERNAME='...' \
MOODLE_TEST_PASSWORD='...' \
./android/scripts/check-no-local-secrets.sh
```

## セキュリティ

- HTTPS のみを許可し、無効な TLS 証明書や混在コンテンツを受け入れません。
- パスワードは保存せず、API トークンと HTML セッション Cookie は Android Keystore の鍵で AES-GCM 暗号化します。
- HTML 通信はアカウントごとに Cookie を分離し、入力した Moodle の HTTPS オリジンおよびサブディレクトリ外へのリダイレクトを拒否します。
- アプリは WebView を使用しません。HTML は Jsoup でサニタイズしてからデータモデルへ変換し、Compose で描画します。
- HTTP 本文、トークン、Cookie をログへ出力しません。
