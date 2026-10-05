package com.example.webshortcut

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import java.io.File
import java.io.FileOutputStream
import java.net.MalformedURLException
import java.net.URL

class MainActivity : ComponentActivity() {

    private var iconBitmap by mutableStateOf<Bitmap?>(null)
    private var labelText by mutableStateOf(TextFieldValue(""))
    private var urlText by mutableStateOf(TextFieldValue(""))
    private var labelError by mutableStateOf(false)
    private var urlError by mutableStateOf(false)

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
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFF3F4F6)
                ) {
                    MainScreen()
                }
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "网页快捷方式生成器",
                        fontSize = 24.sp,
                        color = Color(0xFF111827)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "自定义图标和名称，一键创建网页桌面图标",
                        fontSize = 14.sp,
                        color = Color(0xFF6B7280)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

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

            Spacer(modifier = Modifier.height(24.dp))

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

            Spacer(modifier = Modifier.height(32.dp))

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
        } else {
            Toast.makeText(this, "当前手机不支持添加桌面快捷方式", Toast.LENGTH_LONG).show()
        }
    }
}
