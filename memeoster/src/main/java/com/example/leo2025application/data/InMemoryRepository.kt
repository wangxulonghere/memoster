package com.example.leo2025application.data

import android.content.Context

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
        items.addAll(itemsToAdd)
    }
    
    fun recordReview(record: ReviewRecord, schedule: ReviewSchedule) {
        reviewRecords.add(record)
        reviewSchedules[record.itemId] = schedule
    }
    
    fun getSchedule(itemId: String): ReviewSchedule? = reviewSchedules[itemId]
}
