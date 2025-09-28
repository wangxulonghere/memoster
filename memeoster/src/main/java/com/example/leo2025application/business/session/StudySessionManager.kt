package com.example.leo2025application.business.session

import android.app.Application
import android.util.Log
import com.example.leo2025application.data.models.StudyItem
import com.example.leo2025application.data.models.ReviewRecord
import com.example.leo2025application.data.models.ReviewAction
import com.example.leo2025application.data.models.RecommendationQueue
import com.example.leo2025application.data.repository.StudyDataManager
import com.example.leo2025application.data.repository.BatchProcessor
import com.example.leo2025application.algorithm.queue.QueueManager
import com.example.leo2025application.algorithm.queue.ReviewTimeChecker
import com.example.leo2025application.business.gesture.GestureCallback
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * 学习会话管理器
 * 负责管理整个学习会话的生命周期，包括会话开始、学习记录、会话结束等
 */
class StudySessionManager(
    private val application: Application,
    private val dataManager: StudyDataManager,
    private val batchProcessor: BatchProcessor
) : Application.ActivityLifecycleCallbacks, GestureCallback {
    
    companion object {
        private const val TAG = "StudySessionManager"
        private const val ACCIDENTAL_THRESHOLD = 200L // 误操作阈值(毫秒)
    }
    
    // 当前学习会话
    private var currentSession: StudySession? = null
    
    // 当前学习项目
    private var currentItem: StudyItem? = null
    
    // 当前学习开始时间
    private var currentStudyStartTime = AtomicLong(0L)
    
    // 队列管理器
    private val queueManager = QueueManager()
    
    // 复习时间检查器
    private val reviewTimeChecker = ReviewTimeChecker()
    
    // 推荐队列
    private var recommendationQueue: RecommendationQueue? = null
    
    // 会话回调
    private var sessionCallback: SessionCallback? = null
    
    // 是否已注册生命周期回调
    private var lifecycleRegistered = false
    
    /**
     * 设置会话回调
     */
    fun setSessionCallback(callback: SessionCallback) {
        this.sessionCallback = callback
    }
    
    /**
     * 开始新的学习会话
     */
    fun startSession(): StudySession {
        val sessionId = UUID.randomUUID().toString()
        val session = StudySession(
            sessionId = sessionId,
            startTime = System.currentTimeMillis(),
            isActive = true
        )
        
        currentSession = session
        
        // 构建推荐队列
        val allItems = dataManager.getAllStudyItems()
        recommendationQueue = queueManager.buildInitialQueue(allItems)
        
        // 注册生命周期回调
        if (!lifecycleRegistered) {
            application.registerActivityLifecycleCallbacks(this)
            lifecycleRegistered = true
        }
        
        Log.i(TAG, "开始新的学习会话: ID=$sessionId, 推荐队列大小=${recommendationQueue?.itemIds?.size}")
        
        sessionCallback?.onSessionStarted(session)
        return session
    }
    
    /**
     * 结束当前学习会话
     */
    fun endSession(): StudySessionResult {
        val session = currentSession ?: throw IllegalStateException("没有活跃的学习会话")
        
        // 结束当前学习项目(如果有)
        endCurrentStudy()
        
        // 强制执行批量更新
        batchProcessor.forceExecuteBatch()
        
        val endTime = System.currentTimeMillis()
        val result = StudySessionResult(
            sessionId = session.sessionId,
            startTime = session.startTime,
            endTime = endTime,
            duration = endTime - session.startTime,
            itemsStudied = session.itemsStudied,
            totalActions = session.totalActions
        )
        
        currentSession = null
        currentItem = null
        recommendationQueue = null
        
        Log.i(TAG, "学习会话结束: ID=${session.sessionId}, 学习项目=${session.itemsStudied}, 总操作=${session.totalActions}")
        
        sessionCallback?.onSessionEnded(result)
        return result
    }
    
    /**
     * 开始学习当前项目
     */
    fun startCurrentStudy(): StudyItem? {
        val queue = recommendationQueue ?: return null
        
        currentItem = queueManager.getCurrentStudyItem(queue) { id ->
            dataManager.getStudyItem(id)
        }
        
        if (currentItem != null) {
            currentStudyStartTime.set(System.currentTimeMillis())
            Log.d(TAG, "开始学习项目: ID=${currentItem?.id}, 内容='${currentItem?.word}'")
            sessionCallback?.onStudyStarted(currentItem!!)
        }
        
        return currentItem
    }
    
    /**
     * 结束当前学习项目
     */
    private fun endCurrentStudy() {
        val item = currentItem ?: return
        val startTime = currentStudyStartTime.get()
        
        if (startTime > 0) {
            val endTime = System.currentTimeMillis()
            val dwellTime = endTime - startTime
            
            Log.d(TAG, "结束学习项目: ID=${item.id}, 停留时间=${dwellTime}ms")
            
            // 这里不直接创建ReviewRecord，等待手势回调
            currentStudyStartTime.set(0L)
        }
        
        currentItem = null
    }
    
    /**
     * 移动到下一个学习项目
     */
    fun moveToNextItem(): StudyItem? {
        val queue = recommendationQueue ?: return null
        
        // 结束当前学习
        endCurrentStudy()
        
        // 移动到下一个项目
        val hasNext = queueManager.moveToNextItem(queue)
        
        if (hasNext) {
            return startCurrentStudy()
        } else {
            Log.i(TAG, "推荐队列已结束")
            sessionCallback?.onQueueEmpty()
            return null
        }
    }
    
    /**
     * 暂停学习会话
     */
    fun pauseSession() {
        recommendationQueue?.pause()
        Log.i(TAG, "学习会话已暂停")
        sessionCallback?.onSessionPaused()
    }
    
    /**
     * 恢复学习会话
     */
    fun resumeSession() {
        recommendationQueue?.resume()
        Log.i(TAG, "学习会话已恢复")
        sessionCallback?.onSessionResumed()
    }
    
    /**
     * 获取当前会话状态
     */
    fun getSessionStatus(): SessionStatus {
        val session = currentSession
        val queue = recommendationQueue
        
        return SessionStatus(
            isActive = session?.isActive ?: false,
            sessionId = session?.sessionId,
            currentItem = currentItem,
            queueStatus = queue?.let { queueManager.checkQueueStatus(it) },
            isPaused = queue?.isPaused ?: false,
            startTime = session?.startTime,
            itemsStudied = session?.itemsStudied ?: 0
        )
    }
    
    /**
     * 检查复习时间并更新队列
     */
    fun checkAndUpdateReviewTime(): Boolean {
        val queue = recommendationQueue ?: return false
        
        return reviewTimeChecker.checkAndUpdateQueue(
            queue,
            dataManager.getAllStudyItems()
        ) { id -> dataManager.getStudyItem(id) }
    }
    
    // 实现GestureCallback接口
    override fun onGestureDetected(action: ReviewAction, description: String) {
        val item = currentItem ?: return
        val startTime = currentStudyStartTime.get()
        
        if (startTime == 0L) {
            Log.w(TAG, "没有活跃的学习项目，忽略手势: $description")
            return
        }
        
        val endTime = System.currentTimeMillis()
        val dwellTime = endTime - startTime
        
        // 检查是否为误操作
        if (dwellTime < ACCIDENTAL_THRESHOLD) {
            Log.d(TAG, "检测到误操作: 停留时间${dwellTime}ms < ${ACCIDENTAL_THRESHOLD}ms, 行为=$description")
            sessionCallback?.onAccidentalOperation(dwellTime, description)
            return
        }
        
        // 创建复习记录
        val record = ReviewRecord.create(
            startTime = startTime,
            endTime = endTime,
            action = action,
            sessionId = currentSession?.sessionId
        )
        
        // 记录学习行为
        recordStudy(item, record)
        
        Log.i(TAG, "手势检测完成: 项目ID=${item.id}, 行为=${description}, 停留时间=${dwellTime}ms")
    }
    
    /**
     * 记录学习行为
     */
    private fun recordStudy(item: StudyItem, record: ReviewRecord) {
        val session = currentSession ?: return
        
        // 更新会话统计
        session.itemsStudied++
        session.totalActions++
        
        // 获取历史记录
        val history = dataManager.getReviewHistory(item.id)
        
        // 更新学习内容
        val updatedItem = com.example.leo2025application.algorithm.calculator.NextReviewCalculator()
            .calculateUpdatedItem(item, record, history)
        
        // 更新队列
        recommendationQueue?.let { queue ->
            queueManager.updateAfterStudy(
                queue = queue,
                itemId = item.id,
                newRecord = record,
                history = history,
                dataManager = { id -> dataManager.getStudyItem(id) },
                updateItem = { updatedItem -> dataManager.updateStudyItem(updatedItem) }
            )
        }
        
        // 记录到数据管理器
        dataManager.addReviewRecord(item.id, record)
        dataManager.updateStudyItem(updatedItem)
        
        // 标记为待批量更新
        batchProcessor.markForUpdate(updatedItem)
        batchProcessor.recordStudySafely(item.id, record)
        
        // 通知回调
        sessionCallback?.onStudyCompleted(item, record, updatedItem)
        
        // 重置学习开始时间
        currentStudyStartTime.set(0L)
    }
    
    // 实现Application.ActivityLifecycleCallbacks接口
    override fun onActivityPaused(activity: android.app.Activity) {
        Log.d(TAG, "应用进入后台，强制保存数据")
        batchProcessor.forceExecuteBatch()
    }
    
    override fun onActivityStopped(activity: android.app.Activity) {
        Log.d(TAG, "应用停止，强制保存数据")
        batchProcessor.forceExecuteBatch()
    }
    
    override fun onActivityResumed(activity: android.app.Activity) {
        Log.d(TAG, "应用从后台恢复，检查复习时间")
        checkAndUpdateReviewTime()
    }
    
    // 其他生命周期方法不需要实现
    override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
    override fun onActivityStarted(activity: android.app.Activity) {}
    override fun onActivityDestroyed(activity: android.app.Activity) {}
    override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
    
    /**
     * 清理资源
     */
    fun cleanup() {
        if (lifecycleRegistered) {
            application.unregisterActivityLifecycleCallbacks(this)
            lifecycleRegistered = false
        }
        
        batchProcessor.cleanup()
        Log.i(TAG, "学习会话管理器已清理")
    }
}

/**
 * 学习会话
 */
data class StudySession(
    val sessionId: String,
    val startTime: Long,
    var isActive: Boolean,
    var itemsStudied: Int = 0,
    var totalActions: Int = 0
) {
    fun getDuration(): Long = System.currentTimeMillis() - startTime
    
    fun getFormattedStartTime(): String {
        val date = java.util.Date(startTime)
        val formatter = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return formatter.format(date)
    }
}

/**
 * 学习会话结果
 */
data class StudySessionResult(
    val sessionId: String,
    val startTime: Long,
    val endTime: Long,
    val duration: Long,
    val itemsStudied: Int,
    val totalActions: Int
) {
    fun getFormattedDuration(): String {
        val minutes = duration / (1000 * 60)
        val seconds = (duration % (1000 * 60)) / 1000
        return "${minutes}m ${seconds}s"
    }
    
    fun getAverageTimePerItem(): Long {
        return if (itemsStudied > 0) duration / itemsStudied else 0L
    }
}

/**
 * 会话状态
 */
data class SessionStatus(
    val isActive: Boolean,
    val sessionId: String?,
    val currentItem: StudyItem?,
    val queueStatus: com.example.leo2025application.algorithm.queue.QueueStatus?,
    val isPaused: Boolean,
    val startTime: Long?,
    val itemsStudied: Int
)

/**
 * 会话回调接口
 */
interface SessionCallback {
    fun onSessionStarted(session: StudySession)
    fun onSessionEnded(result: StudySessionResult)
    fun onSessionPaused()
    fun onSessionResumed()
    fun onStudyStarted(item: StudyItem)
    fun onStudyCompleted(item: StudyItem, record: ReviewRecord, updatedItem: StudyItem)
    fun onQueueEmpty()
    fun onAccidentalOperation(dwellTime: Long, description: String)
}
