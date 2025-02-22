package com.example.httpserver;

import android.hardware.Camera;
import android.os.Bundle;
import android.app.Activity;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
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

public class HttpServerActivity extends Activity implements OnClickListener{

	private SocketServer s;
	private TextView logView;
	private TextView bytesView;
	private EditText maxThreads;
	private long bytes = 0;
	public int maxThreadsNumber = 5;

	public static final String MSG_KEY = "Message";
	public static final String B_KEY = "Bytes";

	public static final String TAG = "Http Server Activity";
	public static final String DIRECTORY_NAME = "MyCameraApp";

	public static final Camera mCamera = getCameraInstance();
	public static CameraPreview mPreview;

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

		mPreview = new CameraPreview(this);
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
			Long bytesReceived = bundle.getLong(B_KEY, 0);
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

	/** A safe way to get an instance of the Camera object. */
	public static Camera getCameraInstance(){
		Camera c = null;
		try {
			c = Camera.open(); // attempt to get a Camera instance
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

			File pictureFile = getOutputMediaFile();
			if (pictureFile == null){
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
			mCamera.startPreview();

		}
	};

	/** Create a File for saving an image or video */
	private static File getOutputMediaFile(){
		// To be safe, you should check that the SDCard is mounted
		// using Environment.getExternalStorageState() before doing this.

		File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
				Environment.DIRECTORY_PICTURES), DIRECTORY_NAME);
		// This location works best if you want the created images to be shared
		// between applications and persist after your app has been uninstalled.

		// Create the storage directory if it does not exist
		if (! mediaStorageDir.exists()){
			if (! mediaStorageDir.mkdirs()){
				Log.d(TAG, "failed to create directory");
				return null;
			}
		}

		// Create a media file name
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		File mediaFile;
		mediaFile = new File(mediaStorageDir.getPath() + File.separator +
					"IMG_" + timeStamp + ".jpg");

		return mediaFile;
	}


}
