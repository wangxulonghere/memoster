package com.example.leo2025application.algorithm.calculator

import android.util.Log
import com.example.leo2025application.data.models.ReviewRecord
import kotlin.math.*

/**
 * 记忆强度计算器
 * 负责计算感受度、基础复习间隔等核心算法
 */
class MemoryStrengthCalculator {
    
    companion object {
        private const val TAG = "MemoryStrengthCalculator"
        private const val BASE_INTERVAL_SECONDS = 10L // 基础间隔10秒
        private const val MAX_RECORDS_FOR_AVERAGE = 3  // 用于计算平均值的记录数量
    }
    
    /**
     * 计算感受度 S = tanh(N/n-1) + 2
     * @param actualCount 实际复习次数 n
     * @param virtualCount 虚拟复习次数 N
     * @return 感受度参数 S (1~3)
     */
    fun calculateSensitivity(actualCount: Int, virtualCount: Double): Double {
        if (actualCount == 0) {
            Log.d(TAG, "计算感受度: 实际次数为0，返回默认值1.0")
            return 1.0
        }
        
        val ratio = virtualCount / actualCount - 1
        val sensitivity = tanh(ratio) + 2.0
        
        // 确保感受度在合理范围内 (1~3)
        val clampedSensitivity = sensitivity.coerceIn(1.0, 3.0)
        
        Log.d(TAG, "计算感受度: 实际次数=$actualCount, 虚拟次数=$virtualCount, 比率=$ratio, 感受度=$clampedSensitivity")
        return clampedSensitivity
    }
    
    /**
     * 计算基础复习间隔 t = 10s * S^N
     * @param sensitivity 感受度参数 S
     * @param virtualCount 虚拟复习次数 N
     * @return 基础复习间隔(毫秒)
     */
    fun calculateBaseInterval(sensitivity: Double, virtualCount: Double): Long {
        val intervalSeconds = BASE_INTERVAL_SECONDS * Math.pow(sensitivity, virtualCount)
        val intervalMillis = (intervalSeconds * 1000).toLong()
        
        Log.d(TAG, "计算基础间隔: 感受度=$sensitivity, 虚拟次数=$virtualCount, 间隔=${intervalSeconds}s")
        return intervalMillis
    }
    
    /**
     * 计算平均停留时间(近N次)
     * @param history 历史复习记录
     * @param maxRecords 最多使用多少条记录计算平均值
     * @return 平均停留时间(毫秒)
     */
    fun calculateAverageDwellTime(history: List<ReviewRecord>, maxRecords: Int = MAX_RECORDS_FOR_AVERAGE): Long {
        if (history.isEmpty()) {
            Log.d(TAG, "计算平均停留时间: 历史记录为空，返回0")
            return 0L
        }
        
        val recentRecords = history.takeLast(maxRecords)
        val averageDwell = recentRecords.map { it.dwellTime }.average()
        
        Log.d(TAG, "计算平均停留时间: 使用最近${recentRecords.size}条记录, 平均停留=${averageDwell.toLong()}ms")
        return averageDwell.toLong()
    }
    
    /**
     * 计算停留时间调整系数
     * @param currentDwell 当前停留时间(毫秒)
     * @param averageDwell 平均停留时间(毫秒)
     * @return 调整系数 a
     */
    fun calculateDwellTimeFactor(currentDwell: Long, averageDwell: Long): Double {
        if (averageDwell == 0L) {
            Log.d(TAG, "计算停留时间系数: 平均停留时间为0，返回默认值1.0")
            return 1.0
        }
        
        val factor = currentDwell.toDouble() / averageDwell
        Log.d(TAG, "计算停留时间系数: 当前=${currentDwell}ms, 平均=${averageDwell}ms, 系数=$factor")
        return factor
    }
    
    /**
     * 更新虚拟复习次数
     * @param currentVirtual 当前虚拟复习次数
     * @param action 复习行为
     * @return 更新后的虚拟复习次数
     */
    fun updateVirtualCount(currentVirtual: Double, action: com.example.leo2025application.data.models.ReviewAction): Double {
        val change = when (action) {
            com.example.leo2025application.data.models.ReviewAction.SWIPE_NEXT -> 1.0
            com.example.leo2025application.data.models.ReviewAction.SHOW_MEANING -> 0.5
            com.example.leo2025application.data.models.ReviewAction.MARK_DIFFICULT -> -2.0
        }
        var newVirtual = currentVirtual + change
        
        // 特殊处理标记不熟的情况
        if (action == com.example.leo2025application.data.models.ReviewAction.MARK_DIFFICULT) {
            newVirtual = if (currentVirtual > 2) currentVirtual - 2.0 else 0.0
        }
        
        // 确保虚拟次数不为负数
        newVirtual = maxOf(newVirtual, 0.0)
        
        val actionDesc = when (action) {
            com.example.leo2025application.data.models.ReviewAction.SWIPE_NEXT -> "直接滑走(记住了)"
            com.example.leo2025application.data.models.ReviewAction.SHOW_MEANING -> "点击显示示意(不太熟)"
            com.example.leo2025application.data.models.ReviewAction.MARK_DIFFICULT -> "双击标记不熟(很难记)"
        }
        
        Log.d(TAG, "更新虚拟复习次数: 当前=$currentVirtual, 行为=$actionDesc, 变化=$change, 新值=$newVirtual")
        return newVirtual
    }
    
    /**
     * 检测异常学习模式
     * @param history 历史复习记录
     * @return 异常类型
     */
    fun detectAnomalies(history: List<ReviewRecord>): AnomalyType {
        if (history.size < 3) {
            return AnomalyType.NONE
        }
        
        val recentRecords = history.takeLast(5) // 检查最近5次
        
        // 检测频繁误操作
        val accidentalCount = recentRecords.count { it.isAccidentalOperation() }
        if (accidentalCount >= 3) {
            Log.w(TAG, "检测到异常: 频繁误操作 ($accidentalCount/5)")
            return AnomalyType.FREQUENT_ACCIDENTS
        }
        
        // 检测停留时间异常
        val dwellTimes = recentRecords.map { it.dwellTime }
        val avgDwell = dwellTimes.average()
        val variance = dwellTimes.map { (it - avgDwell).pow(2) }.average()
        val stdDev = sqrt(variance)
        
        if (stdDev > avgDwell * 0.5) { // 标准差超过平均值50%
            Log.w(TAG, "检测到异常: 停留时间波动过大 (标准差=${stdDev.toLong()}ms, 平均值=${avgDwell.toLong()}ms)")
            return AnomalyType.HIGH_VARIANCE
        }
        
        return AnomalyType.NONE
    }
}

/**
 * 异常类型枚举
 */
enum class AnomalyType {
    NONE,                   // 无异常
    FREQUENT_ACCIDENTS,     // 频繁误操作
    HIGH_VARIANCE,          // 高波动性
    UNUSUAL_PATTERN         // 异常模式
}
