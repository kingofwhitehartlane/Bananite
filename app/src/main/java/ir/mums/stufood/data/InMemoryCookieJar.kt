package ir.mums.stufood.data

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory cookie jar that survives across all OkHttp requests issued by the same
 * OkHttpClient instance. This is what makes the ASP.NET session "sticky" — the
 * `ASP.NET_SessionId` and `.ASPXAUTH` cookies set by the server after login are
 * automatically re-sent on every subsequent request, exactly like a browser.
 *
 * We do NOT persist cookies to disk: when the app process dies, the session is gone
 * and the user logs in again. That's the safer default for a credentials-bearing app.
 */
class InMemoryCookieJar : CookieJar {

    // We keep one cookie list per host (so the same OkHttpClient could talk to several
    // sites without leaking cookies between them).
    private val storage: MutableMap<String, MutableList<Cookie>> = ConcurrentHashMap()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val host = url.host
        val bucket = storage.getOrPut(host) { mutableListOf() }
        synchronized(bucket) {
            // Remove any cookie with the same name+path so we replace stale values
            // with the fresh ones from the server.
            cookies.forEach { incoming ->
                bucket.removeAll { it.name == incoming.name && it.path == incoming.path }
                bucket.add(incoming)
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val bucket = storage[host] ?: return emptyList()
        synchronized(bucket) {
            // Filter out expired cookies while we're at it.
            val now = System.currentTimeMillis()
            val valid = bucket.filter { it.expiresAt > now }
            if (valid.size != bucket.size) {
                bucket.clear()
                bucket.addAll(valid)
            }
            return valid
        }
    }

    /** Drop everything — used by the "logout" action. */
    fun clear() {
        storage.clear()
    }

    /** True if we appear to have a session cookie for this host. */
    fun hasSessionFor(host: String): Boolean {
        val bucket = storage[host] ?: return false
        return bucket.any { it.name.equals("ASP.NET_SessionId", ignoreCase = true) ||
                            it.name.equals(".ASPXAUTH", ignoreCase = true) }
    }
}
