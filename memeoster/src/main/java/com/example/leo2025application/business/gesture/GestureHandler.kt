package com.example.leo2025application.business.gesture

import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import com.example.leo2025application.data.models.ReviewAction

/**
 * 手势处理器
 * 负责处理用户的手势操作，包括双击标记不熟、单击显示示意、滑走等
 */
class GestureHandler : GestureDetector.SimpleOnGestureListener() {
    
    companion object {
        private const val TAG = "GestureHandler"
        private const val DOUBLE_TAP_THRESHOLD = 300L // 双击间隔阈值(毫秒)
        private const val LONG_PRESS_THRESHOLD = 500L // 长按阈值(毫秒)
        private const val SWIPE_THRESHOLD = 100f // 滑动手势阈值
        private const val SWIPE_VELOCITY_THRESHOLD = 50f // 滑动速度阈值
    }
    
    private var lastTapTime = 0L
    private var gestureCallback: GestureCallback? = null
    
    /**
     * 设置手势回调
     */
    fun setGestureCallback(callback: GestureCallback) {
        this.gestureCallback = callback
    }
    
    /**
     * 处理单击事件
     */
    override fun onSingleTapUp(e: MotionEvent): Boolean {
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastTapTime < DOUBLE_TAP_THRESHOLD) {
            // 这是双击的一部分，不处理单击
            return false
        }
        
        lastTapTime = currentTime
        Log.d(TAG, "检测到单击事件: x=${e.x}, y=${e.y}")
        
        gestureCallback?.onGestureDetected(ReviewAction.SHOW_MEANING, "单击显示示意")
        return true
    }
    
    /**
     * 处理双击事件
     */
    override fun onDoubleTap(e: MotionEvent): Boolean {
        Log.d(TAG, "检测到双击事件: x=${e.x}, y=${e.y}")
        
        gestureCallback?.onGestureDetected(ReviewAction.MARK_DIFFICULT, "双击标记不熟")
        return true
    }
    
    /**
     * 处理长按事件
     */
    override fun onLongPress(e: MotionEvent) {
        Log.d(TAG, "检测到长按事件: x=${e.x}, y=${e.y}")
        
        gestureCallback?.onGestureDetected(ReviewAction.MARK_DIFFICULT, "长按标记不熟")
    }
    
    /**
     * 处理滑动手势
     */
    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        if (e1 == null) return false
        
        val deltaX = e2.x - e1.x
        val deltaY = e2.y - e1.y
        
        // 判断滑动方向和强度
        when {
            // 向右滑动
            Math.abs(deltaX) > Math.abs(deltaY) && 
            Math.abs(deltaX) > SWIPE_THRESHOLD && 
            Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD && 
            deltaX > 0 -> {
                Log.d(TAG, "检测到向右滑动手势: deltaX=$deltaX, velocityX=$velocityX")
                gestureCallback?.onGestureDetected(ReviewAction.SWIPE_NEXT, "向右滑走下一个")
                return true
            }
            
            // 向左滑动
            Math.abs(deltaX) > Math.abs(deltaY) && 
            Math.abs(deltaX) > SWIPE_THRESHOLD && 
            Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD && 
            deltaX < 0 -> {
                Log.d(TAG, "检测到向左滑动手势: deltaX=$deltaX, velocityX=$velocityX")
                gestureCallback?.onGestureDetected(ReviewAction.SWIPE_NEXT, "向左滑走下一个")
                return true
            }
            
            // 向上滑动
            Math.abs(deltaY) > Math.abs(deltaX) && 
            Math.abs(deltaY) > SWIPE_THRESHOLD && 
            Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD && 
            deltaY < 0 -> {
                Log.d(TAG, "检测到向上滑动手势: deltaY=$deltaY, velocityY=$velocityY")
                gestureCallback?.onGestureDetected(ReviewAction.SWIPE_NEXT, "向上滑走下一个")
                return true
            }
            
            // 向下滑动
            Math.abs(deltaY) > Math.abs(deltaX) && 
            Math.abs(deltaY) > SWIPE_THRESHOLD && 
            Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD && 
            deltaY > 0 -> {
                Log.d(TAG, "检测到向下滑动手势: deltaY=$deltaY, velocityY=$velocityY")
                gestureCallback?.onGestureDetected(ReviewAction.SWIPE_NEXT, "向下滑走下一个")
                return true
            }
        }
        
        return false
    }
    
    /**
     * 处理滚动事件(可用于实现更复杂的手势)
     */
    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        // 可以在这里实现滚动相关的逻辑
        return false
    }
    
    /**
     * 处理触摸按下事件
     */
    override fun onDown(e: MotionEvent): Boolean {
        Log.v(TAG, "触摸按下: x=${e.x}, y=${e.y}")
        return true // 返回true表示要处理后续事件
    }
    
    /**
     * 处理触摸抬起事件
     */
    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        // 确认单击(不是双击)
        Log.d(TAG, "确认单击事件: x=${e.x}, y=${e.y}")
        return true
    }
    
    /**
     * 处理双击确认事件
     */
    override fun onDoubleTapEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                Log.d(TAG, "双击按下事件")
            }
            MotionEvent.ACTION_UP -> {
                Log.d(TAG, "双击抬起事件")
            }
        }
        return true
    }
    
    /**
     * 获取手势检测器配置
     */
    fun getGestureDetectorConfig(): GestureConfig {
        return GestureConfig(
            doubleTapThreshold = DOUBLE_TAP_THRESHOLD,
            longPressThreshold = LONG_PRESS_THRESHOLD,
            swipeThreshold = SWIPE_THRESHOLD,
            swipeVelocityThreshold = SWIPE_VELOCITY_THRESHOLD
        )
    }
    
    /**
     * 重置手势状态
     */
    fun reset() {
        lastTapTime = 0L
        Log.d(TAG, "手势处理器已重置")
    }
}

/**
 * 手势回调接口
 */
interface GestureCallback {
    /**
     * 手势检测回调
     * @param action 检测到的动作
     * @param description 动作描述
     */
    fun onGestureDetected(action: ReviewAction, description: String)
}

/**
 * 手势配置
 */
data class GestureConfig(
    val doubleTapThreshold: Long,
    val longPressThreshold: Long,
    val swipeThreshold: Float,
    val swipeVelocityThreshold: Float
) {
    fun getFormattedThresholds(): String {
        return "双击阈值: ${doubleTapThreshold}ms, 长按阈值: ${longPressThreshold}ms, 滑动阈值: ${swipeThreshold}px, 滑动速度阈值: ${swipeVelocityThreshold}px/s"
    }
}
