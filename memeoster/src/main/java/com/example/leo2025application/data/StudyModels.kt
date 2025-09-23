package com.example.leo2025application.data

import java.util.UUID

data class StudyItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val chineseTranslation: String? = null,
    val notes: String? = null,
    val imageResName: String? = null,
)

data class ReviewRecord(
    val itemId: String,
    val dwellMillis: Long,
    val timestampUtcMillis: Long
)

data class ReviewSchedule(
    val itemId: String,
    val nextReviewUtcMillis: Long,
    val lastStrength: MemoryStrengthLevel,
    val reviewCount: Int
)

enum class MemoryStrengthLevel {
    L0, L1, L2, L3, L4
}
