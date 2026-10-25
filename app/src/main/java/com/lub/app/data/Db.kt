package com.lub.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val techStack: String,
    val createdAt: Long,
    val updatedAt: Long,
    val repoUrl: String?,
    val isUploaded: Boolean,
    val fileCount: Int,
    val totalBytes: Long
)

@Entity(tableName = "project_files")
data class ProjectFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: String,
    val relativePath: String,
    val content: String,
    val confidenceScore: Float,
    val isIncluded: Boolean,
    val inferredLanguage: String,
    val warnings: String
)

@Entity(tableName = "basket_items")
data class BasketItemEntity(
    @PrimaryKey val id: String,
    val source: String, // SCREEN, FULL_CHAT, CLIPBOARD, SHARE, FITNA_IMPORT
    val capturedAt: Long,
    val header: String,
    val code: String,
    val language: String,
    val targetPath: String?,
    val confidence: Float,
    val isSelected: Boolean
)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProjectById(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProjectById(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<ProjectFileEntity>)

    @Query("SELECT * FROM project_files WHERE projectId = :projectId ORDER BY relativePath ASC")
    fun getFilesForProjectFlow(projectId: String): Flow<List<ProjectFileEntity>>

    @Query("SELECT * FROM project_files WHERE projectId = :projectId ORDER BY relativePath ASC")
    suspend fun getFilesForProject(projectId: String): List<ProjectFileEntity>

    @Query("DELETE FROM project_files WHERE projectId = :projectId")
    suspend fun deleteFilesForProject(projectId: String)

    @Update
    suspend fun updateFile(file: ProjectFileEntity)

    @Query("DELETE FROM project_files WHERE id = :fileId")
    suspend fun deleteFileById(fileId: Long)
}

@Dao
interface BasketDao {
    @Query("SELECT * FROM basket_items ORDER BY capturedAt DESC")
    fun getAllBasketItems(): Flow<List<BasketItemEntity>>

    @Query("SELECT * FROM basket_items ORDER BY capturedAt DESC")
    suspend fun getAllBasketItemsList(): List<BasketItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: BasketItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<BasketItemEntity>)

    @Update
    suspend fun updateItem(item: BasketItemEntity)

    @Delete
    suspend fun deleteItem(item: BasketItemEntity)

    @Query("DELETE FROM basket_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM basket_items")
    suspend fun clearBasket()

    @Query("SELECT COUNT(*) FROM basket_items")
    fun getBasketCount(): Flow<Int>
}

@Database(
    entities = [ProjectEntity::class, ProjectFileEntity::class, BasketItemEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun basketDao(): BasketDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lub_database.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
