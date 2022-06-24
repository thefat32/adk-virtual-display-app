package com.android.accessorychat;

import android.app.PendingIntent;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;


import android.os.Bundle;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;


import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
//import java.util.logging.Logger;


public class AccessoryChat extends Activity implements Runnable, TextView.OnEditorActionListener
{

    private static final String ACTION_USB_PERMISSION = "com.android.accessorychat.action.USB_PERMISSION";
    private final String TAG = "_ITE";

    private TextView mLog;
    private EditText mEditText;
    private UsbManager mUsbManager;
    private PendingIntent mPermissionIntent;
    private boolean mPermissionRequestPending;

    UsbAccessory mAccessory;
    ParcelFileDescriptor mFileDescriptor;
    FileInputStream mInputStream;
    FileOutputStream mOutputStream;

    private static final int MESSAGE_LOG = 1;
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        mUsbManager = (UsbManager)getSystemService(Context.USB_SERVICE);
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        registerReceiver(mUsbReceiver, filter);

        setContentView(R.layout.accessory_chat);
        mLog = findViewById(R.id.log);
        mEditText = findViewById(R.id.message);
        mEditText.setOnEditorActionListener(this);

        //enableControls(false);
    }

    @Override
    public void onResume()
    {
        super.onResume();

        Intent intent = getIntent();
        if (mInputStream != null && mOutputStream != null) {
            return;
        }

        UsbAccessory[] accessories = mUsbManager.getAccessoryList();
        UsbAccessory accessory = (accessories == null ? null : accessories[0]);
        if (accessory != null)
        {
            if (mUsbManager.hasPermission(accessory))
            {
                openAccessory(accessory);
            }
            else
            {
                synchronized (mUsbReceiver)
                {
                    if (!mPermissionRequestPending)
                    {
                        mUsbManager.requestPermission(accessory,mPermissionIntent);
                        mPermissionRequestPending = true;
                    }
                }
            }
        }
        else
        {
            Log.d(TAG, "mAccessory is null");
        }
    }

    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_DONE && mOutputStream != null) {
            try {
                mOutputStream.write(v.getText().toString().getBytes());
            } catch (IOException e) {
                Log.e(TAG, "write failed", e);
            }
            v.setText("");
            return true;
        }
        Log.d(TAG, "onEditorAction " + actionId + " event: " + event);
        return false;
    }

    @Override
    public void onPause() {
        super.onPause();
        closeAccessory();
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mUsbReceiver);
        super.onDestroy();
    }



    private void openAccessory(UsbAccessory accessory)
    {
        mFileDescriptor = mUsbManager.openAccessory(accessory);
        if (mFileDescriptor != null) {
            mAccessory = accessory;
            FileDescriptor fd = mFileDescriptor.getFileDescriptor();
            mInputStream = new FileInputStream(fd);
            mOutputStream = new FileOutputStream(fd);
            Thread thread = new Thread(null, this, "AccessoryController");
            thread.start();
            Log.d(TAG, "accessory opened");
            //enableControls(true);
        } else {
            Log.d(TAG, "accessory open fail");
        }
    }

    private void closeAccessory() {
        //enableControls(false);

        try {
            if (mFileDescriptor != null) {
                mFileDescriptor.close();
            }
        } catch (IOException e) {
        } finally {
            mFileDescriptor = null;
            mAccessory = null;
        }
    }

    /*
     * This receiver monitors for the event of a user granting permission to use
     * the attached accessory.  If the user has checked to always allow, this will
     * be generated following attachment without further user interaction.
     */
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbAccessory accessory = (UsbAccessory) intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        openAccessory(accessory);
                    } else {
                        Log.d(TAG, "permission denied for accessory "+ accessory);
                    }
                    mPermissionRequestPending = false;
                }
            } else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
                UsbAccessory accessory = (UsbAccessory) intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
                if (accessory != null && accessory.equals(mAccessory)) {
                    closeAccessory();
                }
            }
        }
    };

    /*
     * Runnable block that will poll the accessory data stream
     * for regular updates, posting each message it finds to a
     * Handler.  This is run on a spawned background thread.
     */
    public void run() {
        int ret = 0;
        byte[] buffer = new byte[16384];
        int i;

        while (ret >= 0)
        {
            try
            {
                ret = mInputStream.read(buffer);
                if (ret > 0) {
                    Message m = Message.obtain(mHandler, MESSAGE_LOG);
                    String text = new String(buffer, 0, ret);
                    Log.d(TAG, "chat: " + text);
                    m.obj = text;
                    mHandler.sendMessage(m);
                }
            } catch (IOException e) {
                break;
            }
        }
    }


    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, "messageHandler");
            switch (msg.what) {
                case MESSAGE_LOG:
                    mLog.setText(mLog.getText() + (String)msg.obj);
                    break;
            }
        }
    };

}