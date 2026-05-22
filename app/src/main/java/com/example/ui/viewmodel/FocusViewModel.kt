package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.service.TimerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class FocusViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FocusRepository
    private val sharedPrefs = application.getSharedPreferences("focusnest_prefs", Context.MODE_PRIVATE)

    // Reactive database streams
    val allTasks: StateFlow<List<Task>>
    val allSessions: StateFlow<List<FocusSession>>

    // Live Settings States (SharedPreferences backed)
    val pomodoroMinutes = MutableStateFlow(sharedPrefs.getInt("pomodoro_min", 25))
    val shortBreakMinutes = MutableStateFlow(sharedPrefs.getInt("short_break_min", 5))
    val longBreakMinutes = MutableStateFlow(sharedPrefs.getInt("long_break_min", 15))
    val targetSessionsGoal = MutableStateFlow(sharedPrefs.getInt("target_sessions", 4))
    val targetMinutesGoal = MutableStateFlow(sharedPrefs.getInt("target_minutes", 100))

    // UI Interactive States
    val selectedTaskId = MutableStateFlow<Int?>(null)
    val selectedTask = MutableStateFlow<Task?>(null)

    // Service countdown flows mapped inside the VM
    val timeLeftSeconds = TimerService.timeLeftSeconds
    val totalDurationSeconds = TimerService.totalDurationSeconds
    val isRunning = TimerService.isRunning
    val currentType = TimerService.currentType
    val currentTaskId = TimerService.currentTaskId
    val currentTaskTitle = TimerService.currentTaskTitle
    val distractionsCount = TimerService.distractions
    val isNoiseEnabled = TimerService.isNoiseEnabled

    // Today's Date String Reference
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val todayDateStrStr: String
        get() = dateFormatter.format(Date())

    // Combined Today's Stats
    val todayCompletedSessionsCount: StateFlow<Int>
    val todayFocusMinutes: StateFlow<Int>
    val currentStreak: StateFlow<Int>
    val focusScore: StateFlow<Int>
    val todayGoal: StateFlow<DailyGoal>

    init {
        val database = AppDatabase.getDatabase(application)
        repository = FocusRepository(database)

        allTasks = repository.allTasks.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        allSessions = repository.allSessions.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Today's goal setup loaded dynamically
        todayGoal = targetSessionsGoal.combine(targetMinutesGoal) { sess, mins ->
            DailyGoal(date = todayDateStrStr, targetSessions = sess, targetMinutes = mins)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DailyGoal(todayDateStrStr)
        )

        // Today completed sessions and total focus time
        val todayStartMs = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        todayCompletedSessionsCount = allSessions.map { list ->
            list.filter { it.completed && it.type == "FOCUS" && it.startTime >= todayStartMs }.size
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

        todayFocusMinutes = allSessions.map { list ->
            val seconds = list.filter { it.completed && it.type == "FOCUS" && it.startTime >= todayStartMs }.sumOf { it.durationSeconds }
            seconds / 60
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

        // Dynamic Streak Calculation
        currentStreak = allSessions.map { list ->
            calculateStreak(list)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

        // Dynamic Focus Score Calculation
        focusScore = allSessions.map { list ->
            calculateFocusScore(list)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    }

    // Settings modifications
    fun updateSettings(pMin: Int, sbMin: Int, lbMin: Int, targetSess: Int, targetMins: Int) {
        pomodoroMinutes.value = pMin
        shortBreakMinutes.value = sbMin
        longBreakMinutes.value = lbMin
        targetSessionsGoal.value = targetSess
        targetMinutesGoal.value = targetMins

        sharedPrefs.edit().apply {
            putInt("pomodoro_min", pMin)
            putInt("short_break_min", sbMin)
            putInt("long_break_min", lbMin)
            putInt("target_sessions", targetSess)
            putInt("target_minutes", targetMins)
            apply()
        }
    }

    // Task operations
    fun addTask(title: String, colorHex: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertTask(Task(title = title, colorHex = colorHex))
        }
    }

    fun editTask(task: Task) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateTask(task)
        }
    }

    fun selectTask(task: Task?) {
        selectedTaskId.value = task?.id
        selectedTask.value = task
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteTask(task)
            if (selectedTaskId.value == task.id) {
                selectedTaskId.value = null
                selectedTask.value = null
            }
        }
    }

    // Timer Controls
    fun startFocusSession(context: Context, type: String = "FOCUS") {
        val duration = when (type) {
            "FOCUS" -> pomodoroMinutes.value * 60
            "SHORT_BREAK" -> shortBreakMinutes.value * 60
            else -> longBreakMinutes.value * 60
        }
        val intent = Intent(context, TimerService::class.java).apply {
            action = TimerService.ACTION_START
            putExtra(TimerService.EXTRA_DURATION, duration)
            putExtra(TimerService.EXTRA_TYPE, type)
            selectedTask.value?.let { task ->
                putExtra(TimerService.EXTRA_TASK_ID, task.id)
                putExtra(TimerService.EXTRA_TASK_TITLE, task.title)
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun pauseFocusSession(context: Context) {
        val intent = TimerService.serviceIntent(context, TimerService.ACTION_PAUSE)
        context.startService(intent)
    }

    fun resumeFocusSession(context: Context) {
        val intent = TimerService.serviceIntent(context, TimerService.ACTION_RESUME)
        context.startService(intent)
    }

    fun stopFocusSession(context: Context) {
        val intent = TimerService.serviceIntent(context, TimerService.ACTION_STOP)
        context.startService(intent)
    }

    fun toggleWhiteNoise(context: Context) {
        val intent = TimerService.serviceIntent(context, TimerService.ACTION_TOGGLE_NOISE)
        context.startService(intent)
    }

    fun recordDistraction() {
        TimerService.distractions.value += 1
    }

    // Core algorithms
    private fun calculateStreak(sessions: List<FocusSession>): Int {
        val completedDatesSet = sessions
            .filter { it.completed && it.type == "FOCUS" }
            .map { dateFormatter.format(Date(it.startTime)) }
            .toSet()

        if (completedDatesSet.isEmpty()) return 0

        val calendar = Calendar.getInstance()
        var streak = 0
        var checkDate = dateFormatter.format(calendar.time)

        // If not completed today, check yesterday
        if (!completedDatesSet.contains(checkDate)) {
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            checkDate = dateFormatter.format(calendar.time)
            if (!completedDatesSet.contains(checkDate)) {
                return 0 // No sessions today or yesterday
            }
        }

        // Walk backward finding contiguous complete days
        while (completedDatesSet.contains(checkDate)) {
            streak++
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            checkDate = dateFormatter.format(calendar.time)
        }

        return streak
    }

    private fun calculateFocusScore(sessions: List<FocusSession>): Int {
        val oneWeekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
        val recentSessions = sessions.filter { it.startTime >= oneWeekAgo && it.completed && it.type == "FOCUS" }
        
        if (recentSessions.isEmpty()) return 80 // Base default score

        val completedCount = recentSessions.size
        val distractionsCount = recentSessions.sumOf { it.distractions }
        val streakValue = calculateStreak(sessions)

        // Consistency ratio: How many distinct days in the last 7 days had completed sessions
        val distinctDays = recentSessions.map { dateFormatter.format(Date(it.startTime)) }.distinct().size
        
        // Custom formula: Base 60, +6 for each completed pomodoro up to 30, -5 for distractions, +5 for each consistent checkin day, +10 for active streak
        val baseScore = 60
        val sessionAdded = (completedCount * 4).coerceAtMost(25)
        val distractionSubtracted = distractionsCount * 5
        val consistencyAdded = distinctDays * 4
        val streakAdded = (streakValue * 3).coerceAtMost(15)

        return (baseScore + sessionAdded - distractionSubtracted + consistencyAdded + streakAdded).coerceIn(10, 100)
    }
}
