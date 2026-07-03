package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun AnimatedBackground(
    isPlaying: Boolean,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    // Standard infinite transitions to power continuous movement
    val infiniteTransition = rememberInfiniteTransition(label = "background_movement")

    // Multipliers for speed when playing music
    val speedMultiplier = if (isPlaying) 2.5f else 1.0f
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isPlaying) 1500 else 4000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Base angles to generate complex swirling coordinates
    val angle1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (18000 / speedMultiplier).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle_1"
    )

    val angle2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (25000 / speedMultiplier).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle_2"
    )

    val angle3 by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (32000 / speedMultiplier).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle_3"
    )

    // Define colors based on Theme
    val colors = remember(isDarkTheme) {
        if (isDarkTheme) {
            BackgroundColors(
                base = Color(0xFF111318), // Match MinimalBackground
                blob1 = Color(0xFF1F2F4D), // Soft Blue
                blob2 = Color(0xFF321A47), // Soft Purple
                blob3 = Color(0xFF133B32), // Soft Emerald/Teal
                particle = Color(0xFFD1E4FF) // MinimalPrimary
            )
        } else {
            BackgroundColors(
                base = Color(0xFFF9FAFC), // Match LightBack
                blob1 = Color(0xFFE3EDF7), // Pastel Blue
                blob2 = Color(0xFFF7E3EF), // Pastel Purple/Pink
                blob3 = Color(0xFFEAF7E3), // Pastel Green
                particle = Color(0xFF005FAF) // LightPrimary
            )
        }
    }

    // Static Particle System with persistent random characteristics
    val particles = remember {
        List(40) {
            ParticleState(
                xPercent = Random.nextFloat(),
                yPercent = Random.nextFloat(),
                speed = Random.nextFloat() * 0.05f + 0.02f,
                baseSize = Random.nextFloat() * 3f + 1f,
                alphaPhase = Random.nextFloat() * 2f * Math.PI.toFloat()
            )
        }
    }

    // Update particle positions every frame using a animateFloat timer
    val particleTimer by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(100000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particles"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        if (w == 0f || h == 0f) return@Canvas

        // Draw solid background base
        drawRect(color = colors.base)

        // Calculate swirling coordinates for glowing blobs
        val rad1 = Math.toRadians(angle1.toDouble())
        val rad2 = Math.toRadians(angle2.toDouble())
        val rad3 = Math.toRadians(angle3.toDouble())

        // Blob 1: Swirls around top-left to middle
        val x1 = (0.35f + 0.15f * cos(rad1).toFloat()) * w
        val y1 = (0.30f + 0.15f * sin(rad1).toFloat()) * h
        val radius1 = 0.45f * w * pulseScale

        // Blob 2: Swirls around bottom-right to middle
        val x2 = (0.65f + 0.20f * sin(rad2).toFloat()) * w
        val y2 = (0.70f + 0.15f * cos(rad2).toFloat()) * h
        val radius2 = 0.50f * w * pulseScale

        // Blob 3: Oscillates horizontally across the top-middle
        val x3 = (0.50f + 0.25f * cos(rad3).toFloat()) * w
        val y3 = (0.45f + 0.10f * sin(rad3 * 1.5).toFloat()) * h
        val radius3 = 0.40f * w * (2f - pulseScale)

        // Draw Blob 1
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(colors.blob1.copy(alpha = 0.55f), Color.Transparent),
                center = Offset(x1, y1),
                radius = radius1
            ),
            center = Offset(x1, y1),
            radius = radius1
        )

        // Draw Blob 2
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(colors.blob2.copy(alpha = 0.50f), Color.Transparent),
                center = Offset(x2, y2),
                radius = radius2
            ),
            center = Offset(x2, y2),
            radius = radius2
        )

        // Draw Blob 3
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(colors.blob3.copy(alpha = 0.45f), Color.Transparent),
                center = Offset(x3, y3),
                radius = radius3
            ),
            center = Offset(x3, y3),
            radius = radius3
        )

        // Draw Starry Particle Field
        particles.forEach { p ->
            // Shift y position upwards over time based on the active speed
            val timeOffset = particleTimer * speedMultiplier * p.speed * 8f
            var yPos = ((p.yPercent * h - timeOffset) % h)
            if (yPos < 0) yPos += h

            val xPos = p.xPercent * w

            // Sparkle / Twinkle intensity based on phase
            val sparkleIntensity = sin(rad1 * 2f + p.alphaPhase).toFloat()
            val baseAlpha = if (isDarkTheme) 0.15f else 0.3f
            val alpha = (baseAlpha + 0.15f * sparkleIntensity).coerceIn(0.05f, 0.7f)

            // Dynamic size
            val currentSize = p.baseSize * (1f + 0.3f * sparkleIntensity)

            drawCircle(
                color = colors.particle.copy(alpha = alpha),
                radius = currentSize,
                center = Offset(xPos, yPos)
            )
        }
    }
}

private data class BackgroundColors(
    val base: Color,
    val blob1: Color,
    val blob2: Color,
    val blob3: Color,
    val particle: Color
)

private data class ParticleState(
    val xPercent: Float,
    val yPercent: Float,
    val speed: Float,
    val baseSize: Float,
    val alphaPhase: Float
)
