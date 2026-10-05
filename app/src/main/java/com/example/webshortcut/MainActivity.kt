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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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

val GradientColors = listOf(Color(0xFF667eea), Color(0xFF764ba2))
val BackgroundGradient = Brush.linearGradient(
    colors = listOf(Color(0xFFf0f4ff), Color(0xFFe8ecf8))
)
val CardBackground = Color(0xD9FFFFFF)
val IconPreviewGradient = Brush.linearGradient(
    colors = listOf(Color(0xFFe0e7ff), Color(0xFFc7d2fe))
)
val TextGradient = Brush.linearGradient(
    colors = GradientColors
)

data class ShortcutRecord(
    val name: String,
    val url: String,
    val createTime: Long
)

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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BackgroundGradient)
                ) {
                    when (currentPage) {
                        Page.MAIN -> MainScreen()
                        Page.HISTORY -> HistoryScreen()
                    }
                }
            }
        }
    }

    @Composable
    fun GradientText(text: String, fontSize: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(brush = TextGradient)) {
                    append(text)
                }
            },
            fontSize = fontSize,
            modifier = modifier,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
    }

    @Composable
    fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
        Card(
            modifier = modifier
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(24.dp),
                    ambientColor = Color(0xFF667eea).copy(alpha = 0.15f),
                    spotColor = Color(0xFF667eea).copy(alpha = 0.15f)
                ),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground)
        ) {
            Column(modifier = Modifier.padding(24.dp), content = content)
        }
    }

    @Composable
    fun GradientButton(
        onClick: () -> Unit,
        text: String,
        modifier: Modifier = Modifier,
        icon: @Composable (() -> Unit)? = null
    ) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(colors = GradientColors))
                .clickable(onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon?.invoke()
                if (icon != null) Spacer(modifier = Modifier.width(8.dp))
                Text(text = text, color = Color.White, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            }
        }
    }

    @Composable
    fun MainScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GradientText(text = "网页快捷方式", fontSize = 26.sp)
                IconButton(onClick = { currentPage = Page.HISTORY }) {
                    Icon(Icons.Outlined.History, contentDescription = "历史记录", tint = Color(0xFF667eea))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "✨ 快捷方式图标",
                    fontSize = 16.sp,
                    color = Color(0xFF4a5568),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(18.dp))
                IconPreview()
                Spacer(modifier = Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    GradientButton(
                        onClick = { checkAndOpenGallery() },
                        text = "选择图片",
                        icon = { Icon(Icons.Outlined.Image, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                    )
                    OutlinedButton(
                        onClick = { iconBitmap = null },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xB3FFFFFF))
                    ) {
                        Icon(Icons.Outlined.Clear, contentDescription = null, tint = Color(0xFF667eea), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("清除", color = Color(0xFF667eea), fontSize = 14.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "📝 快捷方式信息",
                    fontSize = 16.sp,
                    color = Color(0xFF4a5568),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(18.dp))

                OutlinedTextField(
                    value = labelText,
                    onValueChange = {
                        labelText = it
                        labelError = false
                    },
                    label = { Text("桌面图标名称", color = Color(0xFF718096)) },
                    placeholder = { Text("例如：B站、百度", color = Color(0xFFa0aec0)) },
                    singleLine = true,
                    isError = labelError,
                    supportingText = { if (labelError) Text("名称不能为空", color = Color(0xFFe53e3e)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xE6FFFFFF),
                        unfocusedContainerColor = Color(0xE6FFFFFF),
                        focusedBorderColor = Color(0xFF667eea),
                        unfocusedBorderColor = Color(0xFFe2e8f0)
                    )
                )

                Spacer(modifier = Modifier.height(18.dp))

                OutlinedTextField(
                    value = urlText,
                    onValueChange = {
                        urlText = it
                        urlError = false
                    },
                    label = { Text("网页地址URL", color = Color(0xFF718096)) },
                    placeholder = { Text("https://xxx.com", color = Color(0xFFa0aec0)) },
                    singleLine = true,
                    isError = urlError,
                    supportingText = { if (urlError) Text("网址格式不正确", color = Color(0xFFe53e3e)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xE6FFFFFF),
                        unfocusedContainerColor = Color(0xE6FFFFFF),
                        focusedBorderColor = Color(0xFF667eea),
                        unfocusedBorderColor = Color(0xFFe2e8f0)
                    )
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(colors = GradientColors))
                    .clickable(onClick = { createShortcutAction() })
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("🚀 添加到桌面", color = Color.White, fontSize = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
        }
    }

    @Composable
    fun IconPreview() {
        val bitmap = iconBitmap
        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(IconPreviewGradient)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(28.dp),
                    ambientColor = Color(0xFF667eea).copy(alpha = 0.25f),
                    spotColor = Color(0xFF667eea).copy(alpha = 0.25f)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "图标预览",
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(24.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(text = "预览图标", color = Color(0xFF667eea), fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            }
        }
    }

    @Composable
    fun HistoryScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { currentPage = Page.MAIN }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = Color(0xFF667eea))
                    }
                    GradientText(text = "历史记录", fontSize = 24.sp)
                }
                TextButton(onClick = { clearAllHistory() }) {
                    Text("一键清空", color = Color(0xFFe53e3e), fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (historyList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    GlassCard {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
                            Icon(
                                Icons.Outlined.History,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = Color(0xFFc7d2fe)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(text = "暂无历史记录", color = Color(0xFF718096), fontSize = 16.sp)
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(20.dp),
                    ambientColor = Color(0xFF667eea).copy(alpha = 0.1f),
                    spotColor = Color(0xFF667eea).copy(alpha = 0.1f)
                )
                .clickable {
                    labelText = TextFieldValue(record.name)
                    urlText = TextFieldValue(record.url)
                    labelError = false
                    urlError = false
                    currentPage = Page.MAIN
                    Toast.makeText(this@MainActivity, "已填充，可直接添加到桌面", Toast.LENGTH_SHORT).show()
                },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.name,
                        fontSize = 16.sp,
                        color = Color(0xFF2d3748),
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = record.url,
                        fontSize = 13.sp,
                        color = Color(0xFF718096),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = formatTime(record.createTime),
                        fontSize = 12.sp,
                        color = Color(0xFFa0aec0)
                    )
                }
                IconButton(onClick = { deleteHistory(record) }) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "删除",
                        tint = Color(0xFFe53e3e)
                    )
                }
            }
        }
    }

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
            val newList = historyList.filterNot { it.name == record.name && it.url == record.url }.toMutableList()
            newList.add(0, record)
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
