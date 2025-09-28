package com.example.leo2025application.data.repository

import android.content.Context
import android.util.LruCache
import android.util.Log
import com.example.leo2025application.data.models.StudyItem
import com.example.leo2025application.data.models.ReviewRecord
import java.util.concurrent.ConcurrentHashMap

/**
 * 学习数据管理器
 * 负责管理学习内容的数据存储、缓存和访问
 */
class StudyDataManager(private val context: Context) {
    
    companion object {
        private const val TAG = "StudyDataManager"
        private const val HOT_CACHE_SIZE = 1000 // 热数据缓存大小
        private const val HISTORY_CACHE_SIZE = 500 // 历史记录缓存大小
    }
    
    // 热数据缓存 - 最近访问的学习内容
    private val hotDataCache = LruCache<String, StudyItem>(HOT_CACHE_SIZE)
    
    // 历史记录缓存 - 按需加载的复习记录
    private val historyCache = LruCache<String, List<ReviewRecord>>(HISTORY_CACHE_SIZE)
    
    // 温数据索引 - 复习时间索引，用于快速查找到期项目
    private val reviewTimeIndex = ConcurrentHashMap<Long, MutableSet<String>>()
    
    // 所有学习内容的ID集合
    private val allItemIds = mutableSetOf<String>()
    
    // 内存中的学习内容存储
    private val studyItems = ConcurrentHashMap<String, StudyItem>()
    
    /**
     * 添加新的学习内容
     * @param item 学习内容
     */
    fun addStudyItem(item: StudyItem) {
        studyItems[item.id] = item
        allItemIds.add(item.id)
        
        // 更新热数据缓存
        hotDataCache.put(item.id, item)
        
        // 更新复习时间索引
        updateReviewTimeIndex(item.id, Long.MAX_VALUE, item.nextReviewTime)
        
        Log.i(TAG, "添加学习内容: ID=${item.id}, 内容='${item.word}', 总数量=${studyItems.size}")
    }
    
    /**
     * 获取学习内容
     * @param itemId 学习内容ID
     * @return 学习内容，如果不存在则返回null
     */
    fun getStudyItem(itemId: String): StudyItem? {
        // 1. 先从热数据缓存查找
        var item = hotDataCache.get(itemId)
        
        if (item == null) {
            // 2. 从内存存储查找
            item = studyItems[itemId]
            
            if (item != null) {
                // 3. 放入热数据缓存
                hotDataCache.put(itemId, item)
            } else {
                Log.w(TAG, "找不到学习内容: ID=$itemId")
            }
        }
        
        return item
    }
    
    /**
     * 获取所有学习内容
     * @return 所有学习内容列表
     */
    fun getAllStudyItems(): List<StudyItem> {
        return studyItems.values.toList()
    }
    
    /**
     * 获取到期的学习内容ID列表
     * @return 到期项目ID列表，按复习时间排序
     */
    fun getDueItemIds(): List<String> {
        val now = System.currentTimeMillis()
        val dueItems = mutableListOf<String>()
        
        // 从复习时间索引中快速查找到期项目
        reviewTimeIndex.keys.filter { it <= now }.sorted().forEach { reviewTime ->
            reviewTimeIndex[reviewTime]?.let { itemIds ->
                dueItems.addAll(itemIds)
            }
        }
        
        Log.d(TAG, "获取到期项目: 总数=${dueItems.size}, 当前时间=${formatTime(now)}")
        return dueItems
    }
    
    /**
     * 更新学习内容
     * @param item 更新后的学习内容
     */
    fun updateStudyItem(item: StudyItem) {
        val oldItem = studyItems[item.id]
        
        // 更新内存存储
        studyItems[item.id] = item
        
        // 更新热数据缓存
        hotDataCache.put(item.id, item)
        
        // 更新复习时间索引
        if (oldItem != null) {
            updateReviewTimeIndex(item.id, oldItem.nextReviewTime, item.nextReviewTime)
        } else {
            updateReviewTimeIndex(item.id, Long.MAX_VALUE, item.nextReviewTime)
        }
        
        Log.d(TAG, "更新学习内容: ID=${item.id}, 下次复习时间=${item.getFormattedNextReviewTime()}")
    }
    
    /**
     * 删除学习内容
     * @param itemId 学习内容ID
     */
    fun removeStudyItem(itemId: String) {
        val item = studyItems.remove(itemId)
        allItemIds.remove(itemId)
        
        // 从缓存中移除
        hotDataCache.remove(itemId)
        historyCache.remove(itemId)
        
        // 从复习时间索引中移除
        if (item != null) {
            removeFromReviewTimeIndex(itemId, item.nextReviewTime)
        }
        
        Log.i(TAG, "删除学习内容: ID=$itemId")
    }
    
    /**
     * 获取复习历史记录
     * @param itemId 学习内容ID
     * @return 复习历史记录列表
     */
    fun getReviewHistory(itemId: String): List<ReviewRecord> {
        // 先从缓存查找
        var history = historyCache.get(itemId)
        
        if (history == null) {
            // 从存储加载(这里简化实现，实际应该从数据库加载)
            history = loadHistoryFromStorage(itemId)
            
            // 放入缓存
            historyCache.put(itemId, history)
        }
        
        return history
    }
    
    /**
     * 添加复习记录
     * @param itemId 学习内容ID
     * @param record 复习记录
     */
    fun addReviewRecord(itemId: String, record: ReviewRecord) {
        val currentHistory = getReviewHistory(itemId).toMutableList()
        
        // 添加新记录
        currentHistory.add(record)
        
        // 只保留最近的200条记录
        if (currentHistory.size > 200) {
            currentHistory.removeAt(0)
            Log.i(TAG, "历史记录已达上限，删除最老记录: ID=$itemId")
        }
        
        // 更新缓存
        historyCache.put(itemId, currentHistory)
        
        // 保存到存储
        saveHistoryToStorage(itemId, currentHistory)
        
        val actionDesc = when (record.action) {
            com.example.leo2025application.data.models.ReviewAction.SWIPE_NEXT -> "直接滑走(记住了)"
            com.example.leo2025application.data.models.ReviewAction.SHOW_MEANING -> "点击显示示意(不太熟)"
            com.example.leo2025application.data.models.ReviewAction.MARK_DIFFICULT -> "双击标记不熟(很难记)"
        }
        Log.d(TAG, "添加复习记录: ID=$itemId, 行为=$actionDesc, 停留时间=${record.dwellTime}ms")
    }
    
    /**
     * 获取数据统计信息
     * @return 数据统计信息
     */
    fun getDataStatistics(): DataStatistics {
        val totalItems = studyItems.size
        val hotCacheSize = hotDataCache.size()
        val historyCacheSize = historyCache.size()
        val dueItems = getDueItemIds().size
        
        return DataStatistics(
            totalItems = totalItems,
            dueItems = dueItems,
            hotCacheSize = hotCacheSize,
            historyCacheSize = historyCacheSize,
            memoryUsage = estimateMemoryUsage()
        )
    }
    
    /**
     * 清理缓存
     */
    fun clearCache() {
        hotDataCache.evictAll()
        historyCache.evictAll()
        Log.i(TAG, "已清理所有缓存")
    }
    
    /**
     * 更新复习时间索引
     */
    private fun updateReviewTimeIndex(itemId: String, oldTime: Long, newTime: Long) {
        // 从旧时间移除
        if (oldTime != Long.MAX_VALUE) {
            removeFromReviewTimeIndex(itemId, oldTime)
        }
        
        // 添加到新时间
        reviewTimeIndex.computeIfAbsent(newTime) { mutableSetOf() }.add(itemId)
    }
    
    /**
     * 从复习时间索引中移除
     */
    private fun removeFromReviewTimeIndex(itemId: String, reviewTime: Long) {
        reviewTimeIndex[reviewTime]?.remove(itemId)
        if (reviewTimeIndex[reviewTime]?.isEmpty() == true) {
            reviewTimeIndex.remove(reviewTime)
        }
    }
    
    /**
     * 从存储加载历史记录(简化实现)
     */
    private fun loadHistoryFromStorage(itemId: String): List<ReviewRecord> {
        // 这里应该从数据库或文件加载，暂时返回空列表
        Log.d(TAG, "从存储加载历史记录: ID=$itemId")
        return emptyList()
    }
    
    /**
     * 保存历史记录到存储(简化实现)
     */
    private fun saveHistoryToStorage(itemId: String, history: List<ReviewRecord>) {
        // 这里应该保存到数据库或文件，暂时只记录日志
        Log.d(TAG, "保存历史记录到存储: ID=$itemId, 记录数量=${history.size}")
    }
    
    /**
     * 估算内存使用量
     */
    private fun estimateMemoryUsage(): Long {
        val itemSize = studyItems.size * 200L // 估算每个StudyItem约200字节
        val cacheSize = (hotDataCache.size() + historyCache.size()) * 100L // 估算缓存大小
        return itemSize + cacheSize
    }
    
    private fun formatTime(timestamp: Long): String {
        val date = java.util.Date(timestamp)
        val formatter = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return formatter.format(date)
    }
}

/**
 * 数据统计信息
 */
data class DataStatistics(
    val totalItems: Int,
    val dueItems: Int,
    val hotCacheSize: Int,
    val historyCacheSize: Int,
    val memoryUsage: Long
) {
    fun getFormattedMemoryUsage(): String {
        return when {
            memoryUsage < 1024 -> "${memoryUsage}B"
            memoryUsage < 1024 * 1024 -> "${memoryUsage / 1024}KB"
            else -> "${memoryUsage / (1024 * 1024)}MB"
        }
    }
}
