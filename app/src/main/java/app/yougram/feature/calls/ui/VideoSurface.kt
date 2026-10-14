package app.yougram.feature.calls.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.telegram.messenger.voip.VideoCapturerDevice
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSink

/**
 * Compose-обёртка поверх WebRTC SurfaceViewRenderer для отображения
 * локального (превью) или удалённого видеопотока.
 */
@Composable
fun VideoSurface(
    videoSinkSetter: ((VideoSink?) -> Unit)?,
    modifier: Modifier = Modifier,
    isOverlay: Boolean = false,
    mirror: Boolean = false,
    scalingType: RendererCommon.ScalingType = RendererCommon.ScalingType.SCALE_ASPECT_FILL,
) {
    val context = LocalContext.current
    val renderer = remember {
        SurfaceViewRenderer(context).apply {
            init(VideoCapturerDevice.getSharedEGLContext(), null)
            setScalingType(scalingType)
            setEnableHardwareScaler(true)
            if (isOverlay) {
                setZOrderMediaOverlay(true)
            }
        }
    }

    DisposableEffect(renderer, mirror) {
        renderer.setMirror(mirror)
        videoSinkSetter?.invoke(renderer)
        onDispose {
            videoSinkSetter?.invoke(null)
            renderer.release()
        }
    }

    AndroidView(
        factory = { renderer },
        modifier = modifier,
    )
}
