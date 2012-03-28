package org.metatoys.bt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

import org.metatoys.bt.R;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.util.Log;

/**
 * A simple Bluetooth example activity
 * <pre>
 * sudo screen /dev/tty.SC-03D-BTHello
 * </pre>
 */
public class BTHelloActivity extends Activity {
	private static final String TAG = "BTHello";
    private static final String SERVICE_NAME = "BTHello";
    private static final String SERIAL_PORT_SERVICE_ID = "00001101-0000-1000-8000-00805F9B34FB";
    private static final UUID SERVICE_ID = UUID.fromString(SERIAL_PORT_SERVICE_ID);
    AcceptThread thread;

    BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
    	thread = new AcceptThread();
    	thread.start();
    }
    
    @Override
    protected void onDestroy () {
    	if (thread != null) {
    		thread.cancel();
    	}
    }
    
    private class AcceptThread extends Thread {
        private BluetoothServerSocket serverSocket;
     
        public AcceptThread() {
            try {
            	serverSocket = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_ID);
            } catch (IOException e) { }
        }
     
        public void run() {
            BluetoothSocket socket = null;
            while (true) {
                try {
                    socket = serverSocket.accept();
                } catch (IOException e) {
					Log.e(TAG, "Fail to accept.", e);
                    break;
                }
            	Log.d(TAG, "A connection was accepted.");
                if (socket != null) {
                    connect(socket);
                }
            	Log.d(TAG, "The session was closed. Listen again.");
            }
        }
        
        private void connect(BluetoothSocket socket) {
        	
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

        	try {
        		InputStream in = socket.getInputStream();
        		OutputStream out = socket.getOutputStream();
        		
    	    	Log.d(TAG, "Connection established.");
    	        out.write("Hello I'm Bluetooth! Press Q to quit.\r\n".getBytes());
    	        while (true) {
                	byte[] buffer = new byte[1024];
                    int bytes = in.read(buffer);
                    Log.d(TAG, "input =" + new String(buffer, 0, bytes));
                    out.write((df.format(new Date()) + ": " + new String (buffer, 0, bytes) + "\r\n").getBytes());
                    if (buffer[0] == 'q') {
                        out.write(("Bye!\r\n").getBytes());
                    	break;
                    }
    	        }
    	        socket.close();
    	    	
    		} catch (IOException e) {
    			Log.e(TAG, "Something bad happened!", e);
    		}
        }
     
        /** Will cancel the listening socket, and cause the thread to finish */
        public void cancel() {
            try {
                serverSocket.close();
    	    	Log.d(TAG, "The server socket is closed.");
            } catch (IOException e) { }
        }
    }
}
