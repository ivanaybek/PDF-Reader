# Ayaz Viewer

Kişisel kullanım için, tamamen çevrimdışı çalışan **PDF ve Excel (XLSX) görüntüleyici** Android uygulaması.
Hesap, abonelik, reklam, analitik, sunucu ve internet izni yoktur.

- Paket adı: `com.ayaz.viewer`
- Sürüm: 1.0.0 (versionCode 1)
- Android 8.0+ (minSdk 26) üzerinde çalışır; hedef: Android 13, 14, 15 ve 16 (targetSdk/compileSdk 36)

---

## 1. Kullanılan teknolojiler

| Alan | Seçim | Neden |
|---|---|---|
| Dil / UI | Kotlin 2.1, Jetpack Compose, Material 3 | Native, modern, akıcı arayüz |
| Derleme | Gradle 8.14.3 (wrapper), Android Gradle Plugin 8.10.1 | Standart Android Studio projesi |
| PDF | Android `PdfRenderer` (sistem bileşeni) | Ek SDK gerektirmez, çevrimdışı |
| PDF arama | `PdfRenderer.Page.searchText()` | Android 15+ (veya güncel sistem PDF bileşeni) |
| PDF düzenleme/oluşturma | [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android) 2.0.27 (Apache 2.0) | Çevrimdışı; işaretlemeleri sayfaya çizer, sayfa döndürür/siler, metinden PDF üretir |
| Yazı tipi | DejaVu Sans (Bitstream Vera lisansı, `assets/fonts`) | PDF'e Türkçe karakterli metin yazmak için |
| XLSX | Projeye özel `:xlsx` modülü (saf Kotlin, ZIP + akışlı XML) | Apache POI gibi ağır kitaplıklar yerine hızlı ve hafif |
| Dosya erişimi | Storage Access Framework (SAF), `FileProvider` | Geniş depolama izni gerekmez |
| Veri saklama | Uygulama içi JSON (`AtomicFile`), `SharedPreferences` | Ek veritabanı kitaplığı yok |

Reklam, analitik veya ağ kullanan hiçbir SDK yoktur. AndroidX/Compose ve Kotlin coroutines dışında tek kitaplık,
PDF düzenleme için gereken açık kaynak PdfBox-Android'dir (internet izni yoktur; CI her derlemede doğrular).

### Proje yapısı

```
AyazViewer/
├── app/                         Android uygulaması
│   └── src/main/java/com/ayaz/viewer/
│       ├── MainActivity.kt      Ana ekran (alt menü: Ana Sayfa, Son Açılanlar, Dosyalar, Ayarlar)
│       ├── viewer/              Görüntüleyici etkinliği
│       │   ├── pdf/             PdfController (tembel çizim + LRU önbellek), PdfViewerScreen
│       │   └── xlsx/            XlsxController, SheetGrid (Canvas tablo), XlsxViewerScreen
│       ├── data/                Son açılanlar, ayarlar, SAF erişimi, klasörler, dosya açıcı
│       ├── share/               WhatsApp/Telegram paylaşım penceresi ve mantığı
│       └── ui/                  Tema, simgeler, ekranlar ve ortak bileşenler
├── xlsx/                        Bağımsız XLSX okuma motoru (JVM'de test edilebilir)
├── tools/generate_samples.py    Test dosyası üretici (PDF + XLSX)
└── samples/                     Telefonda elle denemek için örnek dosyalar
```

---

## 2. Özellikler

**Arayüz**
- Varsayılan koyu tema (gerçek siyaha yakın zemin), açık tema ve “sistemi izle” seçeneği
- Mavi–mor vurgu renkleri, yuvarlatılmış kartlar, hafif animasyonlar, özgün dosya türü simgeleri
- Ana sayfada büyük **Dosya Aç** düğmesi, PDF/Excel kısayolları, İndirilenler kısayolu
- Türkçe arayüz ve Türkçe hata mesajları

**PDF görüntüleyici**
- Çok sayfalı belgeler, dikey kaydırma, iki parmakla yakınlaştırma (1×–5×), yakınlaştırılmışken sürükleme ve savurma, çift dokunuşla yakınlaştırma
- Sayfa göstergesi (“3 / 120”), sayfaya git
- Son görüntülenen sayfayı hatırlama (Ayarlar’dan kapatılabilir)
- Belgede metin arama ve eşleşmeleri vurgulama (bkz. sınırlamalar)
- Performans: yalnızca görünen sayfalar çizilir; bitmap’ler bellek sınırlı LRU önbellekte tutulur
  (cihaz bellek sınıfının ⅓’ü, en fazla 128 MB); tek sayfa en fazla ~8 megapiksel; yakınlaştırma bittikten sonra netlik için yeniden çizilir

**Excel (XLSX) görüntüleyici**
- A, B, C… sütun ve 1, 2, 3… satır başlıkları, yatay/dikey kaydırma, yakınlaştırma
- Birden çok çalışma sayfası; alt kısımda sayfa sekmeleri (gizli sayfalar “(gizli)” olarak gösterilir)
- Metin, sayı, tarih, saat, yüzde, para birimi, bilimsel gösterim ve kesir biçimleri
- Hücre dolgu rengi, yazı rengi, kalın/italik/altı çizili/üstü çizili, hizalama, metin kaydırma, kenarlıklar, tema renkleri
- Birleşik hücreler, boş hücreler, dondurulmuş satır/sütunlar, gizli satır/sütunlar, sütun genişlikleri ve satır yükseklikleri
- Uzun metinlerin Excel’deki gibi boş komşu hücrelere taşması; sığmayan sayılar için `####`
- Hücre arama (Türkçe büyük/küçük harf duyarsız: “ığdır” → “Iğdır”), sonuçlar arasında gezinme
- Dokunulan hücrenin adı ve değeri üst çubukta gösterilir, kopyalanabilir
- Formüller yeniden hesaplanmaz; dosyadaki son hesaplanmış (önbellek) değerler gösterilir
- Büyük tablolar: yalnızca ekrandaki hücreler çizilir; sayfa başına en fazla 3 milyon dolu hücre okunur (aşılırsa uyarı ile kesilir)

**Dosya yönetimi**
- Sistem dosya seçicisi ile açma (İndirilenler klasöründen doğrudan başlatma dahil)
- WhatsApp ve diğer uygulamalardan **Birlikte aç** (VIEW) ve **Paylaş → Ayaz Viewer** (SEND)
- Son açılanlar: ad, boyut, son açılma tarihi; ada göre arama, PDF/Excel filtresi; üç nokta menüsünde Aç / Paylaş / Listeden kaldır
- **Dosyalar** sekmesi: seçtiğiniz klasörleri (ör. `Download/WhatsApp Documents`) ekleyip içindeki PDF/XLSX dosyalarına göz atma ve arama
- Kalıcı erişim izni verilemeyen dosyalar (ör. WhatsApp’tan gelenler) uygulamanın özel alanına birebir kopyalanır; böylece “Son Açılanlar”dan tekrar açılabilir. Bu kopyalar Ayarlar’dan temizlenebilir
- Kullanıcının dosyaları hiçbir zaman değiştirilmez veya silinmez; “Listeden kaldır” yalnızca listeyi ve uygulamanın kendi kopyasını etkiler
- Bozuk, boş, şifreli veya desteklenmeyen dosyalarda uygulama çökmez; Türkçe açıklama gösterilir

**Düzenleme**
- Görüntüleyicinin üst çubuğunda **Düzenle** (kalem) simgesi. Düzenleme kipinde: **Geri al**, **Kaydet**, **Bitti**.
- **Excel:** hücreye dokunun, üstteki kutuya değer yazıp ✓'ye basın (sonra alttaki hücreye geçer). Sayılar Türkçe
  yazılır (`1.234,56`, `12,5%`); tarih biçimli hücrelere `05.03.2024`; formüller İngilizce yazılır (`=SUM(A1:A5)`);
  başına `'` konan değer metin olarak kalır. Satır/sütun menüsü: üstüne/altına satır ekle, satır sil, soluna/sağına
  sütun ekle, sütun sil, hücreyi temizle. Ekleme/silmede formüller, birleşik hücreler, sütun genişlikleri, koşullu
  biçimler ve veri doğrulamaları birlikte kayar; silinen hücreye başvuran formül Excel'deki gibi `#REF!` olur.
- **PDF:** Kalem, vurgulayıcı, metin notu (5 renk, 3 kalınlık), silgi (henüz kaydedilmemiş işaretler için), sayfayı
  döndür, sayfayı sil. Tek parmakla çizilir; iki parmakla kaydırılır/yakınlaştırılır. İşaretlemeler kaydedilince sayfanın
  içine çizilir. (Android'in yerleşik PDF bileşeni PDF notlarını göstermediği için not olarak değil, sayfa içeriği
  olarak eklenir; böylece Ayaz Viewer dahil her uygulamada aynı görünür.) Belgenin kendi metni değişmez, aranabilir ve
  seçilebilir kalır; eklenen metin notları da aranabilir.
- **Kaydetme her seferinde sorulur:** *Yeni kopya olarak kaydet* (sistem kaydetme ekranıyla istediğiniz klasöre,
  "Ad (düzenlendi).xlsx" önerisiyle) veya *Asıl dosyanın üzerine yaz* (yalnızca yazma izni varsa; geri alınamaz
  uyarısı gösterilir). Kaydetmeden çıkmaya çalışırsanız uyarı çıkar. Kaydedene kadar asıl dosyaya dokunulmaz.

**Oluştur**
- Ana sayfadaki **Oluştur** kartı: *Yeni Excel tablosu* (boş tablo, doğrudan düzenleme kipinde), *Yeni PDF (metin
  yaz)* (başlık + metin yazılır, satırlar kaydırılarak sayfalara bölünür), *Boş PDF* (1/3/5 sayfa; kalem ve not için).
- Oluşturulan belgeler uygulamada saklanır ve Son Açılanlar'da görünür. *Yeni kopya olarak kaydet* ile telefona
  (ör. İndirilenler) kaydedilir; WhatsApp/Telegram ile paylaşılabilir. Bu belgeleri listeden kaldırmak onları
  sildiği için onay istenir.

**WhatsApp / Telegram ile paylaşım**
- Görüntüleyicinin sağ üstünde paylaş simgesi; son açılanlarda üç nokta → **Paylaş**
- Uygulamanın kendi paylaşım penceresi; **yalnızca iki seçenek**: WhatsApp ve Telegram. Sistem paylaşım menüsü açılmaz
- WhatsApp ve WhatsApp Business desteklenir; ikisi de yüklüyse hangisinin kullanılacağı sorulur (Telegram, Telegram X vb. için de aynı)
- Dosya bağlantı olarak değil, gerçek PDF/XLSX dosyası olarak (`FileProvider` content URI + geçici okuma izni) gönderilir
- Dosya adı ve uzantısı korunur; içerik bayt bayt aynıdır
- Uygulama yüklü değilse Türkçe uyarı gösterilir; paylaşımdan vazgeçmek hata oluşturmaz

**Gizlilik**
- `INTERNET` izni yoktur (CI her derlemede bunu doğrular); geniş depolama izni yoktur
- Reklam, analitik, takip, hesap yoktur; dosyalar hiçbir sunucuya gönderilmez
- Dosya içerikleri loglanmaz; uygulama verisi buluta yedeklenmez (`allowBackup=false`)

---

## 3. APK’yi edinme ve kurma

### A) Depodaki hazır debug APK (en kolayı)
Derleme bittiğinde GitHub, kurulabilir APK’yı depoya otomatik ekler: **`AyazViewer/AyazViewer-debug.apk`**
(yanında `AyazViewer-debug.apk.sha256` doğrulama dosyası). GitHub’da dosyayı açıp **Download raw file** ile indirebilir
veya depoyu indirdiğinizde `AyazViewer` klasöründe bulabilirsiniz.

### A2) GitHub Actions’tan debug APK
Her `push` işleminde GitHub sunucularında APK otomatik derlenir.

1. GitHub’da depoyu açın → **Actions** → **Ayaz Viewer** iş akışı → en son başarılı çalıştırma.
2. Sayfanın altındaki **Artifacts** bölümünden **AyazViewer-debug-apk** dosyasını indirin (zip içinde `AyazViewer-debug.apk`).
3. APK’yi telefona aktarın (USB, Google Drive, kendinize WhatsApp vb.).
4. Telefonda APK’ye dokunun. İlk kurulumda “Bilinmeyen uygulamaları yükle” iznini o uygulama (Dosyalar, Chrome vb.) için açın.
5. Play Protect uyarı verirse **Ayrıntılar → Yine de yükle** seçin (Play Store dışı kişisel uygulamalarda normaldir).

Bilgisayardan kurmak için: `adb install -r AyazViewer-debug.apk`

> Debug APK, depodaki sabit `app/debug.keystore` ile imzalanır. Böylece yeni sürümleri kaldırmadan
> üzerine kurabilirsiniz. Bu anahtar gizli değildir; uzun süreli kullanım için aşağıdaki release imzası önerilir.

### B) Android Studio ile derleme
1. Android Studio (Ladybug veya daha yeni) → **Open** → `AyazViewer` klasörü.
2. Gradle eşitlemesi bitince: **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
3. Çıktı: `app/build/outputs/apk/debug/app-debug.apk`

Komut satırından (JDK 17 ve Android SDK kurulu olmalı):
```bash
cd AyazViewer
./gradlew :app:assembleDebug          # debug APK
./gradlew :xlsx:test :app:testDebugUnitTest   # birim testleri
./gradlew :app:connectedDebugAndroidTest      # bağlı cihaz/emülatörde testler
```

### C) İmzalı release APK
1. Anahtar deposu oluşturun (bir kez; dosyayı ve parolaları güvenli saklayın, depoya eklemeyin):
   ```bash
   keytool -genkeypair -v -keystore ~/ayaz-release.jks -alias ayaz -keyalg RSA -keysize 4096 -validity 10000
   ```
2. `AyazViewer/keystore.properties` dosyası oluşturun (`.gitignore` içindedir):
   ```properties
   AYAZ_KEYSTORE_FILE=/home/kullanici/ayaz-release.jks
   AYAZ_KEYSTORE_PASSWORD=depo_parolasi
   AYAZ_KEY_ALIAS=ayaz
   AYAZ_KEY_PASSWORD=anahtar_parolasi
   ```
   (Aynı değerler ortam değişkeni olarak da verilebilir.)
3. Derleyin: `./gradlew :app:assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
   (R8 ile küçültülmüş ve imzalı).
4. Doğrulayın: `apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk`

Android Studio ile: **Build → Generate Signed Bundle / APK → APK** → anahtar deposunu seçin → *release*.

> Debug sürümden release sürüme geçerken imza farklı olduğu için önce eski uygulamayı kaldırmanız gerekir.

---

## 4. Testler

Testlerin tamamı depoda bulunur ve GitHub Actions’ta otomatik çalışır:

| Test grubu | Nerede | Kapsam |
|---|---|---|
| `xlsx/src/test` (JVM, 23 test) | `./gradlew :xlsx:test` | Hücre referansları, 60+ sayı/tarih biçimi senaryosu, basit/çok sayfalı/birleşik/el yazımı XLSX, Türkçe karakterler, 1904 tarih sistemi, bozuk/boş/eski .xls/ZIP-olmayan dosyalar, 100.000 × 12 büyük sayfa, hücre sınırı, iptal, Türkçe arama |
| `app/src/test` (JVM, 6 test) | `./gradlew :app:testDebugUnitTest` | Dosya türü tespiti (içerik imzası), Türkçe ad temizleme, uzantı, boyut/tarih biçimleri |
| `app/src/androidTest` (34 test; emülatör: Android 13 ve 15) | `connectedDebugAndroidTest` | Tek/çok sayfalı/400 sayfalık/karışık boyutlu PDF çizimi ve süreleri, şifreli/bozuk/boş PDF, Türkçe dosya adı, PDF metin araması (Android 15), XLSX’in Android XML ayrıştırıcısı ile okunması, büyük XLSX, son açılanların yeniden başlatmada korunması, “Birlikte aç” (VIEW) ve “Paylaş” (SEND) niyetleri, paylaşım dosyasının adı/boyutu/içeriği, WhatsApp/Telegram yüklü değil uyarısı, alt menü gezinmesi, koyu/açık tema, ekran döndürme, sayfaya git ve son sayfayı hatırlama, sayfa sekmeleri, hücre araması |

Test dosyaları `tools/generate_samples.py` ile üretilir; `samples/` klasöründeki dosyaları telefona kopyalayarak elle de deneyebilirsiniz.

Gerçek test sonuçları ve hangi testlerin nerede çalıştığı için `TEST_RAPORU.md` dosyasına bakın.

---

## 5. Bilinen sınırlamalar

- **PDF metin araması** Android 15+ (veya Google Play sistem güncellemesiyle güncel PDF bileşeni olan) cihazlarda çalışır. Android 13/14’te bileşen eskiyse arama yerine Türkçe bilgi mesajı gösterilir. Taranmış (görüntü) PDF’lerde metin katmanı olmadığından arama sonuç vermez.
- **Parola korumalı PDF** ve **parola korumalı XLSX** dosyaları açılamaz (açıklayıcı mesaj gösterilir).
- **Eski .xls (Excel 97–2003)** biçimi desteklenmez; dosyayı .xlsx olarak kaydetmek gerekir.
- **PDF'te var olan metin değiştirilemez** (PDF bir baskı düzeni biçimidir); üzerine yazma/işaretleme, sayfa döndürme
  ve silme yapılabilir. Silgi yalnızca henüz kaydedilmemiş işaretlemeleri siler; kaydedilen işaretlemeler sayfanın
  parçası olur ve sonradan silinemez (kaydetmeden önce "Geri al" kullanın).
- Başka uygulamalarda eklenmiş **PDF notları/vurguları ve form alanları** Ayaz Viewer'da görünmez (Android'in yerleşik
  PDF bileşeni bunları çizmez); belge içeriğinin kendisi eksiksiz gösterilir. Sayfa döndürme/silme sonrası "Geri al" ile geri dönülebilir.
- **Excel düzenlemede** formüller yeniden hesaplanmaz (Excel'de açılınca hesaplanır), formül yazımı İngilizcedir.
  Hücre biçimi (renk, kalın vb.) değiştirilemez, değer yazılan hücrenin mevcut biçimi korunur. Excel *tablosu*
  (Tablo olarak biçimlendir) içeren sayfalarda satır/sütun ekleme-silme kapalıdır; başka sayfalardaki formüller ve
  adlandırılmış aralıklar kaydırılmaz; resim ve grafikler yerinde kalır. Paylaşılan formüller ilk düzenlemede tek
  tek formüllere açılır (sonuç aynıdır).
- **Yazma izni:** "Üzerine yaz" yalnızca dosya Dosya Aç / klasör ile yazma izniyle açıldıysa veya uygulamanın kendi
  kopyasıysa (ör. WhatsApp'tan açılan) mümkündür. WhatsApp'tan açılan dosyada üzerine yazmak WhatsApp'taki dosyayı
  değil, uygulamadaki kopyayı değiştirir; değiştirilmiş hali "Yeni kopya" veya Paylaş ile gönderebilirsiniz.
- XLSX’te **grafikler, resimler, şekiller, koşullu biçimlendirme, açıklamalar ve köprüler** gösterilmez; grafik sayfaları “görüntülenemiyor” mesajıyla listelenir.
- Formüller **yeniden hesaplanmaz**; dosyayı kaydeden programın sakladığı son değer gösterilir. Bazı araçlar (ör. kod ile üretilen dosyalar) önbellek değeri yazmazsa formül hücresi boş görünür.
- Excel yazı tipleri yerine Android’in sistem yazı tipi kullanılır; sütun genişlikleri Excel ölçüsüne göre yaklaşık hesaplanır.
- Zengin metin hücrelerinde (bir hücrede birden çok biçim) tek bir biçim uygulanır.
- Dosyalar sekmesi yalnızca sizin eklediğiniz klasörleri listeler (geniş depolama izni bilinçli olarak istenmez). Android 11+ “Download” kök klasörünün tamamına klasör izni vermeye izin vermez; alt klasör (ör. `Download/WhatsApp Documents`) eklenebilir veya “İndirilenler” kısayoluyla seçici kullanılabilir.
- Arayüz metinleri Türkçe olarak kaynak koda yazılmıştır (yalnızca Türkçe kullanım için).
