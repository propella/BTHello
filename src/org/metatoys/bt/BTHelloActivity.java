package org.metatoys.bt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

import org.metatoys.bt.R;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
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
    private static final int REQUEST_ENABLE_BT = 1;
    private static final String NAME = "BTHello";
//    private static final UUID MY_UUID = UUID.fromString("d3c171a0-6e98-11e1-b0c4-0800200c9a66");
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    AcceptThread thread;

    BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
    }
    
    @Override
    protected void onActivityResult (int requestCode, int resultCode, Intent data) {
    	Log.d(TAG, "resultCode = " + resultCode + "data = " + data);
    	Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
    	if (pairedDevices.size() > 0) {
    	    for (BluetoothDevice device : pairedDevices) {
//    	        mArrayAdapter.add(device.getName() + "\n" + device.getAddress());
    	        Log.d(TAG, device.getName() + ": " + device.getAddress());
    	    }
    	}
    	thread = new AcceptThread();
    	thread.start();
    }
    
    @Override
    protected void onDestroy () {
    	if (thread != null) {
    		thread.cancel();
    	}
    }
    
    void manageConnectedSocket(BluetoothSocket socket) {
    	
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    	InputStream in = null;
    	OutputStream out = null;

    	try {
			in = socket.getInputStream();
	    	out = socket.getOutputStream();
		} catch (IOException e) {
			Log.e(TAG, "Unable to get sockets", e);
			return;
		}
    	
        try {
	    	Log.d(TAG, "Connection established.");
	        out.write("Hello! Press Q to quit.\r\n".getBytes());
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
        } catch (IOException e) {
			Log.e(TAG, "Connection closed.", e);
        }
        
        try {
			socket.close();
		} catch (IOException e) {}
    }
    
    private class AcceptThread extends Thread {
        private BluetoothServerSocket serverSocket;
     
        public AcceptThread() {
            try {
            	serverSocket = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(NAME, MY_UUID);
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
                    manageConnectedSocket(socket);
                }
            	Log.d(TAG, "The session was closed. Listen again.");
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
