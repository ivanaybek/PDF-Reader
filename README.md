# Search-Movie

Bu depoda iki proje bulunur:

| Klasör / dosya | İçerik |
|---|---|
| [`AyazViewer/`](AyazViewer/) | **Ayaz Viewer** – çevrimdışı PDF ve Excel (XLSX) görüntüleyici/düzenleyici Android uygulaması (Kotlin, Jetpack Compose) |
| `index.html`, `script.js`, `style.css` | Film arama web sayfası (OMDb API) |

## Ayaz Viewer – hızlı bağlantılar

- 📱 **Kurulabilir APK:** [`AyazViewer/AyazViewer-debug.apk`](AyazViewer/AyazViewer-debug.apk)
  (SHA-256: [`AyazViewer-debug.apk.sha256`](AyazViewer/AyazViewer-debug.apk.sha256))
- 📖 **Kullanım, derleme ve release imzalama:** [`AyazViewer/README.md`](AyazViewer/README.md)
- ✅ **Test raporu:** [`AyazViewer/TEST_RAPORU.md`](AyazViewer/TEST_RAPORU.md)
- 📄 **Örnek dosyalar:** [`AyazViewer/samples/`](AyazViewer/samples/)
- ⚙️ **Otomatik derleme ve testler:** [`.github/workflows/ayaz-viewer.yml`](.github/workflows/ayaz-viewer.yml)

APK, GitHub Actions üzerinde derlenir; derleme, birim testleri ve Android 13/15 emülatör
testleri geçtikten sonra depoya otomatik olarak eklenir.
