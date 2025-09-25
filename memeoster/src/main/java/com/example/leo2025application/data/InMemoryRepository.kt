package com.example.leo2025application.data

import android.content.Context
import android.util.Log

class InMemoryRepository(private val context: Context) {
    private val items: MutableList<StudyItem> = mutableListOf(
        StudyItem(text = "Good morning", chineseTranslation = "早上好", notes = "常用问候语"),
        StudyItem(text = "Good afternoon", chineseTranslation = "下午好", notes = "下午问候语"),
        StudyItem(text = "Good evening", chineseTranslation = "晚上好", notes = "晚上问候语"),
    )
    
    private val reviewRecords: MutableList<ReviewRecord> = mutableListOf()
    private val reviewSchedules: MutableMap<String, ReviewSchedule> = mutableMapOf()
    
    fun getAll(): List<StudyItem> = items.toList()
    
    fun add(text: String, chineseTranslation: String? = null, notes: String? = null, imageResName: String? = null): StudyItem {
        val item = StudyItem(text = text, chineseTranslation = chineseTranslation, notes = notes, imageResName = imageResName)
        items.add(item)
        return item
    }
    
    fun addBatch(itemsToAdd: List<StudyItem>) {
        Log.d("InMemoryRepository", "addBatch: 准备添加 ${itemsToAdd.size} 条")
        Log.d("InMemoryRepository", "addBatch: 添加前items.size = ${items.size}")
        items.addAll(itemsToAdd)
        Log.d("InMemoryRepository", "addBatch: 添加后items.size = ${items.size}")
        Log.d("InMemoryRepository", "addBatch: 新添加的内容: ${itemsToAdd.map { it.text }}")
    }
    
    fun recordReview(record: ReviewRecord, schedule: ReviewSchedule) {
        reviewRecords.add(record)
        reviewSchedules[record.itemId] = schedule
    }
    
    fun getSchedule(itemId: String): ReviewSchedule? = reviewSchedules[itemId]
    
    fun getAllItems(): List<StudyItem> = items.toList()
    
    fun clearAllItems() {
        Log.d("InMemoryRepository", "清空所有项目，当前数量: ${items.size}")
        items.clear()
        Log.d("InMemoryRepository", "清空完成，当前数量: ${items.size}")
    }
}
