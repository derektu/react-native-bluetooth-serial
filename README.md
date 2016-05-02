# react-native-bluetooth-serial

A react-native module for serial IO communication over bluetooth device, support iOS (BLE) and Android (bluetooth 2).

The source code is largely from [BluetoothSerial cordova plugin](https://github.com/don/BluetoothSerial).
However only part of the source code is ported due to time constraint.

## Installation

```bash
 npm i --save react-native-bluetooth-serial
```

### iOS

* Drag RNBluetoothSerial.xcodeproj from the node_modules/react-native-bluetooth-serial folder into your XCode project.
* Click on the your project in XCode, goto Build Phases then Link Binary With Libraries and add libRNBluetoothSerial.a and CoreBluetooth.framework.

### Android

* update `android/settings.gradle`

```gradle
...
include ':react-native-bluetooth-serial'
project(':react-native-bluetooth-serial').projectDir = new File(settingsDir, '../node_modules/react-native-bluetooth-serial/android')
```

* update `android/app/build.gradle`

```gradle
...
dependencies {
    ...
    compile project(':react-native-bluetooth-serial')
}
```

* Register module in MainActivity.java

```java
import idv.derektu.rnbluetoothserial.BTSerialPackage;    // <--- import

public class MainActivity extends Activity implements DefaultHardwareBackBtnHandler {

    @Override
    protected List<ReactPackage> getPackages() {
        return Arrays.<ReactPackage>asList(
            new MainReactPackage(),
            new BTSerialPackage()       // <----- add package
        );
    }

  ......

}
```


## API

```js
var BTSerial = require('react-native-bluetooth-serial');
```

- [BTSerial.isEnabled](#isEnabled)
- [BTSerial.enable](#enable)
- [BTSerial.showSettings](#showSettings)
- [BTSerial.listDevices](#listDevices)
- [BTSerial.connect](#connect)
- [BTSerial.disconnect](#disconnect)
- [BTSerial.write](#write)
- [BTSerial.available](#available)
- [BTSerial.read](#read)
- [BTSerial.setConnectionStatusCallback](#setConnectionStatusCallback)
- [BTSerial.setDataAvailableCallback](#setDataAvailableCallback)


## isEnabled

Check if Bluetooth is enabled.

    BTSerial.isEnabled(function(err, enabled) {
        // enabled is true/false
    });

## enable

Enable Bluetooth. If Bluetooth is not enabled, will request user to enable BT. This is only supported in Android platform.

    BTSerial.enable(function(err, enabled) {
      // enabled is true/false
    ));

## showSettings

Display System Bluebooth settings screen. This is only supported in Android platform.

    BTSerial.showSettings();

## listDevices

List paired devices. For Android, devices is an array of {id:.., address:.., name:..}. For iOS, devices is an array of {id:.., uuid:.., name:..}

    BTSerial.listDevices(function(err, devices) {
    })

## connect

Connect to a paired device. If device is connected, status is true, and deviceName is the
name of remote device. Otherwise status is false. For Android, pass device's address. For iOS, pass device's name or uuid.

    BTSerial.connect(address, function(err, status, deviceName){
    });

## disconnect

Disconnect from connected device.

    BTSerial.disconnect();

## write

Write (utf-8) string data to connected devices.

    BTSerial.write(string, function(err) {
    });

## available

Check if there is any data received from connected device.

    BTSerial.available(function(err, length) {
        // length is the # of characters in the read buffer
    });

## read

Read (utf-8) string data from connected device. If there is no data, empty string('') will be returned.

    BTSerial.read(function(err, string) {
    })

# setConnectionStatusCallback

Register a callback that will be invoked when remote connection is aborted. When callback, 'e' is
{devicename: 'this device'}

    BTSerial.setConnectionStatusCallback(function(e) {
    })

# setDataAvailableCallback

Register a callback that will be invoked when remote device sends data. When callback 'e' is
{available: count}

    BTSerial.setDataAvailableCallback(function(e) {
    })
