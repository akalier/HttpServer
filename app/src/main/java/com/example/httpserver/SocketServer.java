package com.example.httpserver;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;

import android.hardware.Camera;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.webkit.MimeTypeMap;

public class SocketServer extends Thread {
	
	ServerSocket serverSocket;
	public final int port = 12345;
	private boolean bRunning;
	private Semaphore semaphore;
	private Camera mCamera;

	Handler mHandler;

	int maxThreads;

	public SocketServer(Handler mHandler, int maxThreads, Camera mCamera) {
	    this.mHandler = mHandler;
	    this.maxThreads = maxThreads;
	    this.mCamera = mCamera;
		this.semaphore = new Semaphore(maxThreads, true);
		Log.d("SEMAPHORE", "starting with " + this.semaphore.availablePermits());
    }
	
	public void close() {
		try {
			serverSocket.close();
		} catch (IOException e) {
			Log.d("SERVER", "Error, probably interrupted in accept(), see log");
			e.printStackTrace();
		}
		bRunning = false;
	}
	
	public void run() {
        try {
        	Log.d("SERVER", "Creating Socket");
            serverSocket = new ServerSocket(port);
            bRunning = true;

            while (bRunning) {
            	Log.d("SERVER", "Socket Waiting for connection");
                Socket s = serverSocket.accept();
                if (semaphore.tryAcquire()) {
					Log.d("SEMAPHORE", "acquired, " + this.semaphore.availablePermits());
					ClientThread ct = new ClientThread(s, mHandler, semaphore, mCamera);
					ct.start();
				} else {
					Log.d("SEMAPHORE", "SERVER TOO BUSY");
				}

            }
        } 
        catch (IOException e) {
            if (serverSocket != null && serverSocket.isClosed())
            	Log.d("SERVER", "Normal exit");
            else {
            	Log.d("SERVER", "Error");
            	e.printStackTrace();
            }
        }
        finally {
			if (mCamera != null){
				mCamera.release();        // release the camera for other applications
				mCamera = null;
			}
        	serverSocket = null;
        	bRunning = false;
        }
    }

}
