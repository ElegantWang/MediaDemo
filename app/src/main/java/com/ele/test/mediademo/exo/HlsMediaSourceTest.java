package com.ele.test.mediademo.exo;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.hls.DefaultHlsExtractorFactory;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Util;

import java.io.IOException;
import java.util.ArrayList;

import timber.log.Timber;

public class HlsMediaSourceTest implements MediaSource.SourceInfoRefreshListener, MediaPeriod.Callback, Handler.Callback {
    private static final String TAG = "HlsMediaSourceTest";
    private String hlsSrc = "http://tx.hls.huya.com/huyalive/30765679-2475713500-10633108516765696000-2789274576-10057-A-0-1.m3u8";
    private Context mContext;
    private String userAgent;
    // private HlsDownloader mHlsDownloader;
    private HlsMediaSource mediaSource;
    private HandlerThread mediaSourceThread;
    private Handler mediaSourceHandler;

    private final Allocator allocator;
    public Timeline timeline;
    public Object manifest;
    public MediaPeriod[] mediaPeriods;
    private final ArrayList<MediaPeriod> pendingMediaPeriods;

    private boolean released;

    private static final int MESSAGE_PREPARE_SOURCE = 0;
    private static final int MESSAGE_CHECK_FOR_FAILURE = 1;
    private static final int MESSAGE_CONTINUE_LOADING = 2;
    private static final int MESSAGE_RELEASE = 3;

    public HlsMediaSourceTest(Context context) {
        mContext = context;
        userAgent = Util.getUserAgent(context, "HDP Live");

        DataSource.Factory dataSourceFactory = new DefaultHttpDataSourceFactory(userAgent);
        mediaSource = new HlsMediaSource.Factory(dataSourceFactory).createMediaSource(Uri.parse(hlsSrc));

        allocator = new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE);
        pendingMediaPeriods = new ArrayList<>();

        mediaSourceThread = new HandlerThread("HlsMediaSourceTest");
        mediaSourceThread.start();
        mediaSourceHandler = Util.createHandler(mediaSourceThread.getLooper(), /* callback= */ this);
    }

    public void start() {
        mediaSourceHandler.sendEmptyMessage(MESSAGE_PREPARE_SOURCE);
    }

    @Override
    public boolean handleMessage(@NonNull Message message) {
        switch (message.what) {
            case MESSAGE_PREPARE_SOURCE:
                mediaSource.prepareSource(this, null);
                mediaSourceHandler.sendEmptyMessage(MESSAGE_CHECK_FOR_FAILURE);
                return true;
            case MESSAGE_CHECK_FOR_FAILURE:
                try {
                    if (mediaPeriods == null) {
                        mediaSource.maybeThrowSourceInfoRefreshError();
                    } else {
                        for (int i = 0; i < pendingMediaPeriods.size(); i++) {
                            pendingMediaPeriods.get(i).maybeThrowPrepareError();
                        }
                    }
                    mediaSourceHandler.sendEmptyMessageDelayed(
                            MESSAGE_CHECK_FOR_FAILURE, /* delayMillis= */ 100);
                } catch (IOException e) {
                    /*downloadHelperHandler
                            .obtainMessage(DOWNLOAD_HELPER_CALLBACK_MESSAGE_FAILED, *//* obj= *//* e)
                            .sendToTarget();*/
                }
                return true;
            case MESSAGE_CONTINUE_LOADING:
                MediaPeriod mediaPeriod = (MediaPeriod) message.obj;
                if (pendingMediaPeriods.contains(mediaPeriod)) {
                    mediaPeriod.continueLoading(/* positionUs= */ 0);
                }
                return true;
            case MESSAGE_RELEASE:
                if (mediaPeriods != null) {
                    for (MediaPeriod period : mediaPeriods) {
                        mediaSource.releasePeriod(period);
                    }
                }
                mediaSource.releaseSource(this);
                mediaSourceHandler.removeCallbacksAndMessages(null);
                mediaSourceThread.quit();
                return true;
            default:
                return false;
        }
    }


    // MediaSource.SourceInfoRefreshListener implementation.
    @Override
    public void onSourceInfoRefreshed(MediaSource source, Timeline timeline, @Nullable Object manifest) {
        if (this.timeline != null) {
            // Ignore dynamic updates.
            return;
        }

        this.timeline = timeline;
        this.manifest = manifest;
        mediaPeriods = new MediaPeriod[timeline.getPeriodCount()];
        for (int i = 0; i < mediaPeriods.length; i++) {
            MediaPeriod mediaPeriod = mediaSource.createPeriod(new MediaSource.MediaPeriodId(timeline.getUidOfPeriod(i)), allocator, 0);
            mediaPeriods[i] = mediaPeriod;
            pendingMediaPeriods.add(mediaPeriod);
        }
        for (MediaPeriod mediaPeriod : mediaPeriods) {
            mediaPeriod.prepare(this, 0);
        }
    }

    // MediaPeriod.Callback implementation.
    @Override
    public void onPrepared(MediaPeriod mediaPeriod) {
        pendingMediaPeriods.remove(mediaPeriod);
        if (pendingMediaPeriods.isEmpty()) {
            mediaSourceHandler.removeMessages(MESSAGE_CHECK_FOR_FAILURE);
            // downloadHelperHandler.sendEmptyMessage(DOWNLOAD_HELPER_CALLBACK_MESSAGE_PREPARED);
        }
    }

    @Override
    public void onContinueLoadingRequested(MediaPeriod mediaPeriod) {
        if (pendingMediaPeriods.contains(mediaPeriod)) {
            mediaSourceHandler.obtainMessage(MESSAGE_CONTINUE_LOADING, mediaPeriod).sendToTarget();
        }
    }

    public void release() {
        if (released) {
            return;
        }
        released = true;
        mediaSourceHandler.sendEmptyMessage(MESSAGE_RELEASE);
    }
}
