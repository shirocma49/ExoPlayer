package jp.co.pixela.exoplayerwrapper;

import jp.co.pixela.exoplayerwrapper.player.DemoPlayer;
import jp.co.pixela.exoplayerwrapper.player.DemoPlayer.RendererBuilder;
import jp.co.pixela.exoplayerwrapper.player.HlsRendererBuilder;
import jp.co.pixela.exoplayerwrapper.player.DashRendererBuilder;
import jp.co.pixela.exoplayerwrapper.player.SmoothStreamingRendererBuilder;
import jp.co.pixela.exoplayerwrapper.player.ExtractorRendererBuilder;

import com.google.android.exoplayer.AspectRatioFrameLayout;
import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.MediaCodecTrackRenderer.DecoderInitializationException;
import com.google.android.exoplayer.MediaCodecUtil.DecoderQueryException;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.audio.AudioCapabilities;
import com.google.android.exoplayer.audio.AudioCapabilitiesReceiver;
import com.google.android.exoplayer.drm.UnsupportedDrmException;
import com.google.android.exoplayer.metadata.id3.GeobFrame;
import com.google.android.exoplayer.metadata.id3.Id3Frame;
import com.google.android.exoplayer.metadata.id3.PrivFrame;
import com.google.android.exoplayer.metadata.id3.TxxxFrame;
import com.google.android.exoplayer.text.CaptionStyleCompat;
import com.google.android.exoplayer.text.Cue;
import com.google.android.exoplayer.text.SubtitleLayout;
import com.google.android.exoplayer.util.DebugTextViewHelper;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.SystemClock;
import com.google.android.exoplayer.util.Util;
import com.google.android.exoplayer.util.VerboseLogUtil;


import android.Manifest.permission;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.List;
import java.util.Locale;

/**
 * Created by kumam on 2016/02/23.
 */
public class PlayerManager
        implements
        DemoPlayer.Listener
//      ,  DemoPlayer.CaptionListener,
//      ,  DemoPlayer.Id3MetadataListener,
//      ,  AudioCapabilitiesReceiver.Listener
{
    //DemoPlayer.CaptionListener imple
//    @Override
//    public void onCues(List<Cue> cues) {
//
//    }

    //DemoPlayer.Id3MetadataListener implementation
//    @Override
//    public void onId3Metadata(List<Id3Frame> id3Frames) {
//
//    }

    //HDMIの動作が変わった時などのコールバック？
    //AudioCapabilitiesReceiver.Listener implementation
//    @Override
//    public void onAudioCapabilitiesChanged(AudioCapabilities audioCapabilities) {
//          //PlayerActivity だと、ここでpreparePlayer(bool) で強制play/pause切り替えるようだ
//    }

    //DemoPlayer.Listener implementation

    private int state = 0;
    private boolean whenReady = false;

    @Override
    public void onStateChanged(boolean playWhenReady, int playbackState)
    {
        whenReady = playWhenReady;
        state = playbackState;
        System.out.println("onStateChanged. > " + String.valueOf(state) + "," + String.valueOf(whenReady));
    }

    @Override
    public void onError(Exception e) {
        String errorString = null;

        if (e instanceof UnsupportedDrmException) {
            // Special case DRM failures.
            UnsupportedDrmException unsupportedDrmException = (UnsupportedDrmException) e;

            errorString = (Util.SDK_INT < 18)
                    ? "error_drm_not_supported"
                    : (unsupportedDrmException.reason == UnsupportedDrmException.REASON_UNSUPPORTED_SCHEME)
                        ? "error_drm_unsupported_scheme"
                        : "error_drm_unknown";
        }
        else if (e instanceof ExoPlaybackException
                && e.getCause() instanceof DecoderInitializationException) {
            // Special case for decoder initialization failures.
            DecoderInitializationException decoderInitializationException =
                    (DecoderInitializationException) e.getCause();
            if (decoderInitializationException.decoderName == null) {
                if (decoderInitializationException.getCause() instanceof DecoderQueryException) {
                    errorString = "error_querying_decoders";
                } else if (decoderInitializationException.secureDecoderRequired) {
                    errorString = "error_no_secure_decoder :" +
                            decoderInitializationException.mimeType.toString();
                } else {
                    errorString = "error_no_decoder :" +
                            decoderInitializationException.mimeType.toString();
                }
            } else {
                errorString = "error_instantiating_decoder :" +
                        decoderInitializationException.decoderName.toString();
            }
        }

        //ToDo something
        if (errorString != null && !(errorString.equals(""))) {
            System.out.println(errorString);
        }
    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio)
    {
        float aspectRatio = height == 0 ? 1 : (width * pixelWidthHeightRatio) / height;
        //ToDo announce?
        System.out.println("onVideoSizeChanged");
    }

    public int GetCurrentState()
    {
        if (demoPlayer == null) {
            return ExoPlayer.STATE_IDLE;
        }

        int exoState = demoPlayer.getPlaybackState();
        return state;
    }

    private Surface currentSurface;
    private Context currentContext;
    private DemoPlayer demoPlayer;
    private UnitySurfaceTexture unitySurfaceTexture;

    private PlayerState playerState;

    PlayerManager()
    {
        playerState = PlayerState.None;
        unitySurfaceTexture = new UnitySurfaceTexture();
    }

    public int Initialize(Context context, int textureId, int width, int height)
    {
        if (context == null)
        {
            return -1;
        }

        currentSurface = (Surface)unitySurfaceTexture.getSurface();
        unitySurfaceTexture.setUnityTexture(textureId, width, height);
        currentContext = context;
        playerState = PlayerState.Initialized;
        return 0;
    }

    public int Finalize()
    {
        Unload();
        return 0;
    }

    public int Load(String url,  int seekto)
    {
        //surface 指定が先
        //context 指定も先
        //typeはひとまずhls固定(内部)
        if (url == null || url.equals(""))
        {
            return -1;
        }

        if (currentSurface == null || currentContext == null)
        {
            return -2;
        }

        {
            RendererBuilder builder = getRendererBuilder(url, Util.TYPE_HLS);
            if (builder == null) {
                return -3;
            }

            demoPlayer = new DemoPlayer(builder);
            demoPlayer.addListener(this);
            demoPlayer.seekTo(seekto);

            demoPlayer.setSurface(currentSurface);

            demoPlayer.prepare();


            demoPlayer.setPlayWhenReady(true);
        }

        return 0;
    }

    public int Unload()
    {
        return -1;
    }

    private void releasePlayer()
    {
        if (demoPlayer != null)
        {
            demoPlayer.release();
            demoPlayer = null;
        }
    }


    private RendererBuilder getRendererBuilder(String url, int contentType)
    {
        String userAgent = Util.getUserAgent(currentContext, "ExoPlayerWrapper");
        switch (contentType)
        {
            case Util.TYPE_HLS:
                return new HlsRendererBuilder(currentContext, userAgent, url);
            default:
                return null;
        }
    }

    public int UpdateVideoFrame()
    {
        if (unitySurfaceTexture == null)
        {
            return 1;
        }
        unitySurfaceTexture.updateVideoTexture();
        return 0;
    }

    public int UpdateAudioFrame() { return -1; }

//    public void SetSurface(Surface surface)
//    {
//        currentSurface = surface;
//    }



    //test
    public int Test(int id)
    {
        return id + 1;
    }

}
