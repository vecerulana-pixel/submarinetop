package com.submarinewin.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.android.awaitFrame
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    private var openedWebView = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val cachedUrl = WebUrlStore.getCachedUrl(this)
        if (cachedUrl != null) {
            openWebView(cachedUrl)
            return
        }

        enableEdgeToEdge()
        setContent {
            SubmarineTapTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF063F5D)) {
                    SubmarineTapGame()
                }
            }
        }

        FirebaseWebUrlChecker.checkUrl(
            onUrlFound = { url ->
                WebUrlStore.saveUrl(this, url)
                openWebView(url)
            },
        )
    }

    private fun openWebView(url: String) {
        if (openedWebView || isFinishing || isDestroyed) return
        openedWebView = true
        startActivity(Intent(this, WebViewActivity::class.java).putExtra(WebViewActivity.EXTRA_URL, url))
        finish()
    }
}

@Composable
private fun SubmarineTapTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

@Composable
private fun SubmarineTapGame() {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    var submarineY by remember { mutableFloatStateOf(screenHeightPx * 0.48f) }
    var velocity by remember { mutableFloatStateOf(0f) }
    var isPressing by remember { mutableStateOf(false) }
    var isGameOver by remember { mutableStateOf(false) }
    var score by remember { mutableIntStateOf(0) }
    var bestScore by remember { mutableIntStateOf(0) }
    var lastFrameNanos by remember { mutableLongStateOf(0L) }
    val obstacles = remember { mutableStateListOf<Obstacle>() }

    fun restart() {
        submarineY = screenHeightPx * 0.48f
        velocity = 0f
        isPressing = false
        isGameOver = false
        score = 0
        lastFrameNanos = 0L
        obstacles.clear()
    }

    LaunchedEffect(screenWidthPx, screenHeightPx, isGameOver) {
        if (screenWidthPx <= 0f || screenHeightPx <= 0f || isGameOver) return@LaunchedEffect
        if (obstacles.isEmpty()) {
            obstacles += Obstacle.next(screenWidthPx + 220f, screenHeightPx, score)
            obstacles += Obstacle.next(screenWidthPx + 560f, screenHeightPx, score)
        }

        while (!isGameOver) {
            val frameNanos = awaitFrame()
            val deltaSeconds = if (lastFrameNanos == 0L) 0f else {
                ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.033f)
            }
            lastFrameNanos = frameNanos

            val lift = if (isPressing) -1380f else 1050f
            velocity = (velocity + lift * deltaSeconds).coerceIn(-760f, 820f)
            submarineY = (submarineY + velocity * deltaSeconds).coerceIn(82f, screenHeightPx - 88f)

            val speed = 245f + min(score, 40) * 4.5f
            for (index in obstacles.indices) {
                val obstacle = obstacles[index]
                obstacles[index] = obstacle.copy(x = obstacle.x - speed * deltaSeconds)
            }

            obstacles.removeAll { it.x + it.width < -80f }
            while (obstacles.size < 3) {
                val nextX = (obstacles.maxOfOrNull { it.x } ?: screenWidthPx) + Random.nextFloat() * 150f + 280f
                obstacles += Obstacle.next(nextX, screenHeightPx, score)
            }

            obstacles.forEachIndexed { index, obstacle ->
                if (!obstacle.scored && obstacle.x + obstacle.width < 96f) {
                    obstacles[index] = obstacle.copy(scored = true)
                    score += when (obstacle.type) {
                        ObstacleType.Mine -> 2
                        ObstacleType.Fish -> 1
                        ObstacleType.Coral -> 1
                        ObstacleType.Pipe -> 3
                    }
                    bestScore = max(bestScore, score)
                }
            }

            val submarineBounds = Rect(
                left = 50f,
                top = submarineY - 28f,
                right = 144f,
                bottom = submarineY + 28f,
            )
            val hitWall = submarineY <= 84f || submarineY >= screenHeightPx - 90f
            val hitObstacle = obstacles.any { it.bounds.overlaps(submarineBounds) }
            if (hitWall || hitObstacle) {
                isGameOver = true
                isPressing = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF063F5D))
            .pointerInput(isGameOver) {
                detectTapGestures(
                    onPress = {
                        if (isGameOver) {
                            restart()
                            return@detectTapGestures
                        }
                        isPressing = true
                        tryAwaitRelease()
                        isPressing = false
                    },
                    onTap = {
                        if (isGameOver) restart()
                    },
                )
            }
            .semantics {
                contentDescription = "Submarine Tap game. Hold to rise, release to dive."
            },
    ) {
        OceanScene(
            submarineY = submarineY,
            obstacles = obstacles,
            isPressing = isPressing,
            modifier = Modifier.fillMaxSize(),
        )

        GameHud(
            score = score,
            bestScore = bestScore,
            isGameOver = isGameOver,
            onRestart = ::restart,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 28.dp, start = 16.dp, end = 16.dp),
        )

        if (!isGameOver && score == 0) {
            Text(
                text = "Hold to rise",
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 42.dp),
            )
        }
    }
}

@Composable
private fun GameHud(
    score: Int,
    bestScore: Int,
    isGameOver: Boolean,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScorePill(label = "Score", value = score)
            Spacer(modifier = Modifier.size(10.dp))
            ScorePill(label = "Best", value = bestScore)
        }

        if (isGameOver) {
            Spacer(modifier = Modifier.height(22.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 26.dp)
                    .background(Color(0xCC062D42), RoundedCornerShape(8.dp))
                    .padding(20.dp),
            ) {
                Text(
                    text = "Submarine lost",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Dodge mines, coral, pipes, and fish.",
                    color = Color(0xFFD6F7FF),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = onRestart,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFC857),
                        contentColor = Color(0xFF143044),
                    ),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(text = "Restart", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ScorePill(label: String, value: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(Color(0xB30B3048), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            color = Color(0xFFB8ECF7),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = value.toString(),
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun OceanScene(
    submarineY: Float,
    obstacles: List<Obstacle>,
    isPressing: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "ocean")
    val waveOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "waveOffset",
    )

    Canvas(modifier = modifier) {
        drawOceanBackground(waveOffset)
        drawSeaFloor(waveOffset)
        drawBubbles(waveOffset, submarineY)
        obstacles.forEach(::drawObstacle)
        drawSubmarine(submarineY, isPressing)
    }
}

private fun DrawScope.drawOceanBackground(waveOffset: Float) {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0B6E8F),
                Color(0xFF075373),
                Color(0xFF052B44),
            ),
        ),
    )

    repeat(4) { layer ->
        val y = size.height * (0.18f + layer * 0.16f)
        val alpha = 0.08f + layer * 0.02f
        val path = Path().apply {
            moveTo(-size.width, y)
            var x = -size.width
            while (x <= size.width * 2f) {
                quadraticTo(
                    x + 70f,
                    y + if (layer % 2 == 0) 20f else -20f,
                    x + 140f,
                    y,
                )
                x += 140f
            }
        }
        drawPath(
            path = path,
            color = Color.White.copy(alpha = alpha),
            style = Stroke(width = 3f + layer, cap = StrokeCap.Round),
            alpha = 1f,
        )
    }

    val lightX = size.width * (0.22f + waveOffset * 0.1f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0x55B9F4FF), Color.Transparent),
            center = Offset(lightX, size.height * 0.08f),
            radius = size.width * 0.65f,
        ),
        radius = size.width * 0.65f,
        center = Offset(lightX, size.height * 0.08f),
    )
}

private fun DrawScope.drawSeaFloor(waveOffset: Float) {
    val floorTop = size.height - 54f
    drawRoundRect(
        color = Color(0xFF214A4D),
        topLeft = Offset(-20f, floorTop),
        size = Size(size.width + 40f, 86f),
        cornerRadius = CornerRadius(34f, 34f),
    )
    repeat(8) { index ->
        val x = ((index * 92f - waveOffset * 70f) % (size.width + 120f)) - 40f
        drawOval(
            color = Color(0xFF2F6E65),
            topLeft = Offset(x, floorTop + 15f + (index % 3) * 7f),
            size = Size(64f, 18f),
        )
    }
}

private fun DrawScope.drawBubbles(waveOffset: Float, submarineY: Float) {
    repeat(9) { index ->
        val drift = (waveOffset * 140f + index * 53f) % (size.width + 120f)
        val bubbleX = size.width - drift
        val bubbleY = (submarineY + (index - 4) * 21f + waveOffset * 45f) % size.height
        drawCircle(
            color = Color.White.copy(alpha = 0.24f),
            radius = 4f + (index % 3) * 2f,
            center = Offset(bubbleX, bubbleY),
            style = Stroke(width = 2f),
        )
    }
}

private fun DrawScope.drawSubmarine(centerY: Float, isPressing: Boolean) {
    val nose = 142f
    val bodyLeft = 48f
    val bodyTop = centerY - 30f
    val bodySize = Size(96f, 60f)
    val tilt = if (isPressing) -7f else 5f

    drawRoundRect(
        color = Color(0xFFE9A33A),
        topLeft = Offset(bodyLeft, bodyTop + tilt),
        size = bodySize,
        cornerRadius = CornerRadius(34f, 34f),
    )
    drawPath(
        path = Path().apply {
            moveTo(bodyLeft + 4f, centerY + tilt)
            lineTo(bodyLeft - 28f, centerY - 22f + tilt)
            lineTo(bodyLeft - 28f, centerY + 22f + tilt)
            close()
        },
        color = Color(0xFFE55C43),
    )
    drawPath(
        path = Path().apply {
            moveTo(nose - 4f, centerY - 20f + tilt)
            lineTo(nose + 26f, centerY - 34f + tilt)
            lineTo(nose + 26f, centerY + 34f + tilt)
            lineTo(nose - 4f, centerY + 20f + tilt)
            close()
        },
        color = Color(0xFFE64A54),
    )
    drawRoundRect(
        color = Color(0xFF0E6A8A),
        topLeft = Offset(78f, centerY - 54f + tilt),
        size = Size(32f, 28f),
        cornerRadius = CornerRadius(9f, 9f),
    )
    drawLine(
        color = Color(0xFF0E6A8A),
        start = Offset(94f, centerY - 60f + tilt),
        end = Offset(94f, centerY - 76f + tilt),
        strokeWidth = 6f,
        cap = StrokeCap.Round,
    )
    drawCircle(Color(0xFFE8FBFF), radius = 13f, center = Offset(90f, centerY + tilt))
    drawCircle(Color(0xFF083E58), radius = 8f, center = Offset(90f, centerY + tilt))
    drawCircle(Color.White.copy(alpha = 0.75f), radius = 3f, center = Offset(86f, centerY - 4f + tilt))
}

private fun DrawScope.drawObstacle(obstacle: Obstacle) {
    when (obstacle.type) {
        ObstacleType.Mine -> drawMine(obstacle)
        ObstacleType.Coral -> drawCoral(obstacle)
        ObstacleType.Pipe -> drawPipe(obstacle)
        ObstacleType.Fish -> drawFish(obstacle)
    }
}

private fun DrawScope.drawMine(obstacle: Obstacle) {
    val center = Offset(obstacle.x + obstacle.width / 2f, obstacle.y + obstacle.height / 2f)
    repeat(8) { index ->
        val angleX = if (index % 2 == 0) 1f else -1f
        val angleY = if (index < 4) 1f else -1f
        drawLine(
            color = Color(0xFF162C38),
            start = center,
            end = Offset(center.x + angleX * obstacle.width * 0.48f, center.y + angleY * obstacle.height * 0.38f),
            strokeWidth = 5f,
            cap = StrokeCap.Round,
        )
    }
    drawCircle(Color(0xFF263F4C), radius = obstacle.width * 0.42f, center = center)
    drawCircle(Color(0xFFE94A5A), radius = obstacle.width * 0.13f, center = Offset(center.x - 8f, center.y - 8f))
}

private fun DrawScope.drawCoral(obstacle: Obstacle) {
    val baseY = obstacle.y + obstacle.height
    repeat(4) { index ->
        val x = obstacle.x + 10f + index * obstacle.width * 0.24f
        val branchHeight = obstacle.height * (0.55f + (index % 2) * 0.22f)
        drawLine(
            color = Color(0xFFFF6B6B),
            start = Offset(x, baseY),
            end = Offset(x, baseY - branchHeight),
            strokeWidth = 10f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color(0xFFFF8E72),
            start = Offset(x, baseY - branchHeight * 0.55f),
            end = Offset(x - 18f, baseY - branchHeight * 0.8f),
            strokeWidth = 7f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color(0xFFFFC15E),
            start = Offset(x, baseY - branchHeight * 0.42f),
            end = Offset(x + 17f, baseY - branchHeight * 0.65f),
            strokeWidth = 6f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawPipe(obstacle: Obstacle) {
    drawRoundRect(
        color = Color(0xFF4EA87D),
        topLeft = Offset(obstacle.x, obstacle.y),
        size = Size(obstacle.width, obstacle.height),
        cornerRadius = CornerRadius(8f, 8f),
    )
    drawRoundRect(
        color = Color(0xFF70D39A),
        topLeft = Offset(obstacle.x - 10f, obstacle.y),
        size = Size(obstacle.width + 20f, 24f),
        cornerRadius = CornerRadius(10f, 10f),
    )
    drawRect(
        color = Color(0x33244E3D),
        topLeft = Offset(obstacle.x + obstacle.width * 0.58f, obstacle.y),
        size = Size(10f, obstacle.height),
    )
}

private fun DrawScope.drawFish(obstacle: Obstacle) {
    val center = Offset(obstacle.x + obstacle.width / 2f, obstacle.y + obstacle.height / 2f)
    drawOval(
        color = Color(0xFF8DEBFF),
        topLeft = Offset(obstacle.x + 12f, obstacle.y + 8f),
        size = Size(obstacle.width - 22f, obstacle.height - 16f),
    )
    drawPath(
        path = Path().apply {
            moveTo(obstacle.x + obstacle.width - 8f, center.y)
            lineTo(obstacle.x + obstacle.width + 22f, center.y - 18f)
            lineTo(obstacle.x + obstacle.width + 22f, center.y + 18f)
            close()
        },
        color = Color(0xFF54CFE8),
    )
    drawCircle(Color(0xFF083E58), radius = 4f, center = Offset(obstacle.x + 30f, center.y - 5f))
}

private data class Obstacle(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val type: ObstacleType,
    val scored: Boolean = false,
) {
    val bounds: Rect
        get() {
            val padding = when (type) {
                ObstacleType.Mine -> 10f
                ObstacleType.Fish -> 8f
                ObstacleType.Coral -> 12f
                ObstacleType.Pipe -> 0f
            }
            return Rect(
                left = x + padding,
                top = y + padding,
                right = x + width - padding,
                bottom = y + height - padding,
            )
        }

    companion object {
        fun next(x: Float, screenHeight: Float, score: Int): Obstacle {
            val type = ObstacleType.entries.random()
            val levelOffset = min(score, 25) * 2f
            return when (type) {
                ObstacleType.Mine -> {
                    val size = Random.nextFloat() * 18f + 54f + levelOffset * 0.2f
                    Obstacle(
                        x = x,
                        y = Random.nextFloat() * (screenHeight - 250f) + 112f,
                        width = size,
                        height = size,
                        type = type,
                    )
                }
                ObstacleType.Coral -> {
                    val height = Random.nextFloat() * 68f + 90f + levelOffset
                    Obstacle(
                        x = x,
                        y = screenHeight - 54f - height,
                        width = 82f,
                        height = height,
                        type = type,
                    )
                }
                ObstacleType.Pipe -> {
                    val fromTop = Random.nextBoolean()
                    val height = Random.nextFloat() * 100f + 130f + levelOffset
                    Obstacle(
                        x = x,
                        y = if (fromTop) 0f else screenHeight - 54f - height,
                        width = 58f,
                        height = height,
                        type = type,
                    )
                }
                ObstacleType.Fish -> Obstacle(
                    x = x,
                    y = Random.nextFloat() * (screenHeight - 250f) + 118f,
                    width = 86f,
                    height = 48f,
                    type = type,
                )
            }
        }
    }
}

private enum class ObstacleType {
    Mine,
    Coral,
    Pipe,
    Fish,
}

private fun Rect.overlaps(other: Rect): Boolean =
    left < other.right &&
        right > other.left &&
        top < other.bottom &&
        bottom > other.top
