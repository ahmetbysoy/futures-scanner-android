# Futures Scanner (Android)

Binance USDT-M Futures için profesyonel DOM Ladder, likidite ısı haritası, Order-Flow (Whale / Sweep / Absorption / Delta-Burst / Spoof) ve çoklu P90 likidite duvarı takibi yapan Android uygulaması.

Ticaret mantığı ve grafik motoru (TradingView LightweightCharts tabanlı) WebView içinde lokal asset olarak çalışır; bildirim, titreşim, uygulama kabuğu ve yaşam döngüsü tamamen native Kotlin + Jetpack Compose ile yazılmıştır.

> Not: Signal/flow event'leri WebView'dan Kotlin tarafına `@JavascriptInterface` köprüsü (`AndroidBridge.kt`) ile aktarılır; bu sayede yüksek öncelikli whale/delta burst/sweep anlarında sistem bildirimi + titreşim + ses alırsınız.

## Özellikler

- Canlı Binance Futures 1s mum + hacim
- Gerçek zamanlı CVD, Δ(tick) anlık göstergeler
- **DOM Ladder** (sağ şerit, best 10 seviye)
- **15 dakikalık likidite ısı haritası** (Limit Order Book yoğunluğu)
- **Order-Flow avı**
  - 🐋 Whale (≥ whaleMin notional)
  - 💦 Sweep (zincirleme agresif yeme)
  - 🧲 Absorption (pasif emilim)
  - ⚡ Delta Burst (kısa süreli CVD spike)
  - 🎭 Spoof (duvarın emir çekmesi)
- **P90 Likidite duvarları** — yaş takibi, çizgi + süre etiketi (⏱12s gibi)
- Spread/Duvar metrikleri, OBI anlık, Sync durumu badge'i
- Sinyal sistemi: EMA Ribbon + Squeeze + CVD divergence + TrendScore
- Bildirim + titreşim (signal ve yüksek şiddetli whale/sweep/delta-burst için)

## APK Build (GitHub Actions)

Repository'e push attığınızda GitHub Actions otomatik olarak debug APK üretir:

1. GitHub üzerinde yeni bir repo oluştur (ya da `gh` CLI kullan).
2. Bu klasörü reponun kökü yap:
   ```bash
   cd FuturesScanner
   git init
   git add .
   git commit -m "feat: ilk sürüm - Futures Scanner Android"
   gh repo create futures-scanner-android --private --source=. --push
   ```
3. GitHub → Actions → **Build Debug APK** workflow çalışacak.
4. Run bittikten sonra işin **Artifacts** bölümünden `futures-scanner-debug-apk.zip` indirilir, içindeki `app-debug.apk` cihaza yüklenir.

İlk build'de Gradle wrapper jar'ı otomatik olarak bootstrap edilir (workflow içinde `gradle wrapper` adımı), yani wrapper jar'ı repoda tutulmaz.

## Lokal build

```bash
cd FuturesScanner
# JDK 17 ve Android SDK (min 34) yüklü olmalı
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Gereksinimler

- Android 8.0+ (API 26)
- İnternet bağlantısı (Binance Futures public API / WS)
- Bildirim izni (Android 13+ ilk açılışta istenir)

## Gizlilik

- Uygulama hiçbir kişisel veri, API anahtarı veya kullanıcı kimliği toplamaz.
- Tüm veri akışı doğrudan cihazdan `fstream.binance.com` ve `fapi.binance.com`'a yapılır; aracı sunucu yoktur.

## Sorumluluk Reddi

Bu uygulama yalnızca eğitim ve piyasa izleme amaçlıdır; yatırım tavsiyesi değildir. Kripto vadeli işlemler yüksek risk içerir, kendi sorumluluğunuzda kullanın.
