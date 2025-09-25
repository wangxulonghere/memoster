package com.example.leo2025application.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.UUID

class RoomStudyRepository(context: Context) {
    
    private val database = StudyDatabase.getDatabase(context)
    private val studyItemDao = database.studyItemDao()
    
    // 初始化数据库（在后台线程）
    init {
        try {
            // 同步初始化，避免协程问题
            initializeDefaultDataIfEmptySync()
        } catch (e: Exception) {
            Log.e("RoomStudyRepository", "初始化失败", e)
        }
    }
    
    // 获取所有学习项目（Flow形式，支持响应式更新）
    fun getAllItems(): Flow<List<StudyItem>> {
        return studyItemDao.getAllItems().map { entities ->
            entities.map { it.toStudyItem() }
        }
    }
    
    // 添加单个项目
    suspend fun addItem(text: String, chineseTranslation: String? = null, notes: String? = null, imageResName: String? = null): StudyItem {
        return withContext(Dispatchers.IO) {
            val item = StudyItem(
                id = UUID.randomUUID().toString(),
                text = text,
                chineseTranslation = chineseTranslation,
                notes = notes,
                imageResName = imageResName
            )
            studyItemDao.insertItem(item.toStudyItemEntity())
            Log.d("RoomStudyRepository", "添加项目: ${item.text}")
            item
        }
    }
    
    // 批量添加项目
    suspend fun addBatch(items: List<StudyItem>) {
        withContext(Dispatchers.IO) {
            val entities = items.map { it.toStudyItemEntity() }
            studyItemDao.insertItems(entities)
            Log.d("RoomStudyRepository", "批量添加 ${items.size} 个项目")
        }
    }
    
    // 删除项目
    suspend fun deleteItem(itemId: String) {
        withContext(Dispatchers.IO) {
            studyItemDao.deleteItemById(itemId)
            Log.d("RoomStudyRepository", "删除项目ID: $itemId")
        }
    }
    
    // 清空所有项目
    suspend fun clearAllItems() {
        withContext(Dispatchers.IO) {
            studyItemDao.deleteAllItems()
            Log.d("RoomStudyRepository", "清空所有项目")
        }
    }
    
    // 获取项目数量
    suspend fun getItemCount(): Int {
        return withContext(Dispatchers.IO) {
            studyItemDao.getItemCount()
        }
    }
    
    // 初始化默认数据（仅在数据库为空时）
    suspend fun initializeDefaultDataIfEmpty() {
        withContext(Dispatchers.IO) {
            val count = studyItemDao.getItemCount()
            if (count == 0) {
                val defaultItems = listOf(
                    StudyItem(text = "Good morning", chineseTranslation = "早上好", notes = "常用问候语"),
                    StudyItem(text = "Good afternoon", chineseTranslation = "下午好", notes = "下午问候语"),
                    StudyItem(text = "Good evening", chineseTranslation = "晚上好", notes = "晚上问候语"),
                )
                addBatch(defaultItems)
                Log.d("RoomStudyRepository", "初始化默认数据")
            }
        }
    }
    
    // 同步版本的初始化方法
    private fun initializeDefaultDataIfEmptySync() {
        try {
            // 这里我们暂时跳过初始化，让ViewModel来处理
            Log.d("RoomStudyRepository", "Repository初始化完成")
        } catch (e: Exception) {
            Log.e("RoomStudyRepository", "同步初始化失败", e)
        }
    }
}
