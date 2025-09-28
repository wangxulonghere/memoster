package com.example.leo2025application.data.models

import android.util.Log

/**
 * StudyItem ID生成器
 * 使用6位数字字符串，从"000001"开始，支持最多999999个学习内容
 */
class StudyItemId {
    companion object {
        private var nextId = 1
        private const val TAG = "StudyItemId"
        
        /**
         * 生成新的ID
         * @return 6位数字字符串ID，如"000001", "000002"等
         */
        fun generate(): String {
            if (nextId > 999999) {
                Log.e(TAG, "ID已达到上限999999，无法生成新ID")
                throw IllegalStateException("ID已达到上限999999")
            }
            
            val id = String.format("%06d", nextId)
            nextId++
            
            Log.d(TAG, "生成新ID: $id, 下一个ID将是: ${String.format("%06d", nextId)}")
            return id
        }
        
        /**
         * 验证ID格式是否有效
         * @param id 要验证的ID字符串
         * @return true如果ID格式有效且在范围内
         */
        fun isValid(id: String): Boolean {
            val isValid = id.matches(Regex("^\\d{6}$")) && id.toInt() in 1..999999
            Log.d(TAG, "验证ID '$id': $isValid")
            return isValid
        }
        
        /**
         * 获取下一个ID(不实际生成)
         * @return 下一个ID的字符串形式
         */
        fun getNextId(): String {
            return String.format("%06d", nextId)
        }
        
        /**
         * 获取当前ID计数
         * @return 当前ID计数器值
         */
        fun getCurrentCount(): Int {
            return nextId - 1
        }
        
        /**
         * 重置ID计数器(仅用于测试)
         */
        fun resetForTesting() {
            nextId = 1
            Log.w(TAG, "ID计数器已重置(仅用于测试)")
        }
    }
}
