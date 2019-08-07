package com.ele.test.mediademo.utils;

import android.media.MediaCodecInfo;

public class MediaCodecUtil {
    public static String descMediaCodecInfo(MediaCodecInfo mediaCodecInfo) {
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("Name: ").append(mediaCodecInfo.getName()).append("\n");
        stringBuilder.append("Is Encoder :").append(mediaCodecInfo.isEncoder()).append("\n");
        stringBuilder.append("Supported Types:");
        for (String type : mediaCodecInfo.getSupportedTypes()) {
            stringBuilder.append("\n");
            stringBuilder.append(type);
        }
        stringBuilder.append("\n");

        return stringBuilder.toString();
    }
}
