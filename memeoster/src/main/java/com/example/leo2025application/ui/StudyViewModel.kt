package com.example.leo2025application.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.leo2025application.data.InMemoryRepository
import com.example.leo2025application.data.MemoryStrengthLevel
import com.example.leo2025application.data.ReviewRecord
import com.example.leo2025application.data.ReviewSchedule
import com.example.leo2025application.data.StudyItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.TimeZone

class StudyViewModel(private val repository: InMemoryRepository) : ViewModel() {
    private val _items: MutableList<StudyItem> = repository.getAll().toMutableList()
    private var currentIndex: Int = 0
    private var lastEnterUtcMillis: Long = nowUtc()

    private val _currentItem = MutableStateFlow(_items.firstOrNull())
    val currentItem: StateFlow<StudyItem?> = _currentItem

    private val _systemLogs = MutableStateFlow(listOf<String>())
    val systemLogs: StateFlow<List<String>> = _systemLogs
    
    private val _learningLogs = MutableStateFlow(listOf<String>())
    val learningLogs: StateFlow<List<String>> = _learningLogs

    private fun logSystem(message: String) {
        _systemLogs.value = (listOf("[" + nowUtc() + "] " + message) + _systemLogs.value).take(50)
    }
    
    private fun logLearning(message: String) {
        _learningLogs.value = (listOf("[" + nowUtc() + "] " + message) + _learningLogs.value).take(50)
    }
    
    private fun log(message: String) {
        logSystem(message)
    }

    fun onAppear() {
        lastEnterUtcMillis = nowUtc()
        log("=== SYSTEM: onAppear() called ===")
        log("Current index: $currentIndex")
        log("Current item: ${_currentItem.value?.text}")
        log("Total items: ${_items.size}")
        log("Items list: ${_items.map { it.text }}")
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

    fun addNewItem(text: String) {
        log("=== SYSTEM: addNewItem() called ===")
        log("Input text: '$text'")
        log("Before add - items count: ${_items.size}")
        
        val new = repository.add(text)
        log("Repository.add() returned: ${new.text}")
        
        _items.add(new)
        log("Added to _items list, new size: ${_items.size}")
        
        log("=== addNewItem() completed ===")
        log("New total items: ${_items.size}")
        log("Current items: ${_items.map { it.text }}")
    }

    private fun advance(delta: Int) {
        log("=== SYSTEM: advance() called ===")
        log("Delta: $delta")
        log("Current index before: $currentIndex")
        log("Items size: ${_items.size}")
        
        val leaveUtc = nowUtc()
        val dwell = leaveUtc - lastEnterUtcMillis
        _currentItem.value?.let { item ->
            val level = computeStrengthLevelFromDwell(dwell)
            val nextReview = computeNextReviewUtc(level, item.id)
            val rec = ReviewRecord(itemId = item.id, dwellMillis = dwell, timestampUtcMillis = leaveUtc)
            val schedule = ReviewSchedule(itemId = item.id, nextReviewUtcMillis = nextReview, lastStrength = level, reviewCount = (repository.getSchedule(item.id)?.reviewCount ?: 0) + 1)
            repository.recordReview(rec, schedule)
        }
        
        currentIndex = (currentIndex + delta + _items.size) % _items.size
        _currentItem.value = _items[currentIndex]
        lastEnterUtcMillis = nowUtc()
        
        log("Switched to index=$currentIndex text='${_currentItem.value?.text}'")
        log("Dwell=${dwell}ms -> S=${computeStrengthLevelFromDwell(dwell)}, next=${computeNextReviewUtc(computeStrengthLevelFromDwell(dwell), _currentItem.value?.id ?: "")}")
        log("=== advance() completed ===")
    }

    private fun computeStrengthLevelFromDwell(dwellMillis: Long): MemoryStrengthLevel {
        return when {
            dwellMillis >= 30000 -> MemoryStrengthLevel.L0
            dwellMillis >= 10000 -> MemoryStrengthLevel.L1
            dwellMillis >= 5000 -> MemoryStrengthLevel.L2
            dwellMillis >= 1000 -> MemoryStrengthLevel.L3
            else -> MemoryStrengthLevel.L4
        }
    }

    private fun computeNextReviewUtc(strength: MemoryStrengthLevel, itemId: String): Long {
        val baseNow = nowUtc()
        val existingSchedule = repository.getSchedule(itemId)
        val reviewCount = existingSchedule?.reviewCount ?: 0
        
        val delayMillis = when (strength) {
            MemoryStrengthLevel.L4 -> minutes(1)
            MemoryStrengthLevel.L3 -> minutes(5)
            MemoryStrengthLevel.L2 -> minutes(30)
            MemoryStrengthLevel.L1 -> hours(2)
            MemoryStrengthLevel.L0 -> minutes(5)
        }
        return baseNow + delayMillis
    }

    fun nowUtc(): Long = System.currentTimeMillis() + TimeZone.getDefault().rawOffset * -1L

    private fun minutes(m: Int) = m * 60_000L
    private fun hours(h: Int) = h * 3_600_000L
}
