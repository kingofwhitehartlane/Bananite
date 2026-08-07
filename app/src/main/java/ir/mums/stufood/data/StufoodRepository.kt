package ir.mums.stufood.data

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.concurrent.TimeUnit

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

    suspend fun fetchLoginPage(): LoginPageData = withClient {
        val html = get("$baseUrl/Default.aspx").use { it.body?.string().orEmpty() }
        val doc = Jsoup.parse(html, baseUrl)

        val viewState = inputValue(doc, "__VIEWSTATE")
        val eventValidation = inputValue(doc, "__EVENTVALIDATION")
        val viewStateGenerator = inputValue(doc, "__VIEWSTATEGENERATOR")

        val usernameName = nameById(doc, "body_txtUsername", default = "body_txtUsername")
        val passwordName = nameById(doc, "body_txtPassword", default = "body_txtPassword")
        val captchaInputName = nameById(doc, "body_txtCaptcha", default = "body_txtCaptcha")

        val loginBtn = doc.selectFirst("#btnLogin")
            ?: doc.selectFirst("input[type=submit]")
            ?: doc.selectFirst("#ctl01")
        val loginBtnName = loginBtn?.attr("name").orEmpty()
        val loginBtnValue = loginBtn?.attr("value").orEmpty().ifEmpty { "Login" }

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
        } else {
            LoginResult.Success
        }
    }

    fun isLoggedIn(): Boolean = cookieJar.hasSessionFor("stufood.mums.ac.ir")

    fun clearSession() = cookieJar.clear()

    // ----------------------------------------------------------------------
    // RESERVATION
    // ----------------------------------------------------------------------

    suspend fun fetchReservationPage(): ReservationPage = withClient {
        val html = get("$baseUrl/WebForm/StudentReserveFood.aspx").body?.string().orEmpty()
        parseReservationPage(html)
    }

    suspend fun selectMeal(current: ReservationPage, mealOptionValue: String): ReservationPage = withClient {
        val form = baseFormBuilder(current)
            .add("__EVENTTARGET", current.mealDropdownName)
            .add("__EVENTARGUMENT", "")
            .add(current.mealDropdownName, mealOptionValue)
            .build()

        val response = client.newCall(
            Request.Builder().url("$baseUrl/WebForm/StudentReserveFood.aspx").post(form).build()
        ).execute()
        val html = response.body?.string().orEmpty()
        response.close()
        parseReservationPage(html)
    }

    suspend fun clickNextWeek(current: ReservationPage): ReservationPage = withClient {
        val builder = baseFormBuilder(current)
            .add("__EVENTTARGET", "")
            .add("__EVENTARGUMENT", "")

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

    suspend fun selectDayRadio(
        current: ReservationPage,
        dayIndex: Int
    ): ReservationPage = withClient {
        val day = current.days.getOrNull(dayIndex)
            ?: return@withClient current

        val form = baseFormBuilder(current)
            .add("__EVENTTARGET", day.radioName)
            .add("__EVENTARGUMENT", "")
            .add(day.radioName, day.radioValue)
            .build()

        val response = client.newCall(
            Request.Builder().url("$baseUrl/WebForm/StudentReserveFood.aspx").post(form).build()
        ).execute()
        val html = response.body?.string().orEmpty()
        response.close()
        parseReservationPage(html)
    }

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

            if (page.nextWeekBtnName == null) {
                return ReservationResult.Failure("Next-week button not found on page.")
            }
            onProgress("Going to next week")
            page = clickNextWeek(page)

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

        val mealSelect = doc.selectFirst("#body_dpFoodMeal")
        val mealDropdownName = mealSelect?.attr("name") ?: "body_dpFoodMeal"
        val mealOptions = mealSelect?.select("option")?.map { it.text() to (it.attr("value").ifEmpty { it.text() }) }
            ?: emptyList()

        val nextWeekBtn = doc.selectFirst("#body_btnNextWeek")
        val nextWeekBtnName = nextWeekBtn?.attr("name")
        val nextWeekBtnValue = nextWeekBtn?.attr("value").orEmpty().ifEmpty { "Next Week" }

        val days = (0..4).mapNotNull { i ->
            val dayDropdown = doc.selectFirst("#body_rptFoodDiet_dpSelf_$i") ?: return@mapNotNull null
            val dayDropdownName = dayDropdown.attr("name")
            val dietOptions = dayDropdown.select("option").map {
                it.text() to (it.attr("value").ifEmpty { it.text() })
            }

            val radio = doc.selectFirst("#body_rptFoodDiet_grdDiet_${i}_rdoDiet_0")
            val radioName = radio?.attr("name") ?: "body_rptFoodDiet_grdDiet_${i}_rdoDiet_0"
            val radioValue = radio?.attr("value") ?: ""

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

    private fun baseFormBuilder(page: ReservationPage): FormBody.Builder = FormBody.Builder()
        .add("__VIEWSTATE", page.viewState)
        .add("__EVENTVALIDATION", page.eventValidation)
        .add("__VIEWSTATEGENERATOR", page.viewStateGenerator)

    private fun inputValue(doc: Document, id: String): String =
        doc.selectFirst("#$id")?.attr("value").orEmpty()

    private fun nameById(doc: Document, id: String, default: String): String =
        doc.selectFirst("#$id")?.attr("name")?.takeIf { it.isNotEmpty() } ?: default

    private fun extractServerError(html: String): String? {
        val doc = Jsoup.parse(html)
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
    // HTTP WRAPPERS
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
        val mealOptions: List<Pair<String, String>>,
        val nextWeekBtnName: String?,
        val nextWeekBtnValue: String?,
        val days: List<DayInfo>
    )

    data class DayInfo(
        val index: Int,
        val dropdownId: String,
        val dropdownName: String,
        val dietOptions: List<Pair<String, String>>,
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