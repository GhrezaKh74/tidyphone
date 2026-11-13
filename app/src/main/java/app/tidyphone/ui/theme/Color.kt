package app.tidyphone.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object Brand {
    val Violet = Color(0xFF7C6CFF)
    val VioletDeep = Color(0xFF5A48F5)
    val Mint = Color(0xFF2EE6B6)
    val Cyan = Color(0xFF3CC8FF)
    val Coral = Color(0xFFFF6B7A)
    val Amber = Color(0xFFFFB547)
    val Ink = Color(0xFF0B0D17)
    val InkRaised = Color(0xFF141726)
    val InkHigh = Color(0xFF1D2135)
    val Mist = Color(0xFFF5F6FB)

    val heroGradient = Brush.linearGradient(listOf(Color(0xFF6A5BFF), Color(0xFF8E5CFF), Color(0xFF3CC8FF)))
    val mintGradient = Brush.linearGradient(listOf(Color(0xFF2EE6B6), Color(0xFF3CC8FF)))
    val dangerGradient = Brush.linearGradient(listOf(Color(0xFFFF6B7A), Color(0xFFFF9F5A)))
}

/** Colours used for file categories in charts and chips. */
object CategoryColors {
    val Images = Color(0xFF7C6CFF)
    val Videos = Color(0xFFFF6B7A)
    val Audio = Color(0xFFFFB547)
    val Documents = Color(0xFF3CC8FF)
    val Archives = Color(0xFFB07CFF)
    val Apks = Color(0xFF2EE6B6)
    val Other = Color(0xFF8A90A8)
}
