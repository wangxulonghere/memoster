package com.example.leo2025application.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyItemDao {
    
    @Query("SELECT * FROM study_items ORDER BY createdAt ASC")
    fun getAllItems(): Flow<List<StudyItemEntity>>
    
    @Query("SELECT * FROM study_items WHERE id = :id")
    suspend fun getItemById(id: String): StudyItemEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: StudyItemEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<StudyItemEntity>)
    
    @Update
    suspend fun updateItem(item: StudyItemEntity)
    
    @Delete
    suspend fun deleteItem(item: StudyItemEntity)
    
    @Query("DELETE FROM study_items WHERE id = :id")
    suspend fun deleteItemById(id: String)
    
    @Query("DELETE FROM study_items")
    suspend fun deleteAllItems()
    
    @Query("SELECT COUNT(*) FROM study_items")
    suspend fun getItemCount(): Int
}
