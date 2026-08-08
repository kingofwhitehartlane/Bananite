package ir.mums.stufood.data

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.concurrent.TimeUnit

/**
 * Talks to stufood.mums.ac.ir.
 *
 * IMPORTANT — read this before touching parseReservationPage / postForm:
 *
 * The real site posts every field of the ASP.NET WebForm on every single postback
 * (that's how classic WebForms / __doPostBack works — the whole form, not just the
 * changed control). Rather than hardcode every hidden field name (view the HAR: there
 * are hidden fields like hfCalcDate, hfFirstdayofWeek, hdnSelectFood, dpSubject,
 * rbList, txtSearchStuNum, etc. that must be echoed back unchanged), we generically
 * walk the parsed HTML form and resend every field we find, only overriding the ones
 * that represent the actual UI action (e.g. the day dropdown that changed). This is
 * exactly what a browser does, and it's robust to hidden fields we haven't seen.
 *
 * Also important: requests must be multipart/form-data (the real site's form has a
 * file upload control, ctl00$body$fuAttachment), not URL-encoded. A plain FormBody
 * post will look like a completely different browser/client to the server and may be
 * rejected or mishandled.
 */
class StufoodRepository(private val cookieJar: InMemoryCookieJar) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .addInterceptor(UserAgentInterceptor)
        .build()

    private val baseUrl = "https://stufood.mums.ac.ir"
    private val reservationUrl = "$baseUrl/WebForm/StudentReserveFood.aspx"

    private object UserAgentInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request().newBuilder()
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
                )
                .build()
            return chain.proceed(request)
        }
    }

    // Field names taken directly from a captured browser session (HAR).
    private object Fields {
        const val MEAL = "ctl00\$body\$dpFoodMeal"
        const val NEXT_WEEK_BTN = "ctl00\$body\$btnNextWeek"
        const val LAST_WEEK_BTN = "ctl00\$body\$btnlastWeek"
        const val FILE_UPLOAD = "ctl00\$body\$fuAttachment"

        // Day dropdowns are ctl00$body$rptFoodDiet$ctlNN$dpSelf, but the site adds or
        // removes days/rows dynamically (weekends, past days, cutoffs, etc.) — so we
        // never assume a fixed count or a fixed set of indices. This regex is used to
        // *discover* whatever day fields actually exist on the current page.
        //
        // NOTE: this must be a normal (non-raw) string literal. In a raw ("""...""")
        // string, backslash is not an escape character, so "\$" doesn't produce a
        // literal $ — it gets read as the start of a ($body) template interpolation,
        // which fails to compile ("Unresolved reference 'body'"). In a normal string,
        // "\\\$" is: "\\" -> one backslash, "\$" -> one dollar sign, giving the regex
        // engine the literal two characters \$ it needs to match ASP.NET's field-name
        // separator (which is itself a literal '$').
        val DAY_DROPDOWN_REGEX = Regex("^ctl00\\\$body\\\$rptFoodDiet\\\$ctl(\\d+)\\\$dpSelf\$")
    }

    // ----------------------------------------------------------------------
    // LOGIN  (unchanged from before — not covered by this HAR)
    // ----------------------------------------------------------------------

    suspend fun fetchLoginPage(): LoginPageData = withClient {
        val response = get("$baseUrl/Default.aspx")
        val html = response.use { it.body?.string().orEmpty() }
        val doc = Jsoup.parse(html, baseUrl)

        val captchaSrcRaw = doc.selectFirst("#body_imgCaptcha")?.attr("src").orEmpty()
        val captchaSrc = if (captchaSrcRaw.startsWith("http")) captchaSrcRaw else "$baseUrl/$captchaSrcRaw"

        val captchaBytes: ByteArray? = if (captchaSrc.isNotEmpty()) {
            try {
                get(captchaSrc).use { it.body?.bytes() }
            } catch (_: Exception) { null }
        } else null

        LoginPageData(captchaImage = captchaBytes)
    }

    suspend fun login(username: String, password: String, captcha: String): LoginResult = withClient {
        val jsonPayload = """
            {
                "username": "$username",
                "password": "$password",
                "captcha": "$captcha"
            }
        """.trimIndent()

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonPayload.toRequestBody(mediaType)

        val request = Request.Builder()
            .url("$baseUrl/Default.aspx/login2")
            .header("Content-Type", "application/json; charset=utf-8")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", "$baseUrl/Default.aspx")
            .header("Origin", baseUrl)
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        val responseText = response.use { it.body?.string().orEmpty() }

        val isSuccess = responseText.contains(""""Key":true""") || responseText.contains(""""Key": true""")

        if (isSuccess) LoginResult.Success else LoginResult.Failure("Login failed: $responseText")
    }

    fun isLoggedIn(): Boolean = cookieJar.hasSessionFor("stufood.mums.ac.ir")

    fun clearSession() = cookieJar.clear()

    // ----------------------------------------------------------------------
    // RESERVATION
    // ----------------------------------------------------------------------

    suspend fun fetchReservationPage(): ReservationPage = withClient {
        val response = get(reservationUrl)
        val html = response.use { it.body?.string().orEmpty() }
        parseReservationPage(html)
    }

    /** Change the meal dropdown (e.g. ناهار / lunch). */
    suspend fun selectMeal(current: ReservationPage, mealOptionValue: String): ReservationPage =
        postForm(current, eventTarget = Fields.MEAL, overrides = mapOf(Fields.MEAL to mealOptionValue))

    suspend fun clickNextWeek(current: ReservationPage): ReservationPage {
        if (current.nextWeek.isUsable.not()) return current
        return postForm(current, eventTarget = "", overrides = mapOf(Fields.NEXT_WEEK_BTN to "next"), clickedButton = Fields.NEXT_WEEK_BTN)
    }

    suspend fun clickLastWeek(current: ReservationPage): ReservationPage {
        if (current.lastWeek.isUsable.not()) return current
        return postForm(current, eventTarget = "", overrides = mapOf(Fields.LAST_WEEK_BTN to "prev"), clickedButton = Fields.LAST_WEEK_BTN)
    }

    /**
     * Selects a diet/cafeteria option for one day, identified by its live field name
     * (see DayInfo.fieldName) rather than a positional index, since which index a day
     * gets can shift as the site adds/removes rows. Per the captured HAR, changing
     * this dropdown IS the reservation action for that day — the server commits it on
     * this postback, there is no separate "confirm" step observed.
     *
     * Returns null (instead of throwing) if [day] is locked/disabled on the current
     * page — always re-check against the freshest ReservationPage before calling.
     */
    suspend fun selectDayDiet(current: ReservationPage, day: DayInfo, dietOptionValue: String): ReservationPage? {
        if (!day.isUsable) return null
        return postForm(current, eventTarget = day.fieldName, overrides = mapOf(day.fieldName to dietOptionValue))
    }

    /**
     * Reserves every day keyed in [selections] (day.fieldName -> chosen option
     * *value*, not label). Re-resolves each day against the freshest page before
     * posting, since a day can become locked (cutoff, already reserved, etc.) or
     * simply disappear between when the user picked a value and when this runs — in
     * that case the day is skipped and reported, not treated as a hard failure.
     * Each day is a separate sequential postback (the server needs the fresh
     * __VIEWSTATE from the previous response before the next request will be valid).
     */
    suspend fun reserveDays(
        startPage: ReservationPage,
        selections: Map<String, String>,
        onProgress: (step: String) -> Unit = {}
    ): ReservationResult {
        return try {
            var page = startPage
            val skipped = mutableListOf<String>()
            val fieldNames = selections.keys.toList()

            for ((n, fieldName) in fieldNames.withIndex()) {
                val value = selections[fieldName] ?: continue
                val day = page.days.firstOrNull { it.fieldName == fieldName }
                if (day == null) {
                    skipped.add("$fieldName (no longer on the page)")
                    continue
                }
                if (!day.isUsable) {
                    skipped.add("${day.dayLabel} (${day.lockedReason ?: "not available"})")
                    continue
                }
                val label = day.dietOptions.firstOrNull { it.second == value }?.first ?: value
                onProgress("${day.dayLabel} (${n + 1}/${fieldNames.size}): reserving \"$label\"")
                page = postForm(page, eventTarget = day.fieldName, overrides = mapOf(day.fieldName to value))
            }

            if (skipped.isNotEmpty()) {
                onProgress("Done, but skipped: ${skipped.joinToString("; ")}")
            } else {
                onProgress("Done!")
            }
            ReservationResult.Success(page, skipped)
        } catch (t: Throwable) {
            ReservationResult.Failure(t.message ?: "Network error while reserving.")
        }
    }

    /** Convenience wrapper matching the old "one tap, whole week" behavior. */
    suspend fun reserveWeekWithDefaults(
        mealText: String = "\u0646\u0627\u0647\u0627\u0631",
        dietText: String = "\u0633\u0644\u0641 \u067e\u0631\u062f\u06cc\u0633",
        onProgress: (step: String) -> Unit = {}
    ): ReservationResult {
        return try {
            onProgress("Loading reservation page\u2026")
            var page = fetchReservationPage()

            val mealValue = page.mealOptions.firstOrNull { it.first == mealText }?.second
                ?: return ReservationResult.Failure("Meal \"$mealText\" not found in dropdown.")
            onProgress("Selecting meal: $mealText")
            page = selectMeal(page, mealValue)

            if (page.nextWeek.isUsable) {
                onProgress("Going to next week")
                page = clickNextWeek(page)
            }

            val selections = mutableMapOf<String, String>()
            for (day in page.days) {
                if (!day.isUsable) continue // skip locked/closed days rather than failing the whole run
                val dietValue = day.dietOptions.firstOrNull { it.first == dietText }?.second
                    ?: return ReservationResult.Failure("Diet \"$dietText\" not found for ${day.dayLabel}.")
                selections[day.fieldName] = dietValue
            }
            return reserveDays(page, selections, onProgress)
        } catch (t: Throwable) {
            ReservationResult.Failure(t.message ?: "Network error during reservation.")
        }
    }

    // ----------------------------------------------------------------------
    // GENERIC FORM POST — walks the parsed form, resends every field, then
    // parses the response into a fresh ReservationPage.
    // ----------------------------------------------------------------------

    /**
     * Rebuilds the multipart body for [current]'s form snapshot, applying [overrides]
     * on top, sets __EVENTTARGET to [eventTarget], and (if [clickedButton] is set)
     * includes that button's field so the server sees it as "clicked". Then posts and
     * re-parses the response.
     */
    private suspend fun postForm(
        current: ReservationPage,
        eventTarget: String,
        overrides: Map<String, String>,
        clickedButton: String? = null
    ): ReservationPage = withClient {
        val bodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)

        // Start from every field we found on the page (hidden inputs, text inputs,
        // selects, checked radios/checkboxes) so nothing the server expects goes
        // missing — this mirrors what a real browser submits.
        val fields = LinkedHashMap(current.fieldSnapshot)

        fields["__EVENTTARGET"] = eventTarget
        fields["__EVENTARGUMENT"] = ""

        overrides.forEach { (k, v) -> fields[k] = v }

        // Buttons are only sent when "clicked" — remove the other nav button's field
        // if present so we don't imply pressing two buttons at once.
        if (clickedButton != Fields.NEXT_WEEK_BTN) fields.remove(Fields.NEXT_WEEK_BTN)
        if (clickedButton != Fields.LAST_WEEK_BTN) fields.remove(Fields.LAST_WEEK_BTN)

        fields.forEach { (name, value) ->
            if (name == Fields.FILE_UPLOAD) return@forEach // handled below
            bodyBuilder.addFormDataPart(name, value)
        }
        // Empty file part, matching the real request's untouched file input.
        bodyBuilder.addFormDataPart(
            Fields.FILE_UPLOAD, "",
            "".toRequestBody("application/octet-stream".toMediaType())
        )

        val request = Request.Builder()
            .url(reservationUrl)
            .header("Referer", reservationUrl)
            .header("Origin", baseUrl)
            .post(bodyBuilder.build())
            .build()

        val response = client.newCall(request).execute()
        val html = response.use { it.body?.string().orEmpty() }
        parseReservationPage(html)
    }

    // ----------------------------------------------------------------------
    // PARSING
    // ----------------------------------------------------------------------

    private fun parseReservationPage(html: String): ReservationPage {
        val doc = Jsoup.parse(html, reservationUrl)

        val mealSelect = doc.selectFirst("select[name='${Fields.MEAL}']")
        val mealOptions = mealSelect?.select("option")
            ?.map { it.text().trim() to (it.attr("value").ifEmpty { it.text() }) }
            ?: emptyList()

        val nextWeek = parseButtonState(doc, Fields.NEXT_WEEK_BTN)
        val lastWeek = parseButtonState(doc, Fields.LAST_WEEK_BTN)

        // Discover whichever day dropdowns actually exist on THIS page load — the
        // site adds/removes rows (weekends, past days, cutoffs), so we never assume a
        // fixed set of indices.
        val dayFieldNames = doc.select("form select")
            .mapNotNull { it.attr("name").takeIf { n -> Fields.DAY_DROPDOWN_REGEX.matches(n) } }
            .distinct()
            .sortedBy { name -> Fields.DAY_DROPDOWN_REGEX.find(name)!!.groupValues[1].toInt() }

        val days = dayFieldNames.mapIndexed { i, name ->
            val dropdown = doc.selectFirst("select[name='$name']")!!
            val dietOptions = dropdown.select("option").map {
                it.text().trim() to (it.attr("value").ifEmpty { it.text() })
            }
            val currentValue = dropdown.select("option[selected]").firstOrNull()?.attr("value")
                ?: dropdown.select("option").firstOrNull()?.attr("value").orEmpty()

            // Best-effort label for the day (date/weekday), read from nearby text —
            // falls back to a plain "Day N" if the site's markup doesn't cooperate.
            val container = dropdown.closest("tr, li, div") ?: dropdown.parent()
            val dayLabel = container?.select("td, span, label, th")
                ?.map { it.text().trim() }
                ?.firstOrNull { it.isNotBlank() && it != dropdown.text().trim() }
                ?: "Day ${i + 1}"

            // The dropdown itself may be disabled/readonly even though it's still in
            // the DOM (e.g. cutoff passed, already reserved, admin locked it). We treat
            // any of these as "not usable" and try to grab a human-readable reason from
            // whatever's sitting next to it.
            val isDisabled = dropdown.hasAttr("disabled") || dropdown.hasAttr("readonly")
            val lockedReason = if (isDisabled) findNearbyReasonText(container, dropdown) else null

            DayInfo(
                index = i,
                fieldName = name,
                dietOptions = dietOptions,
                currentValue = currentValue,
                dayLabel = dayLabel,
                isUsable = dietOptions.isNotEmpty() && !isDisabled,
                lockedReason = lockedReason
            )
        }

        return ReservationPage(
            fieldSnapshot = snapshotForm(doc),
            mealOptions = mealOptions,
            nextWeek = nextWeek,
            lastWeek = lastWeek,
            days = days
        )
    }

    /**
     * A nav button (next/last week) can be: absent entirely, present but disabled, or
     * present and clickable. We treat only the last case as usable.
     */
    private fun parseButtonState(doc: Document, fieldName: String): ButtonState {
        val btn = doc.selectFirst("input[name='$fieldName']") ?: return ButtonState(exists = false, isUsable = false)
        val disabled = btn.hasAttr("disabled")
        return ButtonState(exists = true, isUsable = !disabled)
    }

    /** Best-effort scrape of an error/help message near a disabled control. */
    private fun findNearbyReasonText(container: Element?, dropdown: Element): String? {
        val candidates = listOfNotNull(container, dropdown.parent(), dropdown.nextElementSibling())
        for (el in candidates) {
            val text = el?.select("span, small, [class*=error], [class*=Error], [class*=message], [class*=Message]")
                ?.map { it.text().trim() }
                ?.firstOrNull { it.isNotBlank() && it.length < 200 }
            if (!text.isNullOrBlank()) return text
        }
        return null
    }

    /**
     * Generic ASP.NET-postback-style form snapshot: every hidden/text input's value,
     * every select's currently-selected option, every checked radio/checkbox. Submit
     * buttons and the file input are deliberately excluded (buttons are added back
     * explicitly only when "clicked"; the file input is sent separately as an empty
     * file part).
     */
    private fun snapshotForm(doc: Document): Map<String, String> {
        val result = LinkedHashMap<String, String>()

        doc.select("form input").forEach { input: Element ->
            val name = input.attr("name")
            if (name.isBlank()) return@forEach
            when (input.attr("type").lowercase().ifEmpty { "text" }) {
                "submit", "button", "image", "file" -> return@forEach
                "checkbox", "radio" -> if (input.hasAttr("checked")) result[name] = input.attr("value")
                else -> result[name] = input.attr("value")
            }
        }
        doc.select("form select").forEach { select: Element ->
            val name = select.attr("name")
            if (name.isBlank()) return@forEach
            val selected = select.select("option[selected]").firstOrNull()
                ?: select.select("option").firstOrNull()
            result[name] = selected?.attr("value").orEmpty()
        }
        doc.select("form textarea").forEach { area: Element ->
            val name = area.attr("name")
            if (name.isBlank()) return@forEach
            result[name] = area.text()
        }

        return result
    }

    // ----------------------------------------------------------------------
    // HTTP WRAPPERS
    // ----------------------------------------------------------------------

    private suspend fun <T> withClient(block: () -> T): T =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { block() }

    private fun get(url: String) = client.newCall(Request.Builder().url(url).get().build()).execute()

    // ----------------------------------------------------------------------
    // PUBLIC DATA CLASSES
    // ----------------------------------------------------------------------

    data class LoginPageData(val captchaImage: ByteArray?)

    sealed class LoginResult {
        object Success : LoginResult()
        data class Failure(val message: String) : LoginResult()
    }

    data class ReservationPage(
        val fieldSnapshot: Map<String, String>,
        val mealOptions: List<Pair<String, String>>,
        val nextWeek: ButtonState,
        val lastWeek: ButtonState,
        val days: List<DayInfo>
    )

    /** exists = the button is present in the DOM at all; isUsable = present AND not disabled. */
    data class ButtonState(val exists: Boolean, val isUsable: Boolean)

    data class DayInfo(
        val index: Int,
        val fieldName: String,
        val dietOptions: List<Pair<String, String>>,
        /** The value currently selected on the server for this day. */
        val currentValue: String,
        val dayLabel: String,
        /** False if this day's dropdown is disabled/locked/has no options right now. */
        val isUsable: Boolean,
        /** Best-effort human-readable reason it's locked, if we could find one. */
        val lockedReason: String?
    )

    sealed class ReservationResult {
        data class Success(val finalPage: ReservationPage, val skipped: List<String> = emptyList()) : ReservationResult()
        data class Failure(val message: String) : ReservationResult()
    }
}