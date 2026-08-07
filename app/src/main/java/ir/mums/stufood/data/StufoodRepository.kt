package ir.mums.stufood.data

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.concurrent.TimeUnit

/**
 * Single source of truth for talking to stufood.mums.ac.ir.
 *
 * This is the native-Android replacement for the Selenium script. Instead of driving a
 * real browser, we talk HTTP directly. The trick to making ASP.NET WebForms happy is:
 *
 *   1. Always carry the hidden fields __VIEWSTATE / __EVENTVALIDATION / __VIEWSTATEGENERATOR.
 *      They change on every postback, so we re-parse them out of every response.
 *   2. For a control with AutoPostBack (dropdown change, radio click), set
 *      __EVENTTARGET to that control's `name` attribute. ASP.NET uses this to know
 *      which control fired the postback.
 *   3. For a button click, leave __EVENTTARGET empty and add `buttonName=buttonValue`
 *      to the form data — that's how a real browser signals "this button was clicked".
 *   4. Always use the same cookie jar so the session sticks.
 *
 * Every public function is `suspend` and runs the network call on an IO dispatcher
 * (the ViewModels take care of that).
 */
class StufoodRepository(private val cookieJar: InMemoryCookieJar) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val baseUrl = "https://stufood.mums.ac.ir"

    // ----------------------------------------------------------------------
    // LOGIN
    // ----------------------------------------------------------------------

    /**
     * Fetches the login page + the captcha image in one logical step.
     *
     * The captcha is an <img id="body_imgCaptcha" src="..."> — we resolve its src to an
     * absolute URL, then fetch the bytes with the same cookies so the server ties the
     * image to this session. When the user submits the form, the answer they typed is
     * matched against what the server generated for this captcha.
     */
    suspend fun fetchLoginPage(): LoginPageData = withClient {
        val html = get("$baseUrl/Default.aspx").use { it.body?.string().orEmpty() }
        val doc = Jsoup.parse(html, baseUrl)

        val viewState = inputValue(doc, "__VIEWSTATE")
        val eventValidation = inputValue(doc, "__EVENTVALIDATION")
        val viewStateGenerator = inputValue(doc, "__VIEWSTATEGENERATOR")

        val usernameName = nameById(doc, "body_txtUsername", default = "body_txtUsername")
        val passwordName = nameById(doc, "body_txtPassword", default = "body_txtPassword")
        val captchaInputName = nameById(doc, "body_txtCaptcha", default = "body_txtCaptcha")

        // The login button is rendered as <input type="submit" id="btnLogin">. We try
        // that ID first; if it's missing we fall back to the form's primary submit button,
        // which is what `ctl01` was doing in the Python script.
        val loginBtn = doc.selectFirst("#btnLogin")
            ?: doc.selectFirst("input[type=submit]")
            ?: doc.selectFirst("#ctl01")
        val loginBtnName = loginBtn?.attr("name").orEmpty()
        val loginBtnValue = loginBtn?.attr("value").orEmpty().ifEmpty { "Login" }

        // Captcha image — resolve the relative src to absolute.
        val captchaSrcRaw = doc.selectFirst("#body_imgCaptcha")?.attr("src").orEmpty()
        val captchaSrc = if (captchaSrcRaw.startsWith("http")) captchaSrcRaw else "$baseUrl/$captchaSrcRaw"
        val captchaBytes: ByteArray? = if (captchaSrc.isNotEmpty()) {
            try {
                get(captchaSrc).use { it.body?.bytes() }
            } catch (_: Exception) { null }
        } else null

        LoginPageData(
            viewState = viewState,
            eventValidation = eventValidation,
            viewStateGenerator = viewStateGenerator,
            usernameName = usernameName,
            passwordName = passwordName,
            captchaInputName = captchaInputName,
            loginBtnName = loginBtnName,
            loginBtnValue = loginBtnValue,
            captchaImage = captchaBytes
        )
    }

    /**
     * Posts the login form. Returns Success on a successful login, Failure with a
     * server-provided (or generic) message otherwise.
     *
     * Detection: after a successful login, the server redirects us off /Default.aspx
     * (typically to /WebForm/...). After a failed login, we stay on Default.aspx and
     * the response still contains the username field — that's how we tell.
     */
    suspend fun login(username: String, password: String, captcha: String, page: LoginPageData): LoginResult = withClient {
        val form = FormBody.Builder()
            .add("__EVENTTARGET", "")
            .add("__EVENTARGUMENT", "")
            .add("__VIEWSTATE", page.viewState)
            .add("__EVENTVALIDATION", page.eventValidation)
            .add("__VIEWSTATEGENERATOR", page.viewStateGenerator)
            .add(page.usernameName, username)
            .add(page.passwordName, password)
            .add(page.captchaInputName, captcha)
            .also { builder ->
                // Only include the submit button if we found one. ASP.NET identifies
                // which button was clicked by its name being present in the POST data.
                if (page.loginBtnName.isNotEmpty()) {
                    builder.add(page.loginBtnName, page.loginBtnValue)
                }
            }
            .build()

        val request = Request.Builder().url("$baseUrl/Default.aspx").post(form).build()
        val response = client.newCall(request).execute()
        val body = response.body?.string().orEmpty()
        val finalUrl = response.request.url.toString()
        response.close()

        val stillOnLoginPage = finalUrl.contains("Default.aspx", ignoreCase = true) &&
                body.contains("body_txtUsername", ignoreCase = true)

        if (stillOnLoginPage) {
            val errorText = extractServerError(body)
            LoginResult.Failure(errorText ?: "Login failed. URL=$finalUrl Body=${body.take(500)}")
        }
        } else {
            LoginResult.Success
        }
    }

    /** True if we appear to be logged in (i.e. we have a session cookie). */
    fun isLoggedIn(): Boolean = cookieJar.hasSessionFor("stufood.mums.ac.ir")

    /** Clears cookies — used by logout. */
    fun clearSession() = cookieJar.clear()

    // ----------------------------------------------------------------------
    // RESERVATION
    // ----------------------------------------------------------------------

    /**
     * Loads the reservation page and parses out every control we care about:
     * meal dropdown, next-week button, and the per-day dropdowns / radios.
     */
    suspend fun fetchReservationPage(): ReservationPage = withClient {
        val html = get("$baseUrl/WebForm/StudentReserveFood.aspx").body?.string().orEmpty()
        parseReservationPage(html)
    }

    /**
     * Selects a meal in the meal dropdown (e.g. "ناهار"). Triggers the same postback
     * that ASP.NET would fire when the user picks an option in the browser.
     *
     * Returns the refreshed page state — viewstate changes on every postback, so the
     * caller MUST use the returned ReservationPage for the next operation.
     */
    suspend fun selectMeal(current: ReservationPage, mealOptionValue: String): ReservationPage = withClient {
        val form = baseFormBuilder(current)
            // AutoPostBack: tell ASP.NET which control fired the postback.
            .add("__EVENTTARGET", current.mealDropdownName)
            .add("__EVENTARGUMENT", "")
            // The new value of the dropdown — this is what `Select.select_by_visible_text`
            // was doing under the hood in Selenium.
            .add(current.mealDropdownName, mealOptionValue)
            .build()

        val response = client.newCall(
            Request.Builder().url("$baseUrl/WebForm/StudentReserveFood.aspx").post(form).build()
        ).execute()
        val html = response.body?.string().orEmpty()
        response.close()
        parseReservationPage(html)
    }

    /** Clicks the "next week" button. Same effect as the Python `next_week_button.click()`. */
    suspend fun clickNextWeek(current: ReservationPage): ReservationPage = withClient {
        val builder = baseFormBuilder(current)
            .add("__EVENTTARGET", "")
            .add("__EVENTARGUMENT", "")

        // Button click: include the button name=value. ASP.NET uses this to know which
        // button was pressed. If we couldn't find the button on the page, we can't click
        // it — but we still try the postback in case it's wired differently.
        current.nextWeekBtnName?.let { name ->
            builder.add(name, current.nextWeekBtnValue ?: "")
        }

        val response = client.newCall(
            Request.Builder().url("$baseUrl/WebForm/StudentReserveFood.aspx").post(builder.build()).build()
        ).execute()
        val html = response.body?.string().orEmpty()
        response.close()
        parseReservationPage(html)
    }

    /**
     * Selects a diet (cafeteria) in a day's dropdown. Equivalent to:
     *   Select(driver.find_element(By.ID, f"body_rptFoodDiet_dpSelf_{i}"))
     *       .select_by_visible_text("سلف پردیس")
     */
    suspend fun selectDayDiet(
        current: ReservationPage,
        dayIndex: Int,
        dietOptionValue: String
    ): ReservationPage = withClient {
        val day = current.days.getOrNull(dayIndex)
            ?: return@withClient current

        val form = baseFormBuilder(current)
            .add("__EVENTTARGET", day.dropdownName)
            .add("__EVENTARGUMENT", "")
            .add(day.dropdownName, dietOptionValue)
            .build()

        val response = client.newCall(
            Request.Builder().url("$baseUrl/WebForm/StudentReserveFood.aspx").post(form).build()
        ).execute()
        val html = response.body?.string().orEmpty()
        response.close()
        parseReservationPage(html)
    }

    /**
     * Clicks the first radio for a day. Equivalent to:
     *   driver.execute_script("arguments[0].click();", radio_elem)
     *
     * For a radio with AutoPostBack, the browser fires __doPostBack(radioName, ''),
     * and the radio's name=value pair is also submitted (since it's now checked).
     */
    suspend fun selectDayRadio(
        current: ReservationPage,
        dayIndex: Int
    ): ReservationPage = withClient {
        val day = current.days.getOrNull(dayIndex)
            ?: return@withClient current

        val form = baseFormBuilder(current)
            .add("__EVENTTARGET", day.radioName)
            .add("__EVENTARGUMENT", "")
            // Mark the radio as checked by including its name=value in the POST.
            .add(day.radioName, day.radioValue)
            .build()

        val response = client.newCall(
            Request.Builder().url("$baseUrl/WebForm/StudentReserveFood.aspx").post(form).build()
        ).execute()
        val html = response.body?.string().orEmpty()
        response.close()
        parseReservationPage(html)
    }

    /**
     * Convenience: runs the whole "reserve food for the whole week" flow with the
     * defaults that matched the Python script (ناهار + سلف پردیس + first radio).
     *
     * Used by the "Auto-reserve week" button. Reports progress via the callback so the
     * UI can show "Day 2/5..." etc.
     */
    suspend fun reserveWeekWithDefaults(
        mealText: String = "ناهار",
        dietText: String = "سلف پردیس",
        onProgress: (step: String) -> Unit = {}
    ): ReservationResult {
        return try {
            onProgress("Loading reservation page…")
            var page = fetchReservationPage()

            // 1. Select meal
            val mealValue = page.mealOptions.firstOrNull { it.first == mealText }?.second
                ?: return ReservationResult.Failure("Meal \"$mealText\" not found in dropdown.")
            onProgress("Selecting meal: $mealText")
            page = selectMeal(page, mealValue)

            // 2. Click next week
            if (page.nextWeekBtnName == null) {
                return ReservationResult.Failure("Next-week button not found on page.")
            }
            onProgress("Going to next week")
            page = clickNextWeek(page)

            // 3. For each day, select diet then click the first radio
            for (i in 0 until page.days.size) {
                val day = page.days[i]
                val dietValue = day.dietOptions.firstOrNull { it.first == dietText }?.second
                    ?: return ReservationResult.Failure("Diet \"$dietText\" not found for day ${i + 1}.")
                onProgress("Day ${i + 1}/${page.days.size}: selecting $dietText")
                page = selectDayDiet(page, i, dietValue)
                onProgress("Day ${i + 1}/${page.days.size}: confirming radio")
                page = selectDayRadio(page, i)
            }

            onProgress("Done!")
            ReservationResult.Success(page)
        } catch (t: Throwable) {
            ReservationResult.Failure(t.message ?: "Network error during reservation.")
        }
    }

    // ----------------------------------------------------------------------
    // PARSING HELPERS
    // ----------------------------------------------------------------------

    private fun parseReservationPage(html: String): ReservationPage {
        val doc = Jsoup.parse(html)

        val viewState = inputValue(doc, "__VIEWSTATE")
        val eventValidation = inputValue(doc, "__EVENTVALIDATION")
        val viewStateGenerator = inputValue(doc, "__VIEWSTATEGENERATOR")

        // Meal dropdown
        val mealSelect = doc.selectFirst("#body_dpFoodMeal")
        val mealDropdownName = mealSelect?.attr("name") ?: "body_dpFoodMeal"
        val mealOptions = mealSelect?.select("option")?.map { it.text() to (it.attr("value").ifEmpty { it.text() }) }
            ?: emptyList()

        // Next-week button
        val nextWeekBtn = doc.selectFirst("#body_btnNextWeek")
        val nextWeekBtnName = nextWeekBtn?.attr("name")
        val nextWeekBtnValue = nextWeekBtn?.attr("value").orEmpty().ifEmpty { "Next Week" }

        // Per-day dropdowns + radios — pattern from the Python script: 5 days (0..4)
        val days = (0..4).mapNotNull { i ->
            val dayDropdown = doc.selectFirst("#body_rptFoodDiet_dpSelf_$i") ?: return@mapNotNull null
            val dayDropdownName = dayDropdown.attr("name")
            val dietOptions = dayDropdown.select("option").map {
                it.text() to (it.attr("value").ifEmpty { it.text() })
            }

            // First radio in the day's grid: body_rptFoodDiet_grdDiet_{i}_rdoDiet_0
            val radio = doc.selectFirst("#body_rptFoodDiet_grdDiet_${i}_rdoDiet_0")
            val radioName = radio?.attr("name") ?: "body_rptFoodDiet_grdDiet_${i}_rdoDiet_0"
            val radioValue = radio?.attr("value") ?: ""

            // Try to find a day label — usually a heading or label near the dropdown.
            val row = dayDropdown.parent()
            val dayLabel = row?.selectFirst("td, span, label")?.text()?.takeIf { it.isNotBlank() }
                ?: "Day ${i + 1}"

            DayInfo(
                index = i,
                dropdownId = "body_rptFoodDiet_dpSelf_$i",
                dropdownName = dayDropdownName,
                dietOptions = dietOptions,
                radioId = "body_rptFoodDiet_grdDiet_${i}_rdoDiet_0",
                radioName = radioName,
                radioValue = radioValue,
                dayLabel = dayLabel
            )
        }

        return ReservationPage(
            viewState = viewState,
            eventValidation = eventValidation,
            viewStateGenerator = viewStateGenerator,
            mealDropdownName = mealDropdownName,
            mealOptions = mealOptions,
            nextWeekBtnName = nextWeekBtnName,
            nextWeekBtnValue = nextWeekBtnValue,
            days = days
        )
    }

    /** Builds a FormBody pre-populated with the ASP.NET hidden fields. */
    private fun baseFormBuilder(page: ReservationPage): FormBody.Builder = FormBody.Builder()
        .add("__VIEWSTATE", page.viewState)
        .add("__EVENTVALIDATION", page.eventValidation)
        .add("__VIEWSTATEGENERATOR", page.viewStateGenerator)

    private fun inputValue(doc: Document, id: String): String =
        doc.selectFirst("#$id")?.attr("value").orEmpty()

    /** Finds an element by ID and returns its `name` attribute, falling back to a default. */
    private fun nameById(doc: Document, id: String, default: String): String =
        doc.selectFirst("#$id")?.attr("name")?.takeIf { it.isNotEmpty() } ?: default

    /** Pulls a server-rendered error message out of an ASP.NET validation summary. */
    private fun extractServerError(html: String): String? {
        val doc = Jsoup.parse(html)
        // Common patterns: a span with class "errorMessage", an asp:ValidationSummary,
        // or a div with id containing "Message" / "Error".
        val candidates = listOf(
            ".ErrorMessage",
            ".error",
            "[class*=error]",
            "[id*=Message]",
            "[id*=Error]",
            ".alert"
        )
        for (selector in candidates) {
            val el: Element? = doc.selectFirst(selector)
            val text = el?.text()?.trim()
            if (!text.isNullOrEmpty() && text.length < 300) return text
        }
        return null
    }

    // ----------------------------------------------------------------------
    // HTTP WRAPPERS — every public suspend function runs through withClient {}
    // so we don't accidentally block the main thread.
    // ----------------------------------------------------------------------

    private suspend fun <T> withClient(block: () -> T): T =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { block() }

    private fun get(url: String) = client.newCall(Request.Builder().url(url).get().build()).execute()

    // ----------------------------------------------------------------------
    // PUBLIC DATA CLASSES
    // ----------------------------------------------------------------------

    data class LoginPageData(
        val viewState: String,
        val eventValidation: String,
        val viewStateGenerator: String,
        val usernameName: String,
        val passwordName: String,
        val captchaInputName: String,
        val loginBtnName: String,
        val loginBtnValue: String,
        val captchaImage: ByteArray?
    )

    sealed class LoginResult {
        object Success : LoginResult()
        data class Failure(val message: String) : LoginResult()
    }

    data class ReservationPage(
        val viewState: String,
        val eventValidation: String,
        val viewStateGenerator: String,
        val mealDropdownName: String,
        val mealOptions: List<Pair<String, String>>, // (visible text, option value)
        val nextWeekBtnName: String?,
        val nextWeekBtnValue: String?,
        val days: List<DayInfo>
    )

    data class DayInfo(
        val index: Int,
        val dropdownId: String,
        val dropdownName: String,
        val dietOptions: List<Pair<String, String>>, // (visible text, option value)
        val radioId: String,
        val radioName: String,
        val radioValue: String,
        val dayLabel: String
    )

    sealed class ReservationResult {
        data class Success(val finalPage: ReservationPage) : ReservationResult()
        data class Failure(val message: String) : ReservationResult()
    }
}
