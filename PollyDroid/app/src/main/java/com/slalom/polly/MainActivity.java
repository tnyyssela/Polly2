package com.slalom.polly;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.app.Activity;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.*;
//import org.opencv.core.Mat;
//import org.opencv.core.MatOfRect;
//import org.opencv.core.Rect;
//import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import dji.common.camera.SettingsDefinitions;
import dji.common.camera.SystemState;
import dji.common.error.DJIError;
import dji.common.product.Model;
import dji.common.useraccount.UserAccountState;
import dji.common.util.CommonCallbacks;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.Camera;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;
import dji.sdk.useraccount.UserAccountManager;

public class MainActivity extends Activity implements TextureView.SurfaceTextureListener, View.OnClickListener, ServiceResultReceiver.Receiver {

    private static final String TAG = MainActivity.class.getName();
    private static final Scalar    FACE_RECT_COLOR     = new Scalar(0, 255, 0, 255);

    protected VideoFeeder.VideoDataCallback mReceivedVideoDataCallBack = null;

    // Codec for video live view
    protected DJICodecManager mCodecManager = null;

    protected TextureView mVideoSurface = null;
    protected SurfaceView mOCVSurfaceView = null;
    private SurfaceHolder mOCVSurfaceViewHolder = null;
    private Button mCaptureBtn, mShootPhotoModeBtn, mRecordVideoModeBtn;
    private ToggleButton mRecordBtn;
    private TextView recordingTime;

    private Handler handler;
    private CascadeClassifier cascadeClassifier;
    private Mat grayscaleImage;
    private Mat rgbImage;
    private float mRelativeFaceSize   = 0.2f;
    private int mAbsoluteFaceSize   = 0;
    private Paint mPaint;

    private int numberOfFacesInFrame = 0;
    private int azureThrottleTimeout = 5000;
    private long timeOfLastAzureRequest = System.currentTimeMillis();

    private ServiceResultReceiver serviceReceiver;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                    Log.i(TAG, "OpenCV loaded successfully");
                    initializeOpenCVDependencies();
                    break;
                default:
                    super.onManagerConnected(status);
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        handler = new Handler();

        serviceReceiver = new ServiceResultReceiver(handler);
        serviceReceiver.setReceiver(this);

        initUI();

        // The callback for receiving the raw H264 video data for camera live view
        mReceivedVideoDataCallBack = new VideoFeeder.VideoDataCallback() {

            @Override
            public void onReceive(byte[] videoBuffer, int size) {
                if (mCodecManager != null) {
                    mCodecManager.sendDataToDecoder(videoBuffer, size);
                }
            }
        };

        //NOT NEEDED RIGHT NOW
        Camera camera = PollyApplication.getCameraInstance();

        if (camera != null) {

            camera.setSystemStateCallback(new SystemState.Callback() {
                //@Override
                //public void onCamera

                @Override
                public void onUpdate(SystemState cameraSystemState) {
                    if (null != cameraSystemState) {

                        int recordTime = cameraSystemState.getCurrentVideoRecordingTimeInSeconds();
                        int minutes = (recordTime % 3600) / 60;
                        int seconds = recordTime % 60;

                        final String timeString = String.format("%02d:%02d", minutes, seconds);
                        final boolean isVideoRecording = cameraSystemState.isRecording();

                        MainActivity.this.runOnUiThread(new Runnable() {

                            @Override
                            public void run() {

                                recordingTime.setText(timeString);

                                /*
                                 * Update recordingTime TextView visibility and mRecordBtn's check state
                                 */
                                if (isVideoRecording) {
                                    recordingTime.setVisibility(View.VISIBLE);
                                } else {
                                    recordingTime.setVisibility(View.INVISIBLE);
                                }
                            }
                        });
                    }
                }
            });

        }

    }

    @Override
    public void onReceiveServiceResult(int serviceCode, int resultCode, Bundle resultData) {
        try {
            String e = "";
            if (serviceCode == ServiceCodes.DetectFace) {
                switch (resultCode) {
                    case DetectFaceService.ERROR_CODE:
                        e = resultData.getString("e");
                        Log.i("MainActivity", e);
                        showToast(e);
                        break;
                    case DetectFaceService.DEBUG_CODE:
                        e = resultData.getString("e");
                        Log.i("MainActivity", e);
                        showToast(e);
                        break;
                    case DetectFaceService.SUCCESS_CODE:
                        handleDetectFace(resultData);
                        break;
                }
            }
            if (serviceCode == ServiceCodes.IdentifyFacePerson) {
                switch (resultCode) {
                    case IdentifyFacePersonService.ERROR_CODE:
                        e = resultData.getString("e");
                        Log.i("MainActivity", e);
                        showToast(e);
                        break;
                    case IdentifyFacePersonService.DEBUG_CODE:
                        e = resultData.getString("e");
                        Log.i("MainActivity", e);
                        showToast(e);
                        break;
                    case IdentifyFacePersonService.SUCCESS_CODE:
                        showToast(resultData.getString("e"));
                        handleIdentifyFacePerson(resultData);
                        break;
                }
            }
        } catch (Exception e) {
            showToast(e.getMessage());
            Log.i("MainActivity", e.getMessage());
        }

    }

    private void handleDetectFace(Bundle data) {
        try {
            String[] faceIds = data.getStringArray(DetectFaceService.FACES_EXTRA_KEY);

            showToast("faces retrieved");
            Log.i("MainActivity", "faces retrieved");


            if (faceIds == null || faceIds.length < 1) {
                showToast("no face ids");
                Log.i("MainActivity", "no face ids");
                return;
            }

            showToast("start identify service");
            Log.i("MainActivity", "start identify service");

            Intent IdentifyFacePersonIntent = new Intent(Intent.ACTION_SYNC, null, this, IdentifyFacePersonService.class);
            IdentifyFacePersonIntent.putExtra(ServiceResultReceiver.RECEIVER_KEY, serviceReceiver);
            IdentifyFacePersonIntent.putExtra(IdentifyFacePersonService.FACE_IDS_EXTRA_KEY, faceIds);

            startService(IdentifyFacePersonIntent);

        } catch (Exception e) {
            showToast(e.getMessage());
        }

    }

    private void handleIdentifyFacePerson(Bundle data) {
        try {
            Log.i("MainActivity", "handle identify face");

            String name = data.getString(IdentifyFacePersonService.NAME_EXTRA_KEY);
            if(name != null) {
                showToast(name);
            } else {
                showToast("name not found");
            }
        } catch (Exception e) {
            showToast(e.getMessage());
            Log.i("MainActivity", e.getMessage());
        }

    }
    private void initializeOpenCVDependencies() {

        try {
            // Copy the resource into a temp file so OpenCV can load it
            InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
            File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
            File mCascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");
            FileOutputStream os = new FileOutputStream(mCascadeFile);


            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();

            // Load the cascade classifier
            cascadeClassifier = new CascadeClassifier(mCascadeFile.getAbsolutePath());

        } catch (Exception e) {
            Log.e(TAG, "Error loading cascade", e);
        }
    }

    protected void onProductChange() {
        initPreviewer();
        //loginAccount();
    }

    private void loginAccount() {

        UserAccountManager.getInstance().logIntoDJIUserAccount(this,
                new CommonCallbacks.CompletionCallbackWith<UserAccountState>() {
                    @Override
                    public void onSuccess(final UserAccountState userAccountState) {
                        Log.e(TAG, "Login Success");
                    }

                    @Override
                    public void onFailure(DJIError error) {
                        showToast("Login Error:"
                                + error.getDescription());
                    }
                });
    }

    @Override
    public void onResume() {
        Log.e(TAG, "onResume");
        super.onResume();

        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }


        initPreviewer();
        onProductChange();

        if (mVideoSurface == null) {
            Log.e(TAG, "mVideoSurface is null");
        }
    }

    @Override
    public void onPause() {
        Log.e(TAG, "onPause");
        uninitPreviewer();
        super.onPause();
    }

    @Override
    public void onStop() {
        Log.e(TAG, "onStop");
        super.onStop();
    }

    public void onReturn(View view) {
        Log.e(TAG, "onReturn");
        this.finish();
    }

    @Override
    protected void onDestroy() {
        Log.e(TAG, "onDestroy");
        uninitPreviewer();
        super.onDestroy();
    }

    private void initUI() {
        // init mVideoSurface
        mVideoSurface = (TextureView) findViewById(R.id.video_previewer_surface);
        mOCVSurfaceView = (SurfaceView) findViewById(R.id.ocvideo_surface_view);

        recordingTime = (TextView) findViewById(R.id.timer);
        mCaptureBtn = (Button) findViewById(R.id.btn_capture);
        mRecordBtn = (ToggleButton) findViewById(R.id.btn_record);
        mShootPhotoModeBtn = (Button) findViewById(R.id.btn_shoot_photo_mode);
        mRecordVideoModeBtn = (Button) findViewById(R.id.btn_record_video_mode);
        mPaint = new Paint();

        if (null != mVideoSurface) {
            mVideoSurface.setSurfaceTextureListener(this);
        }

        if(null != mOCVSurfaceView) {
            mOCVSurfaceViewHolder = mOCVSurfaceView.getHolder();
        }

//        DisplayMetrics displaymetrics = new DisplayMetrics();
//        getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
//        int width = displaymetrics.widthPixels;
//        int halfScreenWidth = width/2;
//        mVideoSurface.setLayoutParams(new FrameLayout.LayoutParams(halfScreenWidth, mVideoSurface.getHeight()));
//        mOCVSurfaceView.setLayoutParams(new FrameLayout.LayoutParams(halfScreenWidth, mOCVSurfaceView.getHeight()));

        mCaptureBtn.setOnClickListener(this);
        mRecordBtn.setOnClickListener(this);
        mShootPhotoModeBtn.setOnClickListener(this);
        mRecordVideoModeBtn.setOnClickListener(this);

        recordingTime.setVisibility(View.INVISIBLE);

        mRecordBtn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    startRecord();
                } else {
                    stopRecord();
                }
            }
        });
    }

    private void initPreviewer() {

        BaseProduct product = PollyApplication.getProductInstance();

        if (product == null || !product.isConnected()) {
            showToast(getString(R.string.disconnected));
        } else {
            if (null != mVideoSurface) {
                mVideoSurface.setSurfaceTextureListener(this);
            }
            if (!product.getModel().equals(Model.UNKNOWN_AIRCRAFT)) {
                if (VideoFeeder.getInstance().getVideoFeeds() != null
                        && VideoFeeder.getInstance().getVideoFeeds().size() > 0)
                {
                    VideoFeeder.getInstance().getVideoFeeds().get(0).setCallback(mReceivedVideoDataCallBack);
                }
            }
        }
    }

    private void uninitPreviewer() {
        Camera camera = PollyApplication.getCameraInstance();
        if (camera != null) {
            // Reset the callback
            VideoFeeder.getInstance().getVideoFeeds().get(0).setCallback(null);
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.e(TAG, "onSurfaceTextureAvailable");
        showToast("onSurfaceTextureAvailable");

        if (mCodecManager == null) {
            mCodecManager = new DJICodecManager(this, surface, width, height);
        }
        grayscaleImage = new Mat();
        rgbImage = new Mat();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.e(TAG, "onSurfaceTextureSizeChanged");
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.e(TAG, "onSurfaceTextureDestroyed");
        if (mCodecManager != null) {
            mCodecManager.cleanSurface();
            mCodecManager = null;
        }

        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        //video frame is received
        Bitmap bitmap = mVideoSurface.getBitmap();
        rgbImage = new Mat();
        Utils.bitmapToMat(bitmap, rgbImage);
        Mat frameMat = detectFacesInFrame(rgbImage);

        Utils.matToBitmap(frameMat, bitmap);
        DrawToSurface(bitmap);
    }

    public Mat detectFacesInFrame(Mat aInputFrame) {
        // Create a grayscale image
        Imgproc.cvtColor(aInputFrame, grayscaleImage, Imgproc.COLOR_RGBA2RGB);

        if (mAbsoluteFaceSize == 0) {
            int height = grayscaleImage.rows();
            if (Math.round(height * mRelativeFaceSize) > 0) {
                mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
            }
        }

        MatOfRect faces = new MatOfRect();

        // Use the classifier to detect faces
        if (cascadeClassifier != null) {
            cascadeClassifier.detectMultiScale(grayscaleImage, faces, 1.1, 2, 2,
                    new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());
        }

        // If there are any faces found, draw a rectangle around it
        Rect[] facesArray = faces.toArray();
        for (int i = 0; i < facesArray.length; i++) {
            Imgproc.rectangle(aInputFrame, facesArray[i].tl(), facesArray[i].br(), FACE_RECT_COLOR, 3);

            try {
                //instantiating an empty MatOfByte class
                MatOfByte matOfByte = new MatOfByte();

                //Converting the Mat object to MatOfByte
                Imgcodecs.imencode(".jpg", aInputFrame, matOfByte);

                byte[] data = matOfByte.toArray();

//            if (data.length != 0 && facesArray.length != numberOfFacesInFrame) {
                if (data.length != 0) {
                    numberOfFacesInFrame = facesArray.length;
                    identifyPeople(data);
                }
            } catch (Exception e) {
                showToast(e.getMessage());
            }

        }

        return aInputFrame;
    }

    //TODO: ianb - persist frame, support multiple people, show location on still image
    private void identifyPeople(byte[] faceBuffer) {

        try {
            if (System.currentTimeMillis() <= timeOfLastAzureRequest + azureThrottleTimeout) {
                Log.i("MainActivity", "throttled");

                return;
            }

            timeOfLastAzureRequest = System.currentTimeMillis();

            final Intent detectFaceIntent = new Intent(Intent.ACTION_SYNC, null, this, DetectFaceService.class);
            detectFaceIntent.putExtra(ServiceResultReceiver.RECEIVER_KEY, serviceReceiver);

            detectFaceIntent.putExtra(DetectFaceService.FACE_BUFFER_EXTRA_KEY, faceBuffer);
            startService(detectFaceIntent);

        } catch (Exception e) {
            showToast(e.getMessage());
            Log.i("MainActivity", e.getMessage());

        }
    }

    private void DrawToSurface(Bitmap bitmap)
    {
        Canvas canvas = mOCVSurfaceViewHolder.lockCanvas();

        if (canvas != null)
        {
            if(bitmap != null)
            {
                canvas.drawBitmap(bitmap,0,0,mPaint);
            }
            else {
                Log.d(TAG, "******************* bitmap is null !!!!!!!!!");
            }
            mOCVSurfaceViewHolder.unlockCanvasAndPost(canvas);
        }
        else {
            Log.d(TAG, "******************* canvas is null !!!!!!!!!");
        }
    }

    public Bitmap CompressImage(Bitmap bitmap) {

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
        byte[] bitmapData = outputStream.toByteArray();
        Bitmap decodedBitmap = BitmapFactory.decodeByteArray(bitmapData, 0, bitmapData.length);
        //Bitmap scaledBitmap = Bitmap.createScaledBitmap(decodedBitmap, mOCVSurfaceView.getWidth(), mOCVSurfaceView.getHeight(), true);

        return decodedBitmap;
    }

    public void showToast(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onClick(View v) {

        switch (v.getId()) {
            case R.id.btn_capture: {
                captureAction();
                break;
            }
            case R.id.btn_shoot_photo_mode: {
                switchCameraMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO);
                break;
            }
            case R.id.btn_record_video_mode: {
                switchCameraMode(SettingsDefinitions.CameraMode.RECORD_VIDEO);
                break;
            }
            default:
                break;
        }
    }

    private void switchCameraMode(SettingsDefinitions.CameraMode cameraMode) {

        Camera camera = PollyApplication.getCameraInstance();
        if (camera != null) {
            camera.setMode(cameraMode, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError error) {

                    if (error == null) {
                        showToast("Switch Camera Mode Succeeded");
                    } else {
                        showToast(error.getDescription());
                    }
                }
            });
        }
    }

    // Method for taking photo
    private void captureAction(){

        final Camera camera = PollyApplication.getCameraInstance();
        if (camera != null) {
            SettingsDefinitions.ShootPhotoMode photoMode = SettingsDefinitions.ShootPhotoMode.SINGLE; // Set the camera capture mode as Single mode
            camera.setShootPhotoMode(photoMode, new CommonCallbacks.CompletionCallback(){
                @Override
                public void onResult(DJIError djiError) {
                    if (null == djiError) {
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                camera.startShootPhoto(new CommonCallbacks.CompletionCallback() {
                                    @Override
                                    public void onResult(DJIError djiError) {
                                        if (djiError == null) {
                                            showToast("take photo: success");
                                        } else {
                                            showToast(djiError.getDescription());
                                        }
                                    }
                                });
                            }
                        }, 2000);
                    }
                }
            });
        }
    }

    // Method for starting recording
    private void startRecord() {

        final Camera camera = PollyApplication.getCameraInstance();
        if (camera != null) {
            camera.startRecordVideo(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError == null) {
                        showToast("Record video: success");
                    } else {
                        showToast(djiError.getDescription());
                    }
                }
            }); // Execute the startRecordVideo API
        }
    }

    // Method for stopping recording
    private void stopRecord() {

        Camera camera = PollyApplication.getCameraInstance();
        if (camera != null) {
            camera.stopRecordVideo(new CommonCallbacks.CompletionCallback() {

                @Override
                public void onResult(DJIError djiError) {
                    if (djiError == null) {
                        showToast("Stop recording: success");
                    } else {
                        showToast(djiError.getDescription());
                    }
                }
            }); // Execute the stopRecordVideo API
        }

    }
}