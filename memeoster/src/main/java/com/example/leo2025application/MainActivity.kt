package com.example.leo2025application

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
// import androidx.activity.enableEdgeToEdge // 降级版本中不可用
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
        // enableEdgeToEdge() // 降级版本中不可用
        
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
    
    // 状态管理
    var showSystemLogs by remember { mutableStateOf(true) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var newItemText by remember { mutableStateOf("") }
    var newItemChinese by remember { mutableStateOf("") }
    var newItemNotes by remember { mutableStateOf("") }
    
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
            showSystemLogs = showSystemLogs,
            onLogCopied = { viewModel.addNewItem("日志已复制") },
            onToggleLogType = { showSystemLogs = !showSystemLogs }
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
            onAdd = { showAddDialog = true },
            onLeft1 = { viewModel.addNewItem("设置功能") },
            onLeft2 = { showImportDialog = true },
            onRight1 = { viewModel.addNewItem("统计功能") },
            onRight2 = { viewModel.addNewItem("更多功能") }
        )
        
        // 添加项目对话框
        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("添加新内容") },
                text = {
                    Column {
                        TextField(
                            value = newItemText,
                            onValueChange = { newItemText = it },
                            label = { Text("英文内容") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextField(
                            value = newItemChinese,
                            onValueChange = { newItemChinese = it },
                            label = { Text("中文译文（可选）") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextField(
                            value = newItemNotes,
                            onValueChange = { newItemNotes = it },
                            label = { Text("注释（可选）") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newItemText.isNotBlank()) {
                                viewModel.addNewItem(newItemText, newItemChinese.ifBlank { null }, newItemNotes.ifBlank { null })
                                newItemText = ""
                                newItemChinese = ""
                                newItemNotes = ""
                                showAddDialog = false
                            }
                        }
                    ) {
                        Text("添加")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }
        
        // 导入文件对话框
        if (showImportDialog) {
            AlertDialog(
                onDismissRequest = { showImportDialog = false },
                title = { Text("批量导入") },
                text = { Text("请选择Excel文件进行批量导入") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            // TODO: 实现文件选择
                            viewModel.addNewItem("批量导入功能待实现")
                            showImportDialog = false
                        }
                    ) {
                        Text("选择文件")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showImportDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }
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
    val context = LocalContext.current
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        // 日志标题和按钮行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (showSystemLogs) "系统日志" else "学习日志",
                style = MaterialTheme.typography.titleMedium
            )
            
            Row {
                Button(
                    onClick = onToggleLogType,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text(if (showSystemLogs) "切换学习" else "切换系统", style = MaterialTheme.typography.bodySmall)
                }
                
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val currentLogs = if (showSystemLogs) systemLogs else learningLogs
                        val logText = currentLogs.joinToString("\n")
                        clipboard.setPrimaryClip(ClipData.newPlainText("日志", logText))
                        onLogCopied()
                    },
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text("复制当前", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 显示当前选中的日志
        val currentLogs = if (showSystemLogs) systemLogs else learningLogs
        currentLogs.forEach { line ->
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