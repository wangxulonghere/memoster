package com.example.leo2025application.algorithm.calculator

import android.util.Log
import com.example.leo2025application.data.models.ReviewRecord
import com.example.leo2025application.data.models.StudyItem

/**
 * 下次复习时间计算器
 * 负责根据学习行为和历史数据计算下次复习时间
 */
class NextReviewCalculator {
    
    companion object {
        private const val TAG = "NextReviewCalculator"
    }
    
    private val memoryCalculator = MemoryStrengthCalculator()
    
    /**
     * 计算下次复习时间
     * @param item 学习内容
     * @param newRecord 新的复习记录
     * @param history 历史复习记录
     * @return 下次复习时间(UTC毫秒时间戳)
     */
    fun calculateNextReviewTime(
        item: StudyItem,
        newRecord: ReviewRecord,
        history: List<ReviewRecord>
    ): Long {
        Log.i(TAG, "开始计算下次复习时间: ID=${item.id}, 内容='${item.word}'")
        
        // 1. 更新虚拟复习次数
        val updatedVirtualCount = memoryCalculator.updateVirtualCount(
            item.virtualReviewCount,
            newRecord.action
        )
        
        // 2. 计算新的感受度
        val newSensitivity = memoryCalculator.calculateSensitivity(
            item.actualReviewCount + 1,
            updatedVirtualCount
        )
        
        // 3. 计算基础复习间隔
        val baseInterval = memoryCalculator.calculateBaseInterval(
            newSensitivity,
            updatedVirtualCount
        )
        
        // 4. 计算停留时间调整系数
        val averageDwell = memoryCalculator.calculateAverageDwellTime(history)
        val dwellFactor = memoryCalculator.calculateDwellTimeFactor(
            newRecord.dwellTime,
            averageDwell
        )
        
        // 5. 计算最终复习时间 = 基础间隔 / 停留时间系数
        val finalInterval = (baseInterval / dwellFactor).toLong()
        val nextReviewTime = newRecord.reviewTime + finalInterval
        
        // 6. 确保复习时间不会太短(最少5秒)
        val minInterval = 5 * 1000L // 5秒
        val adjustedNextReviewTime = maxOf(nextReviewTime, newRecord.reviewTime + minInterval)
        
        Log.i(TAG, "计算完成: " +
                "虚拟次数 ${item.virtualReviewCount} -> $updatedVirtualCount, " +
                "感受度 ${item.sensitivity} -> $newSensitivity, " +
                "基础间隔 ${baseInterval}ms, " +
                "停留系数 $dwellFactor, " +
                "最终间隔 ${finalInterval}ms, " +
                "下次复习时间 ${formatTime(adjustedNextReviewTime)}")
        
        return adjustedNextReviewTime
    }
    
    /**
     * 计算学习内容的更新属性
     * @param item 原始学习内容
     * @param newRecord 新的复习记录
     * @param history 历史复习记录
     * @return 更新后的学习内容
     */
    fun calculateUpdatedItem(
        item: StudyItem,
        newRecord: ReviewRecord,
        history: List<ReviewRecord>
    ): StudyItem {
        // 更新虚拟复习次数
        val updatedVirtualCount = memoryCalculator.updateVirtualCount(
            item.virtualReviewCount,
            newRecord.action
        )
        
        // 计算新的感受度
        val newSensitivity = memoryCalculator.calculateSensitivity(
            item.actualReviewCount + 1,
            updatedVirtualCount
        )
        
        // 计算下次复习时间
        val nextReviewTime = calculateNextReviewTime(item, newRecord, history)
        
        // 返回更新后的学习内容
        return item.copy(
            virtualReviewCount = updatedVirtualCount,
            actualReviewCount = item.actualReviewCount + 1,
            sensitivity = newSensitivity,
            nextReviewTime = nextReviewTime
        )
    }
    
    /**
     * 预测学习效果
     * @param item 学习内容
     * @param history 历史复习记录
     * @return 学习效果评估
     */
    fun predictLearningEffectiveness(item: StudyItem, history: List<ReviewRecord>): LearningEffectiveness {
        val recentRecords = history.takeLast(5)
        
        if (recentRecords.isEmpty()) {
            return LearningEffectiveness.UNKNOWN
        }
        
        // 分析最近的学习行为
        val swipeNextCount = recentRecords.count { it.action == com.example.leo2025application.data.models.ReviewAction.SWIPE_NEXT }
        val showMeaningCount = recentRecords.count { it.action == com.example.leo2025application.data.models.ReviewAction.SHOW_MEANING }
        val markDifficultCount = recentRecords.count { it.action == com.example.leo2025application.data.models.ReviewAction.MARK_DIFFICULT }
        
        // 计算平均停留时间
        val avgDwellTime = memoryCalculator.calculateAverageDwellTime(recentRecords)
        
        // 分析学习效果
        return when {
            swipeNextCount >= recentRecords.size * 0.8 -> LearningEffectiveness.EXCELLENT
            swipeNextCount >= recentRecords.size * 0.6 -> LearningEffectiveness.GOOD
            markDifficultCount >= recentRecords.size * 0.4 -> LearningEffectiveness.POOR
            avgDwellTime > 10000 -> LearningEffectiveness.SLOW_PROGRESS // 停留时间过长
            else -> LearningEffectiveness.MODERATE
        }
    }
    
    /**
     * 获取复习建议
     * @param item 学习内容
     * @param history 历史复习记录
     * @return 复习建议
     */
    fun getReviewSuggestion(item: StudyItem, history: List<ReviewRecord>): ReviewSuggestion {
        val effectiveness = predictLearningEffectiveness(item, history)
        val anomaly = memoryCalculator.detectAnomalies(history)
        
        return when {
            anomaly != com.example.leo2025application.algorithm.calculator.AnomalyType.NONE -> {
                ReviewSuggestion.REQUIRES_ATTENTION
            }
            effectiveness == LearningEffectiveness.POOR -> {
                ReviewSuggestion.NEEDS_MORE_PRACTICE
            }
            effectiveness == LearningEffectiveness.EXCELLENT -> {
                ReviewSuggestion.READY_FOR_ADVANCEMENT
            }
            else -> {
                ReviewSuggestion.CONTINUE_NORMAL
            }
        }
    }
    
    private fun formatTime(timestamp: Long): String {
        val date = java.util.Date(timestamp)
        val formatter = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return formatter.format(date)
    }
}

/**
 * 学习效果评估
 */
enum class LearningEffectiveness {
    UNKNOWN,        // 未知
    EXCELLENT,      // 优秀
    GOOD,           // 良好
    MODERATE,       // 一般
    POOR,           // 较差
    SLOW_PROGRESS   // 进步缓慢
}

/**
 * 复习建议
 */
enum class ReviewSuggestion {
    CONTINUE_NORMAL,        // 继续正常复习
    NEEDS_MORE_PRACTICE,    // 需要更多练习
    REQUIRES_ATTENTION,     // 需要特别关注
    READY_FOR_ADVANCEMENT   // 可以进入下一阶段
}
