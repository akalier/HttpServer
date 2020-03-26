package com.example.httpserver;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.app.Activity;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;

public class HttpServerActivity extends Activity implements OnClickListener{

	private SocketServer s;
	private TextView logView;
	private TextView bytesView;
	private EditText maxThreads;
	private long bytes = 0;
	public int maxThreadsNumber = 5;

	public static final String MSG_KEY = "Message";
	public static final String B_KEY = "Bytes";

	public static final String TAG = "CAM";

	private Camera mCamera;
	private CameraPreview mPreview;

	public static final int MEDIA_TYPE_IMAGE = 1;
	public static final int MEDIA_TYPE_VIDEO = 2;

	private boolean safeToTakePicture = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_http_server);

		Button btn1 = (Button)findViewById(R.id.button1);
		Button btn2 = (Button)findViewById(R.id.button2);

		logView = (TextView)findViewById(R.id.textLog);
		bytesView = (TextView)findViewById(R.id.bytesView);
		maxThreads = (EditText)findViewById(R.id.maxThreads);

		btn1.setOnClickListener(this);
		btn2.setOnClickListener(this);

		mCamera = getCameraInstance();

		mPreview = new CameraPreview(this, mCamera);
		FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
		preview.addView(mPreview);

		Button captureButton = (Button) findViewById(R.id.button_capture);
		captureButton.setOnClickListener(

				new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						// get an image from the camera
						mCamera.takePicture(null, null, mPicture);
					}
				}
		);

		MyTimerTask myTask = new MyTimerTask();
		Timer myTimer = new Timer();

		//myTimer.schedule(myTask, 5000,3000);
	}

	@Override
	protected void onPause() {
		super.onPause();
		//releaseMediaRecorder();       // if you are using MediaRecorder, release it first
		releaseCamera();              // release the camera immediately on pause event
	}

	private void releaseCamera(){
		if (mCamera != null){
			mCamera.release();        // release the camera for other applications
			mCamera = null;
		}
	}

	@Override
	public void onClick(View v) {
		if (v.getId() == R.id.button1) {
			if (maxThreads.getText().toString().equals("")) {
				maxThreadsNumber = 3;
			} else {
				maxThreadsNumber = Integer.parseInt(maxThreads.getText().toString());
			}
			maxThreads.setEnabled(false);
			s = new SocketServer(mHandler, maxThreadsNumber, mCamera);
			s.start();
		}
		if (v.getId() == R.id.button2) {
			s.close();
			maxThreads.setEnabled(true);
			try {
				s.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	private final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			Bundle bundle = msg.getData();
			String string = bundle.getString(MSG_KEY);
			Long bytesReceived = bundle.getLong(B_KEY);
			logView.setText(logView.getText() + string + "\n");
			bytes = bytes + bytesReceived;
			bytesView.setText("Total transfered: " + bytes + " B");

			final ScrollView scrollview = ((ScrollView) findViewById(R.id.scrollview));
			scrollview.post(new Runnable() {
				@Override
				public void run() {
					scrollview.fullScroll(ScrollView.FOCUS_DOWN);
				}
			});
		}
	};

	/** Check if this device has a camera */
	private boolean checkCameraHardware(Context context) {
		if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)){
			// this device has a camera
			return true;
		} else {
			// no camera on this device
			return false;
		}
	}

	/** A safe way to get an instance of the Camera object. */
	public Camera getCameraInstance(){
		Camera c = null;
		try {
			c = Camera.open(); // attempt to get a Camera instance
			this.safeToTakePicture = true;
		}
		catch (Exception e){
			// Camera is not available (in use or does not exist)
			Log.d(TAG, "Camera getInstance Failed: " + e.getMessage());
		}
		return c; // returns null if camera is unavailable
	}

	private Camera.PictureCallback mPicture = new Camera.PictureCallback() {

		@Override
		public void onPictureTaken(byte[] data, Camera camera) {

			File pictureFile = getOutputMediaFile(MEDIA_TYPE_IMAGE);
			if (pictureFile == null){
				safeToTakePicture = true;
				Log.d(TAG, "Error creating media file, check storage permissions");
				return;
			}

			try {
				FileOutputStream fos = new FileOutputStream(pictureFile);
				fos.write(data);
				fos.close();
			} catch (FileNotFoundException e) {
				Log.d(TAG, "File not found: " + e.getMessage());
			} catch (IOException e) {
				Log.d(TAG, "Error accessing file: " + e.getMessage());
			}

			//finished saving picture
			safeToTakePicture = true;
			mCamera.startPreview();

		}
	};

	/** Create a file Uri for saving an image or video */
	private static Uri getOutputMediaFileUri(int type){
		return Uri.fromFile(getOutputMediaFile(type));
	}

	/** Create a File for saving an image or video */
	private static File getOutputMediaFile(int type){
		// To be safe, you should check that the SDCard is mounted
		// using Environment.getExternalStorageState() before doing this.

		File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
				Environment.DIRECTORY_PICTURES), "MyCameraApp");
		// This location works best if you want the created images to be shared
		// between applications and persist after your app has been uninstalled.

		// Create the storage directory if it does not exist
		if (! mediaStorageDir.exists()){
			if (! mediaStorageDir.mkdirs()){
				Log.d("MyCameraApp", "failed to create directory");
				return null;
			}
		}

		// Create a media file name
		File mediaFile;
		if (type == MEDIA_TYPE_IMAGE){
			mediaFile = new File(mediaStorageDir.getPath() + File.separator +
					"IMG_1" + ".jpg");
		} else if(type == MEDIA_TYPE_VIDEO) {
			mediaFile = new File(mediaStorageDir.getPath() + File.separator +
					"VID_1" + ".mp4");
		} else {
			return null;
		}

		return mediaFile;
	}

	class MyTimerTask extends TimerTask {
		public void run() {
			try {
				if (safeToTakePicture) {
					mCamera.takePicture(null, null, mPicture);
					safeToTakePicture = false;
				}
			} catch (Exception e) {
				Log.d(TAG, "Timertask: " + e.getMessage());
			}
		}
	}

}
