package ir.mums.stufood.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ir.mums.stufood.R

// Montserrat (Latin) + Ganjnameh Sans (Persian fallback) per weight.
// Ganjnameh only ships a single Regular weight, so it's reused across every
// weight bucket below — the Latin side still gets properly bolded, but Persian
// text will render at the same visual weight regardless of which style is used
val MontserratFamily = FontFamily(
    Font(R.font.montserrat_regular, FontWeight.Normal),
    Font(R.font.montserrat_medium, FontWeight.Medium),
    Font(R.font.montserrat_semibold, FontWeight.SemiBold),
    Font(R.font.montserrat_bold, FontWeight.Bold)
)

val GanjnamehFamily = FontFamily(
    Font(R.font.ganjnameh_regular, FontWeight.Normal),
    Font(R.font.ganjnameh_regular, FontWeight.Medium),
    Font(R.font.ganjnameh_regular, FontWeight.SemiBold),
    Font(R.font.ganjnameh_regular, FontWeight.Bold)
)

val VazirFamily = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold)
)

val SahelFamily = FontFamily(
    Font(R.font.sahel_light, FontWeight.Normal),
    Font(R.font.sahel_regular, FontWeight.Medium),
    Font(R.font.sahel_semibold, FontWeight.SemiBold),
    Font(R.font.sahel_bold, FontWeight.Bold)
)

val ParastooFamily = FontFamily(
    Font(R.font.parastoo_regular, FontWeight.Normal),
    Font(R.font.parastoo_regular, FontWeight.Medium),
    Font(R.font.parastoo_bold, FontWeight.SemiBold),
    Font(R.font.parastoo_bold, FontWeight.Bold)
)

val GandomFamily = FontFamily(
    Font(R.font.gandom_regular, FontWeight.Normal),
    Font(R.font.gandom_regular, FontWeight.Medium),
    Font(R.font.gandom_regular, FontWeight.SemiBold),
    Font(R.font.gandom_regular, FontWeight.Bold)
)

val SamimFamily = FontFamily(
    Font(R.font.samim_regular, FontWeight.Normal),
    Font(R.font.samim_medium, FontWeight.Medium),
    Font(R.font.samim_bold, FontWeight.SemiBold),
    Font(R.font.samim_bold, FontWeight.Bold)
)

data class PersianFontOption(
    val id: String,          // stored in prefs, never change once shipped
    val label: String,       // shown in the dropdown
    val family: FontFamily,
    val sizeScale: Float = 1f      // NEW: compensates for fonts that render small/large
)

val PersianFontOptions: List<PersianFontOption> = listOf(
    PersianFontOption("ganjnameh", "Ganjnameh (Default)", GanjnamehFamily),
    PersianFontOption("vazirmatn", "Vazirmatn", VazirFamily),
    PersianFontOption("parastoo", "Parastoo", ParastooFamily, sizeScale = 1.15f),
    PersianFontOption("gandom", "Gandom", GandomFamily),
    PersianFontOption("samim", "Samim", SamimFamily),
)

const val DEFAULT_PERSIAN_FONT_ID = "ganjnameh"

fun persianFontFor(id: String): FontFamily =
    PersianFontOptions.firstOrNull { it.id == id }?.family ?: GanjnamehFamily

fun persianFontScaleFor(id: String): Float =
    PersianFontOptions.firstOrNull { it.id == id }?.sizeScale ?: 1f

// Keep this as the "default" for pure-Latin UI labels
val AppFontFamily = MontserratFamily

// Default Material 3 type scale. Customized slightly with bolder weights for headings.
val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    //  ADDED: Fixes the credit number font in the collapsing header 
    headlineSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    // ADDED: Fixes the credit number font in the TopAppBar pill 
    titleSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    labelLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
)