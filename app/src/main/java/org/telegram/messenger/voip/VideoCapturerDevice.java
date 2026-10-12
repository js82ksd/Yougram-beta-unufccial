package org.telegram.messenger.voip;

import android.media.projection.MediaProjection;

import org.webrtc.EglBase;

// Заглушка: нативный код создаёт этот класс даже в аудиозвонке. Видео не поддерживается.
public class VideoCapturerDevice {
    private static EglBase eglBase;

    public VideoCapturerDevice(boolean screencast) { }

    public void onDestroy() { }

    public static MediaProjection getMediaProjection() { return null; }

    public static synchronized EglBase.Context getSharedEGLContext() {
        if (eglBase == null) {
            eglBase = EglBase.create(null, EglBase.CONFIG_PLAIN);
        }
        return eglBase.getEglBaseContext();
    }
}