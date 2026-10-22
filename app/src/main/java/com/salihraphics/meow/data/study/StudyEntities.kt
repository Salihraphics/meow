package com.salihraphics.meow.data.study

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * The study timer as it was last saved: one row. A timer that was running when the app's
 * process ended is restored paused, at the time saved last, and never counts as completed.
 */
@Entity(tableName = "study_timer")
data class StudyTimerEntity(
    @PrimaryKey val id: Int = SINGLE_ROW,
    val durationMs: Long,
    val elapsedMs: Long,
    /** [com.salihraphics.meow.feature.study.TimerStatus] name. */
    val status: String,
    /** Wall-clock start of the current session, for the session log. */
    val startedAt: Long?,
    val documentId: String?,
    val updatedAt: Long,
) {
    companion object {
        const val SINGLE_ROW = 1
    }
}

/**
 * One study session that ended: by reaching its duration ([completed]) or by being reset
 * early. [elapsedMs] is the time actually studied either way.
 */
@Entity(tableName = "study_sessions")
data class StudySessionEntity(
    @PrimaryKey val id: String,
    val documentId: String?,
    val plannedMs: Long,
    val elapsedMs: Long,
    val completed: Boolean,
    val startedAt: Long,
    val endedAt: Long,
)

@Dao
interface StudyDao {
    @Query("SELECT * FROM study_timer WHERE id = 1")
    suspend fun timer(): StudyTimerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putTimer(timer: StudyTimerEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSession(session: StudySessionEntity)

    @Query("SELECT * FROM study_sessions WHERE endedAt >= :since ORDER BY endedAt")
    fun observeSessionsSince(since: Long): Flow<List<StudySessionEntity>>

    @Query("SELECT * FROM study_sessions ORDER BY endedAt")
    suspend fun allSessions(): List<StudySessionEntity>
}
