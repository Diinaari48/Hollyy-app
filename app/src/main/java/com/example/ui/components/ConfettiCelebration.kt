package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

private data class Particle(
    val initialX: Float,
    val initialY: Float,
    val vx: Float,
    val vy: Float,
    val color: Color,
    val radius: Float,
    val isStar: Boolean = false
)

@Composable
fun ConfettiCelebration(
    modifier: Modifier = Modifier,
    isGrandCelebration: Boolean = false,
    particleCount: Int = if (isGrandCelebration) 100 else 40
) {
    val progress = remember { Animatable(0f) }

    val colors = if (isGrandCelebration) {
        listOf(
            Color(0xFFFFD700), // Gold
            Color(0xFFFFC107), // Amber
            Color(0xFF2E7D32), // Emerald
            Color(0xFF00E676), // Bright Green
            Color(0xFF00B0FF), // Cyan
            Color(0xFFFF4081), // Pink
            Color(0xFFFF6D00)  // Deep Orange
        )
    } else {
        listOf(
            Color(0xFF2E7D32),
            Color(0xFF00B0FF),
            Color(0xFFFFD600),
            Color(0xFFFF5252),
            Color(0xFF00E676),
            Color(0xFFFF4081)
        )
    }

    val particles = remember(isGrandCelebration) {
        List(particleCount) {
            val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
            val speedMultiplier = if (isGrandCelebration) 1.5f else 1.0f
            val speed = (Random.nextFloat() * 450f + 180f) * speedMultiplier
            Particle(
                initialX = 0.5f,
                initialY = if (isGrandCelebration) 0.35f else 0.4f,
                vx = kotlin.math.cos(angle) * speed,
                vy = kotlin.math.sin(angle) * speed,
                color = colors[Random.nextInt(colors.size)],
                radius = if (isGrandCelebration) Random.nextFloat() * 8f + 5f else Random.nextFloat() * 6f + 4f,
                isStar = isGrandCelebration && Random.nextBoolean()
            )
        }
    }

    LaunchedEffect(isGrandCelebration) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = if (isGrandCelebration) 1400 else 900,
                easing = FastOutSlowInEasing
            )
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val p = progress.value
            val alpha = (1f - p).coerceIn(0f, 1f)
            val canvasW = size.width
            val canvasH = size.height

            particles.forEach { particle ->
                val x = (particle.initialX * canvasW) + (particle.vx * p)
                val y = (particle.initialY * canvasH) + (particle.vy * p) + (260f * p * p) // gravity
                drawCircle(
                    color = particle.color.copy(alpha = alpha),
                    radius = particle.radius * (1f - p * 0.25f),
                    center = Offset(x, y)
                )
            }
        }

        // Grand Celebration Trophy Badge in the center
        if (isGrandCelebration && progress.value < 0.95f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF1B5E20),
                    shadowElevation = 12.dp,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFFFD700),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = Color(0xFF422800),
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "5 XIRIXIR AH! 🏆",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFFD700)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Dawadan si buuxda ayaa loo xafiday (Learned)!",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
