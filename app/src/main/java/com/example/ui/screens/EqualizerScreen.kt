package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.viewmodel.MusicPlayerViewModel
import kotlin.math.sin

@Composable
fun EqualizerScreen(
    viewModel: MusicPlayerViewModel,
    modifier: Modifier = Modifier
) {
    val isPlaying by viewModel.isPlaying.collectAsState()
    val bassLevel by viewModel.bassLevel.collectAsState()
    val trebleLevel by viewModel.trebleLevel.collectAsState()

    // Infinite animation transition for dancing bars
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer_dance")
    
    // Wave offset phases
    val wavePhase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * kotlin.math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_1"
    )

    val wavePhase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * kotlin.math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_2"
    )

    val wavePhase3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * kotlin.math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_3"
    )

    // Primary Colors
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Equalizer Header
        Text(
            text = stringResource(R.string.equalizer_title),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        // Visualizer Screen Card
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 16.dp)
                .testTag("visualizer_display_card")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                // Background visualizer grid overlay
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height

                    // Draw glowing grid lines in the background
                    val gridColor = primaryColor.copy(alpha = 0.05f)
                    val gridSpacing = 40.dp.toPx()

                    var x = 0f
                    while (x < width) {
                        drawLine(color = gridColor, start = Offset(x, 0f), end = Offset(x, height), strokeWidth = 1f)
                        x += gridSpacing
                    }

                    var y = 0f
                    while (y < height) {
                        drawLine(color = gridColor, start = Offset(0f, y), end = Offset(width, y), strokeWidth = 1f)
                        y += gridSpacing
                    }

                    // --- ANIMATED DANCING FREQUENCY BARS ---
                    val barCount = 18
                    val padding = 8f
                    val availableWidth = width - (padding * (barCount + 1))
                    val barWidth = availableWidth / barCount

                    val activeAmplitudeScale = if (isPlaying) 1.0f else 0.04f

                    for (i in 0 until barCount) {
                        // Bass affects lower indices, treble affects higher indices
                        val isBassBand = i < barCount / 2
                        val eqScale = if (isBassBand) {
                            (bassLevel / 50f)
                        } else {
                            (trebleLevel / 50f)
                        }

                        // Generate wave height using sine formula combined with the animation phase
                        val phase = when (i % 3) {
                            0 -> wavePhase1
                            1 -> wavePhase2
                            else -> wavePhase3
                        }

                        // Sine wave math with index weights
                        val sineValue = sin(phase + (i * 0.5f))
                        val normalizedSine = (sineValue + 1f) / 2f // 0f to 1f

                        val rawHeight = (height * 0.7f) * normalizedSine * activeAmplitudeScale * eqScale
                        val finalHeight = rawHeight.coerceIn(12f, height * 0.85f)

                        val startX = padding + i * (barWidth + padding)
                        val startY = (height - finalHeight) / 2f + (height * 0.08f) // centered vertically with positive offset

                        // Draw elegant gradient columns
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(primaryColor, secondaryColor, tertiaryColor)
                            ),
                            topLeft = Offset(startX, startY),
                            size = Size(barWidth, finalHeight),
                            alpha = 0.85f
                        )
                    }
                }

                // Wave overlays indicating signal stability
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isPlaying) "SINAL ACTIVO • 44.1 kHz" else "AUDIO EN PAUSA",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = primaryColor,
                            letterSpacing = 1.sp
                        )
                    )
                }
            }
        }

        // Controllers Panel (Bass & Treble sliders)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 100.dp) // leave space for player
                .testTag("eq_controls_card")
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // BASS SLIDER
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.equalizer_bass),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Ajusta las frecuencias bajas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${bassLevel.toInt()}%",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = primaryColor
                        )
                    )
                }
                Slider(
                    value = bassLevel,
                    onValueChange = { viewModel.setBass(it) },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = primaryColor,
                        activeTrackColor = primaryColor,
                        inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .testTag("bass_slider")
                )

                Spacer(modifier = Modifier.height(16.dp))

                // TREBLE SLIDER
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.equalizer_treble),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Ajusta las frecuencias altas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${trebleLevel.toInt()}%",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = tertiaryColor
                        )
                    )
                }
                Slider(
                    value = trebleLevel,
                    onValueChange = { viewModel.setTreble(it) },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = tertiaryColor,
                        activeTrackColor = tertiaryColor,
                        inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .testTag("treble_slider")
                )
            }
        }
    }
}
