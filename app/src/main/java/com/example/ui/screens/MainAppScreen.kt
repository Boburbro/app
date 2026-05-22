package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.DailyGoal
import com.example.data.FocusSession
import com.example.data.Task
import com.example.ui.theme.*
import com.example.ui.viewmodel.FocusViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    viewModel: FocusViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }

    // Request permissions for background notifications
    var permissionGranted by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        permissionGranted = isGranted
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissionGranted = true
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = SlateDarkSurface,
                tonalElevation = 8.dp,
                windowInsets = WindowInsets.navigationBars,
                modifier = Modifier.border(1.dp, SlateDarkBorder, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                val items = listOf(
                    Triple("Home", Icons.Default.Home, Icons.Outlined.Home),
                    Triple("Fokus", Icons.Default.Timer, Icons.Outlined.Timer),
                    Triple("Vazifalar", Icons.Default.CheckCircle, Icons.Outlined.CheckCircle),
                    Triple("Statistika", Icons.Default.BarChart, Icons.Outlined.BarChart),
                    Triple("Sozlamalar", Icons.Default.Settings, Icons.Outlined.Settings)
                )

                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == index) item.second else item.third,
                                contentDescription = item.first,
                                tint = if (selectedTab == index) PrimaryOrange else TextSecondary
                            )
                        },
                        label = {
                            Text(
                                text = item.first,
                                fontSize = 11.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) TextPrimary else TextSecondary
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = SlateDarkBorder
                        ),
                        modifier = Modifier.testTag("nav_tab_${item.first.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            when (selectedTab) {
                0 -> HomeScreen(viewModel, onNavigateToTimer = { selectedTab = 1 })
                1 -> TimerScreen(viewModel)
                2 -> TasksScreen(viewModel)
                3 -> StatisticsScreen(viewModel)
                4 -> SettingsScreen(viewModel)
            }
        }
    }
}

// ==========================================
// 1. HOME SCREEN IMPLEMENTATION
// ==========================================
@Composable
fun HomeScreen(viewModel: FocusViewModel, onNavigateToTimer: () -> Unit) {
    val context = LocalContext.current
    val tasks by viewModel.allTasks.collectAsStateWithLifecycle()
    val selectedTask by viewModel.selectedTask.collectAsStateWithLifecycle()
    
    val todayCompleted by viewModel.todayCompletedSessionsCount.collectAsStateWithLifecycle()
    val todayMins by viewModel.todayFocusMinutes.collectAsStateWithLifecycle()
    val streak by viewModel.currentStreak.collectAsStateWithLifecycle()
    val goal by viewModel.todayGoal.collectAsStateWithLifecycle()

    var showTaskPicker by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "FocusNest",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = PrimaryOrange,
                        fontFamily = FontFamily.SansSerif
                    )
                    Text(
                        text = "Bugungi unumdorlik uyasiga xush kelibsiz",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
                
                // Streak Tally Badge
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.horizontalGradient(listOf(GoldAmber, PrimaryOrange)))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = "Streak",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$streak KUN",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }
            }
        }

        // Today's Goal Progress Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SlateDarkBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = SlateDarkSurface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Bugungi Maqsadlar",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.TaskAlt,
                                contentDescription = "",
                                tint = SuccessGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sessiyalar: $todayCompleted / ${goal.targetSessions}",
                                fontSize = 14.sp,
                                color = TextSecondary
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.HourglassBottom,
                                contentDescription = "",
                                tint = PrimaryOrange,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Fokus vaqti: $todayMins / ${goal.targetMinutes} daqiqa",
                                fontSize = 14.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    // Circular Progress Tracker
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(80.dp)
                    ) {
                        val sessionRatio = if (goal.targetSessions > 0) todayCompleted.toFloat() / goal.targetSessions else 0f
                        val animRatio by animateFloatAsState(
                            targetValue = sessionRatio.coerceIn(0f, 1f),
                            animationSpec = tween(1200, easing = FastOutSlowInEasing)
                        )
                        
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                color = SlateDarkBorder,
                                style = Stroke(width = 8.dp.toPx())
                            )
                            drawArc(
                                color = SuccessGreen,
                                startAngle = -90f,
                                sweepAngle = 360f * animRatio,
                                useCenter = false,
                                style = Stroke(width = 8.dp.toPx())
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val percent = (sessionRatio * 100).toInt().coerceIn(0, 100)
                            Text(
                                text = "$percent%",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = SuccessGreen
                            )
                            Text(
                                text = "completed",
                                fontSize = 8.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }

        // Selected Link Task Section
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "Aktiv Fokus Vazifasi",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (selectedTask != null) {
                    val task = selectedTask!!
                    val parsedColor = remember(task.colorHex) {
                        try { Color(android.graphics.Color.parseColor(task.colorHex)) } catch (e: Exception) { PrimaryOrange }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SlateDarkSurface)
                            .border(1.dp, SlateDarkBorder, RoundedCornerShape(12.dp))
                            .clickable { showTaskPicker = true }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(parsedColor)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = task.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Jami sarflangan: ${task.totalFocusMinutes} daqiqa",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                        
                        Text(
                            text = "O'zgartirish",
                            color = PrimaryOrange,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SlateDarkSurface)
                            .border(1.dp, SlateDarkBorder, RoundedCornerShape(12.dp))
                            .clickable { showTaskPicker = true }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Hozirgi mashg'ulot uchun vazifa biriktirish",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Quick Launch CTA Action Panel
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    onNavigateToTimer()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .testTag("start_session_panel"),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Start",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Fokus sessiyasini boshlash",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }
            }
        }

        // Quick Guidelines and Quotes
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SlateDarkBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = SlateDarkSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Nega FocusNest?",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryOrange
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Pomodoro tizimi sizga miya charchashining oldini olishga va diqqatni to'liq bitta vazifaga yo'naltirishga yordam beradi. Har bir tugallangan sessiya task statistikasiga avtomatik qo'shiladi.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }

    // Task Selection Sheet/Picker Block
    if (showTaskPicker) {
        AlertDialog(
            onDismissRequest = { showTaskPicker = false },
            containerColor = SlateDarkSurface,
            title = {
                Text(
                    text = "Vazifa Tanlang",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Fokuslash uchun aktiv vazifalardan birini belgilang:",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (tasks.isEmpty()) {
                        Text(
                            text = "Hozircha vazifalar mavjud emas. Vazifalar oynasida yangi vazifa qo'shishingiz mumkin.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .heightIn(max = 250.dp)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selectedTask == null) SlateDarkBorder else Color.Transparent)
                                        .clickable {
                                            viewModel.selectTask(null)
                                            showTaskPicker = false
                                        }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(TextSecondary)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Vazifasiz (Faqat erkin timer)",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            items(tasks) { task ->
                                val parsedColor = remember(task.colorHex) {
                                    try { Color(android.graphics.Color.parseColor(task.colorHex)) } catch (e: Exception) { PrimaryOrange }
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selectedTask?.id == task.id) SlateDarkBorder else Color.Transparent)
                                        .clickable {
                                            viewModel.selectTask(task)
                                            showTaskPicker = false
                                        }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(parsedColor)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = task.title,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTaskPicker = false }) {
                    Text("Yopish", color = PrimaryOrange)
                }
            }
        )
    }
}

// ==========================================
// 2. FOCUS TIMER SCREEN & DEEP FOCUS
// ==========================================
@Composable
fun TimerScreen(viewModel: FocusViewModel) {
    val context = LocalContext.current
    
    // Live Service states
    val timeLeft by viewModel.timeLeftSeconds.collectAsStateWithLifecycle()
    val totalDuration by viewModel.totalDurationSeconds.collectAsStateWithLifecycle()
    val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()
    val currentType by viewModel.currentType.collectAsStateWithLifecycle()
    val selectedTask by viewModel.selectedTask.collectAsStateWithLifecycle()
    val distractions by viewModel.distractionsCount.collectAsStateWithLifecycle()
    val isNoisePlaying by viewModel.isNoiseEnabled.collectAsStateWithLifecycle()

    var customDurationOption by remember { mutableStateOf(0) } // 0: 25/5, 1: 50/10, 2: 90/20, 3: Custom

    val minutes = timeLeft / 60
    val seconds = timeLeft % 60
    val timeFormatted = String.format("%02d:%02d", minutes, seconds)

    val progressPercent = if (totalDuration > 0) timeLeft.toFloat() / totalDuration else 0f

    // Animated colors based on type
    val focusTint = when (currentType) {
        "FOCUS" -> PrimaryOrange
        "SHORT_BREAK" -> SuccessGreen
        else -> ElectricBlue
    }

    // Static quotes database for user inspiration during session!
    val quotes = listOf(
        "Fokus - bu barcha narsalardan voz kechib, bitta narsaga diqqat qilish demakdir.",
        "Sekin bo'lsa ham unumli harakat to'xtab qolishdan yaxshiroqdir.",
        "Vaqtingiz cheklangan, uni boshqalarning hayotini yashashga sarflamang.",
        "Haqiqiy muvaffaqiyat har kungi kichik g'alabalardan yig'iladi.",
        "Diqqatingiz qay darajada bo'lsa, natijangiz ham shunga mos bo'ladi.",
        "Telefonni unuting, unumdorlik uyasiga sho'ng'ing!"
    )
    val activeQuote = remember(timeLeft / 300) { quotes[(timeLeft / 300) % quotes.size] } // Change quotes every 5 minutes

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Immersive header
            item {
                Text(
                    text = when (currentType) {
                        "FOCUS" -> "DIQQAT REJIMIDA"
                        "SHORT_BREAK" -> "KICHIK TANAFSDASIZ"
                        else -> "UZIQ TANAFSDASIZ"
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = focusTint,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Big Clock Box
            item {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(240.dp)
                        .padding(vertical = 12.dp)
                ) {
                    // Animating background ring intensity gradient
                    val animProgress by animateFloatAsState(
                        targetValue = progressPercent,
                        animationSpec = tween(1000, easing = LinearEasing)
                    )

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = SlateDarkBorder,
                            style = Stroke(width = 12.dp.toPx())
                        )
                        drawArc(
                            color = focusTint,
                            startAngle = -90f,
                            sweepAngle = 360f * animProgress,
                            useCenter = false,
                            style = Stroke(width = 12.dp.toPx())
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = timeFormatted,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary,
                            letterSpacing = 1.sp,
                            modifier = Modifier.testTag("timer_clock_display")
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (selectedTask != null) selectedTask!!.title else "Erkin loyiha",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 150.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Controller Actions Pane
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isRunning && timeLeft == totalDuration) {
                        // Start Focus Trigger
                        Button(
                            onClick = {
                                viewModel.startFocusSession(context, "FOCUS")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("timer_primary_start_btn").weight(1f).height(50.dp)
                        ) {
                            Text("Sessiyani boshlash", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    } else {
                        // Pause / Resume and Stop actions
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isRunning) {
                                Button(
                                    onClick = { viewModel.pauseFocusSession(context) },
                                    colors = ButtonDefaults.buttonColors(containerColor = SlateDarkBorder),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).height(50.dp).testTag("timer_pause_btn")
                                ) {
                                    Icon(Icons.Default.Pause, "", tint = TextPrimary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pause", color = TextPrimary, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = { viewModel.resumeFocusSession(context) },
                                    colors = ButtonDefaults.buttonColors(containerColor = focusTint),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).height(50.dp).testTag("timer_resume_btn")
                                ) {
                                    Icon(Icons.Default.PlayArrow, "", tint = Color.White)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Davom etish", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }

                            Button(
                                onClick = { viewModel.stopFocusSession(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.2f)),
                                border = BorderStroke(1.dp, Color.Red),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(50.dp).testTag("timer_stop_btn")
                            ) {
                                Icon(Icons.Default.Stop, "", tint = Color.Red)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("To'xtatish", color = Color.Red, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Quick Interval Mode Selectors (only visible when timer is resetting/at rest)
            if (!isRunning && timeLeft == totalDuration) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(SlateDarkSurface)
                            .border(1.dp, SlateDarkBorder, RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Timer Rejimi va Davomiyligi",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        val modes = listOf("25 / 5", "50 / 10", "90 / 20", "Custom")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            modes.forEachIndexed { idx, label ->
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (customDurationOption == idx) PrimaryOrange else SlateDarkBorder)
                                        .clickable {
                                            customDurationOption = idx
                                            when (idx) {
                                                0 -> viewModel.updateSettings(25, 5, 15, viewModel.targetSessionsGoal.value, viewModel.targetMinutesGoal.value)
                                                1 -> viewModel.updateSettings(50, 10, 20, viewModel.targetSessionsGoal.value, viewModel.targetMinutesGoal.value)
                                                2 -> viewModel.updateSettings(90, 20, 30, viewModel.targetSessionsGoal.value, viewModel.targetMinutesGoal.value)
                                            }
                                        }
                                        .padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (customDurationOption == idx) Color.White else TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Short/Long break quick starters toggles (only when customization option is chosen)
                        AnimatedVisibility(visible = customDurationOption == 3) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.startFocusSession(context, "SHORT_BREAK") },
                                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Kichik Tanaffus", fontSize = 12.sp, color = Color.White)
                                    }
                                    Button(
                                        onClick = { viewModel.startFocusSession(context, "LONG_BREAK") },
                                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Uzoq Tanaffus", fontSize = 12.sp, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Killer Features: 1. Deep Focus synthesized white-noise toggle and distraction report card (only active during running state)
            if (isRunning) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SlateDarkBorder, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = SlateDarkSurface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Deep Focus Mashg'ulot Vositasi",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryOrange
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // Custom rain noise generator trigger
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SlateDarkBorder)
                                    .clickable { viewModel.toggleWhiteNoise(context) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isNoisePlaying) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                        contentDescription = "",
                                        tint = if (isNoisePlaying) SuccessGreen else TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Gidro-akustik Yomg'ir Efekti",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "Sintezlangan so'ndiruvchi oq shovqin",
                                            fontSize = 10.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }
                                Switch(
                                    checked = isNoisePlaying,
                                    onCheckedChange = { viewModel.toggleWhiteNoise(context) },
                                    colors = SwitchDefaults.colors(checkedThumbColor = SuccessGreen)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Distraction ticker action button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, SlateDarkBorder, RoundedCornerShape(8.dp))
                                    .clickable { viewModel.recordDistraction() }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "",
                                        tint = GoldAmber,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Chalg'uvchi Ta'sirni Hisoblash",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "Har gal diqqat chalg'iganda bosing",
                                            fontSize = 10.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(GoldAmber.copy(alpha = 0.2f))
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "$distractions marta",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp,
                                        color = GoldAmber
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Dynamic Quote Card Placeholder during diqqat
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateDarkSurface.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatQuote,
                            contentDescription = "",
                            tint = PrimaryOrange,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = activeQuote,
                            fontSize = 13.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 3. TASKS SCREEN (CRUD)
// ==========================================
@Composable
fun TasksScreen(viewModel: FocusViewModel) {
    val tasks by viewModel.allTasks.collectAsStateWithLifecycle()
    val selectedTask by viewModel.selectedTask.collectAsStateWithLifecycle()

    var showAddTaskDialog by remember { mutableStateOf(false) }
    var taskTitleInput by remember { mutableStateOf("") }
    
    // Modern Material 3 palette selection list
    val colorsList = listOf("#F9623B", "#10B981", "#06B6D4", "#F59E0B", "#D946EF", "#8B5CF6")
    var selectedColorIndex by remember { mutableStateOf(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    taskTitleInput = ""
                    selectedColorIndex = 0
                    showAddTaskDialog = true
                },
                containerColor = PrimaryOrange,
                modifier = Modifier.testTag("add_task_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Task", tint = Color.White)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Fokus Vazifalar ro'yxati",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
            )
            Text(
                text = "Har bir vazifaga yo'naltirilgan vaqtlaringiz hisoblab boriladi.",
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (tasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.Assignment,
                            contentDescription = "",
                            tint = TextSecondary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Hozircha vazifalar mavjud emas",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Quyidagi tugma orqali yangisini yarating",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(tasks) { task ->
                        val taskColor = remember(task.colorHex) {
                            try { Color(android.graphics.Color.parseColor(task.colorHex)) } catch (e: Exception) { PrimaryOrange }
                        }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SlateDarkSurface)
                                .border(
                                    width = if (selectedTask?.id == task.id) 2.dp else 1.dp,
                                    color = if (selectedTask?.id == task.id) PrimaryOrange else SlateDarkBorder,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    if (selectedTask?.id == task.id) viewModel.selectTask(null) else viewModel.selectTask(task)
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(taskColor)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = task.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Sarflangan to'liq fokus: ${task.totalFocusMinutes} daqiqa",
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (selectedTask?.id == task.id) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(PrimaryOrange.copy(alpha = 0.2f))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("Aktiv", color = PrimaryOrange, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                IconButton(
                                    onClick = { viewModel.deleteTask(task) },
                                    modifier = Modifier.size(24.dp).testTag("delete_task_${task.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Sheet representation
    if (showAddTaskDialog) {
        AlertDialog(
            onDismissRequest = { showAddTaskDialog = false },
            containerColor = SlateDarkSurface,
            title = {
                Text(
                    text = "Yangi Vazifa Qo'shish",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = taskTitleInput,
                        onValueChange = { taskTitleInput = it },
                        label = { Text("Mavzu nomi") },
                        modifier = Modifier.fillMaxWidth().testTag("add_task_title_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryOrange,
                            unfocusedBorderColor = SlateDarkBorder,
                            focusedLabelColor = PrimaryOrange
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Veb-Rang belgisi:", color = TextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        colorsList.forEachIndexed { index, hex ->
                            val color = remember(hex) { Color(android.graphics.Color.parseColor(hex)) }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (selectedColorIndex == index) 3.dp else 0.dp,
                                        color = if (selectedColorIndex == index) TextPrimary else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColorIndex = index }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (taskTitleInput.isNotBlank()) {
                            viewModel.addTask(taskTitleInput, colorsList[selectedColorIndex])
                            showAddTaskDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange)
                ) {
                    Text("Yaratish", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTaskDialog = false }) {
                    Text("Bekor qilish", color = TextSecondary)
                }
            }
        )
    }
}

// ==========================================
// 4. STATS SCREEN (CHARTS & HEATMAP!)
// ==========================================
@Composable
fun StatisticsScreen(viewModel: FocusViewModel) {
    val sessions by viewModel.allSessions.collectAsStateWithLifecycle()
    val streak by viewModel.currentStreak.collectAsStateWithLifecycle()
    val score by viewModel.focusScore.collectAsStateWithLifecycle()

    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    // Map sessions dynamically to dates
    val sessionStatsMap = remember(sessions) {
        sessions.filter { it.completed && it.type == "FOCUS" }
            .groupBy { dateFormatter.format(Date(it.startTime)) }
            .mapValues { entry ->
                entry.value.sumOf { it.durationSeconds } / 60
            }
    }

    // Weekly focus stats for the simple graph charting
    val weekDaysLabels = listOf("Dsh", "Ssh", "Chsh", "Psh", "Jum", "Shb", "Ysh")
    val weeklyMinutes = remember(sessions) {
        val list = MutableList(7) { 0 }
        val cal = Calendar.getInstance()
        // Walk back from sunday of current week
        for (i in 0 until 7) {
            val key = dateFormatter.format(cal.time)
            val indexInWeek = when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> 0
                Calendar.TUESDAY -> 1
                Calendar.WEDNESDAY -> 2
                Calendar.THURSDAY -> 3
                Calendar.FRIDAY -> 4
                Calendar.SATURDAY -> 5
                Calendar.SUNDAY -> 6
                else -> 0
            }
            list[indexInWeek] = sessionStatsMap[key] ?: 0
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        list
    }

    // 28-day study heatmap coordinates (representing a 4x7 grid)
    val last28DaysList = remember {
        val list = mutableListOf<String>()
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -27)
        for (i in 0 until 28) {
            list.add(dateFormatter.format(cal.time))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        list
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Tahliliy Hisobotlar",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
            )
            Text(
                text = "Faolligingiz va diqqat darajangiz hisoboti.",
                fontSize = 13.sp,
                color = TextSecondary
            )
        }

        // Trophies and Score Board row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Focus Score (0-100) card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, SlateDarkBorder, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = SlateDarkSurface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Fokus Bahosi",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryOrange
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(60.dp)) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawCircle(color = SlateDarkBorder, style = Stroke(4.dp.toPx()))
                                drawArc(
                                    color = PrimaryOrange,
                                    startAngle = -90f,
                                    sweepAngle = 3.6f * score,
                                    useCenter = false,
                                    style = Stroke(width = 4.dp.toPx())
                                )
                            }
                            Text(
                                text = "$score%",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Consistency score",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                // General summary items
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, SlateDarkBorder, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = SlateDarkSurface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Jami statistika",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val totalDurationSec = sessions.filter { it.completed && it.type == "FOCUS" }.sumOf { it.durationSeconds }
                        Text(
                            text = "${totalDurationSec / 60} daqiqa",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "O'qilgan vaqt",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Sessiyalar: ${sessions.filter { it.completed && it.type == "FOCUS" }.size} ta",
                            fontSize = 12.sp,
                            color = SuccessGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Weekly Focus Column Bar Chart
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SlateDarkBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = SlateDarkSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Haftalik fokus davomiyligi",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        val maxMinutes = (weeklyMinutes.maxOrNull() ?: 100).coerceAtLeast(30)
                        
                        weeklyMinutes.forEachIndexed { index, mins ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                // Column bar scaling height
                                val ratio = mins.toFloat() / maxMinutes
                                val animatedRatio by animateFloatAsState(targetValue = ratio, animationSpec = tween(1000))
                                
                                Box(
                                    modifier = Modifier
                                        .width(18.dp)
                                        .fillMaxHeight(0.8f * animatedRatio.coerceIn(0.05f, 1f))
                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                        .background(if (mins > 0) PrimaryOrange else SlateDarkBorder)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = weekDaysLabels[index],
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Killer Feature 3: Study Heatmap contribution-like graph
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SlateDarkBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = SlateDarkSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Uya faollik xaritasi (28 Kun)",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                        Icon(Icons.Default.HelpOutline, "", tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Grid render of 28 cells showing contribution levels
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(7),
                        modifier = Modifier.height(110.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        userScrollEnabled = false
                    ) {
                        items(last28DaysList) { dateKey ->
                            val mins = sessionStatsMap[dateKey] ?: 0
                            val boxColor = when {
                                mins == 0 -> SlateDarkBorder
                                mins in 1..10 -> SuccessGreen.copy(alpha = 0.25f)
                                mins in 11..25 -> SuccessGreen.copy(alpha = 0.5f)
                                mins in 26..50 -> SuccessGreen.copy(alpha = 0.8f)
                                else -> SuccessGreen // 50+ min focus block intense success green
                            }
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(boxColor)
                                    .border(
                                        width = if (mins >= 50) 1.dp else 0.dp,
                                        color = if (mins >= 50) GoldAmber else Color.Transparent,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Oz", fontSize = 10.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.width(4.dp))
                        listOf(0.25f, 0.5f, 0.8f, 1f).forEach { scale ->
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(SuccessGreen.copy(alpha = scale))
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                        }
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Ko'p", fontSize = 10.sp, color = TextSecondary)
                    }
                }
            }
        }
    }
}

// ==========================================
// 5. SETTINGS SCREEN
// ==========================================
@Composable
fun SettingsScreen(viewModel: FocusViewModel) {
    val pMin by viewModel.pomodoroMinutes.collectAsStateWithLifecycle()
    val sbMin by viewModel.shortBreakMinutes.collectAsStateWithLifecycle()
    val lbMin by viewModel.longBreakMinutes.collectAsStateWithLifecycle()
    val tarSess by viewModel.targetSessionsGoal.collectAsStateWithLifecycle()
    val tarMin by viewModel.targetMinutesGoal.collectAsStateWithLifecycle()

    var isEditing by remember { mutableStateOf(false) }

    var pMinTemp by remember(pMin) { mutableStateOf(pMin) }
    var sbMinTemp by remember(sbMin) { mutableStateOf(sbMin) }
    var lbMinTemp by remember(lbMin) { mutableStateOf(lbMin) }
    var tarSessTemp by remember(tarSess) { mutableStateOf(tarSess) }
    var tarMinTemp by remember(tarMin) { mutableStateOf(tarMin) }

    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Timer va Maqsadlar Sozlamasi",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
            )
            Text(
                text = "Fokus tsikllari va tanaffus muddatlarini mustaqil belgilang.",
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Pomodoro block sliders
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SlateDarkBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = SlateDarkSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Sessiya muddatlari (Daqiqa)",
                            fontWeight = FontWeight.Bold,
                            color = PrimaryOrange,
                            fontSize = 14.sp
                        )
                        if (!isEditing) {
                            TextButton(onClick = { isEditing = true }) {
                                Text("O'zgartirish", color = PrimaryOrange)
                            }
                        } else {
                            Row {
                                TextButton(onClick = {
                                    viewModel.updateSettings(pMinTemp, sbMinTemp, lbMinTemp, tarSessTemp, tarMinTemp)
                                    isEditing = false
                                    Toast.makeText(context, "Sozlamalar saqlandi", Toast.LENGTH_SHORT).show()
                                }) {
                                    Text("Saqlash", color = SuccessGreen, fontWeight = FontWeight.Bold)
                                }
                                TextButton(onClick = {
                                    pMinTemp = pMin
                                    sbMinTemp = sbMin
                                    lbMinTemp = lbMin
                                    tarSessTemp = tarSess
                                    tarMinTemp = tarMin
                                    isEditing = false
                                }) {
                                    Text("Bekor qilish", color = TextSecondary)
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))

                    // 1. Focus duration slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Fokus sessiyasi", fontSize = 13.sp, color = TextPrimary)
                            Text("$pMinTemp min", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PrimaryOrange)
                        }
                        Slider(
                            value = pMinTemp.toFloat(),
                            onValueChange = { if (isEditing) pMinTemp = it.toInt() },
                            valueRange = 5f..120f,
                            colors = SliderDefaults.colors(
                                thumbColor = PrimaryOrange,
                                activeTrackColor = PrimaryOrange,
                                inactiveTrackColor = SlateDarkBorder
                            ),
                            enabled = isEditing
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 2. Short break slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Kichik tanaffus", fontSize = 13.sp, color = TextPrimary)
                            Text("$sbMinTemp min", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                        }
                        Slider(
                            value = sbMinTemp.toFloat(),
                            onValueChange = { if (isEditing) sbMinTemp = it.toInt() },
                            valueRange = 1f..30f,
                            colors = SliderDefaults.colors(
                                thumbColor = SuccessGreen,
                                activeTrackColor = SuccessGreen,
                                inactiveTrackColor = SlateDarkBorder
                            ),
                            enabled = isEditing
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 3. Long Break slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Uzoq tanaffus", fontSize = 13.sp, color = TextPrimary)
                            Text("$lbMinTemp min", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ElectricBlue)
                        }
                        Slider(
                            value = lbMinTemp.toFloat(),
                            onValueChange = { if (isEditing) lbMinTemp = it.toInt() },
                            valueRange = 5f..60f,
                            colors = SliderDefaults.colors(
                                thumbColor = ElectricBlue,
                                activeTrackColor = ElectricBlue,
                                inactiveTrackColor = SlateDarkBorder
                            ),
                            enabled = isEditing
                        )
                    }
                }
            }
        }

        // Cumulative targets goals settings
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SlateDarkBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = SlateDarkSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Kundalik vazifalar rejasi",
                        fontWeight = FontWeight.Bold,
                        color = SuccessGreen,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Target Sessions Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Sessiyalar soni", fontSize = 13.sp, color = TextPrimary)
                            Text("$tarSessTemp ta", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                        }
                        Slider(
                            value = tarSessTemp.toFloat(),
                            onValueChange = { if (isEditing) tarSessTemp = it.toInt() },
                            valueRange = 1f..15f,
                            colors = SliderDefaults.colors(
                                thumbColor = SuccessGreen,
                                activeTrackColor = SuccessGreen,
                                inactiveTrackColor = SlateDarkBorder
                            ),
                            enabled = isEditing
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Target total minutes slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Jami fokus vaqti", fontSize = 13.sp, color = TextPrimary)
                            Text("$tarMinTemp min", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PrimaryOrange)
                        }
                        Slider(
                            value = tarMinTemp.toFloat(),
                            onValueChange = { if (isEditing) tarMinTemp = it.toInt() },
                            valueRange = 20f..480f,
                            colors = SliderDefaults.colors(
                                thumbColor = PrimaryOrange,
                                activeTrackColor = PrimaryOrange,
                                inactiveTrackColor = SlateDarkBorder
                            ),
                            enabled = isEditing
                        )
                    }
                }
            }
        }

        // App Meta developer branding
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "FocusNest v1.0.0 (Offline-First)",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Handcrafted with Jetpack Compose & SQLite",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
        }
    }
}
