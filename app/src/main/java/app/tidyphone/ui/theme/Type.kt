package app.tidyphone.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.tidyphone.R

/** Kharazmi (OFL, a Vazirmatn derivative): Persian + Latin with matching chiselled terminals. */
val Kharazmi = FontFamily(
    Font(R.font.kharazmi_light, FontWeight.Light),
    Font(R.font.kharazmi_regular, FontWeight.Normal),
    Font(R.font.kharazmi_medium, FontWeight.Medium),
    Font(R.font.kharazmi_bold, FontWeight.SemiBold),
    Font(R.font.kharazmi_bold, FontWeight.Bold),
    Font(R.font.kharazmi_extrabold, FontWeight.ExtraBold),
)

private val base = Typography()

private fun TextStyle.k(weight: FontWeight? = null) =
    copy(fontFamily = Kharazmi, fontWeight = weight ?: fontWeight)

val TidyTypography = Typography(
    displayLarge = base.displayLarge.k(FontWeight.ExtraBold),
    displayMedium = base.displayMedium.k(FontWeight.ExtraBold),
    displaySmall = base.displaySmall.k(FontWeight.ExtraBold),
    headlineLarge = base.headlineLarge.k(FontWeight.ExtraBold),
    headlineMedium = base.headlineMedium.k(FontWeight.Bold),
    headlineSmall = base.headlineSmall.k(FontWeight.Bold),
    titleLarge = base.titleLarge.k(FontWeight.Bold),
    titleMedium = base.titleMedium.k(FontWeight.Bold),
    titleSmall = base.titleSmall.k(FontWeight.Medium),
    bodyLarge = base.bodyLarge.k(),
    bodyMedium = base.bodyMedium.k(),
    bodySmall = base.bodySmall.k(),
    labelLarge = base.labelLarge.k(FontWeight.Bold),
    labelMedium = base.labelMedium.k(FontWeight.Medium),
    labelSmall = base.labelSmall.k(FontWeight.Medium).copy(letterSpacing = 0.2.sp),
)
