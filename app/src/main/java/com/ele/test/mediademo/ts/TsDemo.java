package com.ele.test.mediademo.ts;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Future;

public class TsDemo {
    private static final String TAG = "TsDemo";

    public static int selectTrack(MediaExtractor extractor) {
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                Log.d(TAG, "Extractor selected track " + i + " (" + mime + "): " + format);
            }
            return i;
        }
        return -1;
    }

    // for video/hevc
    // COLOR_FormatYUV420Flexible 2135033992
    // COLOR_FormatYUV420SemiPlanar 21
    private void showSupportedColorFormat(MediaCodecInfo.CodecCapabilities capabilities) {
        System.out.println("supported color format: ");
        for (int c : capabilities.colorFormats) {
            System.out.println(c + "\t");
        }
    }

    public void openFile(String filePath) throws IOException {
        File videoFile = new File(filePath);
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(videoFile.toString());
        int trackIndex = selectTrack(extractor);
        if (trackIndex < 0) {
            throw new RuntimeException("No video track found in " + filePath);
        }
        extractor.selectTrack(trackIndex);
        MediaFormat mediaFormat = extractor.getTrackFormat(trackIndex);
        String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
        MediaCodec decoder = MediaCodec.createDecoderByType(mime);
        showSupportedColorFormat(decoder.getCodecInfo().getCapabilitiesForType(mime));

        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
    }

}
