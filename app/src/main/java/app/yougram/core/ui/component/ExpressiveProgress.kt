package app.yougram.core.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Выразительный круглый индикатор M3 Expressive с зафиксированной дугой и вырастающим/бегущим волнистым отрезком.
 */
@Composable
fun WavyCircleRing(
    modifier: Modifier = Modifier,
    mainArcColor: Color = MaterialTheme.colorScheme.primary,
    wavySegmentColor: Color = MaterialTheme.colorScheme.tertiary,
    strokeWidth: Dp = 4.dp,
    waveAmplitude: Dp = 5.dp,
    maxWaveSweep: Float = 70f,
    waveFrequency: Float = 3f,
    animating: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "wavy_ring_anim")

    // 1. Анимация бегущей волны (волна течёт вдоль дуги без вращения всего круга)
    val wavePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    // 2. Анимация роста / расширения длины волнистого отрезка
    val sweepProgress by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sweepProgress"
    )

    Canvas(modifier = modifier) {
        val strokeWidthPx = strokeWidth.toPx()
        val amplitudePx = waveAmplitude.toPx()
        val radius = (minOf(size.width, size.height) - strokeWidthPx - amplitudePx * 2) / 2f
        val centerPoint = center

        // Зафиксированное начало волнистой секции (сверху справа, ~305°)
        val waveStartDegree = 305f
        val currentWaveSweep = if (animating) maxWaveSweep * sweepProgress else maxWaveSweep
        val waveEndDegree = waveStartDegree + currentWaveSweep

        // Основная сплошная дуга
        val mainArcStartDegree = (waveEndDegree + 10f) % 360f
        val mainArcSweepDegree = (360f - currentWaveSweep - 20f).coerceAtLeast(10f)

        // 1. Рисуем основную фиксированную дугу
        drawArc(
            color = mainArcColor,
            startAngle = mainArcStartDegree,
            sweepAngle = mainArcSweepDegree,
            useCenter = false,
            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
        )

        // 2. Рисуем растущий волнистый отрезок с бегущей волной
        if (currentWaveSweep > 2f) {
            val path = Path()
            val steps = 80
            val phase = if (animating) wavePhase else 0f

            for (i in 0..steps) {
                val fraction = i.toFloat() / steps
                val angleDeg = waveStartDegree + fraction * currentWaveSweep
                val angleRad = Math.toRadians(angleDeg.toDouble())

                val envelope = sin(fraction * Math.PI).toFloat()
                val waveOffset = sin(fraction * waveFrequency * 2 * Math.PI - phase).toFloat() * amplitudePx * envelope
                val r = radius + waveOffset

                val x = centerPoint.x + r * cos(angleRad).toFloat()
                val y = centerPoint.y + r * sin(angleRad).toFloat()

                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            drawPath(
                path = path,
                color = wavySegmentColor,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    CircularProgressIndicator(modifier = modifier, color = color, strokeWidth = 3.dp)
}

@Composable
fun CircularWavyProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    CircularProgressIndicator(progress = progress, modifier = modifier, color = color, strokeWidth = 3.dp)
}

@Composable
fun LinearWavyProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    LinearProgressIndicator(progress = progress, modifier = modifier, color = color)
}
