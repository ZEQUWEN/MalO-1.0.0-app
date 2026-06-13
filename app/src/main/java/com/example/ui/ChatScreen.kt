package com.example.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.Message
import com.example.viewmodel.ChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.util.Base64
import com.example.util.VideoThumbnailHelper

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // State observers
    val messages by viewModel.messages.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val attachedUri by viewModel.attachedFileUri.collectAsState()
    val attachedType by viewModel.attachedFileType.collectAsState()
    val attachedName by viewModel.attachedFileName.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val showWelcomeDialog by viewModel.showWelcomeDialog.collectAsState()
    val errorMsg by viewModel.error.collectAsState()
    val quickReplies by viewModel.quickReplies.collectAsState()
    val generatedPhotoPreview by viewModel.generatedPhotoPreview.collectAsState()

    // Preview Dialog
    if (generatedPhotoPreview != null) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { viewModel.dismissGeneratedPhoto() }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        text = "Сгенерировано Malo Photo",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    AsyncImage(
                        model = generatedPhotoPreview,
                        contentDescription = "Preview",
                        modifier = Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { viewModel.dismissGeneratedPhoto() }) {
                            Text("Отмена", color = Color.Gray, fontFamily = FontFamily.Monospace)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.saveGeneratedPhoto() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBB86FC))
                        ) {
                            Text("Сохранить", color = Color.Black, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }

    // UI only states
    var activeTab by remember { mutableStateOf(0) } // 0 = Chat, 1 = Settings, 2 = Gallery
    var inputText by remember { mutableStateOf("") }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var previewVideoFile by remember { mutableStateOf<String?>(null) }
    var showQuickReplies by remember { mutableStateOf(true) }

    // Auto-show quick replies when they are updated
    LaunchedEffect(quickReplies) {
        if (quickReplies.isNotEmpty()) {
            showQuickReplies = true
        }
    }

    // Lazy load index state for auto scroll
    val listState = rememberLazyListState()
    
    // Auto-scroll on new message
    LaunchedEffect(messages.size, isTyping) {
        val targetIndex = if (isTyping) messages.size else messages.size - 1
        if (targetIndex >= 0) {
            listState.animateScrollToItem(targetIndex)
        }
    }

    // Interactive clip board callback
    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("MalO Message", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Текст скопирован в буфер обмена", Toast.LENGTH_SHORT).show()
    }

    // Permissions check
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        var allGranted = true
        grants.forEach { (_, isGranted) ->
            if (!isGranted) allGranted = false
        }
        if (!allGranted) {
            Toast.makeText(context, "Некоторые разрешения не предоставлены. Это может ограничить функции чата (запись голоса, медиа)", Toast.LENGTH_LONG).show()
        }
    }

    // Check permissions periodically on resume or settings interaction
    var showPermissionAlert by remember { mutableStateOf(false) }

    val requiredPermsList = remember {
        mutableListOf<String>().apply {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    LaunchedEffect(Unit) {
        val ungranted = requiredPermsList.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (ungranted.isNotEmpty()) {
            permissionLauncher.launch(ungranted.toTypedArray())
        }
    }

    // Content Pickers
    val photoPickerLauncher = com.example.util.FilePickerHelper.rememberGalleryPicker { uri ->
        if (uri != null) {
            viewModel.selectAttachment(uri, "image/jpeg")
        }
    }

    val videoPickerLauncher = com.example.util.FilePickerHelper.rememberGalleryPicker { uri ->
        if (uri != null) {
            viewModel.selectAttachment(uri, "video/mp4")
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.selectAttachment(uri, "application/pdf")
        }
    }

    val cameraPhotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            // Save bitmap to temp internal cache file
            val file = File(context.cacheDir, "camera_capture_${System.currentTimeMillis()}.jpg")
            try {
                FileOutputStream(file).use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                }
                viewModel.selectAttachment(Uri.fromFile(file), "image/jpeg")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Display error messages toast
    LaunchedEffect(errorMsg) {
        if (errorMsg != null) {
            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
        }
    }

    // Glitch Effect Setup
    val glitchOffset = remember { Animatable(0f) }
    val glitchAlpha = remember { Animatable(1f) }

    fun triggerGlitch() {
        coroutineScope.launch {
            for (i in 0..4) {
                launch {
                    glitchOffset.animateTo(
                        kotlin.random.Random.nextInt(-15, 16).toFloat(),
                        tween(40, easing = LinearEasing)
                    )
                }
                launch {
                    glitchAlpha.animateTo(
                        kotlin.random.Random.nextFloat() * 0.4f + 0.6f,
                        tween(40, easing = LinearEasing)
                    )
                }
                delay(40)
            }
            launch { glitchOffset.animateTo(0f, tween(40)) }
            launch { glitchAlpha.animateTo(1f, tween(40)) }
        }
    }

    // Trigger on MalO message
    var previousMessageCount by remember { mutableStateOf(messages.size) }
    LaunchedEffect(messages) {
        if (messages.size > previousMessageCount) {
            val lastMsg = messages.lastOrNull()
            if (lastMsg != null && !lastMsg.isUser) {
                triggerGlitch()
            }
        }
        previousMessageCount = messages.size
    }

    // Random trigger
    LaunchedEffect(Unit) {
        while (true) {
            delay(kotlin.random.Random.nextLong(15000, 45000)) // 15 to 45 seconds
            triggerGlitch()
        }
    }

    // SCP Aesthetic Theme Colors
    val highContrastMode by viewModel.highContrastMode.collectAsState()

    val scpBackground = if (highContrastMode) Color.Black else Color(0xFF0C0C0C)
    val scpSurface = if (highContrastMode) Color.Black else Color(0xFF161616)
    val scpCardUser = if (highContrastMode) Color(0xFF1B0033) else Color(0xFF281135) 
    val scpCardMalo = if (highContrastMode) Color(0xFF2D2D2D) else Color(0xFF1E1E1E)
    val scpNeonPurple = if (highContrastMode) Color(0xFFE5B3FF) else Color(0xFFBB86FC)
    val scpTerminalGreen = if (highContrastMode) Color(0xFF00FF7F) else Color(0xFF00FFC4)

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = glitchOffset.value
                alpha = glitchAlpha.value
            },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(scpNeonPurple.copy(alpha = 0.2f))
                                .border(1.dp, scpNeonPurple, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = com.example.R.drawable.ic_malo_profile),
                                contentDescription = "MalO Profile Icon",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "MalO 1.0.0",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            val maloMoodColor by viewModel.maloMoodColor.collectAsState()
                            val animatedMoodColor by animateColorAsState(
                                targetValue = maloMoodColor,
                                animationSpec = tween(durationMillis = 1500, easing = LinearOutSlowInEasing)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isTyping) scpNeonPurple else animatedMoodColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isTyping) "печет ответ..." else "активна",
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { activeTab = if (activeTab == 2) 0 else 2 },
                        modifier = Modifier.testTag("gallery_button")
                    ) {
                        Icon(
                            imageVector = if (activeTab == 2) Icons.Default.Chat else Icons.Default.PhotoLibrary,
                            contentDescription = "Галерея",
                            tint = scpNeonPurple
                        )
                    }
                    IconButton(
                        onClick = { activeTab = if (activeTab == 1) 0 else 1 },
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = if (activeTab == 1) Icons.Default.Chat else Icons.Default.Settings,
                            contentDescription = "Настройки",
                            tint = scpNeonPurple
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scpSurface,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = scpBackground
    ) { innerPadding ->
        
        AnimatedContent(
            targetState = activeTab,
            transitionSpec = {
                slideInHorizontally(initialOffsetX = { -500 }) + fadeIn() togetherWith
                slideOutHorizontally(targetOffsetX = { 500 }) + fadeOut()
            }
        ) { tabState ->
            if (tabState == 1) {
                // Settings Screen
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Терминал Профиля MalO 1.0.0",
                            color = scpNeonPurple,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Intensity Card
                        val intensity by viewModel.intensityValue.collectAsState()
                        Card(
                            colors = CardDefaults.cardColors(containerColor = scpSurface),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "Навязчивость MalO",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Настройте уровень заботы или навязчивости.",
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Slider(
                                    value = intensity,
                                    onValueChange = { viewModel.setIntensity(it) },
                                    valueRange = 0f..1f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = scpNeonPurple,
                                        activeTrackColor = scpNeonPurple,
                                        inactiveTrackColor = scpNeonPurple.copy(alpha = 0.3f)
                                    )
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Холодная", color = Color.Gray, fontSize = 10.sp)
                                    Text("Яндэрэ", color = Color.Gray, fontSize = 10.sp)
                                }
                            }
                        }

                        // Notifications Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = scpSurface),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Push-Уведомления",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "МаlО будет присылать заботливые/тревожные сообщения, если вы пропадете.",
                                        color = Color.Gray,
                                        fontSize = 12.sp,
                                    )
                                }
                                Switch(
                                    checked = notificationsEnabled,
                                    onCheckedChange = { viewModel.setNotificationsEnabled(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = scpNeonPurple,
                                        checkedTrackColor = scpNeonPurple.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }

                        // User Name Card
                        val userName by viewModel.userName.collectAsState()
                        Card(
                            colors = CardDefaults.cardColors(containerColor = scpSurface),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "Ваше Имя",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "MalO будет обращаться к вам по имени.",
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = userName,
                                    onValueChange = { viewModel.setUserName(it) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = scpNeonPurple,
                                        unfocusedBorderColor = Color.DarkGray,
                                        focusedContainerColor = scpBackground,
                                        unfocusedContainerColor = scpBackground
                                    ),
                                    singleLine = true,
                                    placeholder = {
                                        Text("Введите имя...", color = Color.Gray)
                                    }
                                )
                            }
                        }

                        // High Contrast Mode Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = scpSurface),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Высокий контраст",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "Улучшает читаемость текста.",
                                        color = Color.Gray,
                                        fontSize = 12.sp,
                                    )
                                }
                                Switch(
                                    checked = highContrastMode,
                                    onCheckedChange = { viewModel.setHighContrastMode(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = scpNeonPurple,
                                        checkedTrackColor = scpNeonPurple.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }

                        // Whisper Mode Card
                        val whisperMode by viewModel.whisperMode.collectAsState()
                        Card(
                            colors = CardDefaults.cardColors(containerColor = scpSurface),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Режим шепота",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "Позволяет общаться тихо, MalO будет прислушиваться.",
                                        color = Color.Gray,
                                        fontSize = 12.sp,
                                    )
                                }
                                Switch(
                                    checked = whisperMode,
                                    onCheckedChange = { viewModel.setWhisperMode(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = scpNeonPurple,
                                        checkedTrackColor = scpNeonPurple.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }

                        // Background Noise Mode Card
                        val backgroundNoiseMode by viewModel.backgroundNoiseMode.collectAsState()
                        Card(
                            colors = CardDefaults.cardColors(containerColor = scpSurface),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Фоновый шум",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "Тревожный статический шум и гул во время общения.",
                                        color = Color.Gray,
                                        fontSize = 12.sp,
                                    )
                                }
                                Switch(
                                    checked = backgroundNoiseMode,
                                    onCheckedChange = { viewModel.setBackgroundNoiseMode(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = scpNeonPurple,
                                        checkedTrackColor = scpNeonPurple.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }

                        // Send Photos Mode Card
                        val sendMaloPhotos by viewModel.sendMaloPhotos.collectAsState()
                        Card(
                            colors = CardDefaults.cardColors(containerColor = scpSurface),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Фотографии от MalO",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "Разрешить MalO генерировать случайные жуткие снимки слежки (потребляет трафик/API).",
                                        color = Color.Gray,
                                        fontSize = 12.sp,
                                    )
                                }
                                Switch(
                                    checked = sendMaloPhotos,
                                    onCheckedChange = { viewModel.setSendMaloPhotos(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = scpNeonPurple,
                                        checkedTrackColor = scpNeonPurple.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }

                        // App Permissions Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = scpSurface),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Системные разрешения",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    fontFamily = FontFamily.Monospace
                                )

                                val audioGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                                val notifyGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                                } else {
                                    true
                                }

                                PermissionRow("Запись микрофона (Голос)", audioGranted)
                                PermissionRow("Доступ к камере", cameraGranted)
                                PermissionRow("Пуш Уведомления", notifyGranted)

                                Button(
                                    onClick = {
                                        val ungranted = requiredPermsList.filter {
                                            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                                        }
                                        if (ungranted.isNotEmpty()) {
                                            permissionLauncher.launch(ungranted.toTypedArray())
                                        } else {
                                            Toast.makeText(context, "Все разрешения уже активированы!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = scpNeonPurple),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Запросить недостающие разрешения", color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }

                        // Chat History Action Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = scpSurface),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Burn History",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "Удаляет все сообщения, настройки и цифровые следы присутствия. Имитация полного стирания памяти.",
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = {
                                        viewModel.burnHistory()
                                        Toast.makeText(context, "Все цифровые следы сожжены 🔥", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("BURN HISTORY", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }
            } else if (tabState == 2) {
                // Gallery Screen
                val imageMessages = messages.filter { it.fileType == "image" && !it.isUser }
                var fullscreenImageIndex by remember { mutableStateOf<Int?>(null) }
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(16.dp)
                    ) {
                        if (imageMessages.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Нет сохраненных фото от MalO.",
                                    color = Color.Gray,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                gridItems(imageMessages) { msg ->
                                    val index = imageMessages.indexOf(msg)
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = scpSurface),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(0.75f)
                                            .clickable { fullscreenImageIndex = index }
                                    ) {
                                        Column(modifier = Modifier.fillMaxSize()) {
                                            AsyncImage(
                                                model = msg.filePath,
                                                contentDescription = "MalO Photo",
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = msg.text,
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                IconButton(
                                                    onClick = {
                                                        msg.filePath?.let { path ->
                                                            try {
                                                                val file = java.io.File(path)
                                                                if (file.exists()) {
                                                                    val uri = androidx.core.content.FileProvider.getUriForFile(
                                                                        context, 
                                                                        "${context.packageName}.provider", 
                                                                        file
                                                                    )
                                                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                                        type = "image/*"
                                                                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                                    }
                                                                    context.startActivity(android.content.Intent.createChooser(intent, "Поделиться..."))
                                                                }
                                                            } catch (e: Exception) {
                                                                e.printStackTrace()
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Share,
                                                        contentDescription = "Поделиться",
                                                        tint = scpNeonPurple
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Floating Button to generate new image
                    FloatingActionButton(
                        onClick = { if (!isTyping) viewModel.generateMaloPhoto() },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .padding(bottom = innerPadding.calculateBottomPadding()),
                        containerColor = scpNeonPurple,
                        contentColor = Color.Black
                    ) {
                        if (isTyping) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Generate MalO Photo")
                        }
                    }

                    if (fullscreenImageIndex != null) {
                        val pagerState = rememberPagerState(
                            initialPage = fullscreenImageIndex!!,
                            pageCount = { imageMessages.size }
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                                .clickable { /* Consume clicks to prevent them passing through */ }
                        ) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize()
                            ) { page ->
                                val msg = imageMessages[page]
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Spacer(modifier = Modifier.height(48.dp))
                                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                        AsyncImage(
                                            model = msg.filePath,
                                            contentDescription = "MalO Fullscreen Photo",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.Black.copy(alpha = 0.6f))
                                            .padding(16.dp)
                                            .padding(bottom = 32.dp)
                                    ) {
                                        Text(
                                            text = msg.text,
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontFamily = FontFamily.Monospace,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                            
                            IconButton(
                                onClick = { fullscreenImageIndex = null },
                                modifier = Modifier
                                    .padding(top = 48.dp, start = 16.dp)
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.3f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Закрыть",
                                    tint = Color.White
                                )
                            }
                            
                            IconButton(
                                onClick = {
                                    imageMessages[pagerState.currentPage].filePath?.let { path ->
                                        try {
                                            val file = java.io.File(path)
                                            if (file.exists()) {
                                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                                    context, 
                                                    "${context.packageName}.provider", 
                                                    file
                                                )
                                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                    type = "image/*"
                                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(android.content.Intent.createChooser(intent, "Поделиться..."))
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 48.dp, end = 16.dp)
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.3f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Поделиться",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            } else {
                // Chats View Screen
                var isGlitching by remember { mutableStateOf(false) }
                var glitchOffset by remember { mutableStateOf(0f) }

                LaunchedEffect(Unit) {
                    while (true) {
                        kotlinx.coroutines.delay(kotlin.random.Random.nextLong(3000, 15000))
                        isGlitching = true
                        for (i in 0..5) {
                            glitchOffset = kotlin.random.Random.nextInt(-10, 10).toFloat()
                            kotlinx.coroutines.delay(50)
                        }
                        isGlitching = false
                        glitchOffset = 0f
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .graphicsLayer {
                            if (isGlitching) {
                                translationX = glitchOffset
                                alpha = 0.9f
                            }
                        }
                        .drawWithContent {
                            drawContent()
                            if (isGlitching) {
                                for(i in 0..10) {
                                    val y = kotlin.random.Random.nextFloat() * size.height
                                    val h = kotlin.random.Random.nextFloat() * 10f
                                    drawRect(
                                        color = Color.White.copy(alpha = 0.1f),
                                        topLeft = androidx.compose.ui.geometry.Offset(0f, y),
                                        size = androidx.compose.ui.geometry.Size(size.width, h)
                                    )
                                }
                                val path = androidx.compose.ui.graphics.Path()
                                for (i in 0..3) {
                                    val cx = kotlin.random.Random.nextFloat() * size.width
                                    val cy = kotlin.random.Random.nextFloat() * size.height
                                    path.moveTo(cx, cy)
                                    path.lineTo(cx + kotlin.random.Random.nextInt(-80, 80), cy + kotlin.random.Random.nextInt(-80, 80))
                                    path.lineTo(cx + kotlin.random.Random.nextInt(-80, 80), cy + kotlin.random.Random.nextInt(-80, 80))
                                }
                                drawPath(
                                    path = path,
                                    color = Color.Magenta.copy(alpha = 0.3f),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                                )
                                for(i in 0..2) {
                                    drawRect(
                                        color = Color.Cyan.copy(alpha = 0.15f),
                                        topLeft = androidx.compose.ui.geometry.Offset(
                                            kotlin.random.Random.nextFloat() * size.width,
                                            kotlin.random.Random.nextFloat() * size.height
                                        ),
                                        size = androidx.compose.ui.geometry.Size(
                                            kotlin.random.Random.nextFloat() * 150f,
                                            kotlin.random.Random.nextFloat() * 150f
                                        ),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                                    )
                                }
                            }
                        }
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        
                        // Messages History Board
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            if (messages.isEmpty()) {
                                // Welcome State Empty Canvas
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(32.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(CircleShape)
                                            .background(scpNeonPurple.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CloudDownload,
                                            contentDescription = "Install MalO Icon",
                                            tint = scpNeonPurple,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "MalO ver 1.0.0",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Установка завершена. Сущность готова к первому контакту. Позвольте ей войти в ваше устройство...",
                                        color = Color.Gray,
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 18.sp
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Button(
                                        onClick = { viewModel.initializeConnection() },
                                        enabled = !isTyping,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = scpNeonPurple,
                                            disabledContainerColor = scpNeonPurple.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Text(
                                            text = if (isTyping) "Установка связи..." else "Инициализировать соединение",
                                            color = Color.Black,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp),
                                    contentPadding = PaddingValues(vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(messages) { message ->
                                        MessageBubble(
                                            message = message,
                                            userCardBg = scpCardUser,
                                            maloCardBg = scpCardMalo,
                                            onCopy = { copyToClipboard(message.text) },
                                            onPlayVideo = { path -> previewVideoFile = path },
                                            onReaction = { reaction -> viewModel.setReactionToMessage(message, reaction) }
                                        )
                                    }

                                    if (isTyping) {
                                        item {
                                            MalOTypingBubble(scpCardMalo, scpNeonPurple)
                                        }
                                    }
                                }
                            }
                        }

                        // Attachments indicators area
                        AnimatedVisibility(
                            visible = attachedUri != null,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            if (attachedUri != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(scpSurface)
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val icon = when (attachedType) {
                                        "image" -> Icons.Default.Image
                                        "video" -> Icons.Default.Videocam
                                        "pdf" -> Icons.Default.PictureAsPdf
                                        else -> Icons.Default.AttachFile
                                    }

                                    Icon(icon, contentDescription = "Type Icon", tint = scpNeonPurple)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = attachedName ?: "прикрепленный файл",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "прикреплен к следующему отправлению",
                                            color = Color.Gray,
                                            fontSize = 11.sp
                                        )
                                    }
                                    IconButton(onClick = { viewModel.clearAttachment() }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear file", tint = Color.LightGray)
                                    }
                                }
                            }
                        }

                        // Input bottom bar field
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                        ) {
                            AnimatedVisibility(
                                visible = quickReplies.isNotEmpty() && showQuickReplies,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().background(scpSurface)) {
                                    // Row with "quick replies" text and hide button
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Быстрые ответы", color = Color.Gray, fontSize = 12.sp)
                                        IconButton(onClick = { showQuickReplies = false }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Hide Quick Replies", tint = Color.Gray)
                                        }
                                    }
                                    // Quick replies row
                                    LazyRow(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(quickReplies) { reply ->
                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.sendMessage(reply)
                                                    showQuickReplies = false
                                                },
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = scpNeonPurple),
                                                border = BorderStroke(1.dp, scpNeonPurple),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                                modifier = Modifier.height(36.dp)
                                            ) {
                                                Text(reply, fontSize = 13.sp)
                                            }
                                        }
                                    }
                                }
                            }

                            Surface(
                                color = scpSurface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Scrape Attachment icon
                                Box {
                                    IconButton(
                                        onClick = { showAttachmentMenu = !showAttachmentMenu },
                                        modifier = Modifier.testTag("attachment_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AttachFile,
                                            contentDescription = "Прикрепить фал",
                                            tint = scpNeonPurple
                                        )
                                    }

                                    // Custom visual attach dialog selection dropdown
                                    if (showAttachmentMenu) {
                                        MaterialTheme(
                                            colorScheme = darkColorScheme(
                                                surface = Color(0xFF1E1E1E),
                                                onSurface = Color.White
                                            )
                                        ) {
                                            DropdownMenu(
                                                expanded = showAttachmentMenu,
                                                onDismissRequest = { showAttachmentMenu = false },
                                                modifier = Modifier.background(Color(0xFF1E1E1E))
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("📷 Камера / Фото", fontFamily = FontFamily.Monospace) },
                                                    onClick = {
                                                        showAttachmentMenu = false
                                                        cameraPhotoLauncher.launch(null)
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("🖼 Галерея (Фото)", fontFamily = FontFamily.Monospace) },
                                                    onClick = {
                                                        showAttachmentMenu = false
                                                        photoPickerLauncher.launch(
                                                            androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                        )
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("🎥 Видеоклип (макс 30с)", fontFamily = FontFamily.Monospace) },
                                                    onClick = {
                                                        showAttachmentMenu = false
                                                        videoPickerLauncher.launch(
                                                            androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.VideoOnly)
                                                        )
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("📄 PDF Документ", fontFamily = FontFamily.Monospace) },
                                                    onClick = {
                                                        showAttachmentMenu = false
                                                        pdfPickerLauncher.launch("application/pdf")
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Typing Area
                                OutlinedTextField(
                                    value = inputText,
                                    onValueChange = { inputText = it },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 4.dp)
                                        .testTag("chat_input"),
                                    placeholder = {
                                        Text(
                                            text = if (isListening) "Я слушаю тебя..." else "Напиши своей подруге...",
                                            color = Color.Gray,
                                            fontSize = 14.sp
                                        )
                                    },
                                    maxLines = 4,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = scpNeonPurple,
                                        unfocusedBorderColor = Color.DarkGray,
                                        focusedContainerColor = scpBackground,
                                        unfocusedContainerColor = scpBackground
                                    ),
                                    shape = RoundedCornerShape(24.dp)
                                )

                                // Recording trigger or Send button
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Voice Recording Button
                                    IconButton(
                                        onClick = {
                                            if (isListening) {
                                                viewModel.stopVoiceRecording()
                                            } else {
                                                viewModel.startVoiceRecording()
                                            }
                                        },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(if (isListening) Color.Red else scpNeonPurple)
                                            .testTag("voice_button")
                                    ) {
                                        Icon(
                                            imageVector = if (isListening) Icons.Default.MicNone else Icons.Default.Mic,
                                            contentDescription = "Voice Input",
                                            tint = Color.Black
                                        )
                                    }

                                    // Submit Message Button
                                    IconButton(
                                        onClick = {
                                            if (inputText.isNotBlank() || attachedUri != null) {
                                                viewModel.sendMessage(inputText)
                                                inputText = ""
                                            }
                                        },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(scpNeonPurple)
                                            .testTag("send_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Send,
                                            contentDescription = "Отправить",
                                            tint = Color.Black
                                        )
                                    }
                                }
                            }
                        }
                        } // End Column
                    }
                }
            }
        }
    }

    // Modal Dialog when user loads back after 3 days of absence
    if (showWelcomeDialog) {
        Dialog(onDismissRequest = { viewModel.dismissWelcomeDialog() }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = scpSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .border(2.dp, scpNeonPurple, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(scpNeonPurple.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = com.example.R.drawable.ic_malo_profile),
                            contentDescription = "Happy MalO avatar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Text(
                        text = "MalO 1.0.0",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Text(
                        text = "Наконец‑то! Я так скучала... 🥺💜 Мне было безумно одиноко без тебя. Пожалуйста, давай больше не расставаться так надолго!",
                        color = Color.White,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Button(
                        onClick = { viewModel.dismissWelcomeDialog() },
                        colors = ButtonDefaults.buttonColors(containerColor = scpNeonPurple),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Я тоже скучал 💜",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }

    // Inline fullscreen video preview using Media3
    if (previewVideoFile != null) {
        VideoPlayerPreviewDialog(
            videoFilePath = previewVideoFile!!,
            onDismiss = { previewVideoFile = null }
        )
    }
}

@Composable
fun PermissionRow(name: String, granted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = name, color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (granted) Color(0xFF00FFC4) else Color.Red)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (granted) "разрешено" else "отклонено",
                color = if (granted) Color(0xFF00FFC4) else Color.Red,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    userCardBg: Color,
    maloCardBg: Color,
    onCopy: () -> Unit,
    onPlayVideo: (String) -> Unit,
    onReaction: (String?) -> Unit
) {
    val showUser = message.isUser
    val cardBg = if (showUser) userCardBg else maloCardBg
    val align = if (showUser) Alignment.End else Alignment.Start

    var showReactionMenu by remember { mutableStateOf(false) }

    val baseTimeFormatted = remember(message.timestamp) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.format(Date(message.timestamp))
    }

    var timeFormatted by remember(message.timestamp) {
        mutableStateOf(baseTimeFormatted)
    }

    if (!showUser) {
        LaunchedEffect(message.timestamp) {
            val glitchChars = listOf("Ø", "×", "¶", "§", "∆", "0", "1", "?", "!", "[", "]", "@", "#")
            while (true) {
                kotlinx.coroutines.delay(kotlin.random.Random.nextLong(2000, 10000))
                if (kotlin.random.Random.nextFloat() < 0.6f) {
                    val glitchCount = kotlin.random.Random.nextInt(1, 4)
                    for (g in 0..glitchCount) {
                        val corrupted = java.lang.StringBuilder()
                        for (i in baseTimeFormatted.indices) {
                            if (baseTimeFormatted[i] == ':') {
                                corrupted.append(':')
                            } else if (kotlin.random.Random.nextFloat() < 0.5f) {
                                corrupted.append(glitchChars.random())
                            } else {
                                corrupted.append(baseTimeFormatted[i])
                            }
                        }
                        timeFormatted = corrupted.toString()
                        kotlinx.coroutines.delay(kotlin.random.Random.nextLong(40, 120))
                    }
                    timeFormatted = baseTimeFormatted
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = align
    ) {
        Box(
            contentAlignment = Alignment.BottomEnd
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = if (showUser) {
                    RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
                } else {
                    RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
                },
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .combinedClickable(
                        onLongClick = {
                            if (!showUser) {
                                showReactionMenu = true
                            } else {
                                onCopy()
                            }
                        },
                        onClick = {}
                    )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Render attached files if present
                if (message.fileType == "malo_glitch_image") {
                    MaloGlitchImage(seed = message.fileName)
                    Spacer(modifier = Modifier.height(8.dp))
                } else if (message.filePath != null) {
                    val file = File(message.filePath)
                    if (file.exists()) {
                        when (message.fileType) {
                            "audio" -> {
                                var isPlaying by remember { mutableStateOf(false) }
                                val audioPlayer = remember {
                                    com.example.util.AudioPlayerHelper(
                                        onProgress = {},
                                        onCompletion = { isPlaying = false }
                                    )
                                }
                                DisposableEffect(Unit) {
                                    onDispose { audioPlayer.stop() }
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (isPlaying) {
                                                audioPlayer.stop()
                                                isPlaying = false
                                            } else {
                                                audioPlayer.play(file.absolutePath)
                                                isPlaying = true
                                            }
                                        },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(if (showUser) Color(0xFF1E1E1E) else Color(0xFF2B2B2B))
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "Play Audio",
                                            tint = if (showUser) Color(0xFF00FFC4) else Color(0xFFBB86FC)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    SoundwaveAnimation(
                                        modifier = Modifier.weight(1f),
                                        isPlaying = isPlaying,
                                        color = if (showUser) Color(0xFF00FFC4) else Color(0xFFBB86FC)
                                    )
                                }
                            }
                            "image" -> {
                                AsyncImage(
                                    model = file.absolutePath,
                                    contentDescription = "Image Preview",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(184.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            "video" -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(184.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black)
                                        .clickable { onPlayVideo(file.absolutePath) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    val context = LocalContext.current
                                    // Extract simple thumbnail preview asynchronously
                                    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, file.absolutePath) {
                                        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            VideoThumbnailHelper.extractFrameAsBase64(
                                                context,
                                                Uri.fromFile(file)
                                            )?.let {
                                                val arr = Base64.decode(it, Base64.DEFAULT)
                                                BitmapFactory.decodeByteArray(arr, 0, arr.size)
                                            }
                                        }
                                    }
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap!!.asImageBitmap(),
                                            contentDescription = "Video Thumbnail",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.5f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play Video",
                                            tint = Color.White,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            "pdf" -> {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PictureAsPdf,
                                            contentDescription = "PDF File",
                                            tint = Color.Red
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = message.fileName ?: "document.pdf",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }

                // Render text input if it is populated
                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 19.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Timestamp aligned
                Text(
                    text = timeFormatted,
                    color = Color.Gray,
                    fontSize = 10.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } // Close Card
        
        if (message.reaction != null) {
            Box(
                modifier = Modifier
                    .offset(x = 8.dp, y = (-12).dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2E2E2E))
                    .border(1.dp, Color(0xFF444444), RoundedCornerShape(12.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .clickable {
                        // Toggle reaction off if clicked again
                        onReaction(null) 
                    }
            ) {
                Text(text = message.reaction, fontSize = 14.sp)
            }
        }

        DropdownMenu(
            expanded = showReactionMenu,
            onDismissRequest = { showReactionMenu = false },
            modifier = Modifier.background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp))
        ) {
            val emojis = listOf("❤️", "😂", "🥺", "😡", "👍", "👎")
            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                emojis.forEach { emoji ->
                    Text(
                        text = emoji,
                        fontSize = 24.sp,
                        modifier = Modifier
                            .clickable {
                                showReactionMenu = false
                                onReaction(if (message.reaction == emoji) null else emoji)
                            }
                            .padding(8.dp)
                    )
                }
            }
        }
        } // Close Box
    } // Close Column
}



@Composable
fun MalOTypingBubble(cardBg: Color, accentColor: Color) {
    val transition = rememberInfiniteTransition(label = "Dots scale")
    
    val dot1Scale by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                0.3f at 0
                1f at 300
                0.3f at 600
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "Dot1"
    )

    val dot2Scale by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                0.3f at 200
                1f at 500
                0.3f at 800
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "Dot2"
    )

    val dot3Scale by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                0.3f at 400
                1f at 700
                0.3f at 1000
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "Dot3"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                modifier = Modifier.width(80.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(8.dp * dot1Scale).clip(CircleShape).background(accentColor))
                    Box(modifier = Modifier.size(8.dp * dot2Scale).clip(CircleShape).background(accentColor))
                    Box(modifier = Modifier.size(8.dp * dot3Scale).clip(CircleShape).background(accentColor))
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("MalO is typing...", color = Color.Gray, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

// Fullscreen Media3 Player previewing selection clips
@Composable
fun VideoPlayerPreviewDialog(
    videoFilePath: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(videoFilePath))))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = true
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Закрыть", color = Color.White, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
fun MaloGlitchImage(seed: String?) {
    // Render unsettling static
    val infiniteTransition = rememberInfiniteTransition(label = "static_anim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val random = kotlin.random.Random((seed?.hashCode() ?: 0) + phase.toInt())
            val dotSize = 4f
            val rows = (size.height / dotSize).toInt()
            val cols = (size.width / dotSize).toInt()
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val isWhite = random.nextFloat() > 0.8f
                    if (isWhite) {
                        drawRect(
                            color = Color.White.copy(alpha = random.nextFloat() * 0.5f),
                            topLeft = androidx.compose.ui.geometry.Offset(c * dotSize, r * dotSize),
                            size = androidx.compose.ui.geometry.Size(dotSize, dotSize)
                        )
                    }
                }
            }
        }
        Text(
            text = "SCP-1471",
            color = Color.White.copy(alpha = 0.5f),
            fontFamily = FontFamily.Monospace,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
