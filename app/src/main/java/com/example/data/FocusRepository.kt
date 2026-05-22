package com.example.data

import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

class FocusRepository(private val database: AppDatabase) {
    private val taskDao = database.taskDao()
    private val sessionDao = database.focusSessionDao()
    private val dailyGoalDao = database.dailyGoalDao()

    // Tasks API
    val allTasks: Flow<List<Task>> = taskDao.getAllTasks()

    suspend fun getTaskById(id: Int): Task? = taskDao.getTaskById(id)

    suspend fun insertTask(task: Task): Long = taskDao.insertTask(task)

    suspend fun updateTask(task: Task) = taskDao.updateTask(task)

    suspend fun deleteTask(task: Task) = taskDao.deleteTask(task)

    suspend fun incrementTaskClock(taskId: Int, minutes: Int) =
        taskDao.incrementFocusMinutes(taskId, minutes)


    // Focus Sessions API
    val allSessions: Flow<List<FocusSession>> = sessionDao.getAllSessions()

    fun getSessionsBetween(startDay: Long, endDay: Long): Flow<List<FocusSession>> =
        sessionDao.getSessionsBetween(startDay, endDay)

    suspend fun insertSession(session: FocusSession) {
        sessionDao.insertSession(session)
        // If it's a completed FOCUS session, increment the task clock
        if (session.completed && session.type == "FOCUS" && session.taskId != null) {
            val minutes = (session.durationSeconds / 60).coerceAtLeast(1)
            taskDao.incrementFocusMinutes(session.taskId, minutes)
        }
    }

    suspend fun deleteSessionById(id: Int) = sessionDao.deleteSessionById(id)


    // Daily Goals API
    fun getDailyGoal(date: String): Flow<DailyGoal?> = dailyGoalDao.getGoalForDate(date)

    suspend fun getDailyGoalSuspended(date: String): DailyGoal? =
        dailyGoalDao.getGoalForDateSuspended(date)

    suspend fun saveDailyGoal(goal: DailyGoal) = dailyGoalDao.insertOrUpdateGoal(goal)
}
