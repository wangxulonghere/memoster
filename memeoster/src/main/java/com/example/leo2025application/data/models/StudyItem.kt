package com.example.leo2025application.data.models

import android.util.Log

/**
 * 学习内容实体 - 轻量级核心数据
 * 包含学习内容的基本信息和算法相关的核心属性
 */
data class StudyItem(
    // 基本信息
    val id: String,                           // 6位数字ID
    val word: String,                         // 学习内容(单词/短语等)
    val meaning: String,                      // 含义/翻译
    val level: Int = 1,                       // 难度等级
    
    // 算法相关核心属性
    val virtualReviewCount: Double = 0.0,     // 虚拟复习次数 N
    val actualReviewCount: Int = 0,           // 实际复习次数 n
    val sensitivity: Double = 1.0,            // 感受度参数 S (1~3)
    
    // 复习时间
    var nextReviewTime: Long = System.currentTimeMillis(),
    
    // 创建时间
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        private const val TAG = "StudyItem"
        private const val MAX_RECORDS = 200
        
        /**
         * 创建新的学习内容
         */
        fun create(
            word: String,
            meaning: String,
            level: Int = 1
        ): StudyItem {
            val id = StudyItemId.generate()
            val item = StudyItem(
                id = id,
                word = word,
                meaning = meaning,
                level = level,
                nextReviewTime = System.currentTimeMillis() // 新内容立即复习
            )
            
            Log.i(TAG, "创建新学习内容: ID=$id, 内容='$word', 等级=$level")
            return item
        }
    }
    
    /**
     * 检查是否到了复习时间
     */
    fun isDueForReview(): Boolean {
        val isDue = nextReviewTime <= System.currentTimeMillis()
        Log.d(TAG, "检查复习时间: ID=$id, 下次复习=${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(nextReviewTime))}, 是否到期=$isDue")
        return isDue
    }
    
    /**
     * 获取距离下次复习的时间(毫秒)
     */
    fun getTimeUntilNextReview(): Long {
        return nextReviewTime - System.currentTimeMillis()
    }
    
    /**
     * 获取格式化的下次复习时间
     */
    fun getFormattedNextReviewTime(): String {
        val date = java.util.Date(nextReviewTime)
        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return formatter.format(date)
    }
    
    /**
     * 更新复习时间
     */
    fun updateNextReviewTime(newTime: Long) {
        val oldTime = nextReviewTime
        nextReviewTime = newTime
        Log.d(TAG, "更新复习时间: ID=$id, 从${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(oldTime))}到${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(newTime))}")
    }
    
    /**
     * 更新算法属性
     */
    fun updateAlgorithmProperties(
        newVirtualCount: Double,
        newActualCount: Int,
        newSensitivity: Double
    ) {
        Log.d(TAG, "更新算法属性: ID=$id, 虚拟次数: $virtualReviewCount -> $newVirtualCount, 实际次数: $actualReviewCount -> $newActualCount, 感受度: $sensitivity -> $newSensitivity")
        
        // 注意：由于data class的不可变性，这些更新需要通过copy()方法实现
        // 这里只是记录日志，实际更新在外部进行
    }
}
