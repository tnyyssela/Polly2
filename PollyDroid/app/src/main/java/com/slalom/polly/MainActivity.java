package com.slalom.polly;

import android.app.PendingIntent;
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
import com.microsoft.projectoxford.face.FaceServiceClient;
import com.microsoft.projectoxford.face.FaceServiceRestClient;
import com.microsoft.projectoxford.face.contract.Candidate;
import com.microsoft.projectoxford.face.contract.Face;
import com.microsoft.projectoxford.face.contract.IdentifyResult;
import com.microsoft.projectoxford.face.contract.Person;

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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.Date;
import java.util.UUID;

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

public class MainActivity extends Activity implements TextureView.SurfaceTextureListener, View.OnClickListener {

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

    private int azureThrottleTimeout = 5000;
    private long timeOfLastAzureRequest = System.currentTimeMillis();


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

        initUI();

        initFacialRecognition();

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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == IntentRequestCodes.DetectFace) {
            switch (resultCode) {
                case DetectFaceService.INVALID_URL_CODE:
//                    handleInvalidURL();
                    break;
                case DetectFaceService.ERROR_CODE:
//                    handleError(data);
                    break;
                case DetectFaceService.RESULT_CODE:
                    handleDetectFace(data);
                    break;
            }
            handleDetectFace(data);
        }
        if (requestCode == IntentRequestCodes.IdentifyFace) {
            switch (resultCode) {
                case IdentifyFaceService.INVALID_URL_CODE:
//                    handleInvalidURL();
                    break;
                case IdentifyFaceService.ERROR_CODE:
//                    handleError(data);
                    break;
                case IdentifyFaceService.RESULT_CODE:
                    handleIdentifyFace(data);
                    break;
            }
            handleIdentifyFace(data);
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void handleDetectFace(Intent data) {

    }


    private void handleIdentifyFace(Intent data) {


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
        for (int i = 0; i <facesArray.length; i++) {
            Imgproc.rectangle(aInputFrame, facesArray[i].tl(), facesArray[i].br(), FACE_RECT_COLOR, 3);

            byte[] data = new byte[0];
            grayscaleImage.get(0, 0, data);


            //TODO: ianb - use the services that you made for this express purpose
            String name = identifyPeople(data);
            if(name != null) {
                showToast(name);
            }
        }

        return aInputFrame;
    }

    //TODO: ianb - persist frame, support multiple people, show location on still image
    private String identifyPeople(byte[] faceBuffer) {
        if (System.currentTimeMillis() <= timeOfLastAzureRequest + azureThrottleTimeout) {
            return null;
        }

        timeOfLastAzureRequest = System.currentTimeMillis();

        int confidenceThreshold = 0;

        FaceServiceClient faceServiceClient = new FaceServiceRestClient("3776b03f131645b8b7c3f1653dc35ac0");


/*
        URI imageUri = null;
        try {
            // Retrieve storage account from connection-string.
            CloudStorageAccount storageAccount = CloudStorageAccount.parse("DefaultEndpointsProtocol=https;AccountName=friend;AccountKey=My1Ai1m1VrsiQIhJ78cdeKKvTM0HCMix1a1WIMVZrxcXe4jtNIn3cLf+inWiACqMf1i1EOH2QVshf7oTqCKc6A==;EndpointSuffix=core.windows.net");

            // Create the blob client.
            CloudBlobClient blobClient = storageAccount.createCloudBlobClient();

            // Retrieve reference to a previously created container.
            CloudBlobContainer container = blobClient.getContainerReference("faces");

            UUID blobId = UUID.randomUUID();

            // Create or overwrite the blob with contents from byte array input.
            CloudBlockBlob blob = container.getBlockBlobReference(blobId.toString() + ".jpg");
            blob.upload(new ByteArrayInputStream(faceBuffer), faceBuffer.length);

            imageUri = blob.getUri();

        } catch (Exception e) {

        }
*/
        try {
            Face[] faces = faceServiceClient.detect(new ByteArrayInputStream(faceBuffer), true, true, new FaceServiceClient.FaceAttributeType[0]);
            UUID[] faceIds = new UUID[faces.length];

            for (int i = 0; i < faces.length; i++) {
                faceIds[i] = faces[i].faceId;
            }

            IdentifyResult[] identifyResults = faceServiceClient.identity(IdentifyFaceService.PERSON_GROUP_ID_EXTRA, faceIds, faceIds.length);

            Candidate candidate = identifyResults[0].candidates.get(0);

            if (candidate.confidence > confidenceThreshold) {
                return faceServiceClient.getPerson(IdentifyFaceService.PERSON_GROUP_ID_EXTRA,candidate.personId).name;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;

        /*
        PendingIntent detectFacePendingResult = createPendingResult(
                0, new Intent(), 0);
        Intent detectFaceIntent = new Intent(getApplicationContext(), DetectFaceService.class);
        detectFaceIntent.putExtra(DetectFaceService.PENDING_RESULT_EXTRA, detectFacePendingResult);
        detectFaceIntent.putExtra(DetectFaceService.FACE_URI_EXTRA, imageUri);
        startService(detectFaceIntent);
        */
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

    private void initFacialRecognition() {
        PendingIntent detectFacePendingResult = createPendingResult(
                0, new Intent(), 0);
        Intent detectFaceIntent = new Intent(getApplicationContext(), DetectFaceService.class);
        detectFaceIntent.putExtra(DetectFaceService.PENDING_RESULT_EXTRA, detectFacePendingResult);
        startService(detectFaceIntent);


        PendingIntent identifyFacePendingResult = createPendingResult(
                0, new Intent(), 0);
        Intent identifyFaceIntent = new Intent(getApplicationContext(), IdentifyFaceService.class);
        identifyFaceIntent.putExtra(IdentifyFaceService.PENDING_RESULT_EXTRA, identifyFacePendingResult);

        startService(detectFaceIntent);

    }
}