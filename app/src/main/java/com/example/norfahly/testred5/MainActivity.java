package com.example.norfahly.testred5;

import android.Manifest;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.OnNeverAskAgain;
import permissions.dispatcher.OnPermissionDenied;
import permissions.dispatcher.RuntimePermissions;

@RuntimePermissions

public class MainActivity extends AppCompatActivity {

    @BindView(R.id.surface_view) SurfaceView mSurfaceView;
    @BindView(R.id.camera_switch) ImageView cameraSwitch;
    @BindView(R.id.cameraSwitchFL) FrameLayout cameraSwitchFL;
    @BindView(R.id.exit) ImageView exitCross;
    @BindView(R.id.exitFL) FrameLayout exitFL;
    @BindView(R.id.startLiveTV) TextView startLiveTV;
    Handler handler = new Handler();

    SurfaceHolder mHolder;

    private boolean permissionsGranted = false;
    boolean continueBroadcasting = false;
    boolean stopBroadcast = false, isFrontFacing = true;
    boolean pauseBroadcast = false;

    int retryStreamConnect = 0;


    LiveStreamManager.Red5StreamEventsListener red5StreamEventsListener = event -> {
        switch (event) {
            case CONNECTED:
                break;
            case START_STREAMING:
                retryStreamConnect = 0;
                break;
            case STOP_STREAMING:
                break;
            case DISCONNECTED:
                break;
            case CLOSE:
                break;
            case ERROR:
                //LiveStreamManager.getInstance().checkLiveExists(liveBroadcast.getId());
                break;
        }
    };


    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); //keep screen on while watching a stream
        float ratio = ((float) getScreenMetrics().heightPixels) / ((float) getScreenMetrics().widthPixels);
        MainActivityPermissionsDispatcher.initializeLiveStreamWithCheck(MainActivity.this);
    }

    private void setupLiveStream() {
        if (stopBroadcast) return;
        LiveStreamManager.getInstance().startPublishing(LiveStreamManager.HOST
                , LiveStreamManager.PORT, LiveStreamManager.CONTEXT_NAME
                , "stream");
    }

    private void startLiveUI() {
        stopBroadcast = false;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setupLiveStream();
        startLiveTV.setText("streaming");
    }

    private void endLive() {
        continueBroadcasting = false;
        Log.d("r5", "end text clicked");
        if (LiveStreamManager.getInstance().isPublishing())
            LiveStreamManager.getInstance().stopPublishing();
    }

    private void toggleCamera() {
        LiveStreamManager.getInstance().changeCamera(true);
    }

    // region click event handler

    @OnClick({R.id.exit, R.id.exitFL, R.id.camera_switch, R.id.cameraSwitchFL, R.id.startLiveTV})
    public void handleOnClicks(View view) {
        switch (view.getId()) {
            case R.id.startLiveTV:
                if (startLiveTV.getText().equals("streaming")) {
                    break;
                } else if (permissionsGranted) {
                    startLiveUI();

                } else
                    Log.d("r5", "live cant start without permissions");
                break;
            case R.id.camera_switch:
            case R.id.cameraSwitchFL:
                if (permissionsGranted)
                    toggleCamera();
                else
                    Log.d("r5", "live cant start without permissions");
                break;
            case R.id.exit:
            case R.id.exitFL:
                endLive();
                updateUI();
                break;
        }
    }

    @Override
    public void onBackPressed() {
        if (LiveStreamManager.getInstance().isPublishing()) {
            endLive();
            updateUI();
        }
        finish();
        this.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void updateUI()
    {
        startLiveTV.setVisibility(View.GONE);
        exitFL.setVisibility(View.GONE);
        cameraSwitchFL.setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pauseBroadcast = false;
        if (LiveStreamManager.getInstance().isPublishing())
            LiveStreamManager.getInstance().stopPublishing();
        LiveStreamManager.getInstance().destroyStreamer();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (pauseBroadcast)
            LiveStreamManager.getInstance().resumePublishing();
    }

    @Override
    public void onStop() {
        if (LiveStreamManager.getInstance().isPublishing()) {
            LiveStreamManager.getInstance().pausePublishing();
            pauseBroadcast = true;
        }
        super.onStop();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        LiveStreamManager.getInstance().updateOrientationParams(getWindowManager());
    }


    // region Permissions

    @OnPermissionDenied({Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE})
    void showDeniedForInitializeLiveStream() {
        permissionsGranted = false;
        Toast.makeText(this, "denied", Toast.LENGTH_SHORT).show();
    }

    @OnNeverAskAgain({Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE})
    void showNeverAskForInitializeLiveStreamPicker() {
        permissionsGranted = false;
        Toast.makeText(this, "neverask", Toast.LENGTH_SHORT).show();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // NOTE: delegate the permission handling to generated method
        MainActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);
    }


    // request permissions for gallery and launch it
    @NeedsPermission({Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE})
    public void initializeLiveStream() {
        permissionsGranted = true;
        DisplayMetrics screenMetrics = getScreenMetrics();

        LiveStreamManager.getInstance().initialize(mSurfaceView, this, red5StreamEventsListener, (float) screenMetrics.widthPixels / (float) screenMetrics.heightPixels);
    }

    protected DisplayMetrics getScreenMetrics() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        return displayMetrics;
    }

}
