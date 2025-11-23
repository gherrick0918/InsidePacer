package app.insidepacer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgramDao {
    @Query("SELECT * FROM programs ORDER BY name ASC")
    fun getAllFlow(): Flow<List<ProgramEntity>>

    @Query("SELECT * FROM programs ORDER BY name ASC")
    suspend fun getAll(): List<ProgramEntity>

    @Query("SELECT * FROM programs WHERE id = :id")
    suspend fun getById(id: String): ProgramEntity?

    @Query("SELECT * FROM programs WHERE LOWER(name) = LOWER(:name)")
    suspend fun getByName(name: String): ProgramEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(program: ProgramEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(programs: List<ProgramEntity>)

    @Update
    suspend fun update(program: ProgramEntity)

    @Query("DELETE FROM programs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM programs")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM programs")
    suspend fun count(): Int
}
