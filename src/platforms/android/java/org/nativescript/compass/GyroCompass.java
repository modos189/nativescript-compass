package org.nativescript.compass;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import java.util.Timer;
import java.util.TimerTask;

public class GyroCompass extends Compass {

    private static final float EPSILON = 0.000000001f;
    private static final float FILTER_COEFFICIENT = 0.98f;
    private static final float NS2S = 1.0f / 1000000000.0f;
    private static final int TIME_CONSTANT = 30;

    private final AccMagCompass mAccMagCompass;
    private final AccMagListener mAccMagListener = new AccMagListener();
    private float[] mAccMagOrientation = null;
    private final Context mContext;
    private final float[] mFusedOrientation = new float[3];
    private final Timer mFuseTimer = new Timer();
    private final float[] mGyro = new float[3];
    private float[] mGyroMatrix = null;
    private final float[] mGyroOrientation = { 0, 0, 0 };
    private final Sensor mSensor;
    private final SensorListener mSensorListener = new SensorListener();
    private SensorManager mSensorManager = null;
    private FuseOrientationTask mTask;
    private long mTimestamp;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    public GyroCompass(final Context context) {
        this(context, new AccMagCompass(context));
    }

    public GyroCompass(final Context context, final AccMagCompass compass) {
        super();
        mContext = context;
        mAccMagCompass = compass;
        mSensorManager = (SensorManager) mContext.getSystemService(
            Context.SENSOR_SERVICE
        );
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
    }

    private void onGyroChanged(final SensorEvent event) {
        if (mAccMagOrientation == null) return;

        if (mGyroMatrix == null) mGyroMatrix = getRotationMatrixFromOrientation(
            mAccMagOrientation
        );

        final float[] deltaVector = new float[4];
        if (mTimestamp != 0) {
            final float dT = (event.timestamp - mTimestamp) * NS2S;
            System.arraycopy(event.values, 0, mGyro, 0, 3);
            getRotationVectorFromGyro(mGyro, deltaVector, dT / 2.0f);
        }

        mTimestamp = event.timestamp;

        final float[] deltaMatrix = new float[9];
        SensorManager.getRotationMatrixFromVector(deltaMatrix, deltaVector);
        mGyroMatrix = matrixMultiplication(mGyroMatrix, deltaMatrix);
        SensorManager.getOrientation(mGyroMatrix, mGyroOrientation);
    }

    @Override
    protected void onStart() {
        if (mSensor != null) {
            int sensorDelay = getSensorDelay();
            mSensorManager.registerListener(
                mSensorListener,
                mSensor,
                sensorDelay
            );
            mAccMagCompass.setCallback(
                new CompassCallback() {
                    @Override
                    public void onReading(
                        double heading,
                        double accuracy,
                        double magneticHeading,
                        long timestamp
                    ) {
                        float[] orientation =
                            mAccMagCompass.getLastOrientation();
                        mAccMagListener.onCompassChanged(
                            orientation[0],
                            orientation[1],
                            orientation[2]
                        );
                    }

                    @Override
                    public void onError(String error) {}
                }
            );

            mTask = new FuseOrientationTask();
            mFuseTimer.scheduleAtFixedRate(mTask, 200, TIME_CONSTANT);
        } else {
            // Fallback to AccMag if no gyroscope
            mAccMagCompass.setCallback(
                new CompassCallback() {
                    @Override
                    public void onReading(
                        double heading,
                        double accuracy,
                        double magneticHeading,
                        long timestamp
                    ) {
                        publishOrientation(
                            (float) Math.toRadians(heading),
                            0,
                            0
                        );
                    }

                    @Override
                    public void onError(String error) {}
                }
            );
        }
    }

    @Override
    protected void onStop() {
        if (mSensor != null) {
            mSensorManager.unregisterListener(mSensorListener);
            mAccMagCompass.stop();
            if (mTask != null) {
                mTask.cancel();
            }
        } else {
            mAccMagCompass.stop();
        }
    }

    // Helper methods for gyroscope fusion
    private float[] getRotationMatrixFromOrientation(final float[] o) {
        final float[] xM = new float[9];
        final float[] yM = new float[9];
        final float[] zM = new float[9];

        final float sinX = (float) Math.sin(o[1]);
        final float cosX = (float) Math.cos(o[1]);
        final float sinY = (float) Math.sin(o[2]);
        final float cosY = (float) Math.cos(o[2]);
        final float sinZ = (float) Math.sin(o[0]);
        final float cosZ = (float) Math.cos(o[0]);

        xM[0] = 1.0f;
        xM[1] = 0.0f;
        xM[2] = 0.0f;
        xM[3] = 0.0f;
        xM[4] = cosX;
        xM[5] = sinX;
        xM[6] = 0.0f;
        xM[7] = -sinX;
        xM[8] = cosX;

        yM[0] = cosY;
        yM[1] = 0.0f;
        yM[2] = sinY;
        yM[3] = 0.0f;
        yM[4] = 1.0f;
        yM[5] = 0.0f;
        yM[6] = -sinY;
        yM[7] = 0.0f;
        yM[8] = cosY;

        zM[0] = cosZ;
        zM[1] = sinZ;
        zM[2] = 0.0f;
        zM[3] = -sinZ;
        zM[4] = cosZ;
        zM[5] = 0.0f;
        zM[6] = 0.0f;
        zM[7] = 0.0f;
        zM[8] = 1.0f;

        float[] resultMatrix = matrixMultiplication(xM, yM);
        resultMatrix = matrixMultiplication(zM, resultMatrix);
        return resultMatrix;
    }

    private void getRotationVectorFromGyro(
        final float[] values,
        final float[] deltaRotationVector,
        final float time
    ) {
        final float[] normValues = new float[3];
        final float omegaMagnitude = (float) Math.sqrt(
            values[0] * values[0] +
            values[1] * values[1] +
            values[2] * values[2]
        );

        if (omegaMagnitude > EPSILON) {
            normValues[0] = values[0] / omegaMagnitude;
            normValues[1] = values[1] / omegaMagnitude;
            normValues[2] = values[2] / omegaMagnitude;
        }

        final float thetaOverTwo = omegaMagnitude * time;
        final float sinThetaOverTwo = (float) Math.sin(thetaOverTwo);
        final float cosThetaOverTwo = (float) Math.cos(thetaOverTwo);
        deltaRotationVector[0] = sinThetaOverTwo * normValues[0];
        deltaRotationVector[1] = sinThetaOverTwo * normValues[1];
        deltaRotationVector[2] = sinThetaOverTwo * normValues[2];
        deltaRotationVector[3] = cosThetaOverTwo;
    }

    private float[] matrixMultiplication(final float[] A, final float[] B) {
        final float[] result = new float[9];
        result[0] = A[0] * B[0] + A[1] * B[3] + A[2] * B[6];
        result[1] = A[0] * B[1] + A[1] * B[4] + A[2] * B[7];
        result[2] = A[0] * B[2] + A[1] * B[5] + A[2] * B[8];
        result[3] = A[3] * B[0] + A[4] * B[3] + A[5] * B[6];
        result[4] = A[3] * B[1] + A[4] * B[4] + A[5] * B[7];
        result[5] = A[3] * B[2] + A[4] * B[5] + A[5] * B[8];
        result[6] = A[6] * B[0] + A[7] * B[3] + A[8] * B[6];
        result[7] = A[6] * B[1] + A[7] * B[4] + A[8] * B[7];
        result[8] = A[6] * B[2] + A[7] * B[5] + A[8] * B[8];
        return result;
    }

    private class AccMagListener {

        public void onCompassChanged(
            final float x,
            final float y,
            final float z
        ) {
            if (mAccMagOrientation == null) {
                mGyroOrientation[0] = x;
                mGyroOrientation[1] = y;
                mGyroOrientation[2] = z;
            }
            mAccMagOrientation = new float[] { x, y, z };
        }
    }

    private class FuseOrientationTask extends TimerTask {

        @Override
        public void run() {
            if (mAccMagOrientation == null) return;

            final float oneMinusCoeff = 1.0f - FILTER_COEFFICIENT;

            // Sensor fusion for azimuth
            if (
                mGyroOrientation[0] < -0.5 * Math.PI &&
                mAccMagOrientation[0] > 0.0
            ) {
                mFusedOrientation[0] = (float) (FILTER_COEFFICIENT *
                        (mGyroOrientation[0] + 2.0 * Math.PI) +
                    oneMinusCoeff * mAccMagOrientation[0]);
                mFusedOrientation[0] -= (mFusedOrientation[0] > Math.PI)
                    ? 2.0 * Math.PI
                    : 0;
            } else if (
                mAccMagOrientation[0] < -0.5 * Math.PI &&
                mGyroOrientation[0] > 0.0
            ) {
                mFusedOrientation[0] = (float) (FILTER_COEFFICIENT *
                        mGyroOrientation[0] +
                    oneMinusCoeff * (mAccMagOrientation[0] + 2.0 * Math.PI));
                mFusedOrientation[0] -= (mFusedOrientation[0] > Math.PI)
                    ? 2.0 * Math.PI
                    : 0;
            } else {
                mFusedOrientation[0] =
                    FILTER_COEFFICIENT * mGyroOrientation[0] +
                    oneMinusCoeff * mAccMagOrientation[0];
            }

            // pitch
            if (
                mGyroOrientation[1] < -0.5 * Math.PI &&
                mAccMagOrientation[1] > 0.0
            ) {
                mFusedOrientation[1] = (float) (FILTER_COEFFICIENT *
                        (mGyroOrientation[1] + 2.0 * Math.PI) +
                    oneMinusCoeff * mAccMagOrientation[1]);
                mFusedOrientation[1] -= (mFusedOrientation[1] > Math.PI)
                    ? 2.0 * Math.PI
                    : 0;
            } else if (
                mAccMagOrientation[1] < -0.5 * Math.PI &&
                mGyroOrientation[1] > 0.0
            ) {
                mFusedOrientation[1] = (float) (FILTER_COEFFICIENT *
                        mGyroOrientation[1] +
                    oneMinusCoeff * (mAccMagOrientation[1] + 2.0 * Math.PI));
                mFusedOrientation[1] -= (mFusedOrientation[1] > Math.PI)
                    ? 2.0 * Math.PI
                    : 0;
            } else {
                mFusedOrientation[1] =
                    FILTER_COEFFICIENT * mGyroOrientation[1] +
                    oneMinusCoeff * mAccMagOrientation[1];
            }

            // roll
            if (
                mGyroOrientation[2] < -0.5 * Math.PI &&
                mAccMagOrientation[2] > 0.0
            ) {
                mFusedOrientation[2] = (float) (FILTER_COEFFICIENT *
                        (mGyroOrientation[2] + 2.0 * Math.PI) +
                    oneMinusCoeff * mAccMagOrientation[2]);
                mFusedOrientation[2] -= (mFusedOrientation[2] > Math.PI)
                    ? 2.0 * Math.PI
                    : 0;
            } else if (
                mAccMagOrientation[2] < -0.5 * Math.PI &&
                mGyroOrientation[2] > 0.0
            ) {
                mFusedOrientation[2] = (float) (FILTER_COEFFICIENT *
                        mGyroOrientation[2] +
                    oneMinusCoeff * (mAccMagOrientation[2] + 2.0 * Math.PI));
                mFusedOrientation[2] -= (mFusedOrientation[2] > Math.PI)
                    ? 2.0 * Math.PI
                    : 0;
            } else {
                mFusedOrientation[2] =
                    FILTER_COEFFICIENT * mGyroOrientation[2] +
                    oneMinusCoeff * mAccMagOrientation[2];
            }

            mGyroMatrix = getRotationMatrixFromOrientation(mFusedOrientation);
            System.arraycopy(mFusedOrientation, 0, mGyroOrientation, 0, 3);

            mHandler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        publishOrientation(
                            mFusedOrientation[0],
                            mFusedOrientation[1],
                            mFusedOrientation[2]
                        );
                    }
                }
            );
        }
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
            onGyroChanged(event);
        }
    }
}
