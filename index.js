'use strict';

var { NativeModules, DeviceEventEmitter } = require('react-native');

var BTSerial = NativeModules.RNBluetoothSerial;

// add setConnectionStatusCallback(cb) function
//  cb({devicename: 'xyz'})
//
BTSerial.setConnectionStatusCallback = function(cb) {
    if (DeviceEventEmitter.addListener) {
        DeviceEventEmitter.addListener(BTSerial.eventConnectionLost, cb);
    }
}

// add setDataAvailableCallback(cb) function
//  cb({available: n})
//
BTSerial.setDataAvailableCallback = function(cb) {
    if (DeviceEventEmitter.addListener) {
        DeviceEventEmitter.addListener(BTSerial.eventDataAvailable, cb);
    }
}

module.exports = BTSerial;