package com.example.leo2025application.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.leo2025application.data.RoomStudyRepository
import com.example.leo2025application.data.StudyItem
import com.example.leo2025application.data.ReviewRecord
import com.example.leo2025application.data.ReviewSchedule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.TimeZone

class RoomStudyViewModel(private val repository: RoomStudyRepository) : ViewModel() {
    
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
        // 初始化默认数据并加载
        viewModelScope.launch {
            try {
                repository.initializeDefaultDataIfEmpty()
                loadItems()
            } catch (e: Exception) {
                log("初始化失败: ${e.message}")
                loadItems()
            }
        }
    }
    
    private fun loadItems() {
        viewModelScope.launch {
            repository.getAllItems().collect { items ->
                _items.value = items
                if (items.isNotEmpty() && currentIndex < items.size) {
                    _currentItem.value = items[currentIndex]
                } else if (items.isEmpty()) {
                    _currentItem.value = null
                    currentIndex = 0
                }
                log("加载了 ${items.size} 个项目")
            }
        }
    }
    
    fun onAppear() {
        log("=== SYSTEM: onAppear() called ===")
        lastEnterUtcMillis = nowUtc()
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
            
            try {
                val newItem = repository.addItem(text, chineseTranslation, notes)
                log("添加成功: ${newItem.text}")
            } catch (e: Exception) {
                log("添加失败: ${e.message}")
            }
        }
    }
    
    fun deleteCurrentItem(itemId: String) {
        viewModelScope.launch {
            log("=== SYSTEM: deleteCurrentItem() called ===")
            log("要删除的ID: $itemId")
            
            try {
                repository.deleteItem(itemId)
                log("删除成功")
            } catch (e: Exception) {
                log("删除失败: ${e.message}")
            }
        }
    }
    
    fun refreshItems() {
        // 这个方法现在不需要了，因为Flow会自动更新
        log("refreshItems() called - Flow会自动更新")
    }
    
    fun logSystemMessage(message: String) {
        logSystem(message)
    }
    
    private fun advance(delta: Int) {
        log("=== SYSTEM: advance() called ===")
        log("Delta: $delta")
        log("Current index before: $currentIndex")
        
        val items = _items.value
        if (items.isEmpty()) {
            log("No items available")
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
    
    private fun nowUtc(): Long {
        return System.currentTimeMillis() + TimeZone.getDefault().rawOffset
    }
    
    private fun logSystem(message: String) {
        val timestamp = nowUtc()
        _systemLogs.value = _systemLogs.value + "[$timestamp] $message"
    }
    
    private fun logLearning(message: String) {
        val timestamp = nowUtc()
        _learningLogs.value = _learningLogs.value + "[$timestamp] $message"
    }
    
    private fun log(message: String) {
        logSystem(message)
    }
}
