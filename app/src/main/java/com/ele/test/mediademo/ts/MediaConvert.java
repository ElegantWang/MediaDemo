package com.ele.test.mediademo.ts;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;

public class MediaConvert {
    private static final String TAG = "MediaConvert";
    private MediaEncoderDemo2 mEncoder;
    private MediaDecoderDemo mDecoder;

    private String mSource;
    private String mTarget;
    private String mOutDir;

    private final ArrayBlockingQueue<byte[]> mOutputDataQueue = new ArrayBlockingQueue<>(100);


    public MediaConvert(String source, String target, String outDir) {
        mDecoder = new MediaDecoderDemo();
        mDecoder.setColorFormat(MediaDecoderDemo.COLOR_FormatI420);
        mSource = source;
        mTarget = target;
        mOutDir = outDir;


        // mEncoder = new MediaEncoderDemo("video/hevc", 1280, 720);
        // mEncoder = new MediaEncoderDemo("video/avc", 896, 480);
        mEncoder = new MediaEncoderDemo2("video/avc", 1920, 1080);

    }

    public void start() {
        try {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        mDecoder.decodeVideo(mSource, new MediaDecoderDemo.DecoderListener() {
                            @Override
                            public void onSize(MediaDecoderDemo decoderDemo, int width, int height) {
                                Log.i(TAG, String.format("source size :%d x %d", width, height));
                            }

                            @Override
                            public void onFrameData(MediaDecoderDemo decoderDemo, int frameSeq, long presentationTimeUs, byte[] data) {
                                Log.e(TAG, "decode listener onFrameData " + frameSeq);
                                mEncoder.inputFrameToEncoder(presentationTimeUs, data);
                                /*try {
                                    FileOutputStream outputStream = new FileOutputStream(mOutDir + "/frame" + frameSeq + ".yuv");
                                    outputStream.write(data);
                                    outputStream.close();

                                } catch (Exception e) {
                                    e.printStackTrace();
                                }*/
                            }

                            @Override
                            public void onEnd() {
                                Log.e(TAG, "decode listener onEnd");
                                mEncoder.inputEnd();
                            }
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();


            // final FileOutputStream outStream = new FileOutputStream(mTarget);
            mEncoder.start(new MediaEncoderDemo2.EncoderListener() {
                MediaMuxer mediaMuxer;
                boolean isMediaMuxerStart = false;
                int trackIndex;

                {
                    try {
                        mediaMuxer = new MediaMuxer(mTarget, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                    } catch (Exception e) {
                        mediaMuxer = null;
                    }
                }

                @Override
                public void onOutputFormatChanged(MediaFormat mediaFormat) {
                    checkMediaMuxer();
                    trackIndex = mediaMuxer.addTrack(mediaFormat);
                    mediaMuxer.start();
                    isMediaMuxerStart = true;
                }

                @Override
                public void onDataAvailable(ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo) {
                    if (!isMediaMuxerStart) {
                        throw new RuntimeException("Muxer is not start before on data available");
                    }
                    mediaMuxer.writeSampleData(trackIndex, byteBuffer, bufferInfo);
                }

                @Override
                public void onEnd() {
                    Log.e(TAG, "encoder listener onEnd");
                    checkMediaMuxer();
                    mediaMuxer.stop();
                    mediaMuxer.release();
                }

                private void checkMediaMuxer() {
                    if (mediaMuxer == null) throw new RuntimeException("MediaMuxer is null!");
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "convert error", e);
            mEncoder.release();
        }
    }
}
