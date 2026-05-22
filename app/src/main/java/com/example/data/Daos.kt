package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun getAllTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: Int): Task?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task): Long

    @Update
    suspend fun updateTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)

    @Query("UPDATE tasks SET totalFocusMinutes = totalFocusMinutes + :minutes WHERE id = :taskId")
    suspend fun incrementFocusMinutes(taskId: Int, minutes: Int)
}

@Dao
interface FocusSessionDao {
    @Query("SELECT * FROM focus_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<FocusSession>>

    @Query("SELECT * FROM focus_sessions WHERE startTime >= :startDay AND endTime <= :endDay")
    fun getSessionsBetween(startDay: Long, endDay: Long): Flow<List<FocusSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: FocusSession): Long

    @Query("DELETE FROM focus_sessions WHERE id = :id")
    suspend fun deleteSessionById(id: Int)
    
    @Query("SELECT COUNT(*) FROM focus_sessions WHERE completed = 1 AND type = 'FOCUS'")
    fun getCompletedSessionsCount(): Flow<Int>
}

@Dao
interface DailyGoalDao {
    @Query("SELECT * FROM daily_goals WHERE date = :date LIMIT 1")
    fun getGoalForDate(date: String): Flow<DailyGoal?>

    @Query("SELECT * FROM daily_goals WHERE date = :date LIMIT 1")
    suspend fun getGoalForDateSuspended(date: String): DailyGoal?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateGoal(goal: DailyGoal)
}
