package com.example.httpserver;

import android.hardware.Camera;
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
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;

import static com.example.httpserver.HttpServerActivity.mPreview;

public class ClientThread extends Thread {

    private Socket s;
    private static final String MSG_KEY = "Message";
    private static final String B_KEY = "Bytes";
    private static final String TAG = "Client Thread";
    private Handler mHandler;
    Semaphore semaphore;

    private Camera mCamera;


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

                    if (result.contains("/stream")) {

                        msg = mHandler.obtainMessage();
                        bundle.putString(MSG_KEY, getCurrentTime() + ": " + getStreamResponseShort());
                        bundle.putLong(B_KEY, 0);
                        msg.setData(bundle);
                        mHandler.sendMessage(msg);

                        out.write(getStreamResponse());
                        out.flush();

                        while(result.contains("/stream")) {
                            byte[] data = mPreview.previewData;
                            out.write("--OSMZ_boundary\n" +
                                    "Content-Type: image/jpeg" + "\n" +
                                    "Content-length: " + data.length + "\n\n");
                            out.flush();
                            o.write(data);
                        }

                    }
                    else if(result.contains("/cgi-bin")) {
                        String command = result.substring(9);
                        String[] commands = command.split("%20");

                        if (commands.length > 0) {
                            List<String> arguments = new ArrayList<String>();
                            arguments.add(commands[0]);

                            for (int i = 1; i < commands.length; i++) {
                                arguments.add(commands[1]);
                            }

                            ProcessBuilder processBuilder = new ProcessBuilder(arguments);
                            Process process = processBuilder.start();
                            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()), 1);

                            int c = 0;
                            while ((c = reader.read()) != -1) {
                                o.write(c);
                            }
                            o.flush();
                            process.destroy();

                        }

                        msg = mHandler.obtainMessage();
                        bundle.putString(MSG_KEY, getCurrentTime() + ": " + getCGIResponseShort());
                        bundle.putLong(B_KEY, 0);
                        msg.setData(bundle);
                        mHandler.sendMessage(msg);
                    }

                    else {

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

                                    out.write(getNotFoundResponse());
                                    msg = mHandler.obtainMessage();
                                    bundle.putString(MSG_KEY, getCurrentTime() + ": " + getNotFoundResponseShort());
                                    bundle.putLong(B_KEY, 0);
                                    msg.setData(bundle);
                                    mHandler.sendMessage(msg);
                                } else {
                                    //write contents of directory
                                    out.write("HTTP/1.0 200 OK\n Content-Type: text/html\n\n<html><body><ul>");
                                    out.flush();
                                    for (final File fileEntry : folder.listFiles()) {

                                        out.write("<li><a href='" + folder.getName() + "/" + fileEntry.getName() + "'>" + fileEntry.getName() + "</a></li>");
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
            Log.d(TAG, "Socket Closed");
        } catch (IOException e) {
            msg = mHandler.obtainMessage();
            bundle.putString(MSG_KEY, getCurrentTime() + ": ERROR: " + e.toString());
            bundle.putLong(B_KEY, 0);
            msg.setData(bundle);
            mHandler.sendMessage(msg);
            if (s != null && s.isClosed())
                Log.d(TAG, "Normal exit");
            else {
                Log.d(TAG, "Error");
                e.printStackTrace();
            }
        }
        finally {
            s = null;

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

    public static String getStreamResponse() {

        return ("HTTP/1.1 200 Ok\n" +
                "Content-Type: multipart/x-mixed-replace; boundary=\"--OSMZ_boundary\"" +"\n\n");
    }

    public static String getUnknownTypeResponse() {
        return "HTTP/1.0 404 NOT FOUND\nContent-Type: text/html\n\n<html><body><h3>Unknown type!</h3></body></html>";
    }

    public static String getNotFoundResponse() {
        return  "HTTP/1.0 404 NOT FOUND\nContent-Type: text/html\n\n<html><body><h3>File not found!</h3></body></html>";
    }

    public static String getCGIResponse() {
        return "HTTP/1.0 200 OK\nContent-Type: text/plain\n\n";
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

    public static String getStreamResponseShort() {

        return ("HTTP/1.1 200 Ok\nContent-Type: multipart/x-mixed-replace; boundary=\"--OSMZ_boundary\"");
    }

    public static String getCGIResponseShort() {
        return "HTTP/1.0 200 OK\nContent-Type: text/plain";
    }

    private String getCurrentTime() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss MM/dd/yyyy", Locale.US);
        return dateFormat.format(new Date());
    }


}


