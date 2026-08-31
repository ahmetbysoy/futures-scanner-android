package com.predator.futures

import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * WebView içinden yapılan Binance REST çağrılarını ve CORS proxy çağrılarını native OkHttp
 * üzerinden geçirir. Bu sayede:
 *  - Tarayıcı CORS/451/geo-block kontrolleri tamamen devre dışı kalır (aynı-origin bypass)
 *  - CORS proxy'ler JS tarafında değil native OkHttp'tan çağrılır → WebView preflight/
 *    mixed-content/Origin kontrolleri hiç devreye girmez
 *  - Kullanıcı hangi ağdaysa (VPN vs.) gerçek kullanıcı ağı kullanılır
 *
 * Strateji:
 *  - Hedef URL Binance (path /fapi/ veya /api/v3/ ile başlıyorsa) ise doğrudan OkHttp'tan Binance'e istek at
 *  - URL bilinen bir CORS proxy'si ise (url parametresi Binance'e işaret ediyorsa),
 *    önce hedef Binance URL'sini doğrudan dene (VPN varsa geçer); 451/403 alırsan
 *    proxy URL'yi OkHttp'tan çağır
 */
object BinanceProxy {
    private const val TAG = "BinanceProxy"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    // Futures REST host'ları. DİKKAT: data-api.binance.vision SPOT aynasıdır,
    // futures (path /fapi/ ile başlayanlar) 404 döndürür; listede YOK.
    private val BINANCE_HOSTS = setOf(
        "fapi.binance.com",
        "fapi1.binance.com", "fapi2.binance.com", "fapi3.binance.com", "fapi4.binance.com",
        "api.binance.com",
        "api1.binance.com", "api2.binance.com", "api3.binance.com", "api4.binance.com"
    )

    // Bilinen genel CORS proxy'leri → JS tarafı URL'leri encode edilmiş olarak üretir;
    // native tarafda bunları da yakalayıp OkHttp'tan çağırıyoruz (CORS/preflight riski yok).
    private data class ProxyPattern(val host: String, val extract: (String) -> String?)

    private val CORS_PROXIES = listOf(
        // allorigins: ?url=ENCODED
        ProxyPattern("api.allorigins.win") { url ->
            url.substringAfter("?url=", "").takeIf { it.isNotEmpty() }?.let {
                java.net.URLDecoder.decode(it, "UTF-8")
            }
        },
        // corsproxy.io: /?ENCODED  (path after '/?')
        ProxyPattern("corsproxy.io") { url ->
            url.substringAfter("/?", "").takeIf { it.isNotEmpty() }?.let {
                java.net.URLDecoder.decode(it, "UTF-8")
            }
        },
        // codetabs: ?quest=ENCODED
        ProxyPattern("api.codetabs.com") { url ->
            url.substringAfter("?quest=", "").takeIf { it.isNotEmpty() }?.let {
                java.net.URLDecoder.decode(it, "UTF-8")
            }
        },
        // cors.lol: ?url=ENCODED
        ProxyPattern("api.cors.lol") { url ->
            url.substringAfter("?url=", "").takeIf { it.isNotEmpty() }?.let {
                java.net.URLDecoder.decode(it, "UTF-8")
            }
        },
        // thingproxy: /fetch/https://...
        ProxyPattern("thingproxy.freeboard.io") { url ->
            url.substringAfter("/fetch/", "").takeIf { it.isNotEmpty() }?.let { "https://$it" }
        },
    )

    private val BINANCE_PATH_PREFIXES = arrayOf("/fapi/", "/api/v3/")

    fun isBinanceRest(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return try {
            val u = android.net.Uri.parse(url)
            val host = u.host?.lowercase() ?: return false
            val scheme = u.scheme ?: return false
            if (scheme != "https") return false
            // Binance host'larından biriyse ve futures/spot path'i ise doğrudan yakala
            if (host in BINANCE_HOSTS) {
                val path = u.path ?: ""
                return BINANCE_PATH_PREFIXES.any { path.startsWith(it) }
            }
            // Bilinen CORS proxy host'larından biriyse de yakala
            CORS_PROXIES.any { it.host == host }
        } catch (e: Exception) { false }
    }

    /** URL Binance'e mi gidiyor (path'i kontrol ederek). */
    private fun isBinanceTarget(url: String): Boolean {
        return try {
            val u = android.net.Uri.parse(url)
            val host = u.host?.lowercase() ?: return false
            val path = u.path ?: ""
            ("https" == u.scheme) && host in BINANCE_HOSTS && BINANCE_PATH_PREFIXES.any { path.startsWith(it) }
        } catch (e: Exception) { false }
    }

    private fun doCall(url: String, request: WebResourceRequest): Response {
        val reqBuilder = Request.Builder().url(url)
        for ((k, v) in request.requestHeaders) {
            val lk = k.lowercase()
            if (lk == "origin" || lk == "referer" || lk.startsWith("sec-")) continue
            reqBuilder.addHeader(k, v)
        }
        reqBuilder.header(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Mobile Safari/537.36"
        )
        reqBuilder.header("Accept", "application/json,text/plain,*/*")
        return client.newCall(reqBuilder.build()).execute()
    }

    private fun respond(resp: Response, url: String, t0: Long): WebResourceResponse {
        val body = resp.body?.bytes() ?: ByteArray(0)
        val ct = resp.body?.contentType()
        val mimeType = ct?.toString()?.substringBefore(";")?.trim() ?: "application/json"
        val encoding = ct?.charset()?.name() ?: "UTF-8"
        val statusCode = resp.code
        val reasonPhrase = resp.message.ifBlank { "OK" }
        val headers = mutableMapOf<String, String>()
        headers["Access-Control-Allow-Origin"] = "*"
        headers["Access-Control-Allow-Methods"] = "GET, POST, OPTIONS"
        headers["Access-Control-Allow-Headers"] = "Content-Type, Authorization"
        headers["Access-Control-Max-Age"] = "86400"
        headers["Cache-Control"] = "no-cache"
        for ((name, values) in resp.headers.toMultimap()) {
            val ln = name.lowercase()
            if (ln == "access-control-allow-origin" || ln == "set-cookie") continue
            headers[name] = values.joinToString(",")
        }
        Log.d(TAG, "REST ${resp.code} ${url.takeLast(70)} → ${body.size}B (${System.currentTimeMillis() - t0}ms)")
        return WebResourceResponse(
            mimeType, encoding, statusCode, reasonPhrase, headers, ByteArrayInputStream(body)
        )
    }

    private fun syntheticError(msg: String, code: Int = 502): WebResourceResponse {
        val err = """{"code":-1,"msg":"native proxy error: ${msg.take(120)}"}""".toByteArray()
        return WebResourceResponse(
            "application/json", "UTF-8", code, "Bad Gateway",
            mapOf(
                "Access-Control-Allow-Origin" to "*",
                "Cache-Control" to "no-cache"
            ),
            ByteArrayInputStream(err)
        )
    }

    fun intercept(request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
        if (!isBinanceRest(url)) return null
        val t0 = System.currentTimeMillis()
        return try {
            val uri = android.net.Uri.parse(url)
            val host = uri.host?.lowercase() ?: ""
            val candidates = mutableListOf<String>()

            if (host in BINANCE_HOSTS) {
                // Doğrudan Binance URL'si — önce onu dene (VPN varsa geçer)
                candidates.add(url)
            } else {
                // CORS proxy URL'si — önce hedef Binance URL'sini çöz, direkt dene
                val pattern = CORS_PROXIES.firstOrNull { it.host == host }
                val target = pattern?.extract?.invoke(url)
                if (target != null && isBinanceTarget(target)) {
                    candidates.add(target)                  // 1) önce Binance direkt (VPN/yerel ağ)
                    candidates.add(url)                     // 2) sonra CORS proxy
                } else {
                    candidates.add(url)                     // bilinmeyen proxy — direkt çağır
                }
            }

            var lastErr: IOException? = null
            var lastResp: Response? = null
            for (cand in candidates) {
                try {
                    val resp = doCall(cand, request)
                    if (resp.isSuccessful) {
                        lastResp?.close()
                        return respond(resp, cand, t0)
                    }
                    // 451/403/429 ise sonraki adayı dene
                    if (resp.code == 451 || resp.code == 403 || resp.code == 429 || resp.code == 503) {
                        Log.d(TAG, "skip $cand → HTTP ${resp.code} (sıradaki deneniyor)")
                        lastResp?.close()
                        lastResp = resp
                        continue
                    }
                    // Diğer hatalar da muhtemelen hedefe özel — yine de döndür
                    return respond(resp, cand, t0)
                } catch (e: IOException) {
                    Log.w(TAG, "hata $cand: ${e.message}")
                    lastErr = e
                }
            }
            if (lastResp != null) return respond(lastResp, candidates.last(), t0)
            throw lastErr ?: IOException("tüm adaylar başarısız")
        } catch (e: IOException) {
            Log.w(TAG, "proxy hata $url: ${e.message}")
            syntheticError(e.message ?: "io error")
        }
    }
}
