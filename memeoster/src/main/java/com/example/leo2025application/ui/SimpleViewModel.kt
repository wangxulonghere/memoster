package com.example.leo2025application.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.leo2025application.data.MemoryStrengthLevel
import com.example.leo2025application.data.ReviewRecord
import com.example.leo2025application.data.ReviewSchedule
import com.example.leo2025application.data.SimpleRepository
import com.example.leo2025application.data.StudyItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.TimeZone

class SimpleViewModel(private val repository: SimpleRepository) : ViewModel() {
    
    private val _currentItem = MutableStateFlow<StudyItem?>(null)
    val currentItem: StateFlow<StudyItem?> = _currentItem.asStateFlow()
    
    private val _items = MutableStateFlow<List<StudyItem>>(emptyList())
    val items: StateFlow<List<StudyItem>> = _items.asStateFlow()
    
    private var currentIndex: Int = 0
    private var lastEnterUtcMillis: Long = nowUtc()
    
    // 日志系统
    private val _systemLogs = MutableStateFlow(listOf<String>())
    val systemLogs: StateFlow<List<String>> = _systemLogs.asStateFlow()
    
    private val _learningLogs = MutableStateFlow(listOf<String>())
    val learningLogs: StateFlow<List<String>> = _learningLogs.asStateFlow()
    
    init {
        loadItems()
    }
    
    private fun loadItems() {
        viewModelScope.launch {
            val itemsList = repository.getAllItems()
            _items.value = itemsList
            if (itemsList.isNotEmpty()) {
                _currentItem.value = itemsList[0]
                currentIndex = 0
                lastEnterUtcMillis = nowUtc()
            }
            log("加载了 ${itemsList.size} 个项目")
        }
    }
    
    fun onAppear() {
        log("=== SYSTEM: onAppear() called ===")
        log("Items list: ${_items.value.map { it.text }}")
        log("Total items: ${_items.value.size}")
        log("Current item: ${_currentItem.value?.text}")
        log("Current index: $currentIndex")
    }
    
    fun onTapSpeakRequested() {
        logLearning("Tap to speak: ${_currentItem.value?.text}")
    }
    
    fun onSwipeNext() {
        logLearning("Swipe Next detected!")
        advance(1)
    }
    
    fun onSwipePrev() {
        logLearning("Swipe Prev detected!")
        advance(-1)
    }
    
    fun addNewItem(text: String, chineseTranslation: String? = null, notes: String? = null) {
        viewModelScope.launch {
            log("=== SYSTEM: addNewItem() called ===")
            log("Input text: '$text'")
            log("Chinese: '$chineseTranslation'")
            log("Notes: '$notes'")
            
            val new = repository.add(text, chineseTranslation, notes)
            log("添加新项目: ${new.text}")
            
            // 重新加载items
            loadItems()
        }
    }
    
    fun refreshItems() {
        loadItems()
    }
    
    fun logSystemMessage(message: String) {
        log(message)
    }
    
    fun deleteCurrentItem(itemId: String) {
        viewModelScope.launch {
            log("=== SYSTEM: deleteCurrentItem() called ===")
            log("要删除的ID: $itemId")
            
            val success = repository.deleteItemById(itemId)
            log("删除结果: $success")
            
            // 重新加载items
            loadItems()
        }
    }
    
    fun clearAllItems() {
        viewModelScope.launch {
            log("=== SYSTEM: clearAllItems() called ===")
            repository.clearAllItems()
            log("所有项目已清空")
            loadItems()
        }
    }
    
    private fun advance(delta: Int) {
        log("=== SYSTEM: advance() called ===")
        log("Delta: $delta")
        log("Current index before: $currentIndex")
        
        val items = _items.value
        if (items.isEmpty()) {
            log("No items to advance.")
            return
        }
        
        // 记录当前项目的停留时间
        recordDwellTime()
        
        // 计算新索引
        currentIndex = (currentIndex + delta + items.size) % items.size
        log("New index calculated: $currentIndex")
        
        // 更新当前项目
        _currentItem.value = items[currentIndex]
        lastEnterUtcMillis = nowUtc()
        
        log("Switched to index=$currentIndex text='${_currentItem.value?.text}'")
        log("=== advance() completed ===")
    }
    
    private fun recordDwellTime() {
        val currentItem = _currentItem.value ?: return
        val dwellTime = nowUtc() - lastEnterUtcMillis
        val strength = calculateMemoryStrength(dwellTime)
        
        logLearning("Dwell=${dwellTime}ms -> S=L$strength, next=${nowUtc() + getNextReviewDelay(strength)}")
    }
    
    private fun calculateMemoryStrength(dwellTimeMs: Long): Int {
        val dwellTimeSeconds = dwellTimeMs / 1000.0
        return when {
            dwellTimeSeconds >= 30 -> 0  // 最弱
            dwellTimeSeconds >= 10 -> 1
            dwellTimeSeconds >= 5 -> 2
            dwellTimeSeconds >= 1 -> 3
            else -> 4  // 最强
        }
    }
    
    private fun getNextReviewDelay(strength: Int): Long {
        return when (strength) {
            0 -> 5 * 60 * 1000L      // 5分钟
            1 -> 30 * 60 * 1000L     // 30分钟
            2 -> 2 * 60 * 60 * 1000L // 2小时
            3 -> 24 * 60 * 60 * 1000L // 1天
            4 -> 7 * 24 * 60 * 60 * 1000L // 1周
            else -> 60 * 1000L       // 默认1分钟
        }
    }
    
    private fun log(message: String) {
        val timestamp = System.currentTimeMillis()
        val formattedMessage = "[$timestamp] $message"
        _systemLogs.value = _systemLogs.value + formattedMessage
    }
    
    private fun logLearning(message: String) {
        val timestamp = System.currentTimeMillis()
        val formattedMessage = "[$timestamp] $message"
        _learningLogs.value = _learningLogs.value + formattedMessage
    }
    
    private fun nowUtc(): Long = System.currentTimeMillis() + TimeZone.getDefault().rawOffset
}
