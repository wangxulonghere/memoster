package com.example.leo2025application

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.leo2025application.ui.theme.Leo2025ApplicationTheme
import java.util.Locale

class SimpleMainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            Leo2025ApplicationTheme {
                var ttsReady by remember { mutableStateOf(false) }
                
                DisposableEffect(Unit) {
                    tts = TextToSpeech(this@SimpleMainActivity) { status ->
                        if (status == TextToSpeech.SUCCESS) {
                            tts?.language = Locale.US
                            ttsReady = true
                        }
                    }
                    onDispose {
                        tts?.shutdown()
                    }
                }

                SimpleStudyScreen(
                    speak = { text ->
                        if (ttsReady) {
                            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utterance")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun SimpleStudyScreen(speak: (String) -> Unit) {
    val context = LocalContext.current
    var currentIndex by remember { mutableStateOf(0) }
    var showTranslation by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var logMessages by remember { mutableStateOf(mutableListOf<String>()) }
    
    // 简单的测试数据
    val testItems = listOf(
        "Good morning" to "早上好",
        "Good afternoon" to "下午好", 
        "Good evening" to "晚上好"
    )
    
    val currentItem = testItems.getOrNull(currentIndex)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 简单的日志面板
        Column(
            modifier = Modifier
                .fillMaxHeight(0.4f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .verticalScroll(rememberScrollState())
                .padding(8.dp)
        ) {
            Text(
                text = "简单测试版本 - 当前项目: ${currentIndex + 1}/${testItems.size}",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "内容: ${currentItem?.first ?: "无"}",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "日志:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            
            if (logMessages.isEmpty()) {
                Text(
                    text = "暂无日志",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                logMessages.forEach { message ->
                    Text(
                        text = "• $message",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 学习内容
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = currentItem?.first ?: "暂无内容",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (showTranslation && currentItem?.second != null) {
                Text(
                    text = currentItem.second,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        // 导航按钮
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Button(
                onClick = {
                    currentIndex = (currentIndex - 1 + testItems.size) % testItems.size
                    showTranslation = false
                }
            ) {
                Text("上一个")
            }

            Button(
                onClick = {
                    currentItem?.first?.let { text ->
                        speak(text)
                        showTranslation = true
                    }
                }
            ) {
                Text("播放")
            }

            Button(
                onClick = {
                    currentIndex = (currentIndex + 1) % testItems.size
                    showTranslation = false
                }
            ) {
                Text("下一个")
            }
        }

        // 底部按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .padding(4.dp)
                .background(MaterialTheme.colorScheme.surface),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { 
                    // 设置功能 - 清空日志
                    logMessages.clear()
                },
                modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
            ) {
                Text("设置", style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = { 
                    // 导入功能 - 添加测试内容
                    logMessages.add("导入功能被点击")
                },
                modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
            ) {
                Text("导入", style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = { 
                    // 添加功能 - 显示输入对话框
                    showAddDialog = true
                },
                modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
            ) {
                Text("+", style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = { 
                    // 删除功能 - 删除当前项目
                    if (testItems.isNotEmpty()) {
                        logMessages.add("删除功能被点击")
                    }
                },
                modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
            ) {
                Text("删除", style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = { 
                    // 更多功能
                    logMessages.add("更多功能被点击")
                },
                modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
            ) {
                Text("更多", style = MaterialTheme.typography.bodySmall)
            }
        }
        
        // 添加对话框
        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("添加新内容") },
                text = {
                    Text("这是简化版本，暂时无法添加新内容。\n点击确定会在日志中记录此操作。")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            logMessages.add("用户尝试添加新内容")
                            showAddDialog = false
                        }
                    ) {
                        Text("确定")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }
    }
}
