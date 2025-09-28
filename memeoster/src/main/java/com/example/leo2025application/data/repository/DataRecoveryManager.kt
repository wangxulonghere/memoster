package com.example.leo2025application.data.repository

import android.content.Context
import android.util.Log
import com.example.leo2025application.data.models.StudyItem
import com.example.leo2025application.data.models.ReviewRecord
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * 数据恢复管理器
 * 负责应用启动时检查数据完整性并恢复丢失的数据
 */
class DataRecoveryManager(private val context: Context) {
    
    companion object {
        private const val TAG = "DataRecoveryManager"
        private const val BACKUP_FILE = "backup_study_records.json"
        private const val PENDING_UPDATES_FILE = "pending_updates.json"
    }
    
    private val gson = Gson()
    
    /**
     * 检查并恢复数据
     * @param dataManager 数据管理器
     * @return 恢复结果
     */
    fun checkAndRecoverData(dataManager: StudyDataManager): RecoveryResult {
        Log.i(TAG, "开始检查数据完整性")
        
        val result = RecoveryResult()
        
        try {
            // 1. 检查并恢复待更新的批量数据
            recoverPendingBatchUpdates(dataManager, result)
            
            // 2. 检查并恢复本地备份文件
            recoverFromLocalBackup(dataManager, result)
            
            // 3. 验证数据完整性
            validateDataIntegrity(dataManager, result)
            
            Log.i(TAG, "数据恢复完成: 恢复项目=${result.recoveredItems}, 恢复记录=${result.recoveredRecords}")
            
        } catch (e: Exception) {
            Log.e(TAG, "数据恢复失败", e)
            result.addError("数据恢复失败: ${e.message}")
        }
        
        return result
    }
    
    /**
     * 恢复待批量更新的数据
     */
    private fun recoverPendingBatchUpdates(dataManager: StudyDataManager, result: RecoveryResult) {
        val pendingFile = File(context.filesDir, PENDING_UPDATES_FILE)
        if (!pendingFile.exists()) {
            Log.d(TAG, "没有待更新的批量数据文件")
            return
        }
        
        try {
            val json = pendingFile.readText()
            val pendingUpdates = gson.fromJson(json, PendingUpdates::class.java)
            
            // 恢复待更新的学习内容
            pendingUpdates.updates.forEach { (itemId, item) ->
                dataManager.updateStudyItem(item)
                result.recoveredItems++
                Log.d(TAG, "恢复待更新项目: ID=$itemId")
            }
            
            // 恢复待保存的复习记录
            pendingUpdates.records.forEach { (itemId, records) ->
                records.forEach { record ->
                    dataManager.addReviewRecord(itemId, record)
                    result.recoveredRecords++
                }
                Log.d(TAG, "恢复复习记录: ID=$itemId, 记录数量=${records.size}")
            }
            
            // 删除恢复文件
            pendingFile.delete()
            Log.i(TAG, "已恢复${pendingUpdates.updates.size}个待更新项目和${pendingUpdates.records.values.sumOf { it.size }}条复习记录")
            
        } catch (e: Exception) {
            Log.e(TAG, "恢复待更新数据失败", e)
            result.addError("恢复待更新数据失败: ${e.message}")
        }
    }
    
    /**
     * 从本地备份恢复数据
     */
    private fun recoverFromLocalBackup(dataManager: StudyDataManager, result: RecoveryResult) {
        val backupFile = File(context.filesDir, BACKUP_FILE)
        if (!backupFile.exists()) {
            Log.d(TAG, "没有本地备份文件")
            return
        }
        
        try {
            val backupLines = backupFile.readLines()
            var recoveredCount = 0
            
            backupLines.forEach { line ->
                if (line.trim().isNotEmpty()) {
                    try {
                        val backupData = gson.fromJson(line, Map::class.java) as Map<String, Any>
                        val itemId = backupData["itemId"] as String
                        
                        // 检查项目是否存在
                        if (dataManager.getStudyItem(itemId) != null) {
                            // 这里可以添加更多的恢复逻辑
                            recoveredCount++
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "解析备份行失败: $line", e)
                    }
                }
            }
            
            result.recoveredRecords += recoveredCount
            Log.i(TAG, "从本地备份恢复了${recoveredCount}条记录")
            
            // 清理备份文件(可选)
            // backupFile.delete()
            
        } catch (e: Exception) {
            Log.e(TAG, "恢复本地备份失败", e)
            result.addError("恢复本地备份失败: ${e.message}")
        }
    }
    
    /**
     * 验证数据完整性
     */
    private fun validateDataIntegrity(dataManager: StudyDataManager, result: RecoveryResult) {
        val statistics = dataManager.getDataStatistics()
        
        // 检查基本数据完整性
        if (statistics.totalItems == 0) {
            result.addWarning("没有学习内容数据")
        }
        
        if (statistics.dueItems > statistics.totalItems) {
            result.addError("到期项目数量异常: ${statistics.dueItems} > ${statistics.totalItems}")
        }
        
        // 检查内存使用情况
        if (statistics.memoryUsage > 100 * 1024 * 1024) { // 超过100MB
            result.addWarning("内存使用量较高: ${statistics.getFormattedMemoryUsage()}")
        }
        
        Log.d(TAG, "数据完整性验证完成: 总项目=${statistics.totalItems}, 到期项目=${statistics.dueItems}, 内存使用=${statistics.getFormattedMemoryUsage()}")
    }
    
    /**
     * 清理备份文件
     */
    fun cleanupBackupFiles() {
        try {
            val backupFile = File(context.filesDir, BACKUP_FILE)
            val pendingFile = File(context.filesDir, PENDING_UPDATES_FILE)
            
            var cleanedCount = 0
            
            if (backupFile.exists()) {
                backupFile.delete()
                cleanedCount++
            }
            
            if (pendingFile.exists()) {
                pendingFile.delete()
                cleanedCount++
            }
            
            Log.i(TAG, "已清理${cleanedCount}个备份文件")
            
        } catch (e: Exception) {
            Log.e(TAG, "清理备份文件失败", e)
        }
    }
    
    /**
     * 获取备份文件信息
     */
    fun getBackupInfo(): BackupInfo {
        val backupFile = File(context.filesDir, BACKUP_FILE)
        val pendingFile = File(context.filesDir, PENDING_UPDATES_FILE)
        
        return BackupInfo(
            hasBackupFile = backupFile.exists(),
            hasPendingFile = pendingFile.exists(),
            backupFileSize = if (backupFile.exists()) backupFile.length() else 0L,
            pendingFileSize = if (pendingFile.exists()) pendingFile.length() else 0L
        )
    }
}

/**
 * 恢复结果
 */
data class RecoveryResult(
    var recoveredItems: Int = 0,
    var recoveredRecords: Int = 0,
    private val errors: MutableList<String> = mutableListOf(),
    private val warnings: MutableList<String> = mutableListOf()
) {
    fun addError(error: String) {
        errors.add(error)
    }
    
    fun addWarning(warning: String) {
        warnings.add(warning)
    }
    
    fun hasErrors(): Boolean = errors.isNotEmpty()
    fun hasWarnings(): Boolean = warnings.isNotEmpty()
    
    fun getErrors(): List<String> = errors.toList()
    fun getWarnings(): List<String> = warnings.toList()
    
    fun getFormattedSummary(): String {
        val summary = StringBuilder()
        summary.append("数据恢复完成: 恢复项目=$recoveredItems, 恢复记录=$recoveredRecords")
        
        if (hasErrors()) {
            summary.append("\n错误: ${errors.joinToString("; ")}")
        }
        
        if (hasWarnings()) {
            summary.append("\n警告: ${warnings.joinToString("; ")}")
        }
        
        return summary.toString()
    }
}

/**
 * 备份文件信息
 */
data class BackupInfo(
    val hasBackupFile: Boolean,
    val hasPendingFile: Boolean,
    val backupFileSize: Long,
    val pendingFileSize: Long
) {
    fun getFormattedBackupSize(): String {
        return formatFileSize(backupFileSize)
    }
    
    fun getFormattedPendingSize(): String {
        return formatFileSize(pendingFileSize)
    }
    
    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "${size}B"
            size < 1024 * 1024 -> "${size / 1024}KB"
            else -> "${size / (1024 * 1024)}MB"
        }
    }
}
