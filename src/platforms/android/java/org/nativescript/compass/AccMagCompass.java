package org.nativescript.compass;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class AccMagCompass extends Compass {

    private static final long SENSOR_THROTTLE_NS = 100 * 1000000L; // 100ms in nanoseconds

    private final Context mContext;
    private long mLastUpdate = 0;
    private final SensorListener mListener = new SensorListener();
    private final float[] mOrientation = new float[3];
    private final float[] mRotationMatrix = new float[9];
    private final Sensor mSensorAcc, mSensorMag;
    private final SensorManager mSensorManager;
    private float[] mValuesAcc = null,
        mValuesMag = null;

    public AccMagCompass(final Context context) {
        mContext = context;
        mSensorManager = (SensorManager) mContext.getSystemService(
            Context.SENSOR_SERVICE
        );
        mSensorAcc = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorMag = mSensorManager.getDefaultSensor(
            Sensor.TYPE_MAGNETIC_FIELD
        );
    }

    private void calculateOrientation() {
        if (mValuesAcc == null || mValuesMag == null) return;

        if (
            !SensorManager.getRotationMatrix(
                mRotationMatrix,
                null,
                mValuesAcc,
                mValuesMag
            )
        ) return;
        SensorManager.getOrientation(mRotationMatrix, mOrientation);

        publishOrientation(mOrientation[0], mOrientation[1], mOrientation[2]);
    }

    @Override
    protected void onStart() {
        if (mSensorAcc != null && mSensorMag != null) {
            // Use configured sensor delay
            int sensorDelay = getSensorDelay();

            mSensorManager.registerListener(mListener, mSensorAcc, sensorDelay);
            mSensorManager.registerListener(mListener, mSensorMag, sensorDelay);
        }
    }

    @Override
    protected void onStop() {
        mSensorManager.unregisterListener(mListener);
    }

    private class SensorListener implements SensorEventListener {

        @Override
        public void onAccuracyChanged(final Sensor sensor, final int accuracy) {
            // Track the latest sensor accuracy for callbacks
            switch (accuracy) {
                case SensorManager.SENSOR_STATUS_ACCURACY_HIGH:
                    mLastSensorAccuracy = 1.0;
                    break;
                case SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM:
                    mLastSensorAccuracy = 3.0;
                    break;
                case SensorManager.SENSOR_STATUS_ACCURACY_LOW:
                    mLastSensorAccuracy = 10.0;
                    break;
                case SensorManager.SENSOR_STATUS_UNRELIABLE:
                default:
                    mLastSensorAccuracy = 15.0;
                    break;
            }
        }

        @Override
        public void onSensorChanged(final SensorEvent event) {
            switch (event.sensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                    mValuesAcc = event.values.clone();
                    break;
                case Sensor.TYPE_MAGNETIC_FIELD:
                    mValuesMag = event.values.clone();

                    // Throttle to save battery (similar to example code)
                    if (
                        (event.timestamp - mLastUpdate) < SENSOR_THROTTLE_NS
                    ) break;
                    mLastUpdate = event.timestamp;

                    calculateOrientation();
                    break;
            }
        }
    }
}
