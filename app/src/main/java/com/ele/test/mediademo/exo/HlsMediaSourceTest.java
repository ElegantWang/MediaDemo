package com.ele.test.mediademo.exo;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.SparseIntArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ele.test.mediademo.DemoApplication;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.metadata.MetadataOutput;
import com.google.android.exoplayer2.offline.DownloadHelper;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.hls.DefaultHlsExtractorFactory;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.FixedTrackSelection;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectorResult;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoRendererEventListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    private List<TrackSelection>[][] trackSelectionsByPeriodAndRenderer;
    private List<TrackSelection>[][] immutableTrackSelectionsByPeriodAndRenderer;

    private boolean released;
    private MyMediaCodecVideoRenderer videoRenderer;
    private MyMediaCodecAudioRenderer audioRenderer;
    RenderersFactory renderersFactory;
    private RendererCapabilities[] rendererCapabilities;
    private DefaultTrackSelector trackSelector;

    private TrackGroupArray[] trackGroupArrays;
    private MappingTrackSelector.MappedTrackInfo[] mappedTrackInfos;

    private final SparseIntArray scratchSet;

    private static final int MESSAGE_PREPARE_SOURCE = 0;
    private static final int MESSAGE_CHECK_FOR_FAILURE = 1;
    private static final int MESSAGE_CONTINUE_LOADING = 2;
    private static final int MESSAGE_RELEASE = 3;

    public static final DefaultTrackSelector.Parameters DEFAULT_TRACK_SELECTOR_PARAMETERS =
            new DefaultTrackSelector.ParametersBuilder().setForceHighestSupportedBitrate(true).build();

    public HlsMediaSourceTest(Context context) {
        mContext = context;
        userAgent = Util.getUserAgent(context, "HDP Live");

        DataSource.Factory dataSourceFactory = new DefaultHttpDataSourceFactory(userAgent);
        mediaSource = new HlsMediaSource.Factory(dataSourceFactory).createMediaSource(Uri.parse(hlsSrc));

        allocator = new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE);
        pendingMediaPeriods = new ArrayList<>();

        audioRenderer = new MyMediaCodecAudioRenderer(context, MediaCodecSelector.DEFAULT);
        videoRenderer = new MyMediaCodecVideoRenderer(context, MediaCodecSelector.DEFAULT);

        renderersFactory = new RenderersFactory() {
            @Override
            public Renderer[] createRenderers(Handler eventHandler, VideoRendererEventListener videoRendererEventListener, AudioRendererEventListener audioRendererEventListener, TextOutput textRendererOutput, MetadataOutput metadataRendererOutput, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager) {
                return new Renderer[]{audioRenderer, videoRenderer};
            }
        };

        trackSelector = new DefaultTrackSelector(new FixedTrackSelection.Factory());
        trackSelector.setParameters(DEFAULT_TRACK_SELECTOR_PARAMETERS);
        trackSelector.init(new TrackSelector.InvalidationListener() {
            @Override
            public void onTrackSelectionsInvalidated() {
                Timber.e("onTrackSelectionsInvalidated");
            }
        }, new DefaultBandwidthMeter.Builder(mContext).build());

        rendererCapabilities = Util.getRendererCapabilities(renderersFactory, null);

        scratchSet = new SparseIntArray();

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

        // continue process
        int periodCount = mediaPeriods.length;
        int rendererCount = rendererCapabilities.length;

        trackSelectionsByPeriodAndRenderer =
                (List<TrackSelection>[][]) new List<?>[periodCount][rendererCount];
        immutableTrackSelectionsByPeriodAndRenderer =
                (List<TrackSelection>[][]) new List<?>[periodCount][rendererCount];
        for (int i = 0; i < periodCount; i++) {
            for (int j = 0; j < rendererCount; j++) {
                trackSelectionsByPeriodAndRenderer[i][j] = new ArrayList<>();
                immutableTrackSelectionsByPeriodAndRenderer[i][j] =
                        Collections.unmodifiableList(trackSelectionsByPeriodAndRenderer[i][j]);
            }
        }
        trackGroupArrays = new TrackGroupArray[periodCount];
        mappedTrackInfos = new MappingTrackSelector.MappedTrackInfo[periodCount];
        for (int i = 0; i < periodCount; i++) {
            trackGroupArrays[i] = mediaPeriods[i].getTrackGroups();
            TrackSelectorResult trackSelectorResult = runTrackSelection(i);
            trackSelector.onSelectionActivated(trackSelectorResult.info);
            mappedTrackInfos[i] = Assertions.checkNotNull(trackSelector.getCurrentMappedTrackInfo());
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

    private TrackSelectorResult runTrackSelection(int periodIndex) {
        try {
            TrackSelectorResult trackSelectorResult = trackSelector.selectTracks(
                    rendererCapabilities,
                    trackGroupArrays[periodIndex],
                    new MediaSource.MediaPeriodId(timeline.getUidOfPeriod(periodIndex)),
                    timeline);
            for (int i = 0; i < trackSelectorResult.length; i++) {
                TrackSelection newSelection = trackSelectorResult.selections.get(i);
                if (newSelection == null) continue;
                List<TrackSelection> existingSelectionList = trackSelectionsByPeriodAndRenderer[periodIndex][i];
                boolean mergedWithExistingSelection = false;
                for (int j = 0; j < existingSelectionList.size(); j++) {
                    TrackSelection existingSelection = existingSelectionList.get(j);
                    if (existingSelection.getTrackGroup() == newSelection.getTrackGroup()) {
                        // Merge with existing selection.
                        scratchSet.clear();
                        for (int k = 0; k < existingSelection.length(); k++) {
                            scratchSet.put(existingSelection.getIndexInTrackGroup(k), 0);
                        }
                        for (int k = 0; k < newSelection.length(); k++) {
                            scratchSet.put(newSelection.getIndexInTrackGroup(k), 0);
                        }
                        int[] mergedTracks = new int[scratchSet.size()];
                        for (int k = 0; k < scratchSet.size(); k++) {
                            mergedTracks[k] = scratchSet.keyAt(k);
                        }
                        // existingSelectionList.set(j, new DownloadHelper.DownloadTrackSelection(existingSelection.getTrackGroup(), mergedTracks));
                        existingSelectionList.set(j, new FixedTrackSelection(existingSelection.getTrackGroup(), existingSelection.getIndexInTrackGroup(0)));
                        mergedWithExistingSelection = true;
                        break;
                    }
                }
                if (!mergedWithExistingSelection) {
                    existingSelectionList.add(newSelection);
                }
            }
            return trackSelectorResult;
        } catch (ExoPlaybackException e) {
            // DefaultTrackSelector does not throw exceptions during track selection.
            throw new UnsupportedOperationException(e);
        }
    }
}
