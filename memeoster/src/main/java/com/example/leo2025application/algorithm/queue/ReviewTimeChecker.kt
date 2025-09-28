package com.example.leo2025application.algorithm.queue

import android.util.Log
import com.example.leo2025application.data.models.StudyItem
import com.example.leo2025application.data.models.RecommendationQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * 复习时间检查器
 * 负责定期检查是否有新的内容需要复习，并更新推荐队列
 */
class ReviewTimeChecker {
    
    companion object {
        private const val TAG = "ReviewTimeChecker"
        private const val CHECK_INTERVAL = 60 * 1000L // 1分钟检查一次
        private const val BACKGROUND_CHECK_INTERVAL = 30 * 1000L // 从后台切回时30秒内检查
    }
    
    private var lastCheckTime = AtomicLong(System.currentTimeMillis())
    private var lastBackgroundCheckTime = AtomicLong(0L)
    
    /**
     * 检查是否有新的内容需要复习
     * @param queue 当前推荐队列
     * @param allItems 所有学习内容
     * @param dataManager 数据管理器
     * @return 是否有新的内容被添加到队列
     */
    fun checkAndUpdateQueue(
        queue: RecommendationQueue,
        allItems: List<StudyItem>,
        dataManager: (String) -> StudyItem?
    ): Boolean {
        val now = System.currentTimeMillis()
        val timeSinceLastCheck = now - lastCheckTime.get()
        
        // 检查是否需要执行检查
        if (timeSinceLastCheck < CHECK_INTERVAL) {
            Log.d(TAG, "距离上次检查时间过短: ${timeSinceLastCheck}ms < ${CHECK_INTERVAL}ms")
            return false
        }
        
        lastCheckTime.set(now)
        
        Log.d(TAG, "执行定期复习时间检查: 当前时间=${formatTime(now)}, 队列大小=${queue.itemIds.size}")
        
        // 找出新到期的项目
        val newDueItems = findNewDueItems(queue, allItems, now)
        
        if (newDueItems.isNotEmpty()) {
            // 添加到队列
            newDueItems.forEach { item ->
                queue.addItem(item.id)
                Log.d(TAG, "发现新的到期项目: ID=${item.id}, 内容='${item.word}', 到期时间=${item.getFormattedNextReviewTime()}")
            }
            
            // 重新排序队列
            queue.sortByReviewTime { id ->
                val item = dataManager(id)
                item?.nextReviewTime ?: Long.MAX_VALUE
            }
            
            Log.i(TAG, "队列已更新: 新增${newDueItems.size}个项目, 总项目数=${queue.itemIds.size}")
            return true
        } else {
            Log.d(TAG, "没有发现新的到期项目")
            return false
        }
    }
    
    /**
     * 应用从后台切回时的检查
     * @param queue 当前推荐队列
     * @param allItems 所有学习内容
     * @param dataManager 数据管理器
     * @return 是否有新的内容被添加到队列
     */
    fun checkAfterBackgroundReturn(
        queue: RecommendationQueue,
        allItems: List<StudyItem>,
        dataManager: (String) -> StudyItem?
    ): Boolean {
        val now = System.currentTimeMillis()
        val timeSinceLastBackgroundCheck = now - lastBackgroundCheckTime.get()
        
        // 如果距离上次后台检查时间太短，跳过
        if (timeSinceLastBackgroundCheck < BACKGROUND_CHECK_INTERVAL) {
            Log.d(TAG, "距离上次后台检查时间过短: ${timeSinceLastBackgroundCheck}ms")
            return false
        }
        
        lastBackgroundCheckTime.set(now)
        
        Log.i(TAG, "执行后台切回检查: 当前时间=${formatTime(now)}")
        
        return checkAndUpdateQueue(queue, allItems, dataManager)
    }
    
    /**
     * 强制检查(忽略时间间隔)
     * @param queue 当前推荐队列
     * @param allItems 所有学习内容
     * @param dataManager 数据管理器
     * @return 是否有新的内容被添加到队列
     */
    fun forceCheck(
        queue: RecommendationQueue,
        allItems: List<StudyItem>,
        dataManager: (String) -> StudyItem?
    ): Boolean {
        Log.i(TAG, "执行强制复习时间检查")
        lastCheckTime.set(System.currentTimeMillis())
        
        return checkAndUpdateQueue(queue, allItems, dataManager)
    }
    
    /**
     * 查找新到期的项目
     * @param queue 当前推荐队列
     * @param allItems 所有学习内容
     * @param currentTime 当前时间
     * @return 新到期的项目列表
     */
    private fun findNewDueItems(
        queue: RecommendationQueue,
        allItems: List<StudyItem>,
        currentTime: Long
    ): List<StudyItem> {
        return allItems.filter { item ->
            // 项目已到期
            item.nextReviewTime <= currentTime &&
            // 项目不在当前队列中
            !queue.itemIds.contains(item.id)
        }.sortedBy { it.nextReviewTime }
    }
    
    /**
     * 获取下次检查时间
     * @return 距离下次检查的时间(毫秒)
     */
    fun getTimeUntilNextCheck(): Long {
        val nextCheckTime = lastCheckTime.get() + CHECK_INTERVAL
        val now = System.currentTimeMillis()
        return maxOf(0, nextCheckTime - now)
    }
    
    /**
     * 获取检查器状态
     * @return 检查器状态信息
     */
    fun getCheckerStatus(): CheckerStatus {
        val now = System.currentTimeMillis()
        val timeSinceLastCheck = now - lastCheckTime.get()
        val timeUntilNextCheck = getTimeUntilNextCheck()
        
        return CheckerStatus(
            lastCheckTime = lastCheckTime.get(),
            timeSinceLastCheck = timeSinceLastCheck,
            timeUntilNextCheck = timeUntilNextCheck,
            checkInterval = CHECK_INTERVAL,
            isTimeForNextCheck = timeUntilNextCheck <= 0
        )
    }
    
    /**
     * 重置检查器(用于测试)
     */
    fun resetForTesting() {
        lastCheckTime.set(System.currentTimeMillis())
        lastBackgroundCheckTime.set(0L)
        Log.w(TAG, "检查器已重置(仅用于测试)")
    }
    
    private fun formatTime(timestamp: Long): String {
        val date = java.util.Date(timestamp)
        val formatter = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return formatter.format(date)
    }
}

/**
 * 检查器状态信息
 */
data class CheckerStatus(
    val lastCheckTime: Long,
    val timeSinceLastCheck: Long,
    val timeUntilNextCheck: Long,
    val checkInterval: Long,
    val isTimeForNextCheck: Boolean
) {
    fun getFormattedLastCheckTime(): String {
        val date = java.util.Date(lastCheckTime)
        val formatter = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return formatter.format(date)
    }
    
    fun getFormattedTimeUntilNextCheck(): String {
        val seconds = timeUntilNextCheck / 1000
        return "${seconds}s"
    }
}
