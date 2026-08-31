package com.predator.futures

import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * WebView içinden yapılan Binance REST çağrılarını native OkHttp üzerinden geçirir.
 * Bu sayede tarayıcı CORS/geo-block/451 kısıtlamaları tamamen devre dışı kalır,
 * ve istekler gerçek bir HTTP istemcisi gibi sunucuya gider (kullanıcı ağı, VPN, DNS vs.
 * ne ise onu kullanır).
 *
 * Kullanımı: shouldInterceptRequest içinde eğer URL Binance REST endpoint'i ise
 * intercept(request) → WebResourceResponse döndür. JS tarafı normal fetch() yapar;
 * cevap akışı doğrudan WebView'a verilir.
 */
object BinanceProxy {
    private const val TAG = "BinanceProxy"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    // Intercept edilecek Binance REST host'ları. WS bu listede YOK — onlar doğrudan çalışıyor.
    // DİKKAT: data-api.binance.vision SPOT aynasıdır (/api/v3), futures endpoint'leri (/fapi/*)
    // 404 döndürür. Buraya EKLEMİYORUZ — aksi halde native proxy 404 alır ve JS tarafı gereksiz
    // yere o host'u dener. Spot için api.binance.com zaten listede.
    private val BINANCE_HOSTS = setOf(
        "fapi.binance.com",
        "fapi1.binance.com", "fapi2.binance.com", "fapi3.binance.com", "fapi4.binance.com",
        "api.binance.com",
        "api1.binance.com", "api2.binance.com", "api3.binance.com", "api4.binance.com"
    )

    // Path'ler futures (/fapi/*) veya spot (/api/*) olmalı — statik CDN isteklerini yakalamayız
    private val PROXIED_PATH_PREFIXES = arrayOf("/fapi/", "/api/v3/")

    fun isBinanceRest(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return try {
            val u = android.net.Uri.parse(url)
            val host = u.host?.lowercase() ?: return false
            val path = u.path ?: ""
            ("https" == u.scheme) &&
                    host in BINANCE_HOSTS &&
                    PROXIED_PATH_PREFIXES.any { path.startsWith(it) }
        } catch (e: Exception) { false }
    }

    fun intercept(request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
        if (!isBinanceRest(url)) return null
        val t0 = System.currentTimeMillis()
        return try {
            val reqBuilder = Request.Builder().url(url)
            // Sadece yaygın header'ları kopyala (Cookie, User-Agent, Accept vs.)
            for ((k, v) in request.requestHeaders) {
                val lk = k.lowercase()
                if (lk == "origin" || lk == "referer" || lk.startsWith("sec-")) continue
                reqBuilder.addHeader(k, v)
            }
            reqBuilder.header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36")
            reqBuilder.header("Accept", "application/json,text/plain,*/*")

            val call = client.newCall(reqBuilder.build())
            val resp: Response = runBlocking(Dispatchers.IO) { call.execute() }
            val body = resp.body?.bytes() ?: ByteArray(0)
            val ct = resp.body?.contentType()
            val mimeType = ct?.toString()?.substringBefore(";")?.trim() ?: "application/json"
            val encoding = ct?.charset()?.name() ?: "UTF-8"
            val statusCode = resp.code
            val reasonPhrase = resp.message.ifBlank { "OK" }
            val headers = mutableMapOf<String, String>()
            // Respond with permissive CORS (origin zaten WebView içinden geliyor)
            headers["Access-Control-Allow-Origin"] = "*"
            headers["Access-Control-Allow-Methods"] = "GET, POST, OPTIONS"
            headers["Access-Control-Allow-Headers"] = "Content-Type, Authorization"
            headers["Access-Control-Max-Age"] = "86400"
            headers["Cache-Control"] = "no-cache"
            // Forward response headers (set-cookie hariç — tutmuyoruz)
            for ((name, values) in resp.headers.toMultimap()) {
                val ln = name.lowercase()
                if (ln == "access-control-allow-origin" || ln == "set-cookie") continue
                headers[name] = values.joinToString(",")
            }
            val inputStream = ByteArrayInputStream(body)
            Log.d(TAG, "REST ${resp.code} ${url.takeLast(60)} → ${body.size}B (${System.currentTimeMillis()-t0}ms)")
            WebResourceResponse(mimeType, encoding, statusCode, reasonPhrase, headers, inputStream)
        } catch (e: IOException) {
            Log.w(TAG, "proxy hata $url: ${e.message}")
            // Return synthetic 502 so JS restFetch zinciri sonraki fallback'i dener
            val err = """{"code":-1,"msg":"native proxy error: ${e.message?.take(80)}"}""".toByteArray()
            WebResourceResponse(
                "application/json", "UTF-8", 502, "Bad Gateway",
                mapOf(
                    "Access-Control-Allow-Origin" to "*",
                    "Cache-Control" to "no-cache"
                ),
                ByteArrayInputStream(err)
            )
        }
    }
}
