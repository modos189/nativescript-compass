package org.nativescript.compass;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;

public abstract class Compass {

    public static Compass getDefaultCompass(final Context context) {
        final Sensor gyro = ((SensorManager) context.getSystemService(
                Context.SENSOR_SERVICE
            )).getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        if (gyro != null) return new GyroCompass(context);
        else return new AccMagCompass(context);
    }

    private CompassCallback mCallback = null;
    private boolean mStarted = false;
    protected double mLastSensorAccuracy = 5.0; // Last reported sensor accuracy
    protected int mSensorDelay = SensorManager.SENSOR_DELAY_UI; // Default sensor delay

    protected abstract void onStart();

    protected abstract void onStop();

    private float[] mLastOrientation = new float[3];

    protected void publishOrientation(
        final float azimuth,
        final float pitch,
        final float roll
    ) {
        mLastOrientation[0] = azimuth;
        mLastOrientation[1] = pitch;
        mLastOrientation[2] = roll;

        if (mCallback != null) {
            // Convert radians to degrees and normalize to 0-360
            double heading = Math.toDegrees(azimuth);
            if (heading < 0) heading += 360;

            // Use the last known sensor accuracy
            double accuracy = mLastSensorAccuracy;

            mCallback.onReading(
                heading,
                accuracy,
                heading,
                System.currentTimeMillis()
            );
        }
    }

    public float[] getLastOrientation() {
        return mLastOrientation != null
            ? mLastOrientation.clone()
            : new float[] { 0, 0, 0 };
    }

    public void setSensorDelay(int sensorDelay) {
        mSensorDelay = sensorDelay;
    }

    protected int getSensorDelay() {
        return mSensorDelay;
    }

    // Convert string sensorDelay to Android constant
    public static int sensorDelayFromString(String sensorDelay) {
        if (sensorDelay == null) {
            return SensorManager.SENSOR_DELAY_UI; // Default
        }
        switch (sensorDelay.toLowerCase()) {
            case "fastest":
                return SensorManager.SENSOR_DELAY_FASTEST;
            case "game":
                return SensorManager.SENSOR_DELAY_GAME;
            case "ui":
                return SensorManager.SENSOR_DELAY_UI;
            case "normal":
                return SensorManager.SENSOR_DELAY_NORMAL;
            default:
                return SensorManager.SENSOR_DELAY_UI; // Default fallback
        }
    }

    public void setCallback(final CompassCallback callback) {
        mCallback = callback;
        if (callback != null && !mStarted) {
            onStart();
            mStarted = true;
        } else if (callback == null && mStarted) {
            onStop();
            mStarted = false;
        }
    }

    public void stop() {
        if (mStarted) {
            onStop();
            mStarted = false;
            mCallback = null;
        }
    }
}
