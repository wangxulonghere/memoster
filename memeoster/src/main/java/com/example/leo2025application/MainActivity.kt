package com.example.leo2025application

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.leo2025application.ui.SimpleViewModel
import com.example.leo2025application.ui.theme.Leo2025ApplicationTheme
import com.example.leo2025application.data.SimpleRepository
import com.example.leo2025application.data.ExcelImporter
import com.example.leo2025application.data.StudyItem
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var repository: SimpleRepository
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // enableEdgeToEdge() // 降级版本中不可用
        
        repository = SimpleRepository(this)
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
                    repository = repository,
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
fun StudyScreen(repository: SimpleRepository, speak: (String) -> Unit) {
    val context = LocalContext.current
    val viewModel: SimpleViewModel = viewModel { SimpleViewModel(repository) }
    
    // 状态管理
    var showSystemLogs by remember { mutableStateOf(true) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showTranslation by remember { mutableStateOf(false) }
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
            onLogCopied = { /* 复制完成，不需要添加内容 */ },
            onToggleLogType = { showSystemLogs = !showSystemLogs }
        )
        
        // 学习内容
        StudyContent(
            modifier = Modifier.weight(1f),
            currentItem = currentItem,
            onSpeak = { text ->
                speak(text)
                showTranslation = true
                viewModel.onTapSpeakRequested()
            },
            onSwipeNext = { 
                showTranslation = false
                viewModel.onSwipeNext()
            },
            onSwipePrev = { 
                showTranslation = false
                viewModel.onSwipePrev()
            },
            showTranslation = showTranslation,
            onToggleTranslation = { showTranslation = !showTranslation }
        )
        
        // 导航按钮（上一个/播放/下一个）
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Button(
                onClick = { 
                    showTranslation = false
                    viewModel.onSwipePrev()
                },
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
            ) {
                Text("上一个")
            }
            
            Button(
                onClick = { 
                    currentItem?.text?.let { text -> 
                        speak(text)
                        showTranslation = true
                        viewModel.onTapSpeakRequested()
                    }
                },
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
            ) {
                Text("播放")
            }
            
            Button(
                onClick = { 
                    showTranslation = false
                    viewModel.onSwipeNext()
                },
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
            ) {
                Text("下一个")
            }
        }
        
        // 底部导航
        BottomNavigationBar(
            onAdd = { showAddDialog = true },
            onLeft1 = { 
                // 清理测试数据功能
                GlobalScope.launch {
                    try {
                        android.util.Log.d("MainActivity", "=== 开始清理测试数据 ===")
                        repository.clearAllItems()
                        viewModel.refreshItems()
                        viewModel.logSystemMessage("已清理所有测试数据")
                        android.util.Log.d("MainActivity", "=== 清理测试数据完成 ===")
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "清理数据失败", e)
                    }
                }
            },
            onLeft2 = { showImportDialog = true },
            onRight1 = { 
                // 显示删除确认对话框
                currentItem?.let { currentItem ->
                    showDeleteDialog = true
                }
            },
                   onRight2 = {
                       viewModel.logReviewStatus()
                   }
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
        
        // 文件选择器
        val filePickerLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            android.util.Log.d("MainActivity", "文件选择器回调，URI: $uri")
            uri?.let {
                // 使用协程处理文件导入
                GlobalScope.launch {
                    try {
                        android.util.Log.d("MainActivity", "开始导入文件: $it")
                        val importer = ExcelImporter(context)
                        val items = importer.importFromExcel(it)
                        
                        android.util.Log.d("MainActivity", "导入解析完成，获得 ${items.size} 条内容")
                        
                        if (items.isNotEmpty()) {
                            android.util.Log.d("MainActivity", "开始批量添加到Repository")
                            importer.importBatch(items, repository)
                            
                            android.util.Log.d("MainActivity", "开始刷新ViewModel")
                            // 通知ViewModel更新items列表
                            viewModel.refreshItems()
                            
                            // 只记录到系统日志，不添加到学习内容
                            viewModel.logSystemMessage("成功导入 ${items.size} 条内容")
                        } else {
                            android.util.Log.d("MainActivity", "导入结果为空")
                            viewModel.logSystemMessage("导入失败：文件格式错误或为空")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "导入过程异常", e)
                        android.util.Log.e("MainActivity", "异常堆栈: ${e.stackTrace.joinToString("\n")}")
                        viewModel.addNewItem("导入失败：${e.message}")
                    }
                }
            } ?: run {
                android.util.Log.d("MainActivity", "文件选择被取消或URI为null")
            }
            showImportDialog = false
        }
        
        // 导入文件对话框
        if (showImportDialog) {
            AlertDialog(
                onDismissRequest = { showImportDialog = false },
                title = { Text("批量导入") },
                text = { Text("请选择CSV或TXT文件进行批量导入\n格式：英文,中文,注释") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            android.util.Log.d("MainActivity", "用户点击了选择文件按钮")
                            android.util.Log.d("MainActivity", "准备启动文件选择器")
                            filePickerLauncher.launch(arrayOf("*/*"))
                            android.util.Log.d("MainActivity", "文件选择器已启动")
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
        
        // 删除确认对话框
        if (showDeleteDialog) {
            currentItem?.let { currentItem ->
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text("确认删除") },
                    text = { Text("确定要删除当前学习内容吗？\n\n\"${currentItem.text}\"") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.deleteCurrentItem(currentItem.id)
                                viewModel.logSystemMessage("已删除当前内容: ${currentItem.text}")
                                showDeleteDialog = false
                            }
                        ) {
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteDialog = false }) {
                            Text("取消")
                        }
                    }
                )
            }
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
    val scrollState = rememberScrollState()
    
    // 自动滚动到底部
    LaunchedEffect(systemLogs.size, learningLogs.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        // 固定的标题和按钮行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp),
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
        
        // 可滚动的日志内容区域
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            val currentLogs = if (showSystemLogs) systemLogs else learningLogs
            if (currentLogs.isEmpty()) {
                Text(
                    text = "暂无日志",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                currentLogs.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
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
        
        // 预留中文译文显示空间
        Spacer(modifier = Modifier.height(8.dp))
        
        // 中文译文区域（固定高度，无论是否显示）
        Box(
            modifier = Modifier
                .height(80.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (showTranslation && !currentItem?.chineseTranslation.isNullOrBlank()) {
                Text(
                    text = currentItem?.chineseTranslation ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
        
        // 注释区域（固定高度，无论是否显示）
        Box(
            modifier = Modifier
                .height(60.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (showTranslation && !currentItem?.notes.isNullOrBlank()) {
                Text(
                    text = currentItem?.notes ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
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
            Text("导入", style = MaterialTheme.typography.bodySmall)
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
            Text("删除", style = MaterialTheme.typography.bodySmall)
        }
        
        Button(
            onClick = onRight2,
            modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
        ) {
            Text("更多", style = MaterialTheme.typography.bodySmall)
        }
    }
}