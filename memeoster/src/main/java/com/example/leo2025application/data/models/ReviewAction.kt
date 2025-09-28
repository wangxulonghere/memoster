package com.example.leo2025application.data.models

/**
 * 复习行为枚举
 * 定义用户在学习过程中的不同操作类型
 */
enum class ReviewAction {
    /**
     * 直接滑走 - 表示用户记住了这个内容
     * 算法影响: N = N + 1
     */
    SWIPE_NEXT,
    
    /**
     * 点击显示示意 - 表示用户不太熟悉，需要查看含义
     * 算法影响: N = N + 0.5
     */
    SHOW_MEANING,
    
    /**
     * 双击标记不熟 - 表示用户认为这个内容很难记住
     * 算法影响: if N > 2 then N = N - 2 else N = 0
     */
    MARK_DIFFICULT
}

/**
 * ReviewAction扩展函数
 */
fun ReviewAction.getVirtualCountChange(): Double {
    return when (this) {
        ReviewAction.SWIPE_NEXT -> 1.0
        ReviewAction.SHOW_MEANING -> 0.5
        ReviewAction.MARK_DIFFICULT -> -2.0  // 实际处理时会检查N > 2的条件
    }
}

fun ReviewAction.getDescription(): String {
    return when (this) {
        ReviewAction.SWIPE_NEXT -> "直接滑走(记住了)"
        ReviewAction.SHOW_MEANING -> "点击显示示意(不太熟)"
        ReviewAction.MARK_DIFFICULT -> "双击标记不熟(很难记)"
    }
}
