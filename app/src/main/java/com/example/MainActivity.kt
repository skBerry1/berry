package com.example

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.Choreographer
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val inAppTracker = FpsTracker()
    private var isAccessibilityEnabled by mutableStateOf(false)
    private var isSystemOverlayEnabled by mutableStateOf(false)
    private var isOverlayServiceRunning by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkPermissions()
        
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    FpsDashboardScreen(
                        modifier = Modifier.padding(innerPadding),
                        fpsTracker = inAppTracker,
                        isAccessibilityEnabled = isAccessibilityEnabled,
                        isSystemOverlayEnabled = isSystemOverlayEnabled,
                        isOverlayServiceRunning = isOverlayServiceRunning,
                        onRequestAccessibility = { launchAccessibilitySettings() },
                        onRequestSystemOverlay = { launchPermissionSettings() },
                        onToggleSystemOverlayService = { toggleOverlayService() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    private fun checkPermissions() {
        isAccessibilityEnabled = isAccessibilityServiceEnabled()
        isSystemOverlayEnabled = Settings.canDrawOverlays(this)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedId = "$packageName/${FpsAccessibilityService::class.java.name}"
        val shortId = "$packageName/.FpsAccessibilityService"
        val settingValue = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return settingValue.split(':').any {
            it.equals(expectedId, ignoreCase = true) || it.equals(shortId, ignoreCase = true)
        }
    }

    private fun launchAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun launchPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun toggleOverlayService() {
        val intent = Intent(this, FpsOverlayService::class.java)
        if (isOverlayServiceRunning) {
            stopService(intent)
            isOverlayServiceRunning = false
        } else {
            if (isSystemOverlayEnabled) {
                startService(intent)
                isOverlayServiceRunning = true
            } else {
                launchPermissionSettings()
            }
        }
    }
}

@Composable
fun FpsDashboardScreen(
    modifier: Modifier = Modifier,
    fpsTracker: FpsTracker,
    isAccessibilityEnabled: Boolean,
    isSystemOverlayEnabled: Boolean,
    isOverlayServiceRunning: Boolean,
    onRequestAccessibility: () -> Unit,
    onRequestSystemOverlay: () -> Unit,
    onToggleSystemOverlayService: () -> Unit
) {
    var currentFps by remember { mutableFloatStateOf(0f) }
    var averageFps by remember { mutableFloatStateOf(0f) }
    var minFps by remember { mutableFloatStateOf(0f) }
    var maxFps by remember { mutableFloatStateOf(0f) }
    var stutterCount by remember { mutableIntStateOf(0) }

    val fpsHistory = remember { mutableStateListOf<Float>() }

    var particlesEnabled by remember { mutableStateOf(false) }
    var particleCount by remember { mutableFloatStateOf(1200f) }
    var gravityEnabled by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        val choreographer = Choreographer.getInstance()
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                fpsTracker.recordFrame(frameTimeNanos)
                
                currentFps = fpsTracker.currentFps
                averageFps = fpsTracker.averageFps
                minFps = fpsTracker.minFps
                maxFps = fpsTracker.maxFps
                stutterCount = fpsTracker.stutterCount
                
                choreographer.postFrameCallback(this)
            }
        }
        choreographer.postFrameCallback(callback)
        onDispose {
            choreographer.removeFrameCallback(callback)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(120)
            if (currentFps > 0f) {
                fpsHistory.add(currentFps)
                if (fpsHistory.size > 80) {
                    fpsHistory.removeAt(0)
                }
            }
        }
    }

    val themeBackground = Color(0xFF07090D)
    val neonCyan = Color(0xFF00FFCC)
    val neonPink = Color(0xFFFF007F)
    val textPrimary = Color(0xFFE2E8F0)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(themeBackground)
    ) {
        val screenWidth = maxWidth
        val isWideScreen = screenWidth > 640.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "ACCESSIBILITY OVERLAY ENGINE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = neonPink,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            Text(
                text = "МОНИТОР FPS НА ЭКРАНЕ",
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                color = textPrimary,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (isWideScreen) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        FpsGaugeCard(currentFps = currentFps, neonCyan = neonCyan, textPrimary = textPrimary)
                        Spacer(modifier = Modifier.height(16.dp))
                        FpsGraphCard(fpsHistory = fpsHistory, neonCyan = neonCyan, textPrimary = textPrimary)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        AccessibilityControlCard(
                            isEnabled = isAccessibilityEnabled,
                            onRequestActivation = onRequestAccessibility,
                            neonCyan = neonCyan,
                            neonPink = neonPink,
                            textPrimary = textPrimary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        TelemetryStatsCard(
                            averageFps = averageFps,
                            minFps = minFps,
                            maxFps = maxFps,
                            stutterCount = stutterCount,
                            onReset = { fpsTracker.reset() },
                            neonCyan = neonCyan,
                            textPrimary = textPrimary
                        )
                    }
                }
            } else {
                AccessibilityControlCard(
                    isEnabled = isAccessibilityEnabled,
                    onRequestActivation = onRequestAccessibility,
                    neonCyan = neonCyan,
                    neonPink = neonPink,
                    textPrimary = textPrimary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                FpsGaugeCard(currentFps = currentFps, neonCyan = neonCyan, textPrimary = textPrimary)
                Spacer(modifier = Modifier.height(16.dp))
                FpsGraphCard(fpsHistory = fpsHistory, neonCyan = neonCyan, textPrimary = textPrimary)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                TelemetryStatsCard(
                    averageFps = averageFps,
                    minFps = minFps,
                    maxFps = maxFps,
                    stutterCount = stutterCount,
                    onReset = { fpsTracker.reset() },
                    neonCyan = neonCyan,
                    textPrimary = textPrimary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                SystemOverlayControlCard(
                    isOverlayAllowed = isSystemOverlayEnabled,
                    isServiceRunning = isOverlayServiceRunning,
                    onRequestPermission = onRequestSystemOverlay,
                    onToggleService = onToggleSystemOverlayService,
                    neonCyan = neonCyan,
                    neonPink = neonPink,
                    textPrimary = textPrimary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F141C)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "СИМУЛЯТОР ФИЗИЧЕСКОЙ НАГРУЗКИ",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = neonCyan,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Text(
                        text = "Включите рендеринг физических частиц с векторной гравитацией, чтобы нагрузить рендерер Jetpack Compose и протестировать точность работы счетчика в углу.",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8),
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Частицы симуляции",
                            color = textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Switch(
                            checked = particlesEnabled,
                            onCheckedChange = { particlesEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = neonCyan,
                                checkedTrackColor = Color(0xFF003F33)
                            )
                        )
                    }

                    AnimatedVisibility(
                        visible = particlesEnabled,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Количество тел: ${particleCount.toInt()}",
                                    fontSize = 13.sp,
                                    color = Color(0xFF94A3B8)
                                )
                                Text(
                                    text = when {
                                        particleCount < 1000f -> "Низкая"
                                        particleCount < 4000f -> "Средняя"
                                        particleCount < 8000f -> "Высокая!"
                                        else -> "ЭКСТРЕМАЛЬНАЯ!"
                                    },
                                    color = if (particleCount >= 7000f) neonPink else neonCyan,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            Slider(
                                value = particleCount,
                                onValueChange = { particleCount = it },
                                valueRange = 100f..12000f,
                                colors = SliderDefaults.colors(
                                    thumbColor = neonCyan,
                                    activeTrackColor = neonCyan,
                                    inactiveTrackColor = Color(0xFF1E293B)
                                )
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Вектор силы притяжения (Гравитация)",
                                    fontSize = 13.sp,
                                    color = textPrimary
                                )
                                Switch(
                                    checked = gravityEnabled,
                                    onCheckedChange = { gravityEnabled = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = neonCyan,
                                        checkedTrackColor = Color(0xFF003F33)
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(170.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF07090D))
                                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                            ) {
                                val particleEngine = remember { ParticleEngine() }
                                var localTick by remember { mutableStateOf(0L) }
                                var lastTime by remember { mutableStateOf(0L) }

                                Canvas(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable {
                                            particleEngine.init(
                                                width = 1000f,
                                                height = 500f,
                                                count = particleCount.toInt()
                                            )
                                        }
                                ) {
                                    val canvasWidth = size.width
                                    val canvasHeight = size.height

                                    if (lastTime == 0L) {
                                        lastTime = System.nanoTime()
                                        particleEngine.init(canvasWidth, canvasHeight, particleCount.toInt())
                                    }

                                    val currTime = System.nanoTime()
                                    val delta = (currTime - lastTime) / 1_000_000_000f
                                    lastTime = currTime

                                    if (particleEngine.particles.size != particleCount.toInt()) {
                                        particleEngine.init(canvasWidth, canvasHeight, particleCount.toInt())
                                    }

                                    particleEngine.update(
                                        width = canvasWidth,
                                        height = canvasHeight,
                                        deltaSeconds = delta,
                                        gravityEnabled = gravityEnabled
                                    )

                                    for (p in particleEngine.particles) {
                                        drawCircle(
                                            color = p.color,
                                            radius = p.size,
                                            center = Offset(p.x, p.y)
                                        )
                                    }
                                }

                                LaunchedEffect(particlesEnabled) {
                                    while (particlesEnabled) {
                                        withFrameMillis { frameTime ->
                                            localTick = frameTime
                                        }
                                    }
                                    lastTime = 0L
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0xFF1E293B))
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Симулякация мгновенного микро-фриза",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                    )
                    
                    Text(
                        text = "Блокирует главный GUI поток на 80мс для имитации резкого игрового подвисания и проверки отзывчивости графика.",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )

                    Button(
                        modifier = Modifier.fillMaxWidth().testTag("stutter_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = neonPink),
                        onClick = {
                            val startTime = System.currentTimeMillis()
                            while (System.currentTimeMillis() - startTime < 80) {
                                // Block
                            }
                        }
                    ) {
                        Text(
                            text = "ДРОПНУТЬ С ТАКТОМ (STUTTER)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FpsGaugeCard(
    modifier: Modifier = Modifier,
    currentFps: Float,
    neonCyan: Color,
    textPrimary: Color
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F141C)),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(170.dp)
            ) {
                val animatedFpsState = animateFloatAsState(
                    targetValue = currentFps,
                    animationSpec = tween(durationMillis = 80),
                    label = "fpsArcAnm"
                )
                
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val sizePx = size.minDimension
                    val strokeWidth = 14.dp.toPx()
                    val center = Offset(size.width / 2, size.height / 2)
                    val radius = (sizePx - strokeWidth) / 2
                    
                    drawArc(
                        color = Color(0x1F94A3B8),
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    
                    val swept = (animatedFpsState.value / 120f).coerceIn(0f, 1f) * 270f
                    val arcColor = when {
                        currentFps >= 55f -> neonCyan
                        currentFps >= 30f -> Color(0xFFFFCC00)
                        else -> Color(0xFFFF3366)
                    }
                    
                    drawArc(
                        color = arcColor,
                        startAngle = 135f,
                        sweepAngle = swept,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = String.format(Locale.US, "%.1f", currentFps),
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Black,
                        color = textPrimary,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "FPS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF64748B),
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f ms", if (currentFps > 0f) 1000f / currentFps else 0f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Light,
                        color = Color(0xFF94A3B8),
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            
            Text(
                text = when {
                    currentFps >= 58f -> "СТАБИЛЬНО • ВЫСОКАЯ ЧАСТОТА"
                    currentFps >= 45f -> "СТАТИЧНО • СРЕДНИЙ ДИАПАЗОН"
                    currentFps >= 30f -> "ПРОСАДКА • НИЗКИЙ ПИКСЕЛЬ"
                    else -> "КРИТИЧНО • ВЫСОКИЙ ЛАГПОРОГ"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    currentFps >= 55f -> neonCyan
                    currentFps >= 30f -> Color(0xFFFFCC00)
                    else -> Color(0xFFFF3366)
                },
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun FpsGraphCard(
    modifier: Modifier = Modifier,
    fpsHistory: List<Float>,
    neonCyan: Color,
    textPrimary: Color
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F141C)),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "ИСТОРИЯ ЧАСТОТЫ КАДРОВ",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (fpsHistory.size < 2) return@Canvas
                    
                    val width = size.width
                    val height = size.height
                    val maxVal = 135f
                    
                    val path = Path()
                    val stepX = width / (fpsHistory.size - 1)
                    
                    val points = fpsHistory.mapIndexed { idx, valFps ->
                        val x = idx * stepX
                        val ratio = valFps / maxVal
                        val coerced = ratio.coerceIn(0f, 1f)
                        val y = height - (coerced * height)
                        Offset(x, y)
                    }

                    val renderGrids = listOf(30f, 60f, 90f, 120f)
                    renderGrids.forEach { fpsThreshold ->
                        val ratio = fpsThreshold / maxVal
                        val y = height - (ratio * height)
                        drawLine(
                            color = Color(0x1200FFCC),
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 1f
                        )
                    }

                    val fillPath = Path().apply {
                        moveTo(points.first().x, height)
                        points.forEach { p -> lineTo(p.x, p.y) }
                        lineTo(points.last().x, height)
                        close()
                    }

                    drawPath(
                        path = fillPath,
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0x3300FFCC), Color(0x00000000)),
                            start = Offset(0f, 0f),
                            end = Offset(0f, height)
                        )
                    )

                    path.moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        path.lineTo(points[i].x, points[i].y)
                    }

                    drawPath(
                        path = path,
                        color = neonCyan,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("0 FPS", fontSize = 10.sp, color = Color(0xFF64748B), fontFamily = FontFamily.Monospace)
                Text("60 FPS", fontSize = 10.sp, color = Color(0xFF64748B), fontFamily = FontFamily.Monospace)
                Text("120 FPS", fontSize = 10.sp, color = Color(0xFF64748B), fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
fun TelemetryStatsCard(
    modifier: Modifier = Modifier,
    averageFps: Float,
    minFps: Float,
    maxFps: Float,
    stutterCount: Int,
    onReset: () -> Unit,
    neonCyan: Color,
    textPrimary: Color
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F141C)),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ДЕТАЛЬНАЯ СТАТИСТИКА",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "СБРОС СТАТА",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = neonCyan,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clickable { onReset() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text("СРЕДНИЙ", fontSize = 10.sp, color = Color(0xFF64748B), fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = String.format(Locale.US, "%.1f", averageFps),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text("МИН (1%)", fontSize = 10.sp, color = Color(0xFF64748B), fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = String.format(Locale.US, "%.1f", minFps),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (minFps < 30f && minFps > 0f) Color(0xFFFF3366) else textPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text("МАКСИМУМ", fontSize = 10.sp, color = Color(0xFF64748B), fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = String.format(Locale.US, "%.1f", maxFps),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFF1E293B))
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFFF3366)))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Зафиксировано лагов:",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                Text(
                    text = "$stutterCount",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFF3366),
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun AccessibilityControlCard(
    modifier: Modifier = Modifier,
    isEnabled: Boolean,
    onRequestActivation: () -> Unit,
    neonCyan: Color,
    neonPink: Color,
    textPrimary: Color
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F141C)),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isEnabled) neonCyan else neonPink)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "СТАТУС: СЛУЖБА СПЕЦ. ВОЗМОЖНОСТЕЙ",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isEnabled) neonCyan else neonPink,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "Служба специальных возможностей позволяет рисовать высокоточный счетчик FPS в углу экрана поверх любых игр.",
                fontSize = 13.sp,
                color = textPrimary,
                fontWeight = FontWeight.Bold,
                lineHeight = 16.sp
            )

            Text(
                text = "💡 Как активировать:\n1. Нажмите на розовую кнопку ниже.\n2. В системных настройках в разделе 'Скачанные приложения' (или Услуги) найдите службу 'FPS Monitor Counter'.\n3. Включите переключатель. Всё!",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8),
                lineHeight = 15.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            if (!isEnabled) {
                Button(
                    onClick = onRequestActivation,
                    colors = ButtonDefaults.buttonColors(containerColor = neonPink),
                    modifier = Modifier.fillMaxWidth().testTag("grant_permission_button")
                ) {
                    Text("КЛАЦ - АКТИВИРОВАТЬ В НАСТРОЙКАХ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF07090D))
                        .border(1.dp, neonCyan, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ВИДЖЕТ FPS АКТИВЕН!",
                            color = neonCyan,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Служба запущена. Зажмите плавающую плашку в углу, чтобы переместить.",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = onRequestActivation,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("ВЫКЛЮЧИТЬ СЛУЖБУ В НАСТРОЙКАХ", fontSize = 11.sp, color = textPrimary)
                }
            }
        }
    }
}

@Composable
fun SystemOverlayControlCard(
    modifier: Modifier = Modifier,
    isOverlayAllowed: Boolean,
    isServiceRunning: Boolean,
    onRequestPermission: () -> Unit,
    onToggleService: () -> Unit,
    neonCyan: Color,
    neonPink: Color,
    textPrimary: Color
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "РЕЗЕРВНЫЙ ВАРИАНТ (SYSTEM OVERLAY)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8),
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "Классический метод 'Поверх других приложений'. Используйте его только если у вас нет доступа к специальным возможностям.",
                fontSize = 11.sp,
                color = Color(0xFF64748B),
                lineHeight = 15.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (!isOverlayAllowed) {
                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("ВЫДАТЬ РАЗРЕШЕНИЕ НА НАЛОЖЕНИЕ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF07090D))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isServiceRunning) "ВИДЖЕТ ЗАПУЩЕН" else "ВИДЖЕТ ОТКЛЮЧЕН",
                            color = if (isServiceRunning) neonCyan else Color(0xFF64748B),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Switch(
                        checked = isServiceRunning,
                        onCheckedChange = { onToggleService() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = neonCyan,
                            checkedTrackColor = Color(0xFF003F33)
                        )
                    )
                }
            }
        }
    }
}

class ParticleEngine {
    class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val color: Color,
        val size: Float
    )
    
    val particles = ArrayList<Particle>()
    
    fun init(width: Float, height: Float, count: Int) {
        particles.clear()
        if (width <= 0f || height <= 0f) return
        
        val random = java.util.Random()
        val colors = listOf(
            Color(0xFF00FFCC),
            Color(0xFFFF007F),
            Color(0xFF8A2BE2),
            Color(0xFFFFFF00)
        )
        for (i in 0 until count) {
            particles.add(
                Particle(
                    x = random.nextFloat() * width,
                    y = random.nextFloat() * height,
                    vx = (random.nextFloat() - 0.5f) * 350f,
                    vy = (random.nextFloat() - 0.5f) * 350f,
                    color = colors[random.nextInt(colors.size)],
                    size = 3.5f + random.nextFloat() * 4f
                )
            )
        }
    }
    
    fun update(width: Float, height: Float, deltaSeconds: Float, gravityEnabled: Boolean) {
        if (width <= 0f || height <= 0f) return
        
        val gravity = if (gravityEnabled) 580f else 0f
        val dt = deltaSeconds.coerceIn(0f, 0.05f) 

        for (p in particles) {
            p.vy += gravity * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            
            if (p.x < 0f) {
                p.x = 0f
                p.vx = -p.vx * 0.9f
            } else if (p.x > width) {
                p.x = width
                p.vx = -p.vx * 0.9f
            }
            
            if (p.y < 0f) {
                p.y = 0f
                p.vy = -p.vy * 0.9f
            } else if (p.y > height) {
                p.y = height
                p.vy = -p.vy * 0.9f
            }
        }
    }
}
