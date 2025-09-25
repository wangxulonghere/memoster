package com.example.leo2025application.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "study_items")
data class StudyItemEntity(
    @PrimaryKey
    val id: String,
    val text: String,
    val chineseTranslation: String? = null,
    val notes: String? = null,
    val imageResName: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// 扩展函数：实体转数据类
fun StudyItemEntity.toStudyItem(): StudyItem {
    return StudyItem(
        id = this.id,
        text = this.text,
        chineseTranslation = this.chineseTranslation,
        notes = this.notes,
        imageResName = this.imageResName
    )
}

// 扩展函数：数据类转实体
fun StudyItem.toStudyItemEntity(): StudyItemEntity {
    return StudyItemEntity(
        id = this.id,
        text = this.text,
        chineseTranslation = this.chineseTranslation,
        notes = this.notes,
        imageResName = this.imageResName,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )
}
