package com.example.leo2025application.data.models

import android.util.Log

/**
 * 单次复习记录
 * 记录用户每次学习某个内容时的行为数据
 */
data class ReviewRecord(
    val reviewTime: Long,           // 复习时间点(UTC毫秒时间戳)
    val dwellTime: Long,            // 停留时间(毫秒)
    val action: ReviewAction,       // 复习行为
    val sessionId: String? = null   // 学习会话ID(可选)
) {
    companion object {
        private const val TAG = "ReviewRecord"
        
        /**
         * 创建新的复习记录
         * @param startTime 开始学习时间
         * @param endTime 结束学习时间
         * @param action 学习行为
         * @param sessionId 会话ID
         */
        fun create(
            startTime: Long,
            endTime: Long,
            action: ReviewAction,
            sessionId: String? = null
        ): ReviewRecord {
            val dwellTime = endTime - startTime
            val record = ReviewRecord(
                reviewTime = endTime,
                dwellTime = dwellTime,
                action = action,
                sessionId = sessionId
            )
            
            Log.d(TAG, "创建复习记录: 停留${dwellTime}ms, 行为=${action.getDescription()}")
            return record
        }
    }
    
    /**
     * 判断是否为误操作(< 0.2秒)
     */
    fun isAccidentalOperation(): Boolean {
        val isAccidental = dwellTime < 200L // 0.2秒
        if (isAccidental) {
            Log.d(TAG, "检测到误操作: 停留时间${dwellTime}ms < 200ms")
        }
        return isAccidental
    }
    
    /**
     * 获取停留时间(秒)
     */
    fun getDwellTimeInSeconds(): Double {
        return dwellTime / 1000.0
    }
    
    /**
     * 获取格式化的时间字符串
     */
    fun getFormattedTime(): String {
        val date = java.util.Date(reviewTime)
        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return formatter.format(date)
    }
}
