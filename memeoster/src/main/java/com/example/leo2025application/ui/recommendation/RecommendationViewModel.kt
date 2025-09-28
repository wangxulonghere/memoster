package com.example.leo2025application.ui.recommendation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.leo2025application.data.models.StudyItem
import com.example.leo2025application.data.models.ReviewRecord
import com.example.leo2025application.data.models.ReviewAction
import com.example.leo2025application.data.repository.StudyDataManager
import com.example.leo2025application.data.repository.BatchProcessor
import com.example.leo2025application.data.repository.DataRecoveryManager
import com.example.leo2025application.algorithm.queue.QueueManager
import com.example.leo2025application.algorithm.queue.ReviewTimeChecker
import com.example.leo2025application.business.session.StudySessionManager
import com.example.leo2025application.business.gesture.GestureHandler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 推荐算法ViewModel
 * 整合所有推荐算法组件，提供统一的UI接口
 */
class RecommendationViewModel(application: Application) : AndroidViewModel(application) {
    
    companion object {
        private const val TAG = "RecommendationViewModel"
    }
    
    // 核心组件
    private val dataManager = StudyDataManager(application)
    private val batchProcessor = BatchProcessor(application)
    private val recoveryManager = DataRecoveryManager(application)
    private val sessionManager = StudySessionManager(application, dataManager, batchProcessor)
    private val gestureHandler = GestureHandler()
    
    // UI状态
    private val _currentItem = MutableStateFlow<StudyItem?>(null)
    val currentItem: StateFlow<StudyItem?> = _currentItem.asStateFlow()
    
    private val _sessionStatus = MutableStateFlow("未开始学习")
    val sessionStatus: StateFlow<String> = _sessionStatus.asStateFlow()
    
    private val _queueProgress = MutableStateFlow("0/0")
    val queueProgress: StateFlow<String> = _queueProgress.asStateFlow()
    
    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()
    
    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()
    
    // 日志系统
    private val _systemLogs = MutableStateFlow(listOf<String>())
    val systemLogs: StateFlow<List<String>> = _systemLogs.asStateFlow()
    
    private val _learningLogs = MutableStateFlow(listOf<String>())
    val learningLogs: StateFlow<List<String>> = _learningLogs.asStateFlow()
    
    init {
        setupComponents()
        initializeData()
    }
    
    /**
     * 设置组件
     */
    private fun setupComponents() {
        // 设置批量处理器的数据管理器
        batchProcessor.setDataManager(dataManager)
        
        // 设置手势处理器的回调
        gestureHandler.setGestureCallback(object : com.example.leo2025application.business.gesture.GestureCallback {
            override fun onGestureDetected(action: ReviewAction, description: String) {
                sessionManager.onGestureDetected(action, description)
                val actionDesc = when (action) {
                    com.example.leo2025application.data.models.ReviewAction.SWIPE_NEXT -> "直接滑走(记住了)"
                    com.example.leo2025application.data.models.ReviewAction.SHOW_MEANING -> "点击显示示意(不太熟)"
                    com.example.leo2025application.data.models.ReviewAction.MARK_DIFFICULT -> "双击标记不熟(很难记)"
                }
                logLearning("手势检测: $actionDesc")
            }
        })
        
        // 设置会话管理器的回调
        sessionManager.setSessionCallback(object : com.example.leo2025application.business.session.SessionCallback {
            override fun onSessionStarted(session: com.example.leo2025application.business.session.StudySession) {
                _isSessionActive.value = true
                _sessionStatus.value = "学习会话已开始"
                log("学习会话开始: ${session.sessionId}")
                updateCurrentItem()
            }
            
            override fun onSessionEnded(result: com.example.leo2025application.business.session.StudySessionResult) {
                _isSessionActive.value = false
                _currentItem.value = null
                _sessionStatus.value = "学习会话已结束"
                log("学习会话结束: 学习项目=${result.itemsStudied}, 总操作=${result.totalActions}")
            }
            
            override fun onSessionPaused() {
                _isPaused.value = true
                _sessionStatus.value = "学习已暂停"
                log("学习会话已暂停")
            }
            
            override fun onSessionResumed() {
                _isPaused.value = false
                _sessionStatus.value = "学习已恢复"
                log("学习会话已恢复")
            }
            
            override fun onStudyStarted(item: StudyItem) {
                _currentItem.value = item
                logLearning("开始学习: ${item.word}")
            }
            
            override fun onStudyCompleted(item: StudyItem, record: ReviewRecord, updatedItem: StudyItem) {
                val actionDesc = when (record.action) {
                    com.example.leo2025application.data.models.ReviewAction.SWIPE_NEXT -> "直接滑走(记住了)"
                    com.example.leo2025application.data.models.ReviewAction.SHOW_MEANING -> "点击显示示意(不太熟)"
                    com.example.leo2025application.data.models.ReviewAction.MARK_DIFFICULT -> "双击标记不熟(很难记)"
                }
                logLearning("学习完成: ${item.word} | 行为=$actionDesc | 停留时间=${record.getDwellTimeInSeconds()}秒")
                logLearning("下次复习: ${updatedItem.getFormattedNextReviewTime()}")
                updateCurrentItem()
                updateQueueProgress()
            }
            
            override fun onQueueEmpty() {
                _currentItem.value = null
                _sessionStatus.value = "推荐队列已空"
                log("推荐队列已空，学习结束")
            }
            
            override fun onAccidentalOperation(dwellTime: Long, description: String) {
                logLearning("误操作检测: 停留时间${dwellTime}ms < 200ms, 行为=$description")
            }
        })
    }
    
    /**
     * 初始化数据
     */
    private fun initializeData() {
        viewModelScope.launch {
            try {
                log("开始初始化推荐算法系统")
                
                // 检查并恢复数据
                val recoveryResult = recoveryManager.checkAndRecoverData(dataManager)
                if (recoveryResult.hasErrors()) {
                    log("数据恢复错误: ${recoveryResult.getErrors().joinToString("; ")}")
                }
                if (recoveryResult.hasWarnings()) {
                    log("数据恢复警告: ${recoveryResult.getWarnings().joinToString("; ")}")
                }
                
                log("数据初始化完成: ${recoveryResult.getFormattedSummary()}")
                
                // 创建一些测试数据
                createTestData()
                
            } catch (e: Exception) {
                log("初始化失败: ${e.message}")
            }
        }
    }
    
    /**
     * 创建测试数据
     */
    private fun createTestData() {
        val testItems = listOf(
            StudyItem.create("apple", "苹果", 1),
            StudyItem.create("banana", "香蕉", 1),
            StudyItem.create("orange", "橙子", 2),
            StudyItem.create("grape", "葡萄", 2),
            StudyItem.create("strawberry", "草莓", 3)
        )
        
        testItems.forEach { item ->
            dataManager.addStudyItem(item)
        }
        
        log("创建测试数据: ${testItems.size}个项目")
    }
    
    /**
     * 开始学习会话
     */
    fun startStudySession() {
        viewModelScope.launch {
            try {
                val session = sessionManager.startSession()
                val currentItem = sessionManager.startCurrentStudy()
                _currentItem.value = currentItem
                updateQueueProgress()
                log("学习会话开始: ${session.sessionId}")
            } catch (e: Exception) {
                log("开始学习会话失败: ${e.message}")
            }
        }
    }
    
    /**
     * 结束学习会话
     */
    fun endStudySession() {
        viewModelScope.launch {
            try {
                val result = sessionManager.endSession()
                _isSessionActive.value = false
                _currentItem.value = null
                log("学习会话结束: 学习项目=${result.itemsStudied}, 时长=${result.getFormattedDuration()}")
            } catch (e: Exception) {
                log("结束学习会话失败: ${e.message}")
            }
        }
    }
    
    /**
     * 暂停学习
     */
    fun pauseStudy() {
        sessionManager.pauseSession()
    }
    
    /**
     * 恢复学习
     */
    fun resumeStudy() {
        sessionManager.resumeSession()
    }
    
    /**
     * 移动到下一个项目
     */
    fun moveToNextItem() {
        viewModelScope.launch {
            val nextItem = sessionManager.moveToNextItem()
            _currentItem.value = nextItem
            updateQueueProgress()
        }
    }
    
    /**
     * 添加新学习内容
     */
    fun addNewItem(word: String, meaning: String, level: Int = 1) {
        viewModelScope.launch {
            try {
                val newItem = StudyItem.create(word, meaning, level)
                dataManager.addStudyItem(newItem)
                
                // 如果当前有活跃会话，将新项目添加到队列
                if (_isSessionActive.value) {
                    // sessionManager.addNewItem(newItem)  // 暂时注释掉，需要实现这个方法
                }
                
                log("添加新学习内容: $word - $meaning")
            } catch (e: Exception) {
                log("添加新内容失败: ${e.message}")
            }
        }
    }
    
    /**
     * 获取手势检测器
     */
    fun getGestureDetector(): android.view.GestureDetector {
        return android.view.GestureDetector(getApplication(), gestureHandler)
    }
    
    /**
     * 打印队列内容
     */
    fun logQueueContent() {
        val status = sessionManager.getSessionStatus()
        log("=== 推荐队列内容 ===")
        log("会话状态: ${status.isActive}")
        log("当前项目: ${status.currentItem?.word}")
        log("队列状态: ${status.queueStatus}")
    }
    
    /**
     * 打印数据库全部内容
     */
    fun logDatabaseContent() {
        val statistics = dataManager.getDataStatistics()
        log("=== 数据库全部内容 ===")
        log("总项目数: ${statistics.totalItems}")
        log("到期项目数: ${statistics.dueItems}")
        log("热缓存大小: ${statistics.hotCacheSize}")
        log("历史缓存大小: ${statistics.historyCacheSize}")
        log("内存使用: ${statistics.getFormattedMemoryUsage()}")
    }
    
    /**
     * 更新当前项目
     */
    private fun updateCurrentItem() {
        val status = sessionManager.getSessionStatus()
        _currentItem.value = status.currentItem
    }
    
    /**
     * 更新队列进度
     */
    private fun updateQueueProgress() {
        val status = sessionManager.getSessionStatus()
        val queueStatus = status.queueStatus
        if (queueStatus != null) {
            _queueProgress.value = "${queueStatus.currentIndex + 1}/${queueStatus.totalItems}"
        } else {
            _queueProgress.value = "0/0"
        }
    }
    
    /**
     * 记录系统日志
     */
    private fun log(message: String) {
        val timestamp = System.currentTimeMillis()
        val formattedMessage = "[${formatTime(timestamp)}] $message"
        _systemLogs.value = _systemLogs.value + formattedMessage
        
        // 限制日志数量
        if (_systemLogs.value.size > 100) {
            _systemLogs.value = _systemLogs.value.takeLast(100)
        }
    }
    
    /**
     * 记录学习日志
     */
    private fun logLearning(message: String) {
        val timestamp = System.currentTimeMillis()
        val formattedMessage = "[${formatTime(timestamp)}] $message"
        _learningLogs.value = _learningLogs.value + formattedMessage
        
        // 限制日志数量
        if (_learningLogs.value.size > 100) {
            _learningLogs.value = _learningLogs.value.takeLast(100)
        }
    }
    
    /**
     * 格式化时间
     */
    private fun formatTime(timestamp: Long): String {
        val date = Date(timestamp)
        val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return formatter.format(date)
    }
    
    /**
     * 清理资源
     */
    override fun onCleared() {
        super.onCleared()
        sessionManager.cleanup()
        batchProcessor.cleanup()
        log("RecommendationViewModel已清理")
    }
}
