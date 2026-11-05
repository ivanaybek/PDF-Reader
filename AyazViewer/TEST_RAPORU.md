# Ayaz Viewer – Test Raporu

Tarih: 5 Kasım 2026 • Sürüm: 1.0.0 (düzenleme ve oluşturma eklendi) • Derleme: GitHub Actions, “Ayaz Viewer” iş akışı, commit `fe9845b`

> Bu rapordaki sonuçların tamamı gerçekten çalıştırılmış otomatik testlerdir. Elle (insan eliyle) gerçek bir
> telefonda yapılmış test **yoktur**; bu tür testler aşağıda “Test edilemeyenler” bölümünde ayrıca listelenmiştir.

## 1. Derleme

| Adım | Sonuç |
|---|---|
| XLSX motoru birim testleri (`:xlsx:test`) | ✅ Başarılı |
| Uygulama birim testleri (`:app:testDebugUnitTest`) | ✅ Başarılı |
| Debug APK (`assembleDebug`) | ✅ Üretildi → `AyazViewer-debug.apk` (Actions → Artifacts) |
| Release APK (`assembleRelease`, R8 ile küçültülmüş, imzasız) | ✅ Derlendi |
| APK doğrulaması: `INTERNET` izni yok, geniş depolama izni yok, imza geçerli | ✅ Başarılı |
| Lint | ✅ Çalıştı (rapor Artifacts içinde) |

Not: Bu oturumun yürütüldüğü bulut ortamında Google Maven (dl.google.com) erişime kapalı olduğu için Android
derlemesi yerelde yapılamadı. XLSX motoru yerelde de derlenip test edildi; Android derlemesi ve cihaz testleri
GitHub Actions üzerinde yapıldı.

## 2. JVM birim testleri

**XLSX motoru – 42 test, 42 başarılı** (yerelde ve GitHub Actions’ta; 23 okuma + 19 düzenleme/oluşturma)

- Hücre referansları (A1, AB12, $C$3, XFD), aralıklar
- 60+ sayı/tarih biçimi senaryosu: Genel, binlik ayırıcı, ondalık, yüzde, bilimsel, kesir, para birimi,
  koşullu bölümler, renkler, metin bölümleri, tarih/saat (Türkçe ay ve gün adları), ÖÖ/ÖS, geçen süre ([h]:mm),
  1900 artık yıl hatası, 1904 tarih sistemi, yuvarlama ile gün atlaması
- Basit XLSX, çok sayfalı XLSX (gizli sayfa, boş sayfa, birleşik hücreler, tema rengi + tint, gizli sütun, sekme rengi)
- El yazımı XLSX: satır içi dizeler, `r` özniteliği olmayan hücreler, önbellekli formül sonuçları, zengin metin,
  fonetik metin, `_x000D_` kaçışları, ad alanı öneki (`x:`), mutlak hedef yolları, grafik sayfası
- Türkçe karakterler (Şükrü Çağlayan – İstanbul, Iğdır; ğüşıöç ĞÜŞİÖÇ) ve Türkçe arama (“ığdır” → “Iğdır”)
- Bozuk / boş / eski .xls / ZIP olmayan / XLSX olmayan ZIP → anlaşılır hata, çökme yok
- Büyük sayfa: 100.000 satır × 12 sütun (6,9 MB) yaklaşık 1,1 saniyede okundu
- Hücre sınırı aşımında güvenli kesme, iptal

- **Düzenleme:** hücreye metin/sayı/mantıksal/formül yazma, yeni hücre ve yeni satır, temizleme, biçimin korunması,
  ad alanı önekli dosya, boş sayfa; satır/sütun ekleme ve silmede formül kaydırma (`#REF!` dahil), birleşik hücre,
  sütun genişliği, koşullu biçim (sol üst hücreye göre formül), veri doğrulama, köprü; paylaşılan formüllerin açılması;
  calcChain kaldırma ve `fullCalcOnLoad`; tablo içeren sayfada satır/sütun işleminin reddi; kaynak dosyanın hiç
  değişmemesi; art arda 6 işlem; Türkçe sayı/tarih girişi ayrıştırma; yeni boş çalışma kitabı.
- Düzenlenen ve oluşturulan 13 dosya ayrıca **openpyxl** ile açılarak doğrulandı (Excel uyumlu paket yapısı).

**Uygulama – 6 test, 6 başarılı**: içerik imzasıyla dosya türü tespiti, Türkçe dosya adı temizleme, uzantı
tamamlama, kararlı kimlikler, Türkçe boyut ve tarih biçimleri.

## 3. Cihaz (emülatör) testleri

| Cihaz | Sonuç |
|---|---|
| Android 13 (API 33, Pixel 6 profili) | ✅ 42 başarılı, 0 başarısız; PDF metin araması testi **atlandı** (Android 13’te `PdfRenderer` arama API’si yok) |
| Android 15 (API 35, Pixel 6 profili) | ✅ **43 / 43 başarılı**, atlanan yok (PDF metin araması dahil) |

Kapsanan senaryolar:

| İstenen test | Karşılayan cihaz testi |
|---|---|
| Tek sayfalı PDF | `PdfControllerTest.singlePageRendersContent` (piksel kontrolü), `ViewerUiTest.pdfPageIsActuallyDrawn` (ekranda çizim) |
| Çok sayfalı PDF | `PdfControllerTest.multiPageDocument` (30 sayfa), `ViewerUiTest.pdfOpensRotatesAndShares` |
| Büyük PDF | `PdfControllerTest.largeDocumentIsFastAndBounded` (400 sayfa, 58 sayfa çizimi, süre ve bellek sınırı) |
| Farklı sayfa boyutları | `PdfControllerTest.mixedPageSizes` |
| PDF metin araması | `PdfControllerTest.textSearchWhenSupported` (Android 15’te çalıştı ve geçti) |
| Sayfaya git + son sayfayı hatırlama | `ViewerUiTest.pdfGoToPageAndRememberLastPage` |
| Basit XLSX | `XlsxOnDeviceTest.simpleWorkbook` (Android’in kendi XML ayrıştırıcısı ile) |
| Çok sayfalı XLSX | `XlsxOnDeviceTest.multipleSheetsAndMerges`, `ViewerUiTest.xlsxMultipleSheetTabs` (sekmeler) |
| Hücre stilleri ekranda | `ViewerUiTest.xlsxGridDrawsCellFills` (başlık dolgu rengi piksel kontrolü) |
| Hücre arama | `ViewerUiTest.xlsxOpensSearchesAndRotates` |
| Büyük XLSX | `XlsxOnDeviceTest.largeSheetOnDevice` (50.000 satır × 10 sütun + arama) |
| Türkçe karakterli dosyalar | `PdfControllerTest.turkishFileName`, `ShareAndOpenTest.*` (Türkçe adların korunması) |
| Boş veya bozuk dosyalar | Şifreli/bozuk/boş PDF, boş/bozuk/.xls/XLSX olmayan dosyalar; “Birlikte aç” ile bozuk PDF ve .xls → “Dosya açılamadı”, çökme yok |
| Başka uygulamadan açma (“Birlikte aç” / “Paylaş”) | `ViewerUiTest.openWithFromAnotherAppAndCorruptFile` (ACTION_VIEW ve ACTION_SEND), `ShareAndOpenTest.externalUriWithoutPersistablePermissionIsCopied` |
| Uygulama kapatılıp açılınca son dosyalar | `RecentFilesPersistenceTest.recentsSurviveRestart`, `MainUiTest.recentsSurviveActivityRecreation`, bozuk liste dosyası testi |
| Ekran döndürme | `ViewerUiTest.pdfOpensRotatesAndShares`, `ViewerUiTest.xlsxOpensSearchesAndRotates` |
| Koyu ve açık tema | `MainUiTest.lightAndDarkTheme` (ekran görüntüsünden zemin parlaklığı ölçümü), `homeShowsOpenButtonAndNavigation` |
| Excel düzenleme ve kaydetme | `ViewerUiTest.xlsxEditCellAndOverwriteOriginal` (arayüzden hücre değiştir → Kaydet → Üzerine yaz → dosyada yeni değer), `xlsxUnsavedChangesWarning` (satır ekle → Geri → uyarı), `EditingTest.xlsxEditUndoAndOriginalUntouched` (düzenle, satır ekle, iki kez geri al, asıl dosya değişmedi) |
| PDF düzenleme ve kaydetme | `ViewerUiTest.pdfDrawAndSave` (kırmızı kalınla çiz → Kaydet → Üzerine yaz → kaydedilen dosyada çizim görünür), `EditingTest.inkAndTextAreWrittenAsAnnotationsAndRendered` (çizgi pikselleri, Türkçe metnin aranabilirliği, kaynak değişmedi), `rotateAndDeletePages`, `inkOnRotatedPageLandsWhereDrawn` (döndürülmüş sayfada çizimin doğru yerde olması) |
| Oluştur | `EditingTest.createdDocumentsAreValid` (120 satırlık Türkçe metinden ≥3 sayfalık aranabilir PDF, 3 sayfalık boş PDF, yeni tabloya yazma) |
| WhatsApp/Telegram paylaşımı | `ShareAndOpenTest` (yalnızca iki hedef, FileProvider URI, okuma izni, dosya adı/boyut/içerik bayt bayt aynı), uygulama yüklü değilken Türkçe uyarı (`MainUiTest.recentFilesListAndShareSheet`) |

Geliştirme sırasında testlerin ortaya çıkarıp düzeltilen gerçek hatalar:
- PDF işaretlemeleri ilk sürümde PDF notu olarak yazılıyordu; Android'in PdfRenderer'ı notları çizmediği için kaydedilen
  çizimler uygulamada görünmüyordu → artık sayfa içeriğine çiziliyor.
- Paylaşım niyetinde `ClipData.newUri(null, …)` WhatsApp/Telegram’a paylaşırken çökme oluştururdu → düzeltildi.
- PDF’ye erişim izni kaldırıldığında yanlışlıkla “parola korumalı” mesajı gösterilirdi → düzeltildi.
- Görüntüleyiciden hızlı çıkıldığında son sayfa kaydedilmiyordu → kapanışta da kaydediliyor.

## 4. Test edilemeyenler (dürüst not)

- **Gerçek telefon**: Uygulama sizin telefonunuzda veya herhangi bir fiziksel cihazda denenmedi; testler emülatörde yapıldı.
- **Android 14 ve Android 16**: Emülatörde yalnızca Android 13 ve 15 test edildi. Uygulama Android 16 (API 36) hedefli derlenmiştir, ancak bu sürümlerde çalıştırılmadı.
- **Gerçek WhatsApp/Telegram gönderimi**: Emülatörde bu uygulamalar yüklü olmadığından dosyanın sohbete gerçekten gönderilmesi denenemedi. Niyetin doğru pakete, doğru dosya ve izinle oluşturulduğu doğrulandı.
- **Sistem dosya seçicisi ve “Klasör ekle”** ekranlarındaki dokunuşlar (Android’in kendi arayüzü) otomatik testte çalıştırılmadı; seçiciden gelen sonucu işleyen kod yolu ayrıca test edildi.
- **İki parmakla yakınlaştırma ve sürükleme** hareketleri otomatik testte yapılmadı (yalnızca kod incelemesi). Çift dokunuşla yakınlaştırma da elle denenmedi.
- **Düzenleme:** "Yeni kopya olarak kaydet" sistemin kaydetme ekranını kullandığı için otomatik testte çalıştırılmadı
  (yazma kodu "Üzerine yaz" testleriyle doğrulandı). PDF'te iki parmakla kaydırma/yakınlaştırma sırasında çizim,
  vurgulayıcı ve silgi araçları ile metin notu ekleme arayüzü otomatik testte denenmedi (kalemle çizim ve kaydetme
  denendi). Düzenlenen XLSX dosyaları gerçek Microsoft Excel'de açılarak denenmedi (openpyxl ile doğrulandı).
- Arayüzün görsel görünümü ekran görüntüsü olarak incelenmedi; yalnızca piksel ölçümleriyle (tema zemini, PDF içeriği, hücre dolgusu) doğrulandı.

Telefonda ilk denemede önerilen kontrol listesi: `samples/` klasöründeki dosyaları İndirilenler’e kopyalayın;
Dosya Aç ile PDF ve XLSX açın; iki parmakla yakınlaştırın; WhatsApp’tan kendinize bir PDF gönderip “Birlikte aç”
ile açın; Son Açılanlar’dan üç nokta → Paylaş → WhatsApp/Telegram deneyin.
