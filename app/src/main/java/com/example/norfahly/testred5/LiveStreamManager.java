package com.example.norfahly.testred5;

import android.graphics.Rect;
import android.hardware.Camera;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

import com.red5pro.streaming.R5Connection;
import com.red5pro.streaming.R5Stream;
import com.red5pro.streaming.R5StreamProtocol;
import com.red5pro.streaming.config.R5Configuration;
import com.red5pro.streaming.event.R5ConnectionEvent;
import com.red5pro.streaming.event.R5ConnectionListener;
import com.red5pro.streaming.source.R5AdaptiveBitrateController;
import com.red5pro.streaming.source.R5Camera;
import com.red5pro.streaming.source.R5Microphone;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Created by Noor Orfahly on 7/4/2018.
 */

public class LiveStreamManager implements R5ConnectionListener, SurfaceHolder.Callback {

    public static LiveStreamManager instance;

    public static final String HOST = "34.241.232.197";
    public static final int PORT = 8554;
    public static final String CONTEXT_NAME = "live";
    public static final float BUFFER_TIME = 1.0f;
    public static final String LICENSE_KEY = "U64T-UT5S-RFO6-B7PC";
    public static final int SAMPLE_RATE = 44100;
    private static final int BIT_RATE = 1500;
    private static final int FPS = 15;
    private static final int streamCameraWidth = 720;//BuildConfig.is_dev ? 360 : 720;
    private static final int streamCameraHeight = 1280;// BuildConfig.is_dev ? 640 : 1280;
    public static final int STREAMER_MAX_RECONNECT_TRIES = 4;

    boolean isPublishing = false;
    boolean isCameraPaused = false;
    boolean shouldRecordLocally = true;

    protected LiveStreamManager.PublishListener mPublishListener;


    public R5Configuration initialConfiguration;
    public R5Configuration publishConfiguration;

    protected Camera camera;

    AppCompatActivity publishActivity;
    protected int camOrientation;

    private int currentCamMode = Camera.CameraInfo.CAMERA_FACING_FRONT;


    protected R5Stream stream;
    String mStreamName;
    SurfaceView surface;
    Red5StreamEventsListener activityConnectionListener;
    boolean mOrientationDirty;
    protected int mOrigCamOrientation = 0;

    protected int camDisplayOrientation;
    float ratio = 1.0f;


    public boolean isPublishing() {
        return isPublishing;
    }


    public boolean isCameraPaused() {
        return isCameraPaused;
    }

    private LiveStreamManager() {

    }

    public static LiveStreamManager getInstance() {
        if (instance == null)
            instance = new LiveStreamManager();
        return instance;
    }

    public void initialize(SurfaceView surface, AppCompatActivity publishActivity, Red5StreamEventsListener activityConnectionListener, float ratio) {
        this.activityConnectionListener = activityConnectionListener;
        initialConfiguration = new R5Configuration(R5StreamProtocol.RTSP, HOST, PORT, CONTEXT_NAME, BUFFER_TIME);
        initialConfiguration.setLicenseKey(LICENSE_KEY);
        initialConfiguration.setBundleID(publishActivity.getPackageName());
        this.surface = surface;
        this.publishActivity = publishActivity;
        this.ratio = ratio;
        preview();
        // initialize stream with default configuration
        stream = new R5Stream(new R5Connection(initialConfiguration));
        stream.setView(surface);
        R5Camera r5Camera = new R5Camera(camera, (int) (streamCameraHeight * ratio), streamCameraHeight);
        //R5Camera r5Camera = new R5Camera(camera, 640, 360);
        stream.audioController.sampleRate = SAMPLE_RATE;

        r5Camera.setOrientation(camOrientation);
        r5Camera.setBitrate(BIT_RATE);
        r5Camera.setFramerate(FPS);
        R5AdaptiveBitrateController adaptor = new R5AdaptiveBitrateController();
        adaptor.AttachStream(stream);
        R5Microphone r5Microphone = new R5Microphone();
        stream.attachCamera(r5Camera);
        stream.attachMic(r5Microphone);
        stream.restrainVideo(false);
        stream.restrainAudio(false);
        stream.setScaleMode(2);
        stream.setListener(this);

        shouldRecordLocally = false;
    }


    public void preview() {
        //camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);
        camera = openFrontFacingCamera();
        camera.setDisplayOrientation((camOrientation + 180) % 360);
        surface.getHolder().addCallback(this);
        if (surface.getHolder() != null) {
            try {
                camera.setPreviewDisplay(surface.getHolder());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        camera.startPreview();
    }

    protected Camera openFrontFacingCamera() {
        int cameraCount = 0;
        Camera cam = null;
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        cameraCount = Camera.getNumberOfCameras();
        for (int camIdx = 0; camIdx < cameraCount; camIdx++) {
            Camera.getCameraInfo(camIdx, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                try {
                    cam = Camera.open(camIdx);
                    camOrientation = cameraInfo.orientation;
                    applyDeviceRotation();
                    break;
                } catch (RuntimeException e) {
                    e.printStackTrace();
                }
            }
        }

        return cam;
    }

    protected void applyDeviceRotation() {
        int rotation = publishActivity.getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 270;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 90;
                break;
        }

        Rect screenSize = new Rect();
        publishActivity.getWindowManager().getDefaultDisplay().getRectSize(screenSize);
        float screenAR = (screenSize.width() * 1.0f) / (screenSize.height() * 1.0f);
        Log.d("hh", "applyDeviceRotation: " + screenAR);
        if ((screenAR > 1 && degrees % 180 == 0) || (screenAR < 1 && degrees % 180 > 0))
            degrees += 180;

        System.out.println("Apply Device Rotation: " + rotation + ", degrees: " + degrees);

        camOrientation += degrees;

        camOrientation = camOrientation % 360;
    }

    protected Camera openBackFacingCamera() {
        int cameraCount = 0;
        Camera cam = null;
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        cameraCount = Camera.getNumberOfCameras();
        System.out.println("Number of cameras: " + cameraCount);
        for (int camIdx = 0; camIdx < cameraCount; camIdx++) {
            Camera.getCameraInfo(camIdx, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                try {
                    cam = Camera.open(camIdx);
                    camOrientation = cameraInfo.orientation;
                    applyInverseDeviceRotation();
                    break;
                } catch (RuntimeException e) {
                    e.printStackTrace();
                }
            }
        }

        return cam;
    }

    protected void applyInverseDeviceRotation() {
        int rotation = publishActivity.getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 270;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 90;
                break;
        }

        camOrientation += degrees;

        camOrientation = camOrientation % 360;
    }


    public void changeCamera(boolean shouldChangeCurrentFacing) {
        if (shouldChangeCurrentFacing) {
            if (currentCamMode == Camera.CameraInfo.CAMERA_FACING_FRONT)
                currentCamMode = Camera.CameraInfo.CAMERA_FACING_BACK;
            else
                currentCamMode = Camera.CameraInfo.CAMERA_FACING_FRONT;
        }

        if (stream != null) {
            R5Camera publishCam = (R5Camera) stream.getVideoSource();

            Camera newCam = null;

            //NOTE: Some devices will throw errors if you have a camera open when you attempt to open another
            publishCam.getCamera().stopPreview();
            publishCam.getCamera().release();

            //NOTE: The front facing camera needs to be 180 degrees further rotated than the back facing camera
            int rotate = 0;
            if (currentCamMode == Camera.CameraInfo.CAMERA_FACING_BACK) {
                newCam = openBackFacingCamera();
                rotate = 0;
            } else {
                newCam = openFrontFacingCamera();
                rotate = 180;
            }

            if (newCam != null) {

                newCam.setDisplayOrientation((camOrientation + rotate) % 360);

                publishCam.setCamera(newCam);
                publishCam.setOrientation(camOrientation);
                camera = newCam;
                camera.startPreview();
            }
        }
        stream.setScaleMode(2);
    }

    public void startPublishing(String host, int port, String contextName, String streamName) {
        R5Camera publishCam = (R5Camera) stream.getVideoSource();
        changeCamera(false);
        publishConfiguration = new R5Configuration(R5StreamProtocol.RTSP, host, port, contextName, BUFFER_TIME);
        publishConfiguration.setLicenseKey(LICENSE_KEY);
        publishConfiguration.setBundleID(publishActivity.getPackageName());
        stream.connection = new R5Connection(publishConfiguration);
        stream.connection.stream = stream;
        stream.setView(surface);
        stream.setScaleMode(2);
        stream.restrainAudio(false);
        stream.restrainVideo(false);
        mStreamName = streamName;
        stream.publish(streamName, R5Stream.RecordType.Append);
        isPublishing = true;
        publishCam.getCamera().startPreview();


        if (shouldRecordLocally) {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String fileName = "VID_" + timeStamp + ".mp4";
            stream.beginLocalRecording(publishActivity, fileName);
        }
    }

    public void resumePublishing() {
        if (publishConfiguration == null)
            return;
        stream = new R5Stream(new R5Connection(publishConfiguration));
        stream.setLogLevel(R5Stream.LOG_LEVEL_ERROR);
        stream.setView(surface);
//        if (camera == null)
        if (currentCamMode == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            camera = openFrontFacingCamera();
            camera.setDisplayOrientation((camOrientation + 180) % 360);
        } else {
            camera = openBackFacingCamera();
            camera.setDisplayOrientation((camOrientation) % 360);
        }
        //R5Camera r5Camera = new R5Camera(camera, 720, 1280);
        R5Camera r5Camera = new R5Camera(camera, (int) (streamCameraHeight * ratio), streamCameraHeight);

        r5Camera.setOrientation(camOrientation);
        r5Camera.setBitrate(BIT_RATE);
        r5Camera.setFramerate(FPS);
        R5AdaptiveBitrateController adaptor = new R5AdaptiveBitrateController();
        adaptor.AttachStream(stream);
        R5Microphone r5Microphone = new R5Microphone();
        stream.attachCamera(r5Camera);
        stream.attachMic(r5Microphone);
        stream.restrainVideo(false);
        stream.restrainAudio(false);
        stream.setScaleMode(2);
        stream.setListener(this);
        publishConfiguration.setLicenseKey(LICENSE_KEY);
        publishConfiguration.setBundleID(publishActivity.getPackageName());
        stream.connection = new R5Connection(publishConfiguration);
        stream.connection.stream = stream;
        stream.publish(mStreamName, R5Stream.RecordType.Append);
        isPublishing = true;
        camera.startPreview();
        if (shouldRecordLocally) {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String fileName = "VID_" + timeStamp + ".mp4";
            stream.beginLocalRecording(publishActivity, fileName);
        }
    }

    public void pausePublishing() {
        if (stream != null) {
            if (shouldRecordLocally)
                stream.endLocalRecording();
            stream.stop();
            camera.stopPreview();
            stream = null;
            isPublishing = false;
        }
    }

    public void stopPublishing() {
        if (stream != null) {
            if (shouldRecordLocally)
                stream.endLocalRecording();
            stream.stop();

            if (stream.getVideoSource() != null) {
                Camera c = ((R5Camera) stream.getVideoSource()).getCamera();
                c.stopPreview();
                c.release();
                camera = null;
            }
            stream = null;
        } else {
            if (camera != null) {
                camera.stopPreview();
                camera.release();
                camera = null;
            }
        }
        isPublishing = false;
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        try {
            if (camera != null) {
                camera.setPreviewDisplay(surfaceHolder);
                camera.startPreview();
            } else {
                camera = openFrontFacingCamera();
                camera.setPreviewDisplay(surfaceHolder);
                camera.startPreview();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

    }

    public interface PublishListener {
        void onPublishFlushBufferStart();

        void onPublishFlushBufferComplete();
    }

    @Override
    public void onConnectionEvent(R5ConnectionEvent event) {
        activityConnectionListener.onConnectionEvent(event);
        Log.d("Publisher", ":onConnectionEvent " + event.name());
        if (event.name() == R5ConnectionEvent.LICENSE_ERROR.name()) {
            Log.d("r5", "license error");
        }
        if (event.name() == R5ConnectionEvent.START_STREAMING.name()) {
            Log.d("r5", stream.getVideoSource().getName());
        } else if (event.name() == R5ConnectionEvent.BUFFER_FLUSH_START.name()) {
            if (mPublishListener != null) {
                mPublishListener.onPublishFlushBufferStart();
            }
        } else if (event.name() == R5ConnectionEvent.BUFFER_FLUSH_EMPTY.name() ||
                event.name() == R5ConnectionEvent.DISCONNECTED.name()) {
            if (mPublishListener != null) {
                mPublishListener.onPublishFlushBufferComplete();
                mPublishListener = null;
            }
        }
    }

    public void pauseCamera() {
        if (stream != null) {
            stream.restrainVideo(true);
            if (stream.getVideoSource() != null) {
                Camera c = ((R5Camera) stream.getVideoSource()).getCamera();
                c.stopPreview();
            }
        }
        isCameraPaused = true;
    }

    public void startCamera() {
        if (stream != null) {
            stream.restrainVideo(false);
            if (stream.getVideoSource() != null) {
                Camera c = ((R5Camera) stream.getVideoSource()).getCamera();
                c.startPreview();
            }
        }
        isCameraPaused = false;
    }

    public void checkOrientation() {
        if (mOrientationDirty) {
            Log.w("PublishDeviceOrientTest", "dirty orientation");
            reorient();
        }
    }

    protected void reorient() {
        if (stream == null)
            return;
        R5Camera r5Camera = (R5Camera) stream.getVideoSource();
        if (r5Camera == null)
            return;
        Camera camera = r5Camera.getCamera();

        camera.setDisplayOrientation((camDisplayOrientation + 180) % 360);
        r5Camera.setOrientation(camOrientation);
        mOrientationDirty = false;
        //call for a redraw to fix the aspect
        stream.setScaleMode(2);
    }

    public void updateOrientationParams(WindowManager windowManager) {

        int d_rotation = windowManager.getDefaultDisplay().getRotation();
        Log.w("PublishDeviceOrientTest", "d_rotation: " + d_rotation);

        int degrees = 0;
        switch (d_rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 270;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 90;
                break;
        }

        Rect screenSize = new Rect();
        windowManager.getDefaultDisplay().getRectSize(screenSize);
        float screenAR = (screenSize.width() * 1.0f) / (screenSize.height() * 1.0f);
        if ((screenAR > 1 && degrees % 180 == 0) || (screenAR < 1 && degrees % 180 > 0))
            degrees += 180;

        Log.w("PublishDeviceOrientTest", "degrees: " + degrees);

        camDisplayOrientation = (mOrigCamOrientation + degrees) % 360;
        camOrientation = d_rotation % 2 != 0 ? camDisplayOrientation - 180 : camDisplayOrientation;
        mOrientationDirty = true;
    }

    public void destroyStreamer() {
        if (instance != null)
            instance = null;

    }

    public interface Red5StreamEventsListener {
        void onConnectionEvent(R5ConnectionEvent event);
    }
}
