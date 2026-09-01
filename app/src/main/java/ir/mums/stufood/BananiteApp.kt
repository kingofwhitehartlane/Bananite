package ir.mums.stufood

import android.app.Application
import ir.mums.stufood.data.InMemoryCookieJar
import ir.mums.stufood.data.StufoodRepository
import ir.mums.stufood.data.UserPrefs

/**
 * Application-wide singletons. We keep one Repository + one CookieJar for the whole
 * app process so the session survives configuration changes (rotations, theme toggle).
 */
class StufoodApp : Application() {

    val cookieJar by lazy { InMemoryCookieJar() }
    val repository by lazy { StufoodRepository(cookieJar) }
    val userPrefs by lazy { UserPrefs(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: StufoodApp
            private set
    }
}
