export * from './nativescript-compass.common';

import { Device } from '@nativescript/core';

let compass: any;
if (Device.os.toLowerCase() === 'ios') {
    compass = require('./nativescript-compass.ios');
} else {
    compass = require('./nativescript-compass.android');
}

export const Compass = compass.Compass;