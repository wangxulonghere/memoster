package com.example.leo2025application.algorithm.queue

import android.util.Log
import com.example.leo2025application.data.models.StudyItem
import com.example.leo2025application.data.models.ReviewRecord
import com.example.leo2025application.data.models.RecommendationQueue
import com.example.leo2025application.algorithm.calculator.NextReviewCalculator

/**
 * 队列管理器
 * 负责管理推荐队列的构建、更新和操作
 */
class QueueManager {
    
    companion object {
        private const val TAG = "QueueManager"
    }
    
    private val nextReviewCalculator = NextReviewCalculator()
    
    /**
     * 构建初始推荐队列
     * @param allItems 所有学习内容
     * @return 推荐队列
     */
    fun buildInitialQueue(allItems: List<StudyItem>): RecommendationQueue {
        val now = System.currentTimeMillis()
        
        // 筛选出到期的项目
        val dueItems = allItems.filter { item ->
            item.isDueForReview()
        }.sortedBy { it.nextReviewTime }
        
        val queue = RecommendationQueue.create(dueItems.map { it.id })
        
        Log.i(TAG, "构建初始推荐队列: 总项目=${allItems.size}, 到期项目=${dueItems.size}, 队列ID=${queue.sessionId}")
        
        return queue
    }
    
    /**
     * 学习完成后更新队列
     * @param queue 当前推荐队列
     * @param itemId 学习完成的项目ID
     * @param newRecord 新的复习记录
     * @param history 历史复习记录
     * @param dataManager 数据管理器(用于获取和更新学习内容)
     * @return 更新后的队列
     */
    fun updateAfterStudy(
        queue: RecommendationQueue,
        itemId: String,
        newRecord: ReviewRecord,
        history: List<ReviewRecord>,
        dataManager: (String) -> StudyItem?,
        updateItem: (StudyItem) -> Unit
    ): RecommendationQueue {
        val actionDesc = when (newRecord.action) {
            com.example.leo2025application.data.models.ReviewAction.SWIPE_NEXT -> "直接滑走(记住了)"
            com.example.leo2025application.data.models.ReviewAction.SHOW_MEANING -> "点击显示示意(不太熟)"
            com.example.leo2025application.data.models.ReviewAction.MARK_DIFFICULT -> "双击标记不熟(很难记)"
        }
        Log.i(TAG, "学习完成后更新队列: 项目ID=$itemId, 行为=$actionDesc")
        
        // 获取学习内容
        val item = dataManager(itemId) ?: run {
            Log.e(TAG, "找不到学习内容: ID=$itemId")
            return queue
        }
        
        // 计算更新后的学习内容
        val updatedItem = nextReviewCalculator.calculateUpdatedItem(item, newRecord, history)
        
        // 更新数据管理器中的数据
        updateItem(updatedItem)
        
        // 检查是否还在推荐队列中
        if (updatedItem.nextReviewTime > System.currentTimeMillis()) {
            // 下次复习时间还没到，从队列中移除
            queue.removeItem(itemId)
            Log.d(TAG, "项目已从队列移除: ID=$itemId, 下次复习时间=${updatedItem.getFormattedNextReviewTime()}")
        } else {
            // 重新排序队列
            queue.sortByReviewTime { id ->
                val item = dataManager(id)
                item?.nextReviewTime ?: Long.MAX_VALUE
            }
            Log.d(TAG, "队列已重新排序: 项目ID=$itemId")
        }
        
        return queue
    }
    
    /**
     * 添加新内容到队列
     * @param queue 当前推荐队列
     * @param newItem 新学习内容
     * @return 更新后的队列
     */
    fun addNewItem(queue: RecommendationQueue, newItem: StudyItem): RecommendationQueue {
        // 新内容立即复习
        val immediateReviewItem = newItem.copy(nextReviewTime = System.currentTimeMillis())
        
        queue.addItem(immediateReviewItem.id)
        
        Log.i(TAG, "添加新内容到队列: ID=${immediateReviewItem.id}, 内容='${immediateReviewItem.word}'")
        
        return queue
    }
    
    /**
     * 获取当前学习项目
     * @param queue 推荐队列
     * @param dataManager 数据管理器
     * @return 当前学习项目，如果队列为空则返回null
     */
    fun getCurrentStudyItem(
        queue: RecommendationQueue,
        dataManager: (String) -> StudyItem?
    ): StudyItem? {
        val currentItemId = queue.getCurrentItemId()
        return if (currentItemId != null) {
            dataManager(currentItemId)
        } else {
            null
        }
    }
    
    /**
     * 移动到下一个学习项目
     * @param queue 推荐队列
     * @return 是否成功移动到下一个项目
     */
    fun moveToNextItem(queue: RecommendationQueue): Boolean {
        val moved = queue.moveToNext()
        Log.d(TAG, "移动到下一个项目: 成功=$moved, 当前索引=${queue.currentIndex}, 剩余项目=${queue.getRemainingCount()}")
        return moved
    }
    
    /**
     * 检查队列状态
     * @param queue 推荐队列
     * @return 队列状态信息
     */
    fun checkQueueStatus(queue: RecommendationQueue): QueueStatus {
        val stats = queue.getStatistics()
        
        return QueueStatus(
            isEmpty = queue.isEmpty(),
            hasNext = queue.hasNext(),
            currentIndex = queue.currentIndex,
            totalItems = stats.totalItems,
            remainingItems = stats.remainingItems,
            progress = stats.getProgress(),
            isPaused = queue.isPaused,
            sessionId = queue.sessionId
        )
    }
    
    /**
     * 获取学习建议
     * @param queue 推荐队列
     * @param currentItem 当前学习项目
     * @param history 历史记录
     * @return 学习建议
     */
    fun getLearningSuggestion(
        queue: RecommendationQueue,
        currentItem: StudyItem?,
        history: List<ReviewRecord>
    ): LearningSuggestion {
        if (currentItem == null) {
            return LearningSuggestion.NO_CURRENT_ITEM
        }
        
        val suggestion = nextReviewCalculator.getReviewSuggestion(currentItem, history)
        
        return when (suggestion) {
            com.example.leo2025application.algorithm.calculator.ReviewSuggestion.CONTINUE_NORMAL -> LearningSuggestion.CONTINUE_NORMAL
            com.example.leo2025application.algorithm.calculator.ReviewSuggestion.NEEDS_MORE_PRACTICE -> LearningSuggestion.NEEDS_MORE_PRACTICE
            com.example.leo2025application.algorithm.calculator.ReviewSuggestion.REQUIRES_ATTENTION -> LearningSuggestion.REQUIRES_ATTENTION
            com.example.leo2025application.algorithm.calculator.ReviewSuggestion.READY_FOR_ADVANCEMENT -> LearningSuggestion.READY_FOR_ADVANCEMENT
        }
    }
    
    /**
     * 暂停队列
     */
    fun pauseQueue(queue: RecommendationQueue) {
        queue.pause()
        Log.i(TAG, "队列已暂停: 会话ID=${queue.sessionId}")
    }
    
    /**
     * 恢复队列
     */
    fun resumeQueue(queue: RecommendationQueue) {
        queue.resume()
        Log.i(TAG, "队列已恢复: 会话ID=${queue.sessionId}")
    }
}

/**
 * 队列状态信息
 */
data class QueueStatus(
    val isEmpty: Boolean,
    val hasNext: Boolean,
    val currentIndex: Int,
    val totalItems: Int,
    val remainingItems: Int,
    val progress: Double,
    val isPaused: Boolean,
    val sessionId: String
) {
    fun getFormattedProgress(): String {
        return "${(progress * 100).toInt()}%"
    }
    
    fun getProgressText(): String {
        return "$currentIndex/$totalItems"
    }
}

/**
 * 学习建议
 */
enum class LearningSuggestion {
    NO_CURRENT_ITEM,       // 没有当前项目
    CONTINUE_NORMAL,       // 继续正常学习
    NEEDS_MORE_PRACTICE,   // 需要更多练习
    REQUIRES_ATTENTION,    // 需要特别关注
    READY_FOR_ADVANCEMENT  // 可以进入下一阶段
}
