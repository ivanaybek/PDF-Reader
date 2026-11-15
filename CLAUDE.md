# Ayaz Viewer – yapay zeka için proje rehberi

> Bu dosyayı, bu projede değişiklik yapacak her yapay zeka asistanı (Claude, ChatGPT, Gemini…) **ilk önce** okumalı.
> Claude Code bu dosyayı kendiliğinden okur. Başka bir asistana bu dosyayı ve aşağıdaki "ilgili dosyaları" verin.

## Uygulama
- **Ad:** Ayaz Viewer: PDF ve Excel • **Geliştirici:** TURKMOOD (Google Play kuruluş hesabı)
- **Google Play'de yayında:** https://play.google.com/store/apps/details?id=com.turkmood.pdfreader
- **Paket adı (applicationId):** `com.turkmood.pdfreader` – **asla değiştirmeyin** (Play'deki kalıcı kimlik).
  Kod paketi (namespace) `com.ayaz.viewer` olarak kalır.
- Android uygulaması: Kotlin, Jetpack Compose, Material 3. minSdk 26, targetSdk/compileSdk 36.
- Arayüz **Türkçe**; metinler doğrudan kodda yazılı.
- Bu depo yalnızca bu uygulama içindir. Sahibinin `Search-Movie` deposuna **dokunmayın**.

## Değiştirilmemesi gereken kurallar
- **İnternet izni yok** (`INTERNET`), reklam, analitik, hesap, sunucu yok. CI bunu denetler.
- **Geniş depolama izni yok**; dosyalara yalnızca Storage Access Framework ile erişilir.
- Paylaşım **yalnızca WhatsApp ve Telegram** üzerinden; sistemin genel paylaşım menüsü açılmaz.
- Kullanıcının dosyası **kullanıcı "Kaydet" demeden değişmez**; her kaydetmede "yeni kopya / üzerine yaz" sorulur.
- Bozuk veya desteklenmeyen dosyada uygulama çökmez; Türkçe hata mesajı gösterir.
- Dosya içerikleri loglanmaz.

## Klasörler
```
AyazViewer/
  app/src/main/java/com/ayaz/viewer/
    MainActivity.kt, AyazApp.kt
    ui/        ana ekran (home, recents, files, settings), tema, simgeler, Oluştur penceresi
    viewer/    görüntüleyici: pdf/ (PdfController, PdfViewerScreen, PdfEditor, PdfCreator),
               xlsx/ (XlsxController, SheetGrid, XlsxViewerScreen), ViewerViewModel, SaveSupport
    data/      son açılanlar, ayarlar, dosya erişimi (DocumentAccess), FileOpener, klasörler
    share/     WhatsApp/Telegram paylaşım penceresi
    core/      dosya türü tespiti, Türkçe hata mesajları, biçimlendirme
  app/src/androidTest/   cihaz (emülatör) testleri
  xlsx/                  saf Kotlin XLSX okuma/düzenleme motoru + JVM testleri
  smoketest/             yayın (R8) sürümü uçtan uca testi + mağaza ekran görüntüleri
  magaza/                Play Store metinleri, simge, görseller, ekran görüntüleri
  yayin/                 imzalı AAB/APK (CI üretir; elle düzenlemeyin)
  app/build.gradle.kts   sürüm numarası, imzalama, applicationId
.github/workflows/ayaz-viewer.yml   derleme + testler + yayın paketi
```

## Değişiklik türüne göre okunacak dosyalar
| Değişiklik | Okunacak dosyalar |
|---|---|
| Ana sayfa / menüler | `ui/home/HomeScreen.kt`, `ui/main/MainScreen.kt` |
| PDF görüntüleme/düzenleme | `viewer/pdf/PdfViewerScreen.kt`, `PdfController.kt`, `PdfEditor.kt`, `viewer/ViewerViewModel.kt` |
| Excel görüntüleme/düzenleme | `viewer/xlsx/*.kt`, `xlsx/src/main/kotlin/com/ayaz/viewer/xlsx/*.kt` |
| PDF/Excel oluşturma | `ui/main/CreateUi.kt`, `ui/main/MainViewModel.kt`, `viewer/pdf/PdfCreator.kt` |
| Paylaşım | `share/ShareTargets.kt`, `share/ShareSheet.kt` |
| Ayarlar | `ui/settings/SettingsScreen.kt`, `data/SettingsRepository.kt` |
| Renkler / tema | `ui/theme/Theme.kt` |
| Hata mesajları | `core/ErrorMessages.kt` |
| Mağaza metinleri | `AyazViewer/magaza/README.md` |

(Yollar `AyazViewer/app/src/main/java/com/ayaz/viewer/` altına göredir.)

## Derleme ve test
- Testler GitHub Actions'ta otomatik çalışır (`.github/workflows/ayaz-viewer.yml`): XLSX ve uygulama birim
  testleri, Android 13 ve 15 emülatör testleri, yayın sürümü uçtan uca testi (`smoketest`), 16 KB uyumluluğu.
- Her değişiklikte **bütün işler yeşil olmalı**. Bir testi geçmesi için kapatmayın veya atlamayın; hatayı düzeltin.
- Arayüz metni değişirse `app/src/androidTest` ve `smoketest` içindeki aynı metni arayan testleri de güncelleyin.
- Yerelde: `cd AyazViewer && ./gradlew :xlsx:test :app:testDebugUnitTest :app:assembleDebug`
  (JDK 17 ve Android SDK gerekir).

## Google Play güncellemesi yayınlamak
1. `AyazViewer/app/build.gradle.kts` içinde **`versionCode`'u 1 artırın** (her yüklemede zorunlu) ve
   `versionName`'i güncelleyin (ör. `1.0.0` → `1.0.1`).
2. Değişikliği `main` dalına gönderin. Testler geçince CI, sahibinin anahtarıyla imzalı
   `AyazViewer/yayin/AyazViewer-<sürüm>.aab` dosyasını depoya ekler. (İmza anahtarı GitHub sırlarında:
   `AYAZ_KEYSTORE_BASE64`, `AYAZ_KEYSTORE_PASSWORD`, `AYAZ_KEY_ALIAS`, `AYAZ_KEY_PASSWORD`. Anahtar dosyası
   depoda değildir ve asla depoya eklenmemelidir.)
3. Sahibi bu AAB'yi Play Console → Üretim → Yeni sürüm ile yükler. "Yenilikler" metnini Türkçe yazın.

Ayrıntılar: `AyazViewer/YAYINLAMA.md`, kullanım: `AyazViewer/KULLANIM.md`,
teknik notlar: `AyazViewer/GELISTIRICI.md`, test sonuçları: `AyazViewer/TEST_RAPORU.md`.
