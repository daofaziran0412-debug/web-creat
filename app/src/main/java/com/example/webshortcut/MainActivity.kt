package com.example.webshortcut

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.MalformedURLException
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 数据模型：历史记录
data class ShortcutRecord(
    val name: String,
    val url: String,
    val createTime: Long
)

// 页面枚举
enum class Page { MAIN, HISTORY }

class MainActivity : ComponentActivity() {

    private var iconBitmap by mutableStateOf<Bitmap?>(null)
    private var labelText by mutableStateOf(TextFieldValue(""))
    private var urlText by mutableStateOf(TextFieldValue(""))
    private var labelError by mutableStateOf(false)
    private var urlError by mutableStateOf(false)
    private var currentPage by mutableStateOf(Page.MAIN)
    private var historyList by mutableStateOf<List<ShortcutRecord>>(emptyList())

    private lateinit var prefs: SharedPreferences

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply { inSampleSize = 2 }
            val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()
            bitmap?.let { iconBitmap = it }
        } catch (e: Exception) {
            Log.e("MainActivity", "图片读取失败: ${e.message}")
            runOnUiThread {
                Toast.makeText(this@MainActivity, "图片读取失败，请换一张图片", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val requestMediaPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pickImageLauncher.launch("image/*")
        } else {
            Toast.makeText(this, "需要图片权限，才能选择图标", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("shortcut_history", Context.MODE_PRIVATE)
        loadHistory()
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFF3F4F6)
                ) {
                    when (currentPage) {
                        Page.MAIN -> MainScreen()
                        Page.HISTORY -> HistoryScreen()
                    }
                }
            }
        }
    }

    // ==================== 主页面 ====================
    @Composable
    fun MainScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "网页快捷方式", fontSize = 22.sp, color = Color(0xFF111827))
                IconButton(onClick = { currentPage = Page.HISTORY }) {
                    Icon(Icons.Outlined.History, contentDescription = "历史记录", tint = Color(0xFF374151))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 图标卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "快捷方式图标", fontSize = 16.sp, color = Color(0xFF374151))
                    Spacer(modifier = Modifier.height(16.dp))
                    IconPreview()
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { checkAndOpenGallery() }) {
                            Icon(Icons.Outlined.Image, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("选择图片")
                        }
                        OutlinedButton(onClick = { iconBitmap = null }) {
                            Icon(Icons.Outlined.Clear, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("清除")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 信息卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(text = "快捷方式信息", fontSize = 16.sp, color = Color(0xFF374151))
                    Spacer(modifier = Modifier.height(16.dp))

                    TextField(
                        value = labelText,
                        onValueChange = {
                            labelText = it
                            labelError = false
                        },
                        label = { Text("桌面图标名称") },
                        placeholder = { Text("例如：B站、百度") },
                        singleLine = true,
                        isError = labelError,
                        supportingText = { if (labelError) Text("名称不能为空") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    TextField(
                        value = urlText,
                        onValueChange = {
                            urlText = it
                            urlError = false
                        },
                        label = { Text("网页地址URL") },
                        placeholder = { Text("https://xxx.com") },
                        singleLine = true,
                        isError = urlError,
                        supportingText = { if (urlError) Text("网址格式不正确") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = { createShortcutAction() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("添加到桌面", fontSize = 16.sp)
            }
        }
    }

    @Composable
    fun IconPreview() {
        val bitmap = iconBitmap
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(Color(0xFFE9ECEF), shape = RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "图标预览",
                    modifier = Modifier.size(110.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(text = "预览图标", color = Color.Gray, fontSize = 14.sp)
            }
        }
    }

    // ==================== 历史记录页面 ====================
    @Composable
    fun HistoryScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 32.dp)
        ) {
            // 顶部栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { currentPage = Page.MAIN }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = Color(0xFF374151))
                    }
                    Text(text = "历史记录", fontSize = 20.sp, color = Color(0xFF111827))
                }
                TextButton(onClick = { clearAllHistory() }) {
                    Text("一键清空", color = Color(0xFFDC2626), fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (historyList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.History,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color(0xFFD1D5DB)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "暂无历史记录", color = Color(0xFF9CA3AF), fontSize = 16.sp)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(historyList) { record ->
                        HistoryItem(record = record)
                    }
                }
            }
        }
    }

    @Composable
    fun HistoryItem(record: ShortcutRecord) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    // 点击记录：填充表单并返回主页面
                    labelText = TextFieldValue(record.name)
                    urlText = TextFieldValue(record.url)
                    labelError = false
                    urlError = false
                    currentPage = Page.MAIN
                    Toast.makeText(this@MainActivity, "已填充，可直接添加到桌面", Toast.LENGTH_SHORT).show()
                },
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.name,
                        fontSize = 16.sp,
                        color = Color(0xFF111827),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = record.url,
                        fontSize = 13.sp,
                        color = Color(0xFF6B7280),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatTime(record.createTime),
                        fontSize = 12.sp,
                        color = Color(0xFF9CA3AF)
                    )
                }
                IconButton(onClick = { deleteHistory(record) }) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "删除",
                        tint = Color(0xFFDC2626)
                    )
                }
            }
        }
    }

    // ==================== 历史记录存储逻辑 ====================
    private fun loadHistory() {
        try {
            val jsonStr = prefs.getString("history_list", "[]") ?: "[]"
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<ShortcutRecord>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    ShortcutRecord(
                        name = obj.getString("name"),
                        url = obj.getString("url"),
                        createTime = obj.getLong("createTime")
                    )
                )
            }
            historyList = list
        } catch (e: Exception) {
            Log.e("MainActivity", "加载历史记录失败: ${e.message}")
            historyList = emptyList()
        }
    }

    private fun saveHistory(record: ShortcutRecord) {
        try {
            // 去重：如果同名同网址已存在，先删除旧的
            val newList = historyList.filterNot { it.name == record.name && it.url == record.url }.toMutableList()
            newList.add(0, record) // 最新的放最前面
            // 最多保留50条
            val limitedList = newList.take(50)

            val jsonArray = JSONArray()
            for (item in limitedList) {
                val obj = JSONObject()
                obj.put("name", item.name)
                obj.put("url", item.url)
                obj.put("createTime", item.createTime)
                jsonArray.put(obj)
            }
            prefs.edit().putString("history_list", jsonArray.toString()).apply()
            historyList = limitedList
        } catch (e: Exception) {
            Log.e("MainActivity", "保存历史记录失败: ${e.message}")
        }
    }

    private fun deleteHistory(record: ShortcutRecord) {
        try {
            val newList = historyList.filterNot { it.name == record.name && it.url == record.url && it.createTime == record.createTime }
            val jsonArray = JSONArray()
            for (item in newList) {
                val obj = JSONObject()
                obj.put("name", item.name)
                obj.put("url", item.url)
                obj.put("createTime", item.createTime)
                jsonArray.put(obj)
            }
            prefs.edit().putString("history_list", jsonArray.toString()).apply()
            historyList = newList
            Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("MainActivity", "删除历史记录失败: ${e.message}")
        }
    }

    private fun clearAllHistory() {
        prefs.edit().remove("history_list").apply()
        historyList = emptyList()
        Toast.makeText(this, "已清空全部历史记录", Toast.LENGTH_SHORT).show()
    }

    private fun formatTime(timeMillis: Long): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            sdf.format(Date(timeMillis))
        } catch (e: Exception) {
            ""
        }
    }

    // ==================== 原有功能逻辑 ====================
    private fun checkAndOpenGallery() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                pickImageLauncher.launch("image/*")
            }
            shouldShowRequestPermissionRationale(permission) -> {
                Toast.makeText(this, "需要图片权限，用于选择快捷方式图标", Toast.LENGTH_SHORT).show()
                requestMediaPermission.launch(permission)
            }
            else -> {
                requestMediaPermission.launch(permission)
            }
        }
    }

    private fun createShortcutAction() {
        val shortcutLabel = labelText.text.trim()
        val shortcutUrlRaw = urlText.text.trim()

        if (shortcutLabel.isBlank()) {
            labelError = true
            Toast.makeText(this, "请填写桌面图标名称", Toast.LENGTH_SHORT).show()
            return
        }
        if (shortcutUrlRaw.isBlank()) {
            urlError = true
            Toast.makeText(this, "网址不能为空", Toast.LENGTH_SHORT).show()
            return
        }

        val targetUri: Uri
        try {
            targetUri = Uri.parse(shortcutUrlRaw)
            URL(shortcutUrlRaw)
        } catch (_: MalformedURLException) {
            urlError = true
            Toast.makeText(this, "网址格式不正确，请检查", Toast.LENGTH_SHORT).show()
            return
        }

        val iconBitmap = iconBitmap
        val openWebIntent = Intent(Intent.ACTION_VIEW, targetUri)

        val shortcutBuilder = ShortcutInfoCompat.Builder(this, "web_sc_${System.currentTimeMillis()}")
            .setShortLabel(shortcutLabel)
            .setIntent(openWebIntent)

        if (iconBitmap != null) {
            shortcutBuilder.setIcon(IconCompat.createWithBitmap(iconBitmap))
        }

        val shortcutInfo = shortcutBuilder.build()

        if (ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            val resultIntent = ShortcutManagerCompat.createShortcutResultIntent(this, shortcutInfo)
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                0,
                resultIntent,
                PendingIntent.FLAG_IMMUTABLE
            )
            ShortcutManagerCompat.requestPinShortcut(this, shortcutInfo, pendingIntent.intentSender)

            // 成功发起后，保存到历史记录
            saveHistory(
                ShortcutRecord(
                    name = shortcutLabel,
                    url = shortcutUrlRaw,
                    createTime = System.currentTimeMillis()
                )
            )
        } else {
            Toast.makeText(this, "当前手机不支持添加桌面快捷方式", Toast.LENGTH_LONG).show()
        }
    }
}
