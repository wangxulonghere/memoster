package com.example.leo2025application

import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.leo2025application.ui.StudyViewModel
import com.example.leo2025application.ui.theme.Leo2025ApplicationTheme
import com.example.leo2025application.data.InMemoryRepository
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var repository: InMemoryRepository
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        repository = InMemoryRepository(this)
        setContent {
            Leo2025ApplicationTheme {
                var ttsReady by remember { mutableStateOf(false) }
                
                DisposableEffect(Unit) {
                    tts = TextToSpeech(this@MainActivity) { status ->
                        if (status == TextToSpeech.SUCCESS) {
                            tts?.language = Locale.US
                            ttsReady = true
                        }
                    }
                    onDispose {
                        tts?.shutdown()
                    }
                }
                
                StudyScreen(
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
fun StudyScreen(speak: (String) -> Unit) {
    val repository = InMemoryRepository(LocalContext.current)
    val viewModel: StudyViewModel = viewModel { StudyViewModel(repository) }
    
    LaunchedEffect(Unit) {
        viewModel.onAppear()
    }
    
    val currentItem by viewModel.currentItem.collectAsState()
    val systemLogs by viewModel.systemLogs.collectAsState()
    val learningLogs by viewModel.learningLogs.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 日志面板
        LogPanel(
            modifier = Modifier.fillMaxHeight(0.4f),
            systemLogs = systemLogs,
            learningLogs = learningLogs,
            showSystemLogs = true,
            onLogCopied = { viewModel.addNewItem("日志已复制") },
            onToggleLogType = { /* 暂时不实现 */ }
        )
        
        // 学习内容
        StudyContent(
            modifier = Modifier.weight(1f),
            currentItem = currentItem,
            onSpeak = { text ->
                speak(text)
                viewModel.onTapSpeakRequested()
            },
            onSwipeNext = viewModel::onSwipeNext,
            onSwipePrev = viewModel::onSwipePrev,
            showTranslation = false,
            onToggleTranslation = { /* 暂时不实现 */ }
        )
        
        // 底部导航
        BottomNavigationBar(
            onAdd = { viewModel.addNewItem("新内容") },
            onLeft1 = { viewModel.addNewItem("设置功能") },
            onLeft2 = { viewModel.addNewItem("复习队列") },
            onRight1 = { viewModel.addNewItem("统计功能") },
            onRight2 = { viewModel.addNewItem("更多功能") }
        )
    }
}

@Composable
fun LogPanel(
    modifier: Modifier = Modifier,
    systemLogs: List<String>,
    learningLogs: List<String>,
    showSystemLogs: Boolean,
    onLogCopied: () -> Unit,
    onToggleLogType: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        Text(
            text = "系统日志",
            style = MaterialTheme.typography.titleMedium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        systemLogs.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}

@Composable
fun StudyContent(
    modifier: Modifier = Modifier,
    currentItem: com.example.leo2025application.data.StudyItem?,
    onSpeak: (String) -> Unit,
    onSwipeNext: () -> Unit,
    onSwipePrev: () -> Unit,
    showTranslation: Boolean,
    onToggleTranslation: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 英文内容
        Text(
            text = currentItem?.text ?: "暂无内容",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )
        
        // 中文译文（如果显示）
        if (showTranslation) {
            Text(
                text = currentItem?.chineseTranslation ?: "",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(8.dp)
            )
        }
        
        // 注释（如果显示）
        if (showTranslation && !currentItem?.notes.isNullOrBlank()) {
            Text(
                text = currentItem?.notes ?: "",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(8.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 导航按钮
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(onClick = onSwipePrev) {
                Text("上一个")
            }
            
            Button(
                onClick = { currentItem?.text?.let { text -> onSpeak(text) } }
            ) {
                Text("播放")
            }
            
            Button(onClick = onSwipeNext) {
                Text("下一个")
            }
        }
    }
}

@Composable
fun BottomNavigationBar(
    modifier: Modifier = Modifier,
    onAdd: () -> Unit,
    onLeft1: () -> Unit,
    onLeft2: () -> Unit,
    onRight1: () -> Unit,
    onRight2: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .padding(4.dp)
            .background(MaterialTheme.colorScheme.surface),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onLeft1,
            modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
        ) {
            Text("设置", style = MaterialTheme.typography.bodySmall)
        }
        
        Button(
            onClick = onLeft2,
            modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
        ) {
            Text("复习", style = MaterialTheme.typography.bodySmall)
        }
        
        Button(
            onClick = onAdd,
            modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
        ) {
            Text("+", style = MaterialTheme.typography.bodySmall)
        }
        
        Button(
            onClick = onRight1,
            modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
        ) {
            Text("统计", style = MaterialTheme.typography.bodySmall)
        }
        
        Button(
            onClick = onRight2,
            modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
        ) {
            Text("更多", style = MaterialTheme.typography.bodySmall)
        }
    }
}