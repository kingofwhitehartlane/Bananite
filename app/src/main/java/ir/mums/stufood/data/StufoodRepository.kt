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
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Talks to stufood.mums.ac.ir.
 *
 * IMPORTANT — read this before touching parseReservationPage / postForm:
 *
 * The real site posts every field of the ASP.NET WebForm on every single postback
 * (that's how classic WebForms / __doPostBack works — the whole form, not just the
 * changed control). Rather than hardcode every hidden field name, we generically walk
 * the parsed HTML form and resend every field we find, only overriding the ones that
 * represent the actual UI action (e.g. a day's cafeteria dropdown, or a diet radio),
 * exactly like a browser would.
 *
 * Also important: requests must be multipart/form-data (the real site's form has a
 * file upload control, ctl00$body$fuAttachment), not URL-encoded.
 *
 * Per-day UI on the reservation page is discovered generically by scanning for
 * `lblDayDate_<N>` elements — that span is present on the page regardless of the
 * day's state (not-allowed, needs-cafeteria, needs-diet, already-reserved, received,
 * not-received, not-reserved, or "no food defined yet"), so it's the one reliable
 * anchor we can enumerate days from. Everything else for that day (cafeteria select,
 * diet table, "no food defined" banner, comment button, cancel button, exchange
 * button) is looked up by the same `<N>` suffix and may or may not be present,
 * depending on state.
 *
 * FOOD EXCHANGE ("تبادل غذا") — read this before touching the exchange functions:
 *
 * When a day's diet is locked (deadline passed, radio disabled) but the site still
 * allows offering the food for exchange, a `btnSellFood` image button appears next to
 * the checked option. Clicking it (site JS: `sellFood(this)`) opens a modal with:
 *   - a radio group (`ctl00$body$rbList`, values 1/2/3) choosing the exchange kind
 *   - two dropdowns (`dpSelectSelf` / `dpFoodForExchange`) shown for one kind
 *   - a student-number search (`txtSearchStuNum` / `btnSearchStuFood`) shown for another
 *   - a confirm button (`btnExchangeFood`)
 * Once a request is placed, the button is replaced by `btnCancelSellFood`.
 *
 * We model this the same way as everything else on this page: clicking `btnSellFood`
 * is submitted as an image-button click (x/y coordinates, same convention as the diet
 * cancel button), and every subsequent step (changing the radio, changing a dropdown,
 * searching for a student, confirming) is submitted as a normal postback carrying the
 * whole form snapshot, exactly like `selectMeal`/`selectCafeteria` do.
 *
 * ASSUMPTION FLAG: `sellFood(this)` is a JS function name, which raises the
 * possibility the real site actually populates the two dropdowns and toggles the
 * modal's sections via a client-side AJAX/PageMethod call rather than a full
 * postback. This code assumes a normal postback (consistent with how every other
 * control on this page behaves). If the exchange dialog renders empty or the
 * dropdowns don't show up correctly in the app, capture a HAR of clicking through
 * the exchange flow on the real site (the same way the login fields were captured)
 * and send it over — the field names above are already correct, only the
 * request/response shape around them might need adjusting.
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
    private val mainStudentUrl = "$baseUrl/WebForm/Student/Form_MainStudent.aspx"

    // In-memory cache for the student's full name
    private val _studentName = MutableStateFlow<String?>(null)
    val studentName: StateFlow<String?> = _studentName

    /** Full name shown on the student's main page (#body_lblFullname). Null if not logged in / not found. */
    suspend fun fetchStudentFullName(): String? = withClient {
        val response = get(mainStudentUrl)
        val html = response.use { it.body?.string().orEmpty() }
        val doc = Jsoup.parse(html, mainStudentUrl)
        val name = doc.getElementById("body_lblFullname")?.text()?.trim()?.takeIf { it.isNotEmpty() }
        if (name != null) {
            _studentName.value = name // Cache it in the repository
        }
        name
    }

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
        const val TODAY_BTN = "ctl00\$body\$btnToday"
        const val FILE_UPLOAD = "ctl00\$body\$fuAttachment"
        const val CREDIT_ELEMENT_ID = "body_lblStuCredit"
        const val PLACEHOLDER_VALUE = "0"

        // ---- Food exchange ("تبادل غذا") modal ----
        const val EXCHANGE_TYPE = "ctl00\$body\$rbList"
        const val EXCHANGE_SELF = "ctl00\$body\$dpSelectSelf"
        const val EXCHANGE_FOOD = "ctl00\$body\$dpFoodForExchange"
        const val EXCHANGE_STUDENT_NUM = "ctl00\$body\$txtSearchStuNum"
        const val EXCHANGE_STUDENT_SEARCH_BTN = "ctl00\$body\$btnSearchStuFood"
        const val EXCHANGE_CONFIRM_BTN = "ctl00\$body\$btnExchangeFood"

        // ---- NEW: Hidden fields that the site's JS populates before a postback ----
        const val SELECTED_FOOD_ID = "ctl00\$body\$hdnSelectFood"
        const val SELECTED_SELF_FOR_EXCHANGE = "ctl00\$body\$hdnSelectedSelfForExchange"
        const val SELECTED_FOOD_FOR_EXCHANGE = "ctl00\$body\$hdnSelectedFoodForExchange"
    }

    // Badge labels for the header <div>'s CSS class, per the legend on the site:
    //   NotRecivedFood, RecivedFood, DaySeal, ChangeFood, OnlineDaySaleReserve,
    //   NoReserve, NoFoodChange, OnlineDaySaleWaitingReserve
    private val statusBadgeLabels = mapOf(
        "NotRecivedFood" to "\u062f\u0631\u06cc\u0627\u0641\u062a \u0646\u06a9\u0631\u062f\u0647", // دریافت نکرده
        "RecivedFood" to "\u062f\u0631\u06cc\u0627\u0641\u062a \u06a9\u0631\u062f\u0647", // دریافت کرده
        "DaySeal" to "\u0631\u0648\u0632 \u0641\u0631\u0648\u0634", // روز فروش
        "ChangeFood" to "\u0627\u0645\u06a9\u0627\u0646 \u062a\u063a\u06cc\u06cc\u0631 \u063a\u0630\u0627", // امکان تغییر غذا (اصلاح شد)
        "OnlineDaySaleReserve" to "\u0631\u0648\u0632\u0641\u0631\u0648\u0634 \u0622\u0646\u0644\u0627\u06cc\u0646", // روزفروش آنلاین
        "NoReserve" to "\u0645\u0647\u0644\u062a \u0631\u0632\u0631\u0648 \u06af\u0630\u0634\u062a\u0647 \u0627\u0633\u062a", // مهلت رزرو گذشته است
        "NoFoodChange" to "\u0639\u062f\u0645 \u0627\u0645\u06a9\u0627\u0646 \u062a\u063a\u06cc\u06cc\u0631 \u063a\u0630\u0627", // عدم امکان تغییر غذا (اصلاح شد)
        "OnlineDaySaleWaitingReserve" to "\u062f\u0631 \u0644\u06cc\u0633\u062a \u0627\u0646\u062a\u0638\u0627\u0631 \u062a\u0627\u06cc\u06cc\u062f \u0631\u0648\u0632\u0641\u0631\u0648\u0634" // در لیست انتظار تایید روزفروش
    )

    // ----------------------------------------------------------------------
    // LOGIN  (unchanged)
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

        parseLoginResponse(responseText)
    }

    /**
     * The endpoint replies with an ASP.NET-style AJAX envelope, e.g.
     * `{"d":{"Key":false,"Value":"ERROR MESSAGE HERE"}}`. We only ever want to show
     * the person the human-readable `Value` — never the surrounding JSON — and we
     * fall back to a generic message if the shape doesn't match what we expect (site
     * change, empty body, etc.) rather than leaking raw JSON/text into the UI.
     */
    private fun parseLoginResponse(responseText: String): LoginResult {
        val d = try {
            org.json.JSONObject(responseText).optJSONObject("d")
        } catch (_: Exception) {
            null
        }

        val key = d?.optBoolean("Key", false) ?: false
        if (key) return LoginResult.Success

        val value = d?.optString("Value")?.trim()
        val message = if (!value.isNullOrEmpty()) value else "Login failed. Please check your credentials and try again."
        return LoginResult.Failure(message)
    }

    fun isLoggedIn(): Boolean = cookieJar.hasSessionFor("stufood.mums.ac.ir")

    fun clearSession() {
        cookieJar.clear()
        _studentName.value = null // Clear the cached name on logout
    }

    // ----------------------------------------------------------------------
    // RESERVATION — page load & navigation
    // ----------------------------------------------------------------------

    suspend fun fetchReservationPage(): ReservationPage = withClient {
        val response = get(reservationUrl)
        val html = response.use { it.body?.string().orEmpty() }
        parseReservationPage(html)
    }

    /** Change the meal dropdown (e.g. \u0646\u0627\u0647\u0627\u0631 / lunch). */
    suspend fun selectMeal(current: ReservationPage, mealOptionValue: String): ReservationPage =
        postForm(current, eventTarget = Fields.MEAL, overrides = mapOf(Fields.MEAL to mealOptionValue))

    suspend fun clickToday(current: ReservationPage): ReservationPage {
        if (!current.today.isUsable) return current
        return postForm(
            current,
            eventTarget = "",
            overrides = mapOf(Fields.TODAY_BTN to "\u0627\u0645\u0631\u0648\u0632"),
            clickedButton = Fields.TODAY_BTN
        )
    }

    suspend fun clickNextWeek(current: ReservationPage): ReservationPage {
        if (!current.nextWeek.isUsable) return current
        return postForm(
            current,
            eventTarget = "",
            overrides = mapOf(Fields.NEXT_WEEK_BTN to "next"),
            clickedButton = Fields.NEXT_WEEK_BTN
        )
    }

    suspend fun clickLastWeek(current: ReservationPage): ReservationPage {
        if (!current.lastWeek.isUsable) return current
        return postForm(
            current,
            eventTarget = "",
            overrides = mapOf(Fields.LAST_WEEK_BTN to "prev"),
            clickedButton = Fields.LAST_WEEK_BTN
        )
    }

    /**
     * Step 1 of reserving a day that's in [DayStatus.SELECT_CAFETERIA]: pick a
     * cafeteria. The server responds with the diet options for that day (the day
     * moves to [DayStatus.SELECT_DIET]).
     */
    suspend fun selectCafeteria(current: ReservationPage, day: DayInfo, cafeteriaValue: String): ReservationPage {
        val fieldName = day.cafeteriaFieldName ?: return current
        return postForm(current, eventTarget = fieldName, overrides = mapOf(fieldName to cafeteriaValue))
    }

    /**
     * Step 2: pick (or change) the actual food for a day whose diet table is showing
     * ([DayStatus.SELECT_DIET] or [DayStatus.RESERVED]). Per the site's behavior this
     * commits the reservation immediately — there's no separate confirm step.
     */
    suspend fun selectDiet(current: ReservationPage, option: DietOption): ReservationPage =
        postForm(current, eventTarget = option.fieldName, overrides = mapOf(option.fieldName to "rdoDiet"))

    /**
     * Cancels an already-reserved diet for a day. On the real site this is an
     * `<input type="image">` button, which browsers submit as `name.x` / `name.y`
     * coordinate fields rather than a normal `__doPostBack` — we mirror that here.
     * Always re-check [DietOption.cancelFieldName] against the freshest page; it's
     * null whenever cancellation isn't available for that option (e.g. once the diet
     * is locked, the site only exposes the exchange flow — see [openExchangeDialog]).
     */
    suspend fun cancelDiet(current: ReservationPage, option: DietOption): ReservationPage? {
        val cancelField = option.cancelFieldName ?: return null
        return postForm(
            current,
            eventTarget = "",
            overrides = emptyMap(),
            extraFields = mapOf("$cancelField.x" to "1", "$cancelField.y" to "1")
        )
    }

    // ----------------------------------------------------------------------
    // FOOD EXCHANGE ("تبادل غذا") — see the class-level doc comment for the model
    // ----------------------------------------------------------------------

    /**
     * Opens the exchange dialog for a locked-but-exchangeable [option].
     *
     * IMPORTANT: On the real site, clicking btnSellFood does NOT cause a postback.
     * The JS handler `sellFood(this)` sets a hidden field (`hdnSelectFood`) and shows
     * the modal client-side via Bootstrap. The modal HTML is already present in every
     * page response (just hidden), so [ReservationPage.exchangeDialog] is always
     * populated after parsing. We just return the current page unchanged — the
     * ViewModel reads the already-parsed dialog data and shows it.
     *
     * The food ID from the button's `attre` attribute (stored in
     * [DietOption.exchangeFoodId]) is needed later for AJAX calls and the confirm
     * postback, but is NOT sent in a postback here.
     */
    suspend fun openExchangeDialog(current: ReservationPage, option: DietOption): ReservationPage {
        // No postback — the modal is already in the page HTML.
        return current
    }

    /**
     * Fetches the list of cafeterias available for food exchange, via the site's
     * `GetSelfData` PageMethod (AJAX, not a full postback). Called when the user
     * selects exchange type "2" (تعویض غذا). The site's JS function is
     * `getSeachSelfData()`.
     */
    suspend fun fetchExchangeSelfOptions(foodMeal: String, foodId: String): List<Pair<String, String>> = withClient {
        val payload = org.json.JSONObject().apply {
            put("_foodMeal", foodMeal)
            put("_fid", foodId)
        }.toString()

        val request = Request.Builder()
            .url("$reservationUrl/GetSelfData")
            .header("Content-Type", "application/json; charset=utf-8")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", reservationUrl)
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val text = response.use { it.body?.string().orEmpty() }
        parseAjaxOptionsResponse(text)
    }

    /**
     * Fetches the list of foods available for exchange at a given cafeteria, via the
     * site's `GetFoodData` PageMethod (AJAX). Called when the user picks a cafeteria
     * in the "تعویض غذا" flow. The site's JS is the `dpSelectSelf.change` handler.
     */
    suspend fun fetchExchangeFoodOptions(foodMeal: String, foodId: String, selfId: String): List<Pair<String, String>> = withClient {
        val payload = org.json.JSONObject().apply {
            put("_foodMeal", foodMeal)
            put("_fid", foodId)
            put("_sid", selfId)
        }.toString()

        val request = Request.Builder()
            .url("$reservationUrl/GetFoodData")
            .header("Content-Type", "application/json; charset=utf-8")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", reservationUrl)
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val text = response.use { it.body?.string().orEmpty() }
        parseAjaxOptionsResponse(text)
    }

    /**
     * Parses the JSON response from an ASP.NET PageMethod, which wraps the result
     * in a `d` property. The exchange dropdowns return arrays of
     * `{ "Value": "...", "Text": "..." }` objects.
     */
    private fun parseAjaxOptionsResponse(text: String): List<Pair<String, String>> {
        val arr = try {
            org.json.JSONObject(text).optJSONArray("d")
        } catch (_: Exception) { null } ?: return emptyList()

        val result = mutableListOf<Pair<String, String>>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i)
            val label = item?.optString("Text")?.trim().orEmpty()
            val value = item?.optString("Value")?.trim().orEmpty()
            result.add(label to value)
        }
        return result
    }

    /**
     * Looks up a destination student by number, for the "تعویض غذا با سایرین" flow.
     * This IS a real postback (the search button is a submit button), but it must
     * carry `hdnSelectFood` and `rbList` so the server knows which food and which
     * exchange type the request belongs to.
     */
    suspend fun searchDestinationStudent(current: ReservationPage, request: ExchangeRequest): ReservationPage =
        postForm(
            current,
            eventTarget = "",
            overrides = mapOf(
                Fields.SELECTED_FOOD_ID to request.foodId,
                Fields.EXCHANGE_TYPE to request.exchangeType,
                Fields.EXCHANGE_STUDENT_NUM to request.studentNumber
            ),
            extraFields = mapOf(Fields.EXCHANGE_STUDENT_SEARCH_BTN to "\u062c\u0633\u062a\u062c\u0648") // جستجو
        )

    /**
     * Confirms & submits whichever exchange request is currently configured in the dialog.
     * This is a real postback (the confirm button is a submit button). The site's JS
     * `btnExchangeFood()` function sets hidden fields from the dropdowns before allowing
     * the submit — we do the same via [ExchangeRequest].
     */
    suspend fun confirmExchange(current: ReservationPage, request: ExchangeRequest): ReservationPage {
        val overrides = mutableMapOf(
            Fields.SELECTED_FOOD_ID to request.foodId,
            Fields.EXCHANGE_TYPE to request.exchangeType
        )
        if (request.exchangeType == "2") {
            // The JS sets these hidden fields from the dropdown values before submitting.
            overrides[Fields.SELECTED_SELF_FOR_EXCHANGE] = request.selectedSelf
            overrides[Fields.SELECTED_FOOD_FOR_EXCHANGE] = request.selectedFood
            // Also send the dropdown values themselves, as the browser would.
            overrides[Fields.EXCHANGE_SELF] = request.selectedSelf
            overrides[Fields.EXCHANGE_FOOD] = request.selectedFood
        }
        if (request.exchangeType == "3") {
            overrides[Fields.EXCHANGE_STUDENT_NUM] = request.studentNumber
        }
        return postForm(
            current,
            eventTarget = "",
            overrides = overrides,
            extraFields = mapOf(Fields.EXCHANGE_CONFIRM_BTN to "\u062a\u0627\u06cc\u06cc\u062f \u0648 \u062b\u0628\u062a \u062f\u0631\u062e\u0648\u0627\u0633\u062a") // تایید و ثبت درخواست
        )
    }

    /**
     * Withdraws a pending exchange offer for [option] (the `btnCancelSellFood` image
     * button that replaces `btnSellFood` once a request has been placed).
     */
    suspend fun cancelExchange(current: ReservationPage, option: DietOption): ReservationPage? {
        val cancelField = option.cancelExchangeFieldName ?: return null
        return postForm(
            current,
            eventTarget = "",
            overrides = emptyMap(),
            extraFields = mapOf("$cancelField.x" to "1", "$cancelField.y" to "1")
        )
    }

    // ----------------------------------------------------------------------
    // GENERIC FORM POST
    // ----------------------------------------------------------------------

    private val navButtons = listOf(Fields.NEXT_WEEK_BTN, Fields.LAST_WEEK_BTN, Fields.TODAY_BTN)

    private suspend fun postForm(
        current: ReservationPage,
        eventTarget: String,
        overrides: Map<String, String>,
        clickedButton: String? = null,
        extraFields: Map<String, String> = emptyMap()
    ): ReservationPage = withClient {
        val bodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)

        // Start from every field we found on the page (hidden inputs, text inputs,
        // selects, checked radios/checkboxes) so nothing the server expects goes
        // missing — this mirrors what a real browser submits.
        val fields = LinkedHashMap(current.fieldSnapshot)

        fields["__EVENTTARGET"] = eventTarget
        fields["__EVENTARGUMENT"] = ""

        overrides.forEach { (k, v) -> fields[k] = v }

        // Nav buttons (Next week / Previous week / Today) are only sent when
        // "clicked" — drop the others so we don't imply pressing several at once.
        navButtons.forEach { name -> if (name != clickedButton) fields.remove(name) }

        fields.forEach { (name, value) ->
            if (name == Fields.FILE_UPLOAD) return@forEach // handled below
            bodyBuilder.addFormDataPart(name, value)
        }
        extraFields.forEach { (name, value) -> bodyBuilder.addFormDataPart(name, value) }

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

    private val dayDateIdRegex = Regex("^body_rptFoodDiet_lblDayDate_(\\d+)$")

    private fun parseReservationPage(html: String): ReservationPage {
        val doc = Jsoup.parse(html, reservationUrl)

        // ---- Meal dropdown ("\u0627\u0646\u062a\u062e\u0627\u0628 \u0646\u0645\u0627\u06cc\u06cc\u062f" placeholder included) ----
        val mealSelect = doc.selectFirst("select[name='${Fields.MEAL}']")
        val mealOptions = mealSelect?.select("option")
            ?.map { it.text().trim() to (it.attr("value").ifEmpty { it.text() }) }
            ?: emptyList()
        val selectedMeal = mealSelect?.select("option[selected]")?.firstOrNull()?.attr("value")
            ?: mealOptions.firstOrNull { it.second == Fields.PLACEHOLDER_VALUE }?.second
            ?: Fields.PLACEHOLDER_VALUE

        // ---- Credit / balance ----
        val creditRaw = doc.getElementById(Fields.CREDIT_ELEMENT_ID)?.text()?.trim()
        val creditToman = creditRaw?.let { parseRialTextToTomanLabel(it) }

        // ---- Nav buttons ----
        val today = parseButtonState(doc, Fields.TODAY_BTN)
        val nextWeek = parseButtonState(doc, Fields.NEXT_WEEK_BTN)
        val lastWeek = parseButtonState(doc, Fields.LAST_WEEK_BTN)

        // ---- Days: discovered generically via lblDayDate_<N>, which is always
        // present no matter the day's state. ----
        val dayIndices = doc.select("span[id]")
            .mapNotNull { el -> dayDateIdRegex.find(el.attr("id"))?.groupValues?.get(1)?.toIntOrNull() }
            .distinct()
            .sorted()

        val days = dayIndices.map { i -> parseDay(doc, i) }

        return ReservationPage(
            fieldSnapshot = snapshotForm(doc),
            mealOptions = mealOptions,
            selectedMeal = selectedMeal,
            creditToman = creditToman,
            today = today,
            nextWeek = nextWeek,
            lastWeek = lastWeek,
            days = days,
            exchangeDialog = parseExchangeDialog(doc)
        )
    }

    private fun parseDay(doc: Document, i: Int): DayInfo {
        val dateLabel = doc.getElementById("body_rptFoodDiet_lblDayDate_$i")?.text()?.trim().orEmpty()
        val rawMessage = doc.getElementById("body_rptFoodDiet_lblMsg_$i")?.text()?.trim().orEmpty()
        val message = rawMessage.trim('(', ')', ' ', '\u200c').trim().ifEmpty { null }

        val selfLabelEl = doc.getElementById("body_rptFoodDiet_lblSelf_$i")
        val selfLabelText = selfLabelEl?.text()?.trim()

        val cafeteriaSelect = doc.getElementById("body_rptFoodDiet_dpSelf_$i")
        val cafeteriaFieldName = cafeteriaSelect?.attr("name")?.takeIf { it.isNotBlank() }
        val cafeteriaOptions = cafeteriaSelect?.select("option")
            ?.map { it.text().trim() to (it.attr("value").ifEmpty { it.text() }) }
            ?: emptyList()
        val selectedCafeteria = cafeteriaSelect?.select("option[selected]")?.firstOrNull()?.attr("value")
            ?: cafeteriaOptions.firstOrNull()?.second

        val noFoodEl = doc.getElementById("body_rptFoodDiet_dvNoFood_$i")
        val noFoodDefined = noFoodEl != null && noFoodEl.text().isNotBlank()

        val table = doc.getElementById("body_rptFoodDiet_grdDiet_$i")
        val dietOptions = table?.select("input[type=radio]")?.map { radio -> parseDietOption(table, radio) }
            ?: emptyList()

        val commentBtn = doc.getElementById("body_rptFoodDiet_btnComment_$i")
        val commentFieldName = commentBtn?.attr("name")?.takeIf { it.isNotBlank() }

        val headerDiv = doc.getElementById("body_rptFoodDiet_dvHeader_$i")
        val statusBadge = headerDiv?.classNames()?.firstNotNullOfOrNull { statusBadgeLabels[it] }
        
        val daySealText = doc.getElementById("body_rptFoodDiet_lblDaySeals_$i")
            ?.text()?.trim()?.ifEmpty { null }

        val hasSelf = !selfLabelText.isNullOrBlank()
        val hasSelect = cafeteriaFieldName != null
        val hasTable = dietOptions.isNotEmpty()
        val checkedOption = dietOptions.firstOrNull { it.checked }
        val hasExchangeAction = dietOptions.any {
            it.exchangeFieldName != null || it.cancelExchangeFieldName != null
        }

        val status = when {
            noFoodDefined -> DayStatus.NO_FOOD_DEFINED

            // Informational-only day (no cafeteria select, no diet table): either the
            // day is off-limits, or nothing was reserved for it at all.
            hasSelf && !hasSelect && !hasTable -> {
                if (containsAny("\u0645\u062c\u0627\u0632", message, selfLabelText)) {
                    DayStatus.NOT_ALLOWED
                } else {
                    DayStatus.NOT_RESERVED
                }
            }

            // Read-only history with a diet table (past reservation): received / not
            // received, told apart by the message text.
            hasSelf && hasTable -> {
                when {
                    containsAny("دریافت نشده", message) -> DayStatus.NOT_RECEIVED
                    containsAny("دریافت شده", message) -> DayStatus.RECEIVED

                    // If the site is still offering exchange, this is not a plain received day.
                    hasExchangeAction -> DayStatus.RESERVED

                    checkedOption?.disabled == true -> DayStatus.RECEIVED
                    else -> DayStatus.RESERVED
                }
            }

            // Cafeteria chosen but nothing chosen yet, still needs to pick a cafeteria.
            hasSelect && !hasTable -> DayStatus.SELECT_CAFETERIA

            // Cafeteria select is present alongside the diet table. A *disabled*
            // checked radio here does NOT mean "received" by itself — the deadline for
            // changing the diet may simply have passed while the reservation (and
            // possibly the cafeteria choice, or an exchange offer) is still active;
            // the real "received" / "not received" distinction only ever shows up via
            // the day's message text, exactly like the branch above. Treating disabled
            // as RECEIVED here used to hide the cafeteria dropdown and the exchange
            // controls on days like "( فقط امکان تغییر سلف می باشد )" — fixed.
            hasSelect && hasTable -> {
                when {
                    checkedOption == null -> DayStatus.SELECT_DIET
                    containsAny("\u062f\u0631\u06cc\u0627\u0641\u062a \u0646\u0634\u062f\u0647", message) -> DayStatus.NOT_RECEIVED
                    containsAny("\u062f\u0631\u06cc\u0627\u0641\u062a \u0634\u062f\u0647", message) -> DayStatus.RECEIVED
                    else -> DayStatus.RESERVED
                }
            }

            else -> DayStatus.UNKNOWN
        }

        return DayInfo(
            index = i,
            dateLabel = dateLabel,
            message = message,
            statusBadge = statusBadge,
            status = status,
            selfLabel = selfLabelText,
            cafeteriaFieldName = cafeteriaFieldName,
            cafeteriaOptions = cafeteriaOptions,
            selectedCafeteria = selectedCafeteria,
            dietOptions = dietOptions,
            commentFieldName = commentFieldName,
            daySealText = daySealText
        )
    }

    private fun containsAny(needle: String, vararg texts: String?): Boolean =
        texts.any { it != null && it.contains(needle) }

    private fun parseDietOption(table: Element, radio: Element): DietOption {
        val name = radio.attr("name")
        val checked = radio.hasAttr("checked")
        val disabled = radio.hasAttr("disabled")
        val radioId = radio.attr("id")
        val labelEl = table.selectFirst("label[for='$radioId']")
        val rawText = labelEl?.text().orEmpty()
        val (foodName, priceRial) = parseDietLabelText(rawText)

        val row = radio.parents().firstOrNull { it.tagName() == "tr" }

        // FIX: Changed `~=` (which means exact word match) to `$=` (ends with) 
        // because the name attribute is a single long string, not space-separated words.

        // The cancel button (an <input type="image"> whose name ends in btnCancel)
        // lives in the same row as the checked radio, when cancellation is allowed.
        val cancelBtn = row?.selectFirst("input[type=image][name\$=btnCancel]")
        val cancelFieldName = if (checked) cancelBtn?.attr("name")?.takeIf { it.isNotBlank() } else null

        // "درخواست تبادل با دانشجویان" — offered when the diet is locked (or otherwise
        // not directly cancellable) but the site still allows putting it up for
        // exchange. Lives in the same row as the checked radio.
        val exchangeBtn = row?.selectFirst("input[type=image][name\$=btnSellFood]")
        val exchangeFieldName = exchangeBtn?.attr("name")?.takeIf { it.isNotBlank() }
        val exchangeFoodId = exchangeBtn?.attr("attre")?.takeIf { it.isNotBlank() }

        // Once an exchange request has been placed, the site swaps btnSellFood for
        // btnCancelSellFood in the same spot — "انصراف از تبادل غذا".
        val cancelExchangeBtn = row?.selectFirst("input[type=image][name\$=btnCancelSellFood]")
        val cancelExchangeFieldName = cancelExchangeBtn?.attr("name")?.takeIf { it.isNotBlank() }

        return DietOption(
            fieldName = name,
            label = foodName,
            priceRial = priceRial,
            priceToman = priceRial?.let { formatRialAsToman(it) },
            checked = checked,
            disabled = disabled,
            cancelFieldName = cancelFieldName,
            exchangeFieldName = exchangeFieldName,
            cancelExchangeFieldName = cancelExchangeFieldName,
            exchangeFoodId = exchangeFoodId  // NEW
        )
    }

    /**
     * Splits a diet radio's label text (e.g. "\u0686\u0644\u0648 \u06a9\u0628\u0627\u0628 \u06a9\u0648\u0628\u06cc\u062f\u0647 -  (\u06a9\u0627\u0644\u0631\u06cc : 1050) ( \u0642\u06cc\u0645\u062a : 160000 \u0631\u06cc\u0627\u0644)")
     * into the food name (calories dropped entirely, per requirements) and the Rial
     * price. Both "\u0642\u06cc\u0645\u062a" and "\u0642\u06cc\u0645\u062a" spelling variants seen on the site are handled,
     * as is the optional trailing "\u0631\u06cc\u0627\u0644" unit and the presence/absence of a "-" separator.
     */
    private fun parseDietLabelText(rawText: String): Pair<String, Long?> {
        val priceRegex = Regex("(\u0642\u06cc\u0645\u062a|\u0642\u064a\u0645\u062a)\\s*[:\uff1a]?\\s*([0-9\u06f0-\u06f9,\u066c]+)")
        val priceMatch = priceRegex.find(rawText)
        val price = priceMatch?.groupValues?.get(2)
            ?.replace(",", "")
            ?.replace("\u066c", "")
            ?.let { normalizePersianDigits(it) }
            ?.toLongOrNull()

        // Name = everything before the first "(" (calorie/price parenthetical),
        // with a trailing dash/space trimmed off.
        val name = rawText.substringBefore("(").trim().trimEnd('-', ' ').trim()

        return name to price
    }

    private fun normalizePersianDigits(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            sb.append(
                when (c) {
                    in '\u06F0'..'\u06F9' -> ('0' + (c - '\u06F0'))
                    in '\u0660'..'\u0669' -> ('0' + (c - '\u0660'))
                    else -> c
                }
            )
        }
        return sb.toString()
    }

    /** e.g. "12,510,000" (Rial, as shown on the site) -> "1,251,000T" (Toman). */
    private fun parseRialTextToTomanLabel(rialText: String): String? {
        val digitsOnly = normalizePersianDigits(rialText.replace(",", "").replace("\u066c", "").trim())
        val rial = digitsOnly.toLongOrNull() ?: return null
        return formatRialAsToman(rial)
    }

    private fun formatRialAsToman(rial: Long): String {
        val toman = rial / 10
        return String.format(Locale.US, "%,d", toman) + "T"
    }

    /**
     * A nav button (Today / Next week / Previous week) can be: absent entirely,
     * present but disabled, or present and clickable. We treat only the last case as
     * usable.
     */
    private fun parseButtonState(doc: Document, fieldName: String): ButtonState {
        val btn = doc.selectFirst("input[name='$fieldName']") ?: return ButtonState(exists = false, isUsable = false)
        val disabled = btn.hasAttr("disabled")
        return ButtonState(exists = true, isUsable = !disabled)
    }

    /**
     * Parses the "تبادل غذا" (food exchange) modal, if it's present in this response.
     * The modal's controls (`rbList`, `dpSelectSelf`, `dpFoodForExchange`,
     * `txtSearchStuNum`, `dvChangeFood`, `dvChangeFoodWidthStudent`,
     * `lblDestStudent`) are page-level, singleton controls — there's exactly one
     * modal, regardless of which day/option opened it, so we just look for `rbList`
     * to decide whether the dialog is currently showing.
     */
    private fun parseExchangeDialog(doc: Document): ExchangeDialogData? {
        val radios = doc.select("input[name='${Fields.EXCHANGE_TYPE}']")
        if (radios.isEmpty()) return null

        val types = radios.map { radio ->
            val id = radio.attr("id")
            val label = doc.selectFirst("label[for='$id']")?.text()?.trim().orEmpty()
            label to radio.attr("value")
        }
        val selectedType = radios.firstOrNull { it.hasAttr("checked") }?.attr("value")
            ?: types.firstOrNull()?.second.orEmpty()

        val selfSelect = doc.getElementById("dpSelectSelf")
        val selfOptions = selfSelect?.select("option")?.map { it.text().trim() to it.attr("value") } ?: emptyList()
        val selectedSelf = selfSelect?.select("option[selected]")?.firstOrNull()?.attr("value")
            ?: selfOptions.firstOrNull()?.second

        val foodSelect = doc.getElementById("dpFoodForExchange")
        val foodOptions = foodSelect?.select("option")?.map { it.text().trim() to it.attr("value") } ?: emptyList()
        val selectedFood = foodSelect?.select("option[selected]")?.firstOrNull()?.attr("value")
            ?: foodOptions.firstOrNull()?.second

        val changeFoodDiv = doc.getElementById("dvChangeFood")
        val showChangeFoodFields = changeFoodDiv != null && !isHidden(changeFoodDiv)

        val studentDiv = doc.getElementById("dvChangeFoodWidthStudent")
        val showStudentSearchFields = studentDiv != null && !isHidden(studentDiv)

        val destLabel = doc.getElementById("body_lblDestStudent")?.text()?.trim()?.ifEmpty { null }
        val studentNumber = doc.getElementById("body_txtSearchStuNum")?.attr("value")?.ifEmpty { null }

        return ExchangeDialogData(
            exchangeTypes = types,
            selectedExchangeType = selectedType,
            selfOptions = selfOptions,
            selectedSelf = selectedSelf,
            foodOptions = foodOptions,
            selectedFood = selectedFood,
            showChangeFoodFields = showChangeFoodFields,
            showStudentSearchFields = showStudentSearchFields,
            studentNumber = studentNumber,
            destStudentLabel = destLabel
        )
    }

    private fun isHidden(el: Element): Boolean {
        val style = el.attr("style").replace(" ", "").lowercase()
        return style.contains("display:none")
    }

    /**
     * Generic ASP.NET-postback-style form snapshot: every hidden/text input's value,
     * every select's currently-selected option, every checked radio/checkbox. Submit
     * and image buttons are deliberately excluded (they're added back explicitly only
     * when "clicked"); the file input is sent separately as an empty file part.
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
        /** "0" means the placeholder ("\u0627\u0646\u062a\u062e\u0627\u0628 \u0646\u0645\u0627\u06cc\u06cc\u062f") is selected — i.e. nothing chosen. */
        val selectedMeal: String,
        /** Pre-formatted balance, e.g. "1,251,000T" — null if it couldn't be found. */
        val creditToman: String?,
        val today: ButtonState,
        val nextWeek: ButtonState,
        val lastWeek: ButtonState,
        val days: List<DayInfo>,
        /** Non-null only while the food-exchange modal is showing in this response. */
        val exchangeDialog: ExchangeDialogData?
    )

    /** exists = the button is present in the DOM at all; isUsable = present AND not disabled. */
    data class ButtonState(val exists: Boolean, val isUsable: Boolean)

    enum class DayStatus {
        /** Cafeteria hasn't been picked yet for this day. */
        SELECT_CAFETERIA,
        /** Cafeteria picked; pick a diet to reserve. */
        SELECT_DIET,
        /** A diet is reserved and (usually) still changeable / cancellable / exchangeable. */
        RESERVED,
        /** Past day, food was picked up. */
        RECEIVED,
        /** Past day, a reservation existed but food wasn't picked up. */
        NOT_RECEIVED,
        /** Past (or otherwise closed) day with no reservation at all. */
        NOT_RESERVED,
        /** Not allowed to reserve food for this day. */
        NOT_ALLOWED,
        /** Future day whose menu hasn't been published yet. */
        NO_FOOD_DEFINED,
        UNKNOWN
    }

    data class DietOption(
        /** Full ASP.NET field name of this radio — send it back with value "rdoDiet" to select it. */
        val fieldName: String,
        /** Food name only — calories are intentionally dropped. */
        val label: String,
        val priceRial: Long?,
        /** Pre-formatted Toman price, e.g. "16,000T" — null if it couldn't be parsed. */
        val priceToman: String?,
        val checked: Boolean,
        val disabled: Boolean,
        /** Non-null (and usable) only when this checked option can currently be cancelled outright. */
        val cancelFieldName: String?,
        /** Non-null only when this checked option can currently be offered for exchange ("درخواست تبادل با دانشجویان"). */
        val exchangeFieldName: String? = null,
        /** Non-null only when an exchange request is already pending for this checked option. */
        val cancelExchangeFieldName: String? = null,
        val exchangeFoodId: String? = null  // NEW: the "attre" attribute, e.g. "23621;3"
    ) {
        /** True once an exchange offer has been placed for this option. */
        val exchangePending: Boolean get() = cancelExchangeFieldName != null
    }

    /** Snapshot of the "تبادل غذا" (food exchange) modal, when it's showing. */
    data class ExchangeDialogData(
        /** e.g. "تبادل غذا" -> "1", "تعویض غذا" -> "2", "تعویض غذا با سایرین" -> "3". */
        val exchangeTypes: List<Pair<String, String>>,
        val selectedExchangeType: String,
        val selfOptions: List<Pair<String, String>>,
        val selectedSelf: String?,
        val foodOptions: List<Pair<String, String>>,
        val selectedFood: String?,
        /** True when the cafeteria/food dropdowns should be shown ("تعویض غذا" flow). */
        val showChangeFoodFields: Boolean,
        /** True when the destination-student search should be shown ("تعویض غذا با سایرین" flow). */
        val showStudentSearchFields: Boolean,
        val studentNumber: String?,
        /** Result label once a destination student has been found, if any. */
        val destStudentLabel: String?
    )

    /**
     * Carries all the parameters the server needs to process an exchange-related
     * postback (student search or confirm). On the real site, the JS `btnExchangeFood()`
     * function populates the hidden fields (`hdnSelectFood`, `hdnSelectedSelfForExchange`,
     * `hdnSelectedFoodForExchange`) from the modal's UI controls right before submitting.
     * We mirror that here.
     */
    data class ExchangeRequest(
        /** The "attre" value from btnSellFood, e.g. "23621;3". Goes into hdnSelectFood. */
        val foodId: String,
        /** Selected radio value: "1" (تبادل), "2" (تعویض), "3" (تعویض با سایرین). Goes into rbList. */
        val exchangeType: String,
        /** For type "2": selected cafeteria value. Goes into hdnSelectedSelfForExchange. */
        val selectedSelf: String = "0",
        /** For type "2": selected food value. Goes into hdnSelectedFoodForExchange. */
        val selectedFood: String = "0",
        /** For type "3": destination student number. Goes into txtSearchStuNum. */
        val studentNumber: String = ""
    )

    data class DayInfo(
        val index: Int,
        /** e.g. "1405/05/19 - \u062f\u0648\u0634\u0646\u0628\u0647" */
        val dateLabel: String,
        /** Raw status message from the page, if any (parentheses stripped). */
        val message: String?,
        /** Human label derived from the header's CSS status class, if recognized. */
        val statusBadge: String?,
        val status: DayStatus,
        /** Informational label shown for read-only / locked days (no select present). */
        val selfLabel: String?,
        val cafeteriaFieldName: String?,
        val cafeteriaOptions: List<Pair<String, String>>,
        val selectedCafeteria: String?,
        val dietOptions: List<DietOption>,
        val commentFieldName: String?,
        /** Text from lblDaySeals_<N> — only present on "DaySeal" (روز فروش) days. */
        val daySealText: String? = null
    ) {
        val canPickCafeteria: Boolean get() = status == DayStatus.SELECT_CAFETERIA && cafeteriaFieldName != null
        val canPickDiet: Boolean get() = status == DayStatus.SELECT_DIET || status == DayStatus.RESERVED
        val reservedOption: DietOption? get() = dietOptions.firstOrNull { it.checked }
        val isReadOnly: Boolean get() = status == DayStatus.RECEIVED || status == DayStatus.NOT_RECEIVED ||
            status == DayStatus.NOT_RESERVED || status == DayStatus.NOT_ALLOWED
        /** True when the reserved diet is locked (deadline passed) but the day itself is still active. */
        val dietLocked: Boolean get() = status == DayStatus.RESERVED && reservedOption?.disabled == true
    }
}
