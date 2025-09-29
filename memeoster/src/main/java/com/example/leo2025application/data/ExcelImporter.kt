package com.example.leo2025application.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class ExcelImporter(private val context: Context) {
    
    suspend fun importFromExcel(uri: Uri): List<StudyItem> = withContext(Dispatchers.IO) {
        try {
            Log.d("ExcelImporter", "=== 开始导入文件: $uri ===")
            Log.d("ExcelImporter", "Context: $context")
            
            val inputStream = context.contentResolver.openInputStream(uri)
            Log.d("ExcelImporter", "InputStream: $inputStream")
            if (inputStream == null) {
                Log.e("ExcelImporter", "无法打开文件流")
                return@withContext emptyList()
            }
            
            val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
            val items = mutableListOf<StudyItem>()
            
            // 简单的CSV解析器
            var line: String?
            var lineNumber = 0
            
            while (reader.readLine().also { line = it } != null) {
                lineNumber++
                line?.let { currentLine ->
                    val trimmedLine = currentLine.trim()
                    Log.d("ExcelImporter", "处理行 $lineNumber: '$trimmedLine'")
                    
                    if (trimmedLine.isNotEmpty()) {
                        // 支持逗号和制表符分隔，格式为：英文,中文,注释 或 英文\t中文\t注释
                        val parts = parseDelimitedLine(trimmedLine)
                        Log.d("ExcelImporter", "解析结果: $parts (共${parts.size}部分)")
                        
                        when (parts.size) {
                            1 -> {
                                // 只有英文
                                val text = parts[0].trim()
                                if (text.isNotEmpty()) {
                                    items.add(StudyItem(text = text))
                                    Log.d("ExcelImporter", "添加项目 $lineNumber: '$text'")
                                }
                            }
                            2 -> {
                                // 英文和中文
                                val text = parts[0].trim()
                                val chinese = parts[1].trim()
                                if (text.isNotEmpty()) {
                                    items.add(StudyItem(
                                        text = text,
                                        chineseTranslation = chinese.ifBlank { null }
                                    ))
                                    Log.d("ExcelImporter", "添加项目 $lineNumber: '$text' - '$chinese'")
                                }
                            }
                            3 -> {
                                // 英文、中文、注释
                                val text = parts[0].trim()
                                val chinese = parts[1].trim()
                                val notes = parts[2].trim()
                                if (text.isNotEmpty()) {
                                    items.add(StudyItem(
                                        text = text,
                                        chineseTranslation = chinese.ifBlank { null },
                                        notes = notes.ifBlank { null }
                                    ))
                                    Log.d("ExcelImporter", "添加项目 $lineNumber: '$text' - '$chinese' - '$notes'")
                                }
                            }
                            else -> {
                                Log.w("ExcelImporter", "跳过行 $lineNumber: 格式不正确，有${parts.size}部分")
                            }
                        }
                    } else {
                        Log.d("ExcelImporter", "跳过空行 $lineNumber")
                    }
                }
            }
            
            reader.close()
            inputStream.close()
            
            Log.d("ExcelImporter", "导入完成，共 ${items.size} 条记录")
            items
            
        } catch (e: Exception) {
            Log.e("ExcelImporter", "导入失败", e)
            emptyList()
        }
    }
    
    private fun parseDelimitedLine(line: String): List<String> {
        // 先检查是否包含制表符
        if (line.contains('\t')) {
            Log.d("ExcelImporter", "检测到制表符分隔")
            return line.split('\t').map { it.trim() }
        }
        
        // 否则按CSV格式解析（支持引号）
        Log.d("ExcelImporter", "按CSV格式解析")
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        
        for (i in line.indices) {
            val char = line[i]
            
            when {
                char == '"' -> {
                    inQuotes = !inQuotes
                }
                char == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current = StringBuilder()
                }
                else -> {
                    current.append(char)
                }
            }
        }
        
        result.add(current.toString())
        return result
    }
    
    suspend fun importBatch(items: List<StudyItem>, repository: SimpleRepository) = withContext(Dispatchers.Main) {
        try {
            Log.d("ExcelImporter", "开始批量添加，准备添加 ${items.size} 条")
            
            repository.addBatchWithSave(items)
            
            Log.d("ExcelImporter", "批量添加完成，共 ${items.size} 条")
        } catch (e: Exception) {
            Log.e("ExcelImporter", "批量添加失败", e)
        }
    }
}

