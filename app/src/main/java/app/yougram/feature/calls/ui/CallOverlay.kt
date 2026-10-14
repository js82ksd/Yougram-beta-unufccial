package app.yougram.feature.calls.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.yougram.core.settings.GlassSettings
import app.yougram.core.ui.component.FileAvatar
import app.yougram.core.ui.glass.BackdropState
import app.yougram.core.ui.glass.LocalGlass
import app.yougram.core.ui.glass.glass
import app.yougram.feature.calls.data.ActiveCall
import app.yougram.feature.calls.data.AudioRoute
import app.yougram.feature.calls.data.CallManager
import app.yougram.feature.calls.data.CallPhase
import app.yougram.feature.calls.data.TgCallsEngine
import app.yougram.feature.chat.data.ChatRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Полноэкранный экран видео/аудио звонка по Material 3 Expressive. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CallOverlay(manager: CallManager, chats: ChatRepository, backdrop: BackdropState) {
    val context = LocalContext.current
    val call by manager.call.collectAsState()
    val route by manager.route.collectAsState()

    val acceptPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) manager.accept()
        else Toast.makeText(context, "Нужен доступ к микрофону", Toast.LENGTH_SHORT).show()
    }

    val requestPermissions: (Boolean) -> Unit = { withVideo ->
        val needed = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.BLUETOOTH_CONNECT)
            if (withVideo) add(Manifest.permission.CAMERA)
        }
        val allGranted = needed.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (allGranted) {
            manager.accept(withVideo = withVideo)
        } else {
            acceptPermissions.launch(needed.toTypedArray())
        }
    }

    val c = call ?: return

    var elapsed by remember(c.startedAtMillis) { mutableStateOf(0) }
    LaunchedEffect(c.startedAtMillis, c.phase) {
        val start = c.startedAtMillis ?: return@LaunchedEffect
        while (c.phase == CallPhase.ACTIVE) {
            elapsed = ((System.currentTimeMillis() - start) / 1000).toInt()
            delay(500)
        }
    }

    val status = when {
        c.phase == CallPhase.ACTIVE -> "%d:%02d".format(elapsed / 60, elapsed % 60)
        c.phase == CallPhase.RINGING && !c.isOutgoing -> if (c.isVideo) "Входящий видеозвонок" else "Входящий звонок"
        else -> c.message ?: ""
    }

    val base = LocalGlass.current
    val overlayGlass = remember(base) {
        GlassSettings(blurRadius = maxOf(base.blurRadius, 40f), opacity = 0.55f)
    }

    MaterialExpressiveTheme(motionScheme = MotionScheme.expressive()) {
        Box(
            Modifier
                .fillMaxSize()
                .glass(backdrop, overlayGlass, RectangleShape, MaterialTheme.colorScheme.surfaceContainerLowest)
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
        ) {
            // 1. Удалённый видеопоток
            if (c.isVideo && c.phase == CallPhase.ACTIVE) {
                VideoSurface(
                    videoSinkSetter = { sink ->
                        (manager.engine as? TgCallsEngine)?.remoteVideoSink = sink
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                // Если видео выключено или ещё идёт соединение: аватар и имя
                if (!c.isVideo || c.phase != CallPhase.ACTIVE) {
                    Column(
                        Modifier.align(Alignment.TopCenter).padding(top = 56.dp, start = 24.dp, end = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PulsingAvatar(c, chats, pulsing = c.phase == CallPhase.RINGING || c.phase == CallPhase.CONNECTING)
                        Spacer(Modifier.height(12.dp))
                        Text(c.title.ifEmpty { "Звонок" }, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(6.dp))
                        Text(status, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if ((c.phase == CallPhase.ACTIVE || c.phase == CallPhase.CONNECTING) && c.message == null && c.emojis.isNotEmpty()) {
                            Spacer(Modifier.height(24.dp))
                            Text(c.emojis.joinToString("  "), fontSize = 32.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Сверьте эмодзи с собеседником",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // 2. Локальный видеопоток (Превью камеры с drag-прилипанием)
                AnimatedVisibility(
                    visible = c.isVideo && c.phase == CallPhase.ACTIVE,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    val scope = rememberCoroutineScope()
                    val offsetX = remember { Animatable(0f) }
                    val offsetY = remember { Animatable(0f) }

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                            .padding(16.dp)
                            .size(110.dp, 160.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragEnd = {
                                        scope.launch {
                                            offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessLow))
                                            offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessLow))
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        scope.launch {
                                            offsetX.snapTo(offsetX.value + dragAmount.x)
                                            offsetY.snapTo(offsetY.value + dragAmount.y)
                                        }
                                    }
                                )
                            }
                    ) {
                        VideoSurface(
                            videoSinkSetter = { sink ->
                                (manager.engine as? TgCallsEngine)?.localVideoSink = sink
                            },
                            isOverlay = true,
                            mirror = true,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                // 3. Парящая панель управления в стиле Expressive (Surface с скруглённой формой)
                Surface(
                    shape = RoundedCornerShape(36.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 36.dp),
                ) {
                    val incoming = !c.isOutgoing && c.phase == CallPhase.RINGING
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        if (incoming) {
                            Button(
                                onClick = { requestPermissions(true) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            ) {
                                Icon(Icons.Filled.Videocam, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("С видео")
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = { requestPermissions(false) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            ) {
                                Icon(Icons.Filled.Call, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Голос")
                            }
                            Spacer(Modifier.width(8.dp))
                            MorphingCallButton(
                                icon = Icons.Filled.CallEnd,
                                contentDescription = "Отклонить",
                                isActive = true,
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = Color.White,
                                activeContainerColor = MaterialTheme.colorScheme.error,
                                activeContentColor = Color.White,
                                onClick = { manager.hangUp() },
                            )
                        } else {
                            MorphingCallButton(
                                icon = if (c.muted) Icons.Filled.MicOff else Icons.Filled.Mic,
                                contentDescription = "Микрофон",
                                isActive = c.muted,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                activeContainerColor = MaterialTheme.colorScheme.errorContainer,
                                activeContentColor = MaterialTheme.colorScheme.onErrorContainer,
                                onClick = { manager.setMuted(!c.muted) },
                            )

                            Spacer(Modifier.width(8.dp))

                            MorphingCallButton(
                                icon = if (c.isVideo) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                                contentDescription = "Камера",
                                isActive = c.isVideo,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                onClick = { manager.toggleCamera(!c.isVideo) },
                            )

                            if (c.isVideo) {
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    onClick = { manager.switchCamera() },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.size(64.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Cameraswitch, contentDescription = "Сменить камеру")
                                    }
                                }
                            }

                            if (c.phase == CallPhase.ACTIVE) {
                                Spacer(Modifier.width(8.dp))
                                val (routeIcon, routeLabel) = when (route) {
                                    AudioRoute.SPEAKER -> Icons.AutoMirrored.Filled.VolumeUp to "Громкая связь"
                                    AudioRoute.BLUETOOTH -> Icons.Filled.Bluetooth to "Bluetooth"
                                    AudioRoute.EARPIECE -> Icons.Filled.PhoneInTalk to "Динамик"
                                }
                                Surface(
                                    onClick = { manager.cycleRoute() },
                                    shape = CircleShape,
                                    color = if (route == AudioRoute.EARPIECE) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.primary,
                                    contentColor = if (route == AudioRoute.EARPIECE) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(64.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(routeIcon, contentDescription = routeLabel)
                                    }
                                }
                            }

                            Spacer(Modifier.width(8.dp))

                            MorphingCallButton(
                                icon = Icons.Filled.CallEnd,
                                contentDescription = "Завершить",
                                isActive = true,
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = Color.White,
                                activeContainerColor = MaterialTheme.colorScheme.error,
                                activeContentColor = Color.White,
                                onClick = { manager.hangUp() },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PulsingAvatar(c: ActiveCall, chats: ChatRepository, pulsing: Boolean) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "pulseProgress",
    )
    Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
        if (pulsing) {
            Box(
                Modifier
                    .size(128.dp)
                    .scale(1f + progress * 0.6f)
                    .alpha((1f - progress) * 0.35f)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
            WavyCircleRing(
                modifier = Modifier.size(160.dp),
                mainArcColor = MaterialTheme.colorScheme.primary,
                wavySegmentColor = MaterialTheme.colorScheme.tertiaryContainer,
                strokeWidth = 4.dp,
                waveAmplitude = 5.dp,
                animating = true,
            )
        }
        FileAvatar(
            title = c.title,
            fileId = c.avatarFileId,
            fileState = { id -> chats.fileState(id) },
            size = 128.dp,
        )
    }
}
