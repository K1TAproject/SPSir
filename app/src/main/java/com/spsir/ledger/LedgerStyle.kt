package com.spsir.ledger

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val ExpenseColor = Color(0xFFAD5938)

@Composable
fun LedgerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF28594C), onPrimary = Color.White,
            primaryContainer = Color(0xFFDCEBE3), onPrimaryContainer = Color(0xFF183C32),
            secondary = Color(0xFF59695E), onSecondary = Color.White,
            secondaryContainer = Color(0xFFE6EDE5), onSecondaryContainer = Color(0xFF284538),
            tertiary = ExpenseColor,
            background = Color(0xFFF6F5F0), onBackground = Color(0xFF24352F),
            surface = Color(0xFFFFFEFA), onSurface = Color(0xFF24352F),
            surfaceVariant = Color(0xFFEBEEE7), onSurfaceVariant = Color(0xFF66736B),
            surfaceContainer = Color(0xFFF0F2EB), surfaceContainerLow = Color(0xFFFFFEFA),
            surfaceContainerHigh = Color(0xFFF0F2EB), surfaceContainerHighest = Color(0xFFE7ECE4),
            outline = Color(0xFF8A978D), outlineVariant = Color(0xFFDDE3DA),
        ),
        typography = Typography(
            headlineLarge = TextStyle(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp),
            headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
            titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
            bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 19.sp),
            labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        ),
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp)),
        content = content,
    )
}

@Composable
fun LedgerNavIcon(index: Int) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val unit = size.width / 24
        val stroke = Stroke(1.7f * unit)
        fun line(x: Float, y: Float, x2: Float, y2: Float) =
            drawLine(color, Offset(x * unit, y * unit), Offset(x2 * unit, y2 * unit), 1.7f * unit, StrokeCap.Round)
        when (index) {
            0 -> {
                drawRoundRect(color, Offset(5 * unit, 3 * unit), Size(15 * unit, 18 * unit), CornerRadius(2 * unit), style = stroke)
                line(3f, 8f, 7f, 8f); line(3f, 16f, 7f, 16f)
                line(10f, 9f, 16f, 9f); line(10f, 14f, 16f, 14f)
            }
            1 -> {
                drawRoundRect(color, Offset(3 * unit, 7 * unit), Size(18 * unit, 14 * unit), CornerRadius(3 * unit), style = stroke)
                drawRoundRect(color, Offset(8 * unit, 3 * unit), Size(8 * unit, 4 * unit), CornerRadius(unit), style = stroke)
                line(8f, 11f, 8f, 17f); line(16f, 11f, 16f, 17f)
            }
            else -> {
                line(4f, 20f, 21f, 20f)
                line(6f, 15f, 6f, 18f); line(12f, 10f, 12f, 18f); line(18f, 4f, 18f, 18f)
            }
        }
    }
}
