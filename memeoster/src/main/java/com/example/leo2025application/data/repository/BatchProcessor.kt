package com.example.leo2025application.data.repository

import android.content.Context
import android.util.Log
import com.example.leo2025application.data.models.StudyItem
import com.example.leo2025application.data.models.ReviewRecord
import com.google.gson.Gson
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.Timer
import java.util.TimerTask

/**
 * 批量处理器
 * 负责批量处理数据更新，提高性能并确保数据安全
 */
class BatchProcessor(private val context: Context) {
    
    companion object {
        private const val TAG = "BatchProcessor"
        private const val BATCH_INTERVAL = 5000L // 5秒批量处理一次
        private const val BATCH_SIZE_THRESHOLD = 10 // 批量大小阈值
        private const val AUTO_SAVE_INTERVAL = 30000L // 30秒自动保存一次
    }
    
    // 待批量更新的学习内容
    private val pendingUpdates = ConcurrentHashMap<String, StudyItem>()
    
    // 待批量保存的复习记录
    private val pendingRecords = ConcurrentHashMap<String, MutableList<ReviewRecord>>()
    
    // 上次批量处理时间
    private val lastBatchTime = AtomicLong(System.currentTimeMillis())
    
    // 自动保存定时器
    private val autoSaveTimer = Timer("AutoSaveTimer", true)
    
    // 数据管理器引用
    private var dataManager: StudyDataManager? = null
    
    // 是否已启动自动保存
    private var autoSaveStarted = false
    
    /**
     * 设置数据管理器
     */
    fun setDataManager(dataManager: StudyDataManager) {
        this.dataManager = dataManager
        startAutoSave()
    }
    
    /**
     * 记录学习行为(立即保存复习记录，异步更新其他数据)
     */
    fun recordStudySafely(itemId: String, record: ReviewRecord) {
        val actionDesc = when (record.action) {
            com.example.leo2025application.data.models.ReviewAction.SWIPE_NEXT -> "直接滑走(记住了)"
            com.example.leo2025application.data.models.ReviewAction.SHOW_MEANING -> "点击显示示意(不太熟)"
            com.example.leo2025application.data.models.ReviewAction.MARK_DIFFICULT -> "双击标记不熟(很难记)"
        }
        Log.i(TAG, "安全记录学习行为: ID=$itemId, 行为=$actionDesc")
        
        // 1. 立即保存复习记录到本地备份
        saveToLocalBackup(itemId, record)
        
        // 2. 添加到待批量保存队列
        pendingRecords.computeIfAbsent(itemId) { mutableListOf() }.add(record)
        
        // 3. 检查是否需要执行批量保存
        checkAndExecuteBatch()
    }
    
    /**
     * 标记学习内容需要更新
     */
    fun markForUpdate(item: StudyItem) {
        pendingUpdates[item.id] = item
        Log.d(TAG, "标记学习内容待更新: ID=${item.id}, 待更新数量=${pendingUpdates.size}")
        
        // 检查是否需要执行批量更新
        checkAndExecuteBatch()
    }
    
    /**
     * 检查并执行批量处理
     */
    private fun checkAndExecuteBatch() {
        val now = System.currentTimeMillis()
        val timeToBatch = (now - lastBatchTime.get()) >= BATCH_INTERVAL
        val sizeToBatch = pendingUpdates.size >= BATCH_SIZE_THRESHOLD || 
                         pendingRecords.values.sumOf { it.size } >= BATCH_SIZE_THRESHOLD
        
        if (timeToBatch || sizeToBatch) {
            executeBatchUpdate()
        }
    }
    
    /**
     * 执行批量更新
     */
    private fun executeBatchUpdate() {
        if (pendingUpdates.isEmpty() && pendingRecords.isEmpty()) {
            return
        }
        
        Log.i(TAG, "执行批量更新: 学习内容=${pendingUpdates.size}, 复习记录=${pendingRecords.values.sumOf { it.size }}")
        
        try {
            // 批量更新学习内容
            if (pendingUpdates.isNotEmpty()) {
                batchUpdateStudyItems()
            }
            
            // 批量保存复习记录
            if (pendingRecords.isNotEmpty()) {
                batchSaveReviewRecords()
            }
            
            lastBatchTime.set(System.currentTimeMillis())
            Log.i(TAG, "批量更新完成")
            
        } catch (e: Exception) {
            Log.e(TAG, "批量更新失败", e)
            // 失败时保存到本地备份
            saveFailedUpdatesToBackup()
        }
    }
    
    /**
     * 批量更新学习内容
     */
    private fun batchUpdateStudyItems() {
        val updates = pendingUpdates.toMap()
        pendingUpdates.clear()
        
        dataManager?.let { manager ->
            updates.forEach { (itemId, item) ->
                manager.updateStudyItem(item)
            }
            Log.d(TAG, "批量更新学习内容完成: ${updates.size}个项目")
        }
    }
    
    /**
     * 批量保存复习记录
     */
    private fun batchSaveReviewRecords() {
        val records = pendingRecords.toMap()
        pendingRecords.clear()
        
        dataManager?.let { manager ->
            records.forEach { (itemId, recordList) ->
                recordList.forEach { record ->
                    manager.addReviewRecord(itemId, record)
                }
            }
            Log.d(TAG, "批量保存复习记录完成: ${records.size}个项目")
        }
    }
    
    /**
     * 强制执行批量更新(忽略时间间隔)
     */
    fun forceExecuteBatch() {
        Log.i(TAG, "强制执行批量更新")
        lastBatchTime.set(System.currentTimeMillis())
        executeBatchUpdate()
    }
    
    /**
     * 保存到本地备份
     */
    private fun saveToLocalBackup(itemId: String, record: ReviewRecord) {
        try {
            val backupFile = File(context.filesDir, "backup_study_records.json")
            val backupData = mapOf(
                "itemId" to itemId,
                "record" to record,
                "timestamp" to System.currentTimeMillis()
            )
            
            val json = Gson().toJson(backupData)
            backupFile.appendText("$json\n")
            
            Log.d(TAG, "学习记录已保存到本地备份: ID=$itemId")
        } catch (e: Exception) {
            Log.e(TAG, "保存本地备份失败", e)
        }
    }
    
    /**
     * 保存失败的更新到备份
     */
    private fun saveFailedUpdatesToBackup() {
        try {
            val backupFile = File(context.filesDir, "pending_updates.json")
            val backupData = PendingUpdates(
                updates = pendingUpdates.toMap(),
                records = pendingRecords.toMap()
            )
            
            val json = Gson().toJson(backupData)
            backupFile.writeText(json)
            
            Log.w(TAG, "失败的更新已保存到备份文件")
        } catch (e: Exception) {
            Log.e(TAG, "保存失败更新到备份失败", e)
        }
    }
    
    /**
     * 启动自动保存
     */
    private fun startAutoSave() {
        if (autoSaveStarted) return
        
        autoSaveStarted = true
        autoSaveTimer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                Log.d(TAG, "执行定期自动保存")
                forceExecuteBatch()
            }
        }, AUTO_SAVE_INTERVAL, AUTO_SAVE_INTERVAL)
        
        Log.i(TAG, "自动保存已启动: 间隔=${AUTO_SAVE_INTERVAL}ms")
    }
    
    /**
     * 停止自动保存
     */
    fun stopAutoSave() {
        autoSaveTimer.cancel()
        autoSaveStarted = false
        Log.i(TAG, "自动保存已停止")
    }
    
    /**
     * 获取批量处理器状态
     */
    fun getBatchProcessorStatus(): BatchProcessorStatus {
        return BatchProcessorStatus(
            pendingUpdatesCount = pendingUpdates.size,
            pendingRecordsCount = pendingRecords.values.sumOf { it.size },
            timeSinceLastBatch = System.currentTimeMillis() - lastBatchTime.get(),
            autoSaveStarted = autoSaveStarted,
            batchInterval = BATCH_INTERVAL
        )
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        stopAutoSave()
        forceExecuteBatch() // 执行最后一次批量更新
        Log.i(TAG, "批量处理器已清理")
    }
}

/**
 * 待更新数据
 */
data class PendingUpdates(
    val updates: Map<String, StudyItem>,
    val records: Map<String, List<ReviewRecord>>
)

/**
 * 批量处理器状态
 */
data class BatchProcessorStatus(
    val pendingUpdatesCount: Int,
    val pendingRecordsCount: Int,
    val timeSinceLastBatch: Long,
    val autoSaveStarted: Boolean,
    val batchInterval: Long
) {
    fun getFormattedTimeSinceLastBatch(): String {
        val seconds = timeSinceLastBatch / 1000
        return "${seconds}s"
    }
    
    fun isTimeForBatch(): Boolean {
        return timeSinceLastBatch >= batchInterval
    }
}
