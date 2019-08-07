package com.ele.test.mediademo.ts;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;

public class MediaEncoderDemo {
    private final static String TAG = "MediaEncoderDemo";
    private final static int CACHE_BUFFER_SIZE = 8;

    private MediaCodec mMediaCodec;
    private MediaFormat mMediaFormat;

    private int mViewWidth;
    private int mViewHeight;

    private Handler mVideoEncoderHandler;
    private HandlerThread mVideoEncoderHandlerThread = new HandlerThread("VideoEncoder");

    private EncoderListener mEncoderListener;


    //This video stream format must be I420
    private final static ArrayBlockingQueue<byte[]> mInputDataQueue = new ArrayBlockingQueue<>(CACHE_BUFFER_SIZE);
    //Cache video stream which has been encoded.
    // private final static ArrayBlockingQueue<byte[]> mOutputDataQueue = new ArrayBlockingQueue<>(CACHE_BUFFER_SIZE);

    private MediaCodec.Callback mCallback = new MediaCodec.Callback() {
        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec mediaCodec, int id) {
            ByteBuffer inputBuffer = mediaCodec.getInputBuffer(id);
            inputBuffer.clear();
            byte[] dataSources = mInputDataQueue.poll();
            int length = 0;
            if (dataSources != null) {
                inputBuffer.put(dataSources);
                length = dataSources.length;
            }
            mediaCodec.queueInputBuffer(id, 0, length, 0, 0);
        }

        @Override
        public void onOutputBufferAvailable(@NonNull MediaCodec mediaCodec, int id, @NonNull MediaCodec.BufferInfo bufferInfo) {
            ByteBuffer outputBuffer = mMediaCodec.getOutputBuffer(id);
            MediaFormat outputFormat = mMediaCodec.getOutputFormat(id);
            if (outputBuffer != null && bufferInfo.size > 0) {
                byte[] buffer = new byte[outputBuffer.remaining()];
                outputBuffer.get(buffer);
                if (mEncoderListener != null)
                    mEncoderListener.onDataAvailable(buffer);
                /*boolean result = mOutputDataQueue.offer(buffer);
                if (!result) {
                    Log.d(TAG, "Offer to queue failed, queue in full state");
                }*/
            }
            mMediaCodec.releaseOutputBuffer(id, true);
        }

        @Override
        public void onError(@NonNull MediaCodec mediaCodec, @NonNull MediaCodec.CodecException e) {
            Log.d(TAG, "------> onError");
        }

        @Override
        public void onOutputFormatChanged(@NonNull MediaCodec mediaCodec, @NonNull MediaFormat mediaFormat) {
            Log.d(TAG, "------> onOutputFormatChanged");
        }
    };

    public MediaEncoderDemo(String mimeType, int width, int height) {
        try {
            mMediaCodec = MediaCodec.createEncoderByType(mimeType);
        } catch (IOException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            mMediaCodec = null;
            return;
        }

        this.mViewWidth = width;
        this.mViewHeight = height;

        mVideoEncoderHandlerThread.start();
        mVideoEncoderHandler = new Handler(mVideoEncoderHandlerThread.getLooper());

        mMediaFormat = MediaFormat.createVideoFormat(mimeType, mViewWidth, mViewHeight);
        mMediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
        // mMediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 1920 * 1280);
        // BITRATE_MODE_CQ: 表示完全不控制码率，尽最大可能保证图像质量
        // BITRATE_MODE_CBR: 表示编码器会尽量把输出码率控制为设定值
        // BITRATE_MODE_VBR: 表示编码器会根据图像内容的复杂度（实际上是帧间变化量的大小）来动态调整输出码率，图像复杂则码率高，图像简单则码率低
        mMediaFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
        // mMediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 1280 * 720);
        mMediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        mMediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
    }

    /**
     * Input Video stream which need encode to Queue
     *
     * @param needEncodeData I420 format stream
     */
    public void inputFrameToEncoder(byte[] needEncodeData) {
        boolean inputResult = mInputDataQueue.offer(needEncodeData);
        Log.d(TAG, "-----> inputEncoder queue result = " + inputResult + " queue current size = " + mInputDataQueue.size());
    }

    /**
     * Get Encoded frame from queue
     *
     * @return a encoded frame; it would be null when the queue is empty.
     */
    /*public byte[] pollFrameFromEncoder() {
        return mOutputDataQueue.poll();
    }*/

    /**
     * start the MediaCodec to encode video data
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    public void startEncoder() {
        if (mMediaCodec != null) {
            mMediaCodec.setCallback(mCallback, mVideoEncoderHandler);
            mMediaCodec.configure(mMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mMediaCodec.start();
        } else {
            throw new IllegalArgumentException("startEncoder failed,is the MediaCodec has been init correct?");
        }
    }

    /**
     * stop encode the video data
     */
    public void stopEncoder() {
        if (mMediaCodec != null) {
            mMediaCodec.stop();
            mMediaCodec.setCallback(null);
        }
    }

    /**
     * release all resource that used in Encoder
     */
    public void release() {
        if (mMediaCodec != null) {
            mInputDataQueue.clear();
            // mOutputDataQueue.clear();
            mMediaCodec.release();
        }
    }

    public void setEncoderListener(EncoderListener encoderListener) {
        mEncoderListener = encoderListener;
    }

    public void inputEnd() {
        if (mEncoderListener != null) {
            while (mInputDataQueue.size() > 0) {
                Log.e(TAG, "input end wait queue");
                SystemClock.sleep(500);
            }
            mEncoderListener.onEnd();
        }
    }

    public interface EncoderListener {
        void onDataAvailable(byte[] data);

        void onEnd();
    }
}
