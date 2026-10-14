package app.yougram.feature.calls.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dev.g000sha256.tdl.dto.CallProtocol
import dev.g000sha256.tdl.dto.CallServerTypeTelegramReflector
import dev.g000sha256.tdl.dto.CallServerTypeWebrtc
import dev.g000sha256.tdl.dto.CallStateReady
import org.telegram.messenger.voip.Instance
import org.telegram.messenger.voip.NativeInstance
import org.telegram.messenger.voip.VideoCapturerDevice
import org.webrtc.Camera2Capturer
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.CapturerObserver
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import java.io.File
import java.util.concurrent.Executors

/**
 * Медиа-движок 1:1 звонков на родном tgcalls из Telegram-Android.
 * Сигнализацию и ключ даёт TDLib.
 */
class TgCallsEngine(context: Context) : CallEngine {

    private val app = context.applicationContext

    private class Session {
        val lock = Any()
        val pending = ArrayDeque<ByteArray>()

        @Volatile var instance: NativeInstance? = null
        @Volatile var active = false

        /** Только с рабочего потока. */
        fun release() {
            val i = instance ?: return
            instance = null
            synchronized(lock) { active = false; pending.clear() }
            runCatching { i.stop() }
        }
    }

    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread(r, "tgcalls-worker").apply { isDaemon = true }
    }

    @Volatile private var session: Session? = null
    @Volatile private var muted = false
    @Volatile private var cameraCapturer: CameraVideoCapturer? = null
    @Volatile private var surfaceTextureHelper: SurfaceTextureHelper? = null

    @Volatile var remoteVideoSink: VideoSink? = null
    @Volatile var localVideoSink: VideoSink? = null

    /** Пакеты, пришедшие раньше, чем стартовал движок. */
    private val earlyLock = Any()
    private val early = ArrayDeque<ByteArray>()

    override val isAvailable: Boolean = true
    override val supportsVideo: Boolean = true

    override val protocol: CallProtocol?
        get() = runCatching {
            CallProtocol(
                udpP2p = true,
                udpReflector = true,
                minLayer = 65,
                maxLayer = 92,
                libraryVersions = NativeInstance.getAllVersions(),
            )
        }.getOrNull()

    override fun start(
        userId: Long,
        state: CallStateReady,
        isOutgoing: Boolean,
        isVideo: Boolean,
        sendSignaling: (ByteArray) -> Unit,
        onLink: (EngineLink) -> Unit,
    ) {
        val s = Session()
        synchronized(earlyLock) {
            synchronized(s.lock) { while (early.isNotEmpty()) s.pending.addLast(early.removeFirst()) }
        }
        val previous = session
        session = s
        CallLog.d(TAG, "start user=$userId outgoing=$isOutgoing isVideo=$isVideo")
        worker.execute {
            previous?.release()
            if (session === s) connect(s, state, isOutgoing, isVideo, sendSignaling, onLink) else s.release()
        }
    }

    private fun connect(
        s: Session,
        state: CallStateReady,
        isOutgoing: Boolean,
        isVideo: Boolean,
        sendSignaling: (ByteArray) -> Unit,
        onLink: (EngineLink) -> Unit,
    ) {
        try {
            initWebrtcContext()

            if (isVideo) {
                setupCamera(isFront = true)
            }

            val reflectorIds = state.servers
                .filter { it.type is CallServerTypeTelegramReflector }
                .map { it.id }.sorted()
            val endpoints = state.servers.map { server ->
                when (val type = server.type) {
                    is CallServerTypeTelegramReflector -> Instance.Endpoint(
                        false, server.id, server.ipAddress, server.ipv6Address, server.port,
                        if (type.isTcp) Instance.ENDPOINT_TYPE_TCP_RELAY else Instance.ENDPOINT_TYPE_UDP_RELAY,
                        type.peerTag, false, false, null, null, type.isTcp,
                    ).also { it.reflectorId = reflectorIds.indexOf(server.id) + 1 }
                    is CallServerTypeWebrtc -> Instance.Endpoint(
                        true, server.id, server.ipAddress, server.ipv6Address, server.port,
                        Instance.ENDPOINT_TYPE_UDP_RELAY,
                        null, type.supportsTurn, type.supportsStun, type.username, type.password, false,
                    )
                    else -> error("Неизвестный тип сервера звонка")
                }
            }.toTypedArray()

            val logDir = File(app.cacheDir, "voip").apply { mkdirs() }
            val config = Instance.Config(
                30.0, 10.0, Instance.DATA_SAVING_NEVER,
                state.allowP2p,
                true, true, true, isVideo, false,
                File(logDir, "call.log").absolutePath,
                File(logDir, "call_stats.log").absolutePath,
                state.protocol.maxLayer,
                state.customParameters,
            )
            val version = state.protocol.libraryVersions.firstOrNull()
                ?: NativeInstance.getAllVersions().last()
            val persistent = File(app.cacheDir, "voip_persistent_state.json").absolutePath
            val metrics = app.resources.displayMetrics
            val aspect = minOf(metrics.widthPixels, metrics.heightPixels).toFloat() /
                maxOf(metrics.widthPixels, metrics.heightPixels)

            val instance = NativeInstance.make(
                version, config, persistent, endpoints, null, networkType(),
                Instance.EncryptionKey(state.encryptionKey, isOutgoing), aspect,
            )
            s.instance = instance

            instance.setOnStateUpdatedListener { code ->
                val link = when (code) {
                    Instance.STATE_ESTABLISHED -> EngineLink.CONNECTED
                    Instance.STATE_FAILED -> EngineLink.FAILED
                    else -> EngineLink.CONNECTING
                }
                CallLog.d(TAG, "state=$code -> $link")
                if (session === s) onLink(link)
            }
            instance.setOnSignalDataListener { data -> if (session === s) sendSignaling(data) }
            instance.setMuteMicrophone(muted)

            synchronized(s.lock) {
                if (session !== s) return@synchronized
                while (s.pending.isNotEmpty()) {
                    val data = s.pending.removeFirst()
                    runCatching { instance.onSignalingDataReceive(data) }
                        .onFailure { CallLog.e(TAG, "signaling flush", it) }
                }
                s.active = true
            }
            if (session !== s) s.release()
        } catch (e: Throwable) {
            CallLog.e(TAG, "start failed", e)
            s.release()
            if (session === s) onLink(EngineLink.FAILED)
        }
    }

    fun setupCamera(isFront: Boolean) {
        runCatching {
            val enumerator = Camera2Enumerator(app)
            val deviceNames = enumerator.deviceNames
            val selectedDevice = deviceNames.firstOrNull { enumerator.isFrontFacing(it) == isFront }
                ?: deviceNames.firstOrNull() ?: return

            cameraCapturer?.stopCapture()
            cameraCapturer?.dispose()

            val eglContext = VideoCapturerDevice.getSharedEGLContext()
            surfaceTextureHelper = SurfaceTextureHelper.create("TgCameraThread", eglContext)
            val capturer = Camera2Capturer(app, selectedDevice, null)
            cameraCapturer = capturer

            capturer.initialize(surfaceTextureHelper, app, object : CapturerObserver {
                override fun onCapturerStarted(success: Boolean) {}
                override fun onCapturerStopped() {}
                override fun onFrameCaptured(frame: VideoFrame) {
                    localVideoSink?.onFrame(frame)
                }
            })
            capturer.startCapture(1280, 720, 30)
        }.onFailure { CallLog.e(TAG, "setupCamera failed", it) }
    }

    fun switchCamera() {
        cameraCapturer?.switchCamera(null)
    }

    fun stopCamera() {
        runCatching {
            cameraCapturer?.stopCapture()
            cameraCapturer?.dispose()
            cameraCapturer = null
            surfaceTextureHelper?.dispose()
            surfaceTextureHelper = null
        }
    }

    override fun onSignalingData(data: ByteArray) {
        val s = session
        if (s == null) {
            synchronized(earlyLock) { if (early.size < MAX_PENDING) early.addLast(data) }
            return
        }
        val instance = synchronized(s.lock) {
            if (s.active) {
                s.instance
            } else {
                if (s.pending.size < MAX_PENDING) s.pending.addLast(data)
                null
            }
        } ?: return
        runCatching { instance.onSignalingDataReceive(data) }.onFailure { CallLog.e(TAG, "signaling", it) }
    }

    override fun setMuted(muted: Boolean) {
        this.muted = muted
        val s = session ?: return
        val instance = s.instance ?: return
        if (!s.active) return
        runCatching { instance.setMuteMicrophone(muted) }
    }

    override fun stop() {
        CallLog.d(TAG, "stop")
        stopCamera()
        val s = session
        session = null
        muted = false
        synchronized(earlyLock) { early.clear() }
        if (s != null) worker.execute { s.release() }
    }

    private fun initWebrtcContext() {
        runCatching {
            Class.forName("org.webrtc.ContextUtils")
                .getMethod("initialize", Context::class.java)
                .invoke(null, app)
        }
    }

    private fun networkType(): Int {
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return Instance.NET_TYPE_UNKNOWN
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return Instance.NET_TYPE_UNKNOWN
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Instance.NET_TYPE_WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Instance.NET_TYPE_ETHERNET
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Instance.NET_TYPE_LTE
            else -> Instance.NET_TYPE_OTHER_HIGH_SPEED
        }
    }

    private companion object {
        const val TAG = "TgCallsEngine"
        const val MAX_PENDING = 256
    }
}
