# リリース手順

Mobile Moodleは、`vX.Y.Z`形式のタグがGitHubへpushされたときに、Android APKとiOS IPAをビルドしてGitHub Releaseへ公開します。リリース成果物はどちらも正式な配布用署名を必須とし、署名情報が不足している場合は公開前にワークフローを停止します。

## 1. GitHub Actions Secrets

リポジトリの **Settings → Secrets and variables → Actions** へ、次のRepository secretsを登録します。

### Android

| Secret | 内容 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | 配布用JKS／keystoreファイルを改行なしBase64にした値 |
| `ANDROID_KEYSTORE_PASSWORD` | keystoreのパスワード |
| `ANDROID_KEY_ALIAS` | 配布用キーのalias |
| `ANDROID_KEY_PASSWORD` | 配布用キーのパスワード |

Androidの配布用キーは、アプリの更新に継続して必要です。安全な場所へバックアップし、リポジトリ、Issue、Actionsログへ貼り付けないでください。

### iOS

| Secret | 内容 |
| --- | --- |
| `IOS_DISTRIBUTION_CERTIFICATE_BASE64` | Apple Distribution証明書と秘密鍵を含む`.p12`の改行なしBase64 |
| `IOS_DISTRIBUTION_CERTIFICATE_PASSWORD` | `.p12`の書き出しパスワード |
| `IOS_PROVISIONING_PROFILE_BASE64` | `org.moodle.ios`に対応するProvisioning Profileの改行なしBase64 |
| `IOS_TEAM_ID` | Apple Developer Team ID |

既定のIPA export methodは`release-testing`です。これは登録済み端末向けの配布用Provisioning Profileを想定します。App Store Connect用Profileを使う場合は、Repository variable `IOS_EXPORT_METHOD`を`app-store-connect`へ設定してください。Enterprise配布の場合は`enterprise`を指定できます。

macOSでファイルを改行なしBase64へ変換する例です。値はターミナルへ表示せず、クリップボードへ直接渡します。

```bash
base64 -i release-key.jks | tr -d '\n' | pbcopy
base64 -i distribution-certificate.p12 | tr -d '\n' | pbcopy
base64 -i MobileMoodle.mobileprovision | tr -d '\n' | pbcopy
```

## 2. リリース前の確認

`main`のCIが成功しており、バージョンに含めるコミットがpush済みであることを確認します。タグの`X.Y.Z`はAndroidの`versionName`とiOSの`MARKETING_VERSION`へ自動的に設定され、GitHub Actionsのrun numberがAndroidの`versionCode`とiOSのbuild numberになります。

```bash
git switch main
git pull --ff-only
git status --short
```

## 3. タグを作成して公開

例として`v1.0.0`を公開する場合は、注釈付きタグを作成してpushします。

```bash
git tag -a v1.0.0 -m "Mobile Moodle v1.0.0"
git push origin v1.0.0
```

GitHub Actionsの **Release** ワークフローは次を実行します。

1. タグ形式と必要な署名Secretsを検証する。
2. Androidのテストとlintを実行し、署名済みrelease APKを生成・検証する。
3. iOSのUnit Testを実行し、配布署名済みarchiveからIPAを生成・検証する。
4. APK、IPA、`SHA256SUMS.txt`を同じGitHub Releaseへ公開する。

成果物名は`mobile-moodle-vX.Y.Z.apk`と`mobile-moodle-vX.Y.Z.ipa`です。失敗したタグを作り直す場合は、原因を修正した新しいバージョンタグを使用してください。公開済みタグを書き換えないでください。
