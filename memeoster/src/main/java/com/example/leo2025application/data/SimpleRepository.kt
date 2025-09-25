package com.example.leo2025application.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

class SimpleRepository(private val context: Context) {
    
    private val items: MutableList<StudyItem> = mutableListOf()
    private val reviewRecords: MutableList<ReviewRecord> = mutableListOf()
    private val reviewSchedules: MutableMap<String, ReviewSchedule> = mutableMapOf()
    
    private val prefs: SharedPreferences = context.getSharedPreferences("study_data", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    init {
        // 从SharedPreferences加载数据，如果没有则初始化默认数据
        loadDataFromPreferences()
        if (items.isEmpty()) {
            initializeDefaultData()
        }
    }
    
    private fun initializeDefaultData() {
        val defaultItems = listOf(
            StudyItem(text = "Good morning", chineseTranslation = "早上好", notes = "常用问候语"),
            StudyItem(text = "Good afternoon", chineseTranslation = "下午好", notes = "下午问候语"),
            StudyItem(text = "Good evening", chineseTranslation = "晚上好", notes = "晚上问候语"),
        )
        items.addAll(defaultItems)
        Log.d("SimpleRepository", "初始化默认数据，共 ${items.size} 条")
    }
    
    fun add(text: String, chineseTranslation: String? = null, notes: String? = null, imageResName: String? = null): StudyItem {
        val item = StudyItem(text = text, chineseTranslation = chineseTranslation, notes = notes, imageResName = imageResName)
        items.add(item)
        Log.d("SimpleRepository", "添加新项目: ${item.text}")
        return item
    }
    
    fun addBatch(itemsToAdd: List<StudyItem>) {
        Log.d("SimpleRepository", "批量添加 ${itemsToAdd.size} 条")
        items.addAll(itemsToAdd)
        Log.d("SimpleRepository", "添加后总数: ${items.size}")
    }
    
    fun getAllItems(): List<StudyItem> = items.toList()
    
    fun getItemById(id: String): StudyItem? = items.find { it.id == id }
    
    fun deleteItemById(itemId: String): Boolean {
        val removed = items.removeAll { it.id == itemId }
        Log.d("SimpleRepository", "删除项目 ID: $itemId, 结果: $removed")
        return removed
    }
    
    fun clearAllItems() {
        Log.d("SimpleRepository", "清空所有项目，当前数量: ${items.size}")
        items.clear()
        Log.d("SimpleRepository", "清空完成")
    }
    
    fun recordReview(record: ReviewRecord, schedule: ReviewSchedule) {
        reviewRecords.add(record)
        reviewSchedules[record.itemId] = schedule
        Log.d("SimpleRepository", "记录复习: ${record.itemId}")
    }
    
    fun getSchedule(itemId: String): ReviewSchedule? = reviewSchedules[itemId]
    
    // 数据持久化方法
    private fun loadDataFromPreferences() {
        try {
            val itemsJson = prefs.getString("study_items", null)
            if (itemsJson != null) {
                val type = object : TypeToken<List<StudyItem>>() {}.type
                val loadedItems: List<StudyItem> = gson.fromJson(itemsJson, type)
                items.clear()
                items.addAll(loadedItems)
                Log.d("SimpleRepository", "从SharedPreferences加载了 ${items.size} 条数据")
            }
        } catch (e: Exception) {
            Log.e("SimpleRepository", "加载数据失败", e)
        }
    }
    
    private fun saveDataToPreferences() {
        try {
            val itemsJson = gson.toJson(items)
            prefs.edit().putString("study_items", itemsJson).apply()
            Log.d("SimpleRepository", "保存了 ${items.size} 条数据到SharedPreferences")
        } catch (e: Exception) {
            Log.e("SimpleRepository", "保存数据失败", e)
        }
    }
    
    // 修改所有会改变数据的方法，添加自动保存
    fun addWithSave(text: String, chineseTranslation: String? = null, notes: String? = null, imageResName: String? = null): StudyItem {
        val item = StudyItem(text = text, chineseTranslation = chineseTranslation, notes = notes, imageResName = imageResName)
        items.add(item)
        saveDataToPreferences()
        Log.d("SimpleRepository", "添加新项目: ${item.text}")
        return item
    }
    
    fun addBatchWithSave(itemsToAdd: List<StudyItem>) {
        Log.d("SimpleRepository", "批量添加 ${itemsToAdd.size} 条")
        items.addAll(itemsToAdd)
        saveDataToPreferences()
        Log.d("SimpleRepository", "添加后总数: ${items.size}")
    }
    
    fun deleteItemByIdWithSave(itemId: String): Boolean {
        val removed = items.removeAll { it.id == itemId }
        saveDataToPreferences()
        Log.d("SimpleRepository", "删除项目 ID: $itemId, 结果: $removed")
        return removed
    }
    
    fun clearAllItemsWithSave() {
        Log.d("SimpleRepository", "清空所有项目，当前数量: ${items.size}")
        items.clear()
        saveDataToPreferences()
        Log.d("SimpleRepository", "清空完成")
    }
}
