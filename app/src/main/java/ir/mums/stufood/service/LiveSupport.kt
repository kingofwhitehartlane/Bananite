package ir.mums.stufood.service

import android.os.Build

/**
 * Whether the "Live notifications" feature can work on this phone.
 *
 * It needs Android 16 (Live Updates) AND a phone maker that lets third-party apps use its
 * live island. Xiaomi / Huawei gate theirs behind partner portals; Honor and Transsion brands
 * keep theirs system-only. See LIVE_NOTIFICATIONS.md.
 */
object LiveSupport {

    data class Status(val supported: Boolean, val reason: String? = null)

    fun status(): Status {
        vendorReason()?.let { return Status(false, it) }
        if (Build.VERSION.SDK_INT < 36) {
            return Status(
                false,
                "Needs Android 16 or newer. This phone runs Android ${Build.VERSION.RELEASE}, which has no " +
                    "Live Updates support, so Bananite uses a normal notification instead."
            )
        }
        return Status(true)
    }

    fun isSamsung(): Boolean = names().any { it == "samsung" }

    private fun names(): Set<String> =
        setOf(Build.MANUFACTURER.trim().lowercase(), Build.BRAND.trim().lowercase())

    private fun vendorReason(): String? {
        val n = names()
        fun has(vararg k: String) = n.any { name -> k.any { name == it || name.startsWith("$it ") } }
        return when {
            has("xiaomi", "redmi", "poco") ->
                "Not available on Xiaomi, Redmi and POCO phones: HyperOS only shows Super Island for apps that " +
                    "Xiaomi has approved through its partner portal, so third-party apps can't use it."
            has("huawei") ->
                "Not available on Huawei phones: HarmonyOS only shows its live island for apps approved " +
                    "through Huawei's partner portal."
            has("honor") ->
                "Not available on Honor phones: Magic Capsule is limited to system apps."
            has("infinix", "tecno", "itel", "transsion") ->
                "Not available on Infinix, TECNO and itel phones: Magic Ring, Dynamic Port and Dynamic Bar " +
                    "are limited to system apps."
            else -> null
        }
    }
}
