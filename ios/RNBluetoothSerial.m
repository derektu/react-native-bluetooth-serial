//
//  RNBluetoothSerial.m
//  RNBluetoothSerial
//
//  Created by Derek Tu on 5/2/16.
//  Copyright © 2016 Derek Tu. All rights reserved.
//

#import "RNBluetoothSerial.h"

#import <CoreBluetooth/CoreBluetooth.h>

#import "RCTEventDispatcher.h"
#import "BLE.h"

static NSString* const EVENT_CONNECTION_LOST = @"eventConnectionLost";
static NSString* const EVENT_DATA_AVAILABLE = @"eventDataAvailable";

static const int SCAN_TIMEOUT = 5;  // 5 seconds

@interface RNBluetoothSerial() <BLEDelegate> {
    BLE* _bleShield;
    NSMutableString *_buffer;
    RCTResponseSenderBlock _connectCallback;
}
@end

@implementation RNBluetoothSerial

RCT_EXPORT_MODULE()

@synthesize bridge = _bridge;

#pragma mark Initialization

- (instancetype)init
{
    if (self = [super init]) {
        _bleShield = [[BLE alloc] init];
        [_bleShield controlSetup];
        [_bleShield setDelegate:self];
        _buffer = [[NSMutableString alloc] init];
        _connectCallback = nil;
    }
    
    return self;
}

#pragma mark - ReactNative JS API

// JS Constant
//
- (NSDictionary *)constantsToExport
{
    return @{
        EVENT_CONNECTION_LOST: EVENT_CONNECTION_LOST,
        EVENT_DATA_AVAILABLE: EVENT_DATA_AVAILABLE
    };
}

// JS API:
//  isEnabled((err, enabled)=> {..})
//
RCT_EXPORT_METHOD(isEnabled:(RCTResponseSenderBlock)callback)
{
    int bluetoothState = [[_bleShield CM] state];
    BOOL enabled = bluetoothState == CBCentralManagerStatePoweredOn;
    callback(@[[NSNull null], [NSNumber numberWithBool:enabled]]);
}

// JS API:
//  enable((err, enabled)=> {..})
//
RCT_EXPORT_METHOD(enable:(RCTResponseSenderBlock)callback)
{
    callback(@[@"Not supported on ios platform"]);
}

// JS API:
//  showSettings()
//
RCT_EXPORT_METHOD(showSettings)
{
    // Not supported on ios platform
    //
}

// JS API:
//  listDevices((err, devices)=> {..})
//
RCT_EXPORT_METHOD(listDevices:(RCTResponseSenderBlock)callback)
{
    [self scanForBLEPeripherals:SCAN_TIMEOUT];
    dispatch_time_t delayTime = dispatch_time(DISPATCH_TIME_NOW, SCAN_TIMEOUT * NSEC_PER_SEC);
    dispatch_after(delayTime, dispatch_get_main_queue(), ^(void){
        NSMutableArray *peripherals = [self getPeripheralList];
        callback(@[[NSNull null], peripherals]);
    });
    
}

// JS API:
//  connect(device, (err, statue)=> {..})
//
RCT_EXPORT_METHOD(connect:(NSString*)device callback:(RCTResponseSenderBlock)callback)
{
    if (![device length]) {
        callback(@[@"device is empty"]);
        return;
    }
    
    // track current callback
    //
    _connectCallback = callback;
    
    [self connectToDevice:device];
}

// JS API:
//  disconnect()
//
RCT_EXPORT_METHOD(disconnect)
{
    _connectCallback = nil;
    if (_bleShield.activePeripheral && _bleShield.activePeripheral.state == CBPeripheralStateConnected) {
        [[_bleShield CM] cancelPeripheralConnection:[_bleShield activePeripheral]];
    }
}

// JS API:
//  write(message, (err){..})
//
RCT_EXPORT_METHOD(write:(NSString*)message callback:(RCTResponseSenderBlock)callback)
{
    // encoding is ignored (
    //
    NSData *data = [message dataUsingEncoding:NSUTF8StringEncoding];
    [_bleShield write:data];
    callback(@[[NSNull null]]);
}

// JS API:
//  read((err, message){..})
//
RCT_EXPORT_METHOD(read:(RCTResponseSenderBlock)callback)
{
    NSString* data = @"";
    @synchronized (self) {
        data = [NSString stringWithString:_buffer];
        [_buffer setString:@""];
    }

    callback(@[[NSNull null], data]);
}

// JS API:
//  available((err, length){..})
//
RCT_EXPORT_METHOD(available:(RCTResponseSenderBlock)callback)
{
    @synchronized (self) {
        callback(@[[NSNull null], [NSNumber numberWithInteger:[_buffer length]]]);
    }
}

#pragma mark - BLEDelegate

- (void)bleDidReceiveData:(unsigned char *)data length:(int)length
{
    // Append to the buffer
    NSData *d = [NSData dataWithBytes:data length:length];
    NSString *s = [[NSString alloc] initWithData:d encoding:NSUTF8StringEncoding];
    if (!s) {
        NSLog(@"Error converting received data into a String.");
        return;
    }
    
    NSLog(@"bleDidReceived: %@", s);
    
    @synchronized (self) {
        [_buffer appendString:s];
     
        // Notify event
        // { "available": "12" }
        //
        [self.bridge.eventDispatcher sendDeviceEventWithName:EVENT_DATA_AVAILABLE
                                                        body:@{@"available": [NSString stringWithFormat:@"%i", [_buffer length]]}];
    }
}

- (void)bleDidConnect
{
    NSLog(@"bleDidConnect");

    if (_connectCallback) {
        _connectCallback(@[[NSNull null], [NSNumber numberWithBool:YES], [[_bleShield activePeripheral] name]]);
        _connectCallback = nil;
    }
}

- (void)bleDidDisconnect:(CBPeripheral*)peripheral error:(NSError*)error
{
    NSLog(@"bleDidDisconnect");

    // Notify event
    //
    NSString* name = @"";
    if (peripheral) {
        name = [peripheral name];
        if (![name length])
            name = peripheral.identifier.UUIDString;
    }
    [self.bridge.eventDispatcher sendDeviceEventWithName:EVENT_CONNECTION_LOST
                                                    body:@{@"devicename": name}];
}

#pragma mark - Bluetooth implementation

/// scan for bluetooth devices
///
/// @param timeout # of seconds
///
- (void)scanForBLEPeripherals:(int)timeout
{
    NSLog(@"Scanning for BLE Peripherals");
    
    // disconnect
    //
    if (_bleShield.activePeripheral) {
        if(_bleShield.activePeripheral.state == CBPeripheralStateConnected)
        {
            [[_bleShield CM] cancelPeripheralConnection:[_bleShield activePeripheral]];
            return;
        }
    }
    
    // remove existing peripherals
    //
    if (_bleShield.peripherals) {
        _bleShield.peripherals = nil;
    }
    
    [_bleShield findBLEPeripherals:timeout];
}

/// return list of devices
///
- (NSMutableArray*) getPeripheralList
{
    NSMutableArray *peripherals = [NSMutableArray array];
    
    for (int i = 0; i < _bleShield.peripherals.count; i++) {
        NSMutableDictionary *peripheral = [NSMutableDictionary dictionary];
        CBPeripheral *p = [_bleShield.peripherals objectAtIndex:i];
        
        NSString *uuid = p.identifier.UUIDString;
        [peripheral setObject: uuid forKey: @"uuid"];
        [peripheral setObject: uuid forKey: @"id"];
        
        NSString *name = [p name];
        if (!name) {
            name = [peripheral objectForKey:@"uuid"];
        }
        [peripheral setObject: name forKey: @"name"];
        
        [peripherals addObject:peripheral];
    }
    
    return peripherals;
}

/// connect to bluetooth device
///
/// @param device name or uuid of the device to connect
//
- (void)connectToDevice:(NSString *)device
{
    double interval = 0.1;
    
    if (_bleShield.peripherals.count < 1) {
        interval = SCAN_TIMEOUT;
        [self scanForBLEPeripherals:interval];
    }
    
    dispatch_time_t delayTime = dispatch_time(DISPATCH_TIME_NOW, interval * NSEC_PER_SEC);
    dispatch_after(delayTime, dispatch_get_main_queue(), ^(void){
        CBPeripheral *peripheral = [self findPeripheral:device];
        if (!peripheral) {
            NSString *error = [NSString stringWithFormat:@"Could not find peripheral %@.", device];
            if (_connectCallback) {
                _connectCallback(@[error]);
                _connectCallback = nil;
            }
        }
        else {
            [_bleShield connectPeripheral:peripheral];
        }
    });
}

/// locate bluetooth device by name or uuid
///
/// @param device uuid/name of the device to look for
///
- (CBPeripheral*)findPeripheral:(NSString*)device
{
    NSMutableArray *peripherals = [_bleShield peripherals];
    CBPeripheral *peripheral = nil;
    
    for (CBPeripheral *p in peripherals) {
        if ([device isEqualToString:p.identifier.UUIDString] || [device isEqualToString:[p name]]) {
            peripheral = p;
            break;
        }
    }
    return peripheral;
}


@end


