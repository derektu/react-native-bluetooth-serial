package idv.derektu.rnbluetoothserial;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;


/**
 * Created by derektu on 4/16/16.
 */
public class BTSerialModule extends ReactContextBaseJavaModule implements LifecycleEventListener, ActivityEventListener {

    public static final String REACT_CLASS = "RNBluetoothSerial";

    private BluetoothAdapter mBTAdapter;
    private BTSerialService mBTSerialService;

    private Callback mConnectStatusCB;
    private Callback mEnableBTCB;
    private String mDeviceConnected;

    StringBuffer mBuffer = new StringBuffer(1024);

    // Debugging
    private static final String TAG = "BTSerial";
    private static final boolean D = true;

    // Message types sent from the BTSerialService Handler
    //
    public static final int MESSAGE_CONNECT_OK = 1; // bundle: KEY_DEVICENAME=<device name>
    public static final int MESSAGE_CONNECT_FAIL = 2;
    public static final int MESSAGE_CONNECT_LOST = 3;
    public static final int MESSAGE_READ = 4;
    public static final int MESSAGE_WRITE = 5;

    public static final String KEY_DEVICENAME = "devicename";
    private static final String KEY_AVAILABLE = "available";

    private static final int REQUEST_ENABLE_BT = 1001;

    private static final String charsetDefault = "UTF-8";

    public BTSerialModule(ReactApplicationContext reactContext) {
        super(reactContext);

        mBTAdapter = BluetoothAdapter.getDefaultAdapter();
        mBTSerialService = new BTSerialService(mHandler);

        reactContext.addLifecycleEventListener(this);
        reactContext.addActivityEventListener(this);
    }

    @Override
    public String getName() {
        return REACT_CLASS;
    }

    private static final String EVENT_CONNECTION_LOST = "eventConnectionLost";
    private static final String EVENT_DATA_AVAILABLE = "eventDataAvailable";

    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        constants.put(EVENT_CONNECTION_LOST, EVENT_CONNECTION_LOST);
        constants.put(EVENT_DATA_AVAILABLE, EVENT_DATA_AVAILABLE);
        return constants;
    }


    @Override
    public void onHostResume() {
        // TODO: onResume
    }

    @Override
    public void onHostPause() {
        // TODO: onPause
    }

    @Override
    public void onHostDestroy() {
        mBTSerialService.stop();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (mEnableBTCB != null) {
                mEnableBTCB.invoke(null, resultCode == Activity.RESULT_OK);
                mEnableBTCB = null;
            }
        }
    }

    @ReactMethod
    public void isEnabled(Callback cb) {
        try {
            cb.invoke(null, mBTAdapter.isEnabled());
        }
        catch(Exception e) {
            cb.invoke(e.getMessage());
        }
    }

    /**
     * Check if Bluetooth is enabled.
     * @param cb callback(err, enabled)
     */
    @ReactMethod
    public void enable(Callback cb) {
        synchronized (this) {
            try {
                mEnableBTCB = null;
                if (mBTAdapter.isEnabled()) {
                    cb.invoke(null, true);
                    return;
                }
                Activity currentActivity = getCurrentActivity();
                if (currentActivity == null) {
                    throw new Exception("No activity");
                }

                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                mEnableBTCB = cb;
                currentActivity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
            catch(Exception e) {
                mEnableBTCB = null;
                cb.invoke(e.getMessage());
            }
        }
    }

    /**
     * Display Bluetooth setting UI
     */
    @ReactMethod
    public void showSettings() {
        try {
            Activity currentActivity = getCurrentActivity();
            if (currentActivity == null) {
                throw new Exception("No activity");
            }
            Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
            currentActivity.startActivity(intent);
        }
        catch(Exception ex) {
        }
    }

    /**
     * List bounded devices (曾經配對過的devices)
     * @param cb callback(err, devices), where devices is a JSON string for an array of
     *           device object: {id:.., address:.., name:..}
     */
    @ReactMethod
    public void listDevices(Callback cb) {
        try {
            WritableArray deviceList = Arguments.createArray();

            Set<BluetoothDevice> bondedDevices = mBTAdapter.getBondedDevices();

            for (BluetoothDevice device : bondedDevices) {
                deviceList.pushMap(deviceToObject(device));
            }

            cb.invoke(null, deviceList);
        }
        catch(Exception e) {
            cb.invoke(e.getMessage());
        }
    }

    /**
     * Connect to the specified device.
     * @param cb callback(err, status)
     * @param address device's address
     */
    @ReactMethod
    public void connect(String address, Callback cb) {
        synchronized (this) {
            try {
                BluetoothDevice device = mBTAdapter.getRemoteDevice(address);
                if (device == null) {
                    throw new Exception("Cannot find device:" + address);
                }
                mConnectStatusCB = cb;
                mBTSerialService.connect(device, true);
            }
            catch(Exception e) {
                cb.invoke(e.getMessage());
                mConnectStatusCB = null;
            }
        }
    }

    /**
     * Disconnect from peer device
     */
    @ReactMethod
    public void disconnect() {
        synchronized (this) {
            try {
                mBTSerialService.stop();
            }
            catch(Exception e) {
            }
        }
    }

    /**
     * Write string to peer device.
     * @param message message to write to peer device
     * @param cb callback(err)
     */
    @ReactMethod
    public void write(String message, Callback cb) {
        try {
            byte[] bytes = message.getBytes(charsetDefault);
            mBTSerialService.write(bytes);
            cb.invoke();
        }
        catch(Exception ex) {
            cb.invoke(ex.getMessage());
        }
    }

    /**
     * Read string from peer device.
     * @param cb callback(err, string)
     */
    @ReactMethod
    public void read(Callback cb) {
        try {
            cb.invoke(null, readBuffer());
        }
        catch(Exception ex) {
            cb.invoke(ex.getMessage());
        }
    }

    /**
     * Check # of chars available for read.
     * @param cb callback(err, length)
     */
    @ReactMethod
    public void available(Callback cb) {
        try {
            cb.invoke(null, getBufferLength());
        }
        catch(Exception ex) {
            cb.invoke(ex.getMessage());
        }
    }

    private void sendEvent(ReactContext reactContext, String eventName, @Nullable WritableMap params) {
        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }

    private WritableMap deviceToObject(BluetoothDevice device) throws JSONException {
        WritableMap map = Arguments.createMap();
        map.putString("name", device.getName());
        map.putString("address", device.getAddress());
        map.putString("id", device.getAddress());
        return map;
    }

    // The Handler that gets information back from the BluetoothSerialService
    // Original code used handler for the because it was talking to the UI.
    // Consider replacing with normal callbacks
    //
    private final Handler mHandler = new Handler(Looper.getMainLooper()) {

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_CONNECT_OK:
                    mDeviceConnected = msg.getData().getString(KEY_DEVICENAME);
                    if (D) Log.i(TAG, "CONNECT OK: " + mDeviceConnected);
                    notifyConnectionSuccess();
                    break;

                case MESSAGE_CONNECT_FAIL:
                    if (D) Log.i(TAG, "CONNECT FAIL");
                    notifyConnectionFail();
                    break;

                case MESSAGE_CONNECT_LOST:
                    if (D) Log.i(TAG, "CONNECT LOST");
                    notifyConnectionLost();
                    break;

                case MESSAGE_READ: {
                    byte[] buffer = (byte[]) msg.obj;
                    writeBuffer(buffer);
                    break;
                }

                case MESSAGE_WRITE:
                    break;
            }
        }
    };

    private synchronized void notifyConnectionSuccess() {
        if (mConnectStatusCB != null) {
            mConnectStatusCB.invoke(null, true, mDeviceConnected);
            mConnectStatusCB = null;
        }
    }

    private synchronized void notifyConnectionFail() {
        if (mConnectStatusCB != null) {
            mConnectStatusCB.invoke(null, false);
            mConnectStatusCB = null;
        }
    }

    private synchronized void notifyConnectionLost() {
        WritableMap params = Arguments.createMap();
        params.putString(KEY_DEVICENAME, mDeviceConnected);
        sendEvent(getReactApplicationContext(), EVENT_CONNECTION_LOST, params);
    }

    private synchronized int getBufferLength() {
        return mBuffer.length();
    }

    private synchronized void writeBuffer(byte[] buffer) {
        try {
            mBuffer.append(new String(buffer, charsetDefault));

            WritableMap params = Arguments.createMap();
            params.putInt(KEY_AVAILABLE, mBuffer.length());
            sendEvent(getReactApplicationContext(), EVENT_DATA_AVAILABLE, params);
        }
        catch(Exception ex) {
        }
    }

    private synchronized String readBuffer() throws UnsupportedEncodingException {
        String data = mBuffer.toString();
        mBuffer.setLength(0);
        return data;
    }
}
