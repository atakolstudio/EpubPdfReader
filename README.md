# Kitaplık — EPUB & PDF Reader (Android)

Kotlin + Jetpack Compose + Material3 ile yazılmış, SDK 36 (Android 16) hedefli
EPUB/PDF okuyucu uygulaması.

## Özellikler
- **PDF görüntüleme**: Android'in yerleşik `PdfRenderer` API'si (üçüncü parti
  kütüphane yok), sayfa sayfa kaydırmalı görüntü.
- **EPUB görüntüleme**: EPUB (zip) içindeki `container.xml` / `.opf` dosyaları
  ayrıştırılır, bölümler `WebView` + `WebViewAssetLoader` ile (yerel dosyalara
  güvenli erişim, `file://` açığı olmadan) gösterilir. Bölüm ileri/geri gezinme.
- **İzin gerektirmez**: Dosyalar Storage Access Framework (`ACTION_OPEN_DOCUMENT`)
  ile açılır — bu, Android'in scoped storage sonrası önerdiği güncel yöntemdir.
  `READ_EXTERNAL_STORAGE` / `READ_MEDIA_*` gibi hiçbir tehlikeli izin istenmez.
- **Kotlin marka temalı arayüz**: Material3, dinamik renk (Android 12+ Material You)
  desteği, açık/koyu tema, mor (#7F52FF) Kotlin renk paleti.
- **Eksiksiz adaptive icon**: arka plan + ön plan + monochrome (temalı ikon,
  Android 13+) + round varyant, tamamı vektör (her ekran yoğunluğunda keskin).
- **Güncel Android kuralları**: edge-to-edge zorunluluğu (`enableEdgeToEdge`),
  predictive back (`enableOnBackInvokedCallback`), per-app dil desteği
  (`localeConfig`), minSdk 26 / target-compile SDK 36.
- Dosya ile doğrudan açma: sistemde bir PDF/EPUB'a "Birlikte aç" dendiğinde
  bu uygulama listede çıkar (VIEW intent-filter).

## Projeyi Açma / Derleme
1. Android Studio (Ladybug veya üzeri, AGP 8.7+ ve SDK 36 platformu kurulu) ile
   bu klasörü açın.
2. Gradle senkronizasyonu otomatik başlar (Kotlin DSL — `build.gradle.kts`).
3. `Run ▶` ile cihaz/emülatörde çalıştırın, ya da
   `Build > Generate Signed App Bundle / APK` ile release APK üretin.

Komut satırından derlemek isterseniz (Gradle kurulu bir makinede):
```bash
gradle wrapper --gradle-version 8.11.1   # gradlew dosyalarını oluşturur
./gradlew assembleDebug
# APK çıktısı: app/build/outputs/apk/debug/app-debug.apk
```

> **Not:** Bu paket, izole/ağ kısıtlı bir ortamda hazırlandığı için Google'ın
> Maven deposuna ve Gradle dağıtım sunucusuna erişim yoktu; bu yüzden zip içinde
> **derlenmiş bir .apk bulunmuyor**, sadece tam kaynak kodu var. Android Studio'da
> açıp "Run" dediğinizde birkaç dakika içinde kendi APK'nızı üretebilirsiniz.

## GitHub Actions ile Otomatik APK Derleme

Proje `.github/workflows/android-build.yml` içeriyor. Yapmanız gereken:

1. Bu klasörü bir GitHub deposuna push edin (repo boşsa: `git init && git add . && git commit -m "init" && git remote add origin <repo-url> && git push -u origin main`).
2. GitHub'da **Actions** sekmesine gidin — "Android CI - Build APK" iş akışı otomatik tetiklenir (push/PR/manuel `Run workflow`).
3. İş bitince açılan run sayfasının altındaki **Artifacts** bölümünden:
   - `app-debug` → doğrudan cihaza kurulabilir, imzalı debug APK
   - `app-release-unsigned` → imzasız release APK (yayınlamadan önce kendi anahtarınızla imzalamanız gerekir)
   dosyalarını indirebilirsiniz.

İş akışı `gradlew` yerine `gradle/actions/setup-gradle` ile Gradle 8.11.1'i doğrudan kurup çalıştırır, bu yüzden ayrıca wrapper jar dosyasına ihtiyaç duymaz.


```
com.dgs.readerapp
 ├─ MainActivity.kt        (tek Activity, ekranlar arası basit state navigasyonu)
 ├─ HomeScreen.kt          (PDF/EPUB seçim ekranı, SAF launcher'ları)
 ├─ pdf/PdfViewerScreen.kt (PdfRenderer ile sayfa render)
 ├─ epub/EpubParser.kt     (OPF/spine ayrıştırma, zip açma)
 ├─ epub/EpubViewerScreen.kt (WebView + WebViewAssetLoader)
 └─ ui/theme/              (Color.kt, Type.kt, Theme.kt — Kotlin marka teması)
```
