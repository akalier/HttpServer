package com.example.httpserver;

import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Semaphore;

public class ClientThread extends Thread {

    private Socket s;
    public static final String MSG_KEY = "Message";
    public static final String B_KEY = "Bytes";
    public static final String TAG = "CAM";
    private Handler mHandler;
    Semaphore semaphore;
    Camera mCamera;

    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;

    public ClientThread(Socket s, Handler mHandler, Semaphore semaphore, Camera mCamera) {
        this.s = s;
        this.mHandler = mHandler;
        this.semaphore = semaphore;
        this.mCamera = mCamera;
    }

    public void run() {

        Message msg;
        Bundle bundle = new Bundle();

        try {
            final OutputStream o = s.getOutputStream();
            final BufferedWriter out = new BufferedWriter(new OutputStreamWriter(o));
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));

            Camera.PictureCallback mPicture = new Camera.PictureCallback() {

                @Override
                public void onPictureTaken(byte[] data, Camera camera) {

                    //OutputStream o = null;
                    try {
                        //o = s.getOutputStream();
                        //Log.d("SERVER", "" + o);

                        //BufferedWriter out = new BufferedWriter(new OutputStreamWriter(o));

                        String okResponse = getOKResponse("image/jpeg");
                        out.write(okResponse);
                        out.flush();

                        o.write(data);

                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    //mCamera.startPreview();

                }
            };

            String tmp = in.readLine();
            while(tmp != null && !tmp.isEmpty()) {
                if (tmp.startsWith("GET")) {
                    String result = tmp.split(" ")[1];
                    //Log.d("SERVER", "Requested file: " + result);
                    msg = mHandler.obtainMessage();
                    bundle.putString(MSG_KEY, getCurrentTime() + ": " + tmp);
                    bundle.putLong(B_KEY, 0);
                    msg.setData(bundle);
                    mHandler.sendMessage(msg);

                    File sdPath = Environment.getExternalStorageDirectory();

                    if (result.equals("/camera/snapshot")) {
                        mCamera.takePicture(null, null, mPicture);

                    } else {

                        File f = new File(sdPath + result);
                        if (f.isFile()) {
                            //Log.d("SERVER", "Full file path: " + f.toString());
                            if (f.exists()) {
                                //file exists
                                //Log.d("SERVER", "file exists!");
                                String mimeType = getMimeType(result);

                                FileInputStream fileInputStream = new FileInputStream(f);

                                out.write(getOKResponse(mimeType));
                                out.flush();
                                int content;
                                while ((content = fileInputStream.read()) != -1) {
                                    o.write(content);
                                }
                                msg = mHandler.obtainMessage();
                                bundle.putString(MSG_KEY, getCurrentTime() + ": " + getOKResponseShort(mimeType));
                                bundle.putLong(B_KEY, f.length());
                                msg.setData(bundle);
                                mHandler.sendMessage(msg);
                            }
                        } else {
                            File fileIndex = new File(sdPath + result + "index.html");
                            if (fileIndex.exists()) {
                                //file exists
                                //Log.d("SERVER", "file exists!");

                                String mimeType = getMimeType(result);
                                //Log.d("SERVER", "the file mimeType is: " + mimeType);

                                if (mimeType == null) {
                                    //Log.d("SERVER", "unknown type");
                                    out.write(getUnknownTypeResponse());
                                    msg = mHandler.obtainMessage();
                                    bundle.putString(MSG_KEY, getCurrentTime() + ": " + getUnknownTypeResponseShort());
                                    bundle.putLong(B_KEY, 0);
                                    msg.setData(bundle);
                                    mHandler.sendMessage(msg);
                                } else {
                                    FileInputStream fileInputStream = new FileInputStream(f);

                                    out.write(getOKResponse(mimeType));
                                    out.flush();
                                    int content;
                                    while ((content = fileInputStream.read()) != -1) {
                                        o.write(content);
                                    }
                                    msg = mHandler.obtainMessage();
                                    bundle.putString(MSG_KEY, getCurrentTime() + ": " + getOKResponseShort(mimeType));
                                    bundle.putLong(B_KEY, f.length());
                                    msg.setData(bundle);
                                    mHandler.sendMessage(msg);
                                }

                            } else {
                                //is a directory
                                final File folder = new File(sdPath + result);
                                if (!folder.exists()) {
                                    //file does not exist
                                    //Log.d("SERVER", "file does not exist.");
                                    out.write(getNotFoundResponse());
                                    msg = mHandler.obtainMessage();
                                    bundle.putString(MSG_KEY, getCurrentTime() + ": " + getNotFoundResponseShort());
                                    bundle.putLong(B_KEY, 0);
                                    msg.setData(bundle);
                                    mHandler.sendMessage(msg);
                                } else {
                                    out.write("HTTP/1.0 200 OK\n Content-Type: text/html\n\n<html><body><ul>");
                                    out.flush();
                                    for (final File fileEntry : folder.listFiles()) {
                                        //Log.d("FILE", fileEntry.getName());
                                        out.write("<li><a href='" + fileEntry.getName() + "'>" + fileEntry.getName() + "</a></li>");
                                    }
                                    out.write("</ul></body></html>");
                                    msg = mHandler.obtainMessage();
                                    bundle.putString(MSG_KEY, getCurrentTime() + ": " + "HTTP/1.0 200 OK\n Content-Type: text/html");
                                    bundle.putLong(B_KEY, 0);
                                    msg.setData(bundle);
                                    mHandler.sendMessage(msg);
                                }
                            }

                        }
                    }

                }

                tmp = in.readLine();
            }

            out.flush();

            semaphore.release();
            Log.d("SEMAPHORE", "released, " + this.semaphore.availablePermits());
            s.close();
            Log.d("SERVER", "Socket Closed");
        } catch (IOException e) {
            msg = mHandler.obtainMessage();
            bundle.putString(MSG_KEY, getCurrentTime() + ": ERROR: " + e.toString());
            bundle.putLong(B_KEY, 0);
            msg.setData(bundle);
            mHandler.sendMessage(msg);
            if (s != null && s.isClosed())
                Log.d("SERVER", "Normal exit");
            else {
                Log.d("SERVER", "Error");
                e.printStackTrace();
            }
        }
        finally {
            s = null;
            //bRunning = false;

            mCamera.stopPreview();

        }

    }

    public static String getMimeType(String url) {
        String type = null;
        String extension = MimeTypeMap.getFileExtensionFromUrl(url);
        if (extension != null) {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }
        return type;
    }

    public static String getOKResponse(String contentType) {
        return "HTTP/1.0 200 OK\n Content-Type: " + contentType + "\n\n";
    }

    public static String getUnknownTypeResponse() {
        return "HTTP/1.0 404 NOT FOUND\nContent-Type: text/html\n\n<html><body><h3>Unknown type!</h3></body></html>";
    }

    public static String getNotFoundResponse() {
        return  "HTTP/1.0 404 NOT FOUND\nContent-Type: text/html\n\n<html><body><h3>File not found!</h3></body></html>";
    }

    public static String getOKResponseShort(String contentType) {
        return "HTTP/1.0 200 OK\n Content-Type: " + contentType;
    }

    public static String getUnknownTypeResponseShort() {
        return "HTTP/1.0 404 NOT FOUND\nContent-Type: text/html";
    }

    public static String getNotFoundResponseShort() {
        return  "HTTP/1.0 404 NOT FOUND\nContent-Type: text/html";
    }

    private String getCurrentTime() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss MM/dd/yyyy", Locale.US);
        return dateFormat.format(new Date());
    }

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

}


