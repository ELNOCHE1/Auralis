package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var startAnimation by remember { mutableStateOf(false) }
    
    // Animate scale of the logo
    val scaleAnim by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.4f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    // Animate opacity of content
    val alphaAnim by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(
            durationMillis = 1000,
            easing = LinearOutSlowInEasing
        ),
        label = "alpha"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        // Keep splash screen visible for 3 seconds before transitioning
        delay(3200)
        onFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF090A0F), // Ultra deep dark blue/black
                        Color(0xFF111318), // Match minimalist background
                        Color(0xFF1E202B)  // Dark slate
                    )
                )
            )
            .testTag("splash_screen_container")
    ) {
        // Main content column centered
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer { alpha = alphaAnim }
                .scale(scaleAnim),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Glowing outer circle for the logo
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.02f))
                    .border(
                        width = 1.5.dp,
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary,
                                MaterialTheme.colorScheme.tertiary,
                                MaterialTheme.colorScheme.primary
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Circular Logo
                Image(
                    painter = painterResource(id = R.drawable.img_app_icon_1783005387186),
                    contentDescription = "Auralis Logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(144.dp)
                        .clip(CircleShape)
                        .testTag("splash_logo")
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // App Name
            Text(
                text = "AURALIS",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 6.sp,
                color = Color.White,
                modifier = Modifier.testTag("splash_title")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Tagline / Subtitle
            Text(
                text = "Siente cada nota",
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 2.sp,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.testTag("splash_subtitle")
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Equalizer Soundwaves
            SplashEqualizerWaveform(
                modifier = Modifier
                    .height(50.dp)
                    .testTag("splash_equalizer")
            )
        }

        // Quick Skip option at bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .graphicsLayer { alpha = alphaAnim }
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .clickable { onFinished() }
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Comenzar",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun SplashEqualizerWaveform(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "splash_equalizer")
    val barCount = 9
    // Distinct animation durations to make waves look completely organic
    val animationDurations = listOf(450, 700, 550, 800, 400, 650, 500, 750, 600)
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.15f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(animationDurations[i % animationDurations.size], easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$i"
            )
            
            val barColor = when (i % 3) {
                0 -> MaterialTheme.colorScheme.primary
                1 -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.tertiary
            }
            
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .graphicsLayer {
                        scaleY = scale
                    }
                    .background(barColor, shape = RoundedCornerShape(2.dp))
            )
        }
    }
}
