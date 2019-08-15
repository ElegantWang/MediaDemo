package com.ele.test.mediademo;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.audiofx.LoudnessEnhancer;
import android.os.Build;
import android.os.Bundle;

import com.ele.test.mediademo.ts.MediaConvert;
import com.ele.test.mediademo.ts.MediaDecoderDemo;
import com.ele.test.mediademo.ts.TsDemo;
import com.ele.test.mediademo.utils.MediaCodecUtil;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.FileOutputStream;
import java.io.IOException;

import javax.crypto.Mac;

public class MainActivity extends AppCompatActivity {
    private TextView mHintView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
        mHintView = findViewById(R.id.txt_hint);

        findViewById(R.id.btn_ts_demo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    TsDemo tsDemo = new TsDemo();
                    tsDemo.openFile("/data/local/tmp/dvr_v_p479_4475272588.ts");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        Button btnMediaDemo = findViewById(R.id.btn_media_demo);
        btnMediaDemo.setOnClickListener(new View.OnClickListener() {
            // @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            @Override
            public void onClick(View view) {
                Toast.makeText(MainActivity.this, "btn media demo on click", Toast.LENGTH_SHORT).show();
                Log.e("XXXXX", "btn media demo on click");
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            setHint("Media Demo running...");
                            MediaDecoderDemo mediaDecoderDemo = new MediaDecoderDemo();
                            // mediaDecoderDemo.setSaveFrames(MainActivity.this.getExternalCacheDir().getAbsolutePath(), MediaDecoderDemo.FILE_TypeI420);
                            // mediaDecoderDemo.videoDecode("/data/local/tmp/dvr_v_p479_4475272588.ts");
                            mediaDecoderDemo.setColorFormat(MediaDecoderDemo.COLOR_FormatNV21);
                            // mediaDecoderDemo.decodeVideo("/data/local/tmp/anetstream.ts", null);
                            mediaDecoderDemo.decodeVideo("http://tx.hls.huya.com/huyalive/30765679-2475713500-10633108516765696000-2789274576-10057-A-0-1.m3u8", new MediaDecoderDemo.DecoderListener() {
                                String outDir = MainActivity.this.getExternalCacheDir().getAbsolutePath();
                                String fileNamePattern = outDir + "/frame%05d.yuv";

                                @Override
                                public void onSize(MediaDecoderDemo decoderDemo, int width, int height) {
                                    setHint(String.format("Media Size:%d x %d", width, height));
                                }

                                @Override
                                public void onFrameData(MediaDecoderDemo decoderDemo, int frameSeq, long presentationTimeUs, byte[] data) {
                                    try {
                                        FileOutputStream outputStream = new FileOutputStream(String.format(fileNamePattern, frameSeq));
                                        outputStream.write(data);
                                        outputStream.close();

                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }

                                @Override
                                public void onEnd() {

                                }
                            });
                            setHint("Media Demo finish");
                        } catch (IOException e) {
                            e.printStackTrace();
                            setHint("Media Demo error");
                        }
                    }
                }).start();
            }
        });

        findViewById(R.id.btn_convert_demo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(MainActivity.this, "btn media convert on click", Toast.LENGTH_SHORT).show();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            setHint("Media Convert running...");
                            // String sourceFile = "/data/local/tmp/anetstream.ts";
                            String sourceFile = "/data/local/tmp/dvr_v_p479_4475272588.ts";
                            MediaConvert mediaConvert = new MediaConvert(sourceFile, MainActivity.this.getExternalCacheDir().getAbsolutePath() + "/convert.out", MainActivity.this.getExternalCacheDir().getAbsolutePath());
                            mediaConvert.start();
                            setHint("Media Convert finish");
                        } catch (Exception e) {
                            e.printStackTrace();
                            setHint("Media Convert error");
                        }
                    }
                }).start();
            }
        });

        findViewById(R.id.btn_show_codec).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MediaCodecList mediaCodecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
                MediaCodecInfo[] mediaCodecInfos = mediaCodecList.getCodecInfos();
                for (MediaCodecInfo mediaCodecInfo : mediaCodecInfos) {
                    Log.e("XXXXX", MediaCodecUtil.descMediaCodecInfo(mediaCodecInfo));
                }

                DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
                Display[] displays = displayManager.getDisplays();
                Log.e("XXXXX", "Display Size:" + displays.length);
            }
        });
    }

    private void setHint(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mHintView.setText(text);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    public static String[] runtimePermissions = {
            //Manifest.permission.WRITE_SETTINGS,
            //Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    @Override
    protected void onResume() {
        super.onResume();
        boolean granted = true;
        if (Build.VERSION.SDK_INT >= 23) {
            for (String permission : runtimePermissions) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                }
            }
        }
        if (!granted) {
            ActivityCompat.requestPermissions(this, runtimePermissions, 0);

        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Toast.makeText(MainActivity.this, "onRequestPermissionsResult", Toast.LENGTH_SHORT).show();
    }

}
