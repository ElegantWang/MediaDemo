package com.ele.test.mediademo.ts;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Pair;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

public class MediaEncoderDemo2 {
    private static final String TAG = "MediaEncoderDemo2";
    private static final long TIMEOUT_USEC = 10000;
    private static final int INPUT_END_TIMES = 3;
    private MediaCodec mEncoder;
    private MediaFormat mMediaFormat;
    private String mMime;
    private int mWidth;
    private int mHeight;

    private boolean mInputEndFlag = false;
    private int mInputEndTimes = 0;

    private final static ArrayBlockingQueue<Pair<Long, byte[]>> mInputDataQueue = new ArrayBlockingQueue<>(8);

    public MediaEncoderDemo2(String mime, int width, int height) {
        mMime = mime;
        mWidth = width;
        mHeight = height;
        try {
            mEncoder = MediaCodec.createEncoderByType(mMime);
        } catch (IOException e) {
            e.printStackTrace();
            mEncoder = null;
        }
    }


    public void start(EncoderListener listener) {
        if (mEncoder == null) {
            throw new RuntimeException("mEncode is null!");
        }
        mMediaFormat = MediaFormat.createVideoFormat(mMime, mWidth, mHeight);
        mMediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
        // mMediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 1920 * 1280);
        // BITRATE_MODE_CQ: 表示完全不控制码率，尽最大可能保证图像质量
        // BITRATE_MODE_CBR: 表示编码器会尽量把输出码率控制为设定值
        // BITRATE_MODE_VBR: 表示编码器会根据图像内容的复杂度（实际上是帧间变化量的大小）来动态调整输出码率，图像复杂则码率高，图像简单则码率低
        // mMediaFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
        mMediaFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
        mMediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, (int) (1 * 1000 * 1000)); // 2Mbps
        mMediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        mMediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        mMediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1920 * 1080 * 4);
        // mMediaFormat.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh);
        // mMediaFormat.setInteger(MediaFormat.KEY_LEVEL,);

        mEncoder.configure(mMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mEncoder.start();

        MediaFormat outputFormat = mEncoder.getOutputFormat();
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

        while (true) {
            int inputBufferId = mEncoder.dequeueInputBuffer(TIMEOUT_USEC);
            if (inputBufferId >= 0) {
                ByteBuffer inputBuffer = mEncoder.getInputBuffer(inputBufferId);
                inputBuffer.clear();
                Pair<Long, byte[]> inputData = mInputDataQueue.poll();
                int length = 0;
                if (inputData != null) {
                    inputBuffer.put(inputData.second);
                    length = inputData.second.length;
                }

                long presentationTimeUs = inputData == null ? 0 : inputData.first;
                mEncoder.queueInputBuffer(inputBufferId, 0, length, presentationTimeUs, 0);
            }

            int encoderStatus = mEncoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                Log.e(TAG, "no output available, spinning to await EOS");
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // should happen before receiving buffers, and should only happen once
                outputFormat = mEncoder.getOutputFormat();
                Log.e(TAG, "encoder output format changed: " + outputFormat);
                if (listener != null) {
                    listener.onOutputFormatChanged(outputFormat);
                }
            } else if (encoderStatus < 0) {
                Log.e(TAG, "unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
            } else {
                ByteBuffer encodedData = mEncoder.getOutputBuffer(encoderStatus);
                if (encodedData == null) {
                    throw new RuntimeException("encoderOutputBuffer " + encoderStatus + " was null");
                }
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    Log.e(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                    bufferInfo.size = 0;
                }

                if (bufferInfo.size != 0) {
                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                    encodedData.position(bufferInfo.offset);
                    encodedData.limit(bufferInfo.offset + bufferInfo.size);
                    Log.e(TAG, "Encoder output presentationTimeUs: " + bufferInfo.presentationTimeUs);
                    // bufferInfo.presentationTimeUs = System.nanoTime() / 1000;
                    /*byte[] data = new byte[bufferInfo.size];
                    encodedData.get(data);
                    if (listener != null) {
                        listener.onDataAvailable(data);
                    }*/
                    if (listener != null) {
                        listener.onDataAvailable(encodedData, bufferInfo);
                    }
                    Log.e(TAG, "get " + bufferInfo.size + " bytes from encoder");

                }
                mEncoder.releaseOutputBuffer(encoderStatus, false);

                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.e(TAG, "end of stream reached");
                    break;      // out of while
                }
            }

            if (mInputEndFlag && mInputDataQueue.size() <= 0) {
                if (mInputEndTimes < INPUT_END_TIMES) {
                    SystemClock.sleep(500);
                    mInputEndTimes++;
                    if (mInputEndTimes == INPUT_END_TIMES - 1) {
                        // mEncoder.signalEndOfInputStream();
                    }
                } else {
                    break;
                }
            }
        }

        if (listener != null) listener.onEnd();
    }

    public void inputFrameToEncoder(long presentationTimeUs, byte[] needEncodeData) {
        boolean inputResult = false;
        while (!inputResult) {
            try {
                inputResult = mInputDataQueue.offer(Pair.create(presentationTimeUs, needEncodeData), 10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                Log.d(TAG, "-----> inputEncoder queue result = " + inputResult + " queue current size = " + mInputDataQueue.size());
            }
        }
    }

    public void inputEnd() {
        mInputEndFlag = true;
    }

    public void release() {
        try {
            if (mEncoder != null) {
                mEncoder.stop();
                mEncoder.release();
                mEncoder = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public interface EncoderListener {
        void onOutputFormatChanged(MediaFormat mediaFormat);

        // void onDataAvailable(byte[] data);
        void onDataAvailable(ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo);

        void onEnd();
    }

}
