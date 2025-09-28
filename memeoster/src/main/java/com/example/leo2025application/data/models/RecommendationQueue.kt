package com.example.leo2025application.data.models

import android.util.Log
import java.util.UUID

/**
 * 推荐队列 - 只存储ID引用，避免大对象复制
 */
data class RecommendationQueue(
    val sessionId: String,
    val itemIds: MutableList<String>,           // 只存储StudyItem的ID
    var currentIndex: Int = 0,                  // 改为var，使其可变
    var isPaused: Boolean = false,              // 改为var，使其可变
    val createdAt: Long = System.currentTimeMillis(),
    var lastCheckTime: Long = System.currentTimeMillis()  // 改为var，使其可变
) {
    companion object {
        private const val TAG = "RecommendationQueue"
        
        /**
         * 创建新的推荐队列
         */
        fun create(dueItemIds: List<String>): RecommendationQueue {
            val queue = RecommendationQueue(
                sessionId = UUID.randomUUID().toString(),
                itemIds = dueItemIds.toMutableList()
            )
            
            Log.i(TAG, "创建推荐队列: 会话ID=${queue.sessionId}, 项目数量=${dueItemIds.size}")
            return queue
        }
    }
    
    /**
     * 获取当前学习项目的ID
     */
    fun getCurrentItemId(): String? {
        return if (isValidIndex(currentIndex)) {
            itemIds[currentIndex]
        } else {
            null
        }
    }
    
    /**
     * 移动到下一个项目
     */
    fun moveToNext(): Boolean {
        val nextIndex = currentIndex + 1
        if (isValidIndex(nextIndex)) {
            val oldIndex = currentIndex
            currentIndex = nextIndex
            Log.d(TAG, "移动到下一个项目: 索引从${oldIndex}到${currentIndex}")
            return true
        } else {
            Log.d(TAG, "已到达队列末尾")
            return false
        }
    }
    
    /**
     * 检查队列是否为空
     */
    fun isEmpty(): Boolean {
        val isEmpty = itemIds.isEmpty()
        Log.d(TAG, "检查队列状态: 是否为空=$isEmpty, 项目数量=${itemIds.size}")
        return isEmpty
    }
    
    /**
     * 检查是否还有下一个项目
     */
    fun hasNext(): Boolean {
        return isValidIndex(currentIndex + 1)
    }
    
    /**
     * 获取剩余项目数量
     */
    fun getRemainingCount(): Int {
        val remaining = (itemIds.size - currentIndex).coerceAtLeast(0)
        Log.d(TAG, "剩余项目数量: $remaining (当前索引: $currentIndex, 总数量: ${itemIds.size})")
        return remaining
    }
    
    /**
     * 添加新项目到队列
     */
    fun addItem(itemId: String) {
        if (!itemIds.contains(itemId)) {
            itemIds.add(itemId)
            Log.d(TAG, "添加新项目到队列: ID=$itemId, 新队列大小=${itemIds.size}")
        } else {
            Log.d(TAG, "项目已在队列中: ID=$itemId")
        }
    }
    
    /**
     * 从队列中移除项目
     */
    fun removeItem(itemId: String) {
        val removed = itemIds.remove(itemId)
        Log.d(TAG, "从队列移除项目: ID=$itemId, 是否成功=$removed, 新队列大小=${itemIds.size}")
    }
    
    /**
     * 重新排序队列(按下次复习时间)
     */
    fun sortByReviewTime(dataManager: (String) -> Long) {
        val sortedIds = itemIds.sortedBy { itemId ->
            dataManager(itemId)
        }
        itemIds.clear()
        itemIds.addAll(sortedIds)
        Log.d(TAG, "队列已重新排序: 项目数量=${itemIds.size}")
    }
    
    /**
     * 暂停队列
     */
    fun pause() {
        isPaused = true
        Log.d(TAG, "队列已暂停: 会话ID=$sessionId")
    }
    
    /**
     * 恢复队列
     */
    fun resume() {
        isPaused = false
        Log.d(TAG, "队列已恢复: 会话ID=$sessionId")
    }
    
    /**
     * 获取队列统计信息
     */
    fun getStatistics(): QueueStatistics {
        return QueueStatistics(
            sessionId = sessionId,
            totalItems = itemIds.size,
            currentIndex = currentIndex,
            remainingItems = getRemainingCount(),
            isPaused = isPaused,
            createdAt = createdAt,
            lastCheckTime = lastCheckTime
        )
    }
    
    private fun isValidIndex(index: Int): Boolean {
        return index >= 0 && index < itemIds.size
    }
}

/**
 * 队列统计信息
 */
data class QueueStatistics(
    val sessionId: String,
    val totalItems: Int,
    val currentIndex: Int,
    val remainingItems: Int,
    val isPaused: Boolean,
    val createdAt: Long,
    val lastCheckTime: Long
) {
    fun getProgress(): Double {
        return if (totalItems > 0) {
            currentIndex.toDouble() / totalItems.toDouble()
        } else {
            0.0
        }
    }
    
    fun getFormattedProgress(): String {
        return "${(getProgress() * 100).toInt()}%"
    }
}
