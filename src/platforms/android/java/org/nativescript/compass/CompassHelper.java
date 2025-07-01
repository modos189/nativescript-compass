package org.nativescript.compass;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import java.util.Timer;
import java.util.TimerTask;

public class CompassHelper {

    // Main CompassHelper class
    private final Context mContext;
    private final double mMinChangeThreshold;
    private final long mUpdateThrottle;
    private final double mFilter;
    private final boolean mUseSensorFusion;
    private final int mSensorDelay; // Converted from string to int constant
    private final CompassCallback mUserCallback;

    private Compass mCompass;
    private double mLastHeading = 0;
    private long mLastCallbackTime = 0;
    private double mFilteredHeading = Double.NaN;

    // Timer-based throttling
    private Timer mThrottleTimer;
    private double mLatestAccuracy = 0;
    private double mLatestMagneticHeading = 0;
    private long mLatestTimestamp = 0;

    public CompassHelper(
        Context context,
        double minChangeThreshold,
        long updateThrottle,
        double filter,
        boolean useSensorFusion,
        String sensorDelayString,
        CompassCallback callback
    ) {
        mContext = context;
        mMinChangeThreshold = minChangeThreshold;
        mUpdateThrottle = updateThrottle;
        mFilter = filter;
        mUseSensorFusion = useSensorFusion;
        mSensorDelay = Compass.sensorDelayFromString(sensorDelayString);
        mUserCallback = callback;
    }

    public static boolean isCompassAvailable(Context context) {
        SensorManager sensorManager = (SensorManager) context.getSystemService(
            Context.SENSOR_SERVICE
        );
        Sensor accelerometer = sensorManager.getDefaultSensor(
            Sensor.TYPE_ACCELEROMETER
        );
        Sensor magnetometer = sensorManager.getDefaultSensor(
            Sensor.TYPE_MAGNETIC_FIELD
        );
        return accelerometer != null && magnetometer != null;
    }

    public boolean startUpdating() {
        try {
            if (mUseSensorFusion) {
                mCompass = Compass.getDefaultCompass(mContext);
            } else {
                mCompass = new AccMagCompass(mContext);
            }

            // Set sensor delay
            mCompass.setSensorDelay(mSensorDelay);

            mCompass.setCallback(
                new CompassCallback() {
                    @Override
                    public void onReading(
                        double heading,
                        double accuracy,
                        double magneticHeading,
                        long timestamp
                    ) {
                        processReading(
                            heading,
                            accuracy,
                            magneticHeading,
                            timestamp
                        );
                    }

                    @Override
                    public void onError(String error) {
                        if (mUserCallback != null) {
                            mUserCallback.onError(error);
                        }
                    }
                }
            );

            startThrottleTimer();

            return true;
        } catch (Exception e) {
            if (mUserCallback != null) {
                mUserCallback.onError(
                    "Failed to start compass: " + e.getMessage()
                );
            }
            return false;
        }
    }

    public boolean stopUpdating() {
        try {
            if (mCompass != null) {
                mCompass.stop();
                mCompass = null;
            }
            stopThrottleTimer();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void processReading(
        double rawHeading,
        double accuracy,
        double magneticHeading,
        long timestamp
    ) {
        // Apply filter and store latest data
        mFilteredHeading = applyFilter(rawHeading);
        mLatestAccuracy = accuracy;
        mLatestMagneticHeading = magneticHeading;
        mLatestTimestamp = timestamp;
    }

    private double applyFilter(double rawHeading) {
        if (Double.isNaN(mFilteredHeading)) {
            mFilteredHeading = rawHeading;
            return rawHeading;
        }

        double delta = rawHeading - mFilteredHeading;
        if (delta > 180) delta -= 360;
        else if (delta < -180) delta += 360;

        mFilteredHeading += delta * (1 - mFilter);
        if (mFilteredHeading < 0) mFilteredHeading += 360;
        else if (mFilteredHeading >= 360) mFilteredHeading -= 360;

        return mFilteredHeading;
    }

    public static void getCurrentReading(
        Context context,
        boolean useSensorFusion,
        String sensorDelayString,
        CompassCallback callback
    ) {
        try {
            Compass compass = useSensorFusion
                ? Compass.getDefaultCompass(context)
                : new AccMagCompass(context);

            // Set sensor delay
            compass.setSensorDelay(
                Compass.sensorDelayFromString(sensorDelayString)
            );

            // Single reading with timeout
            Timer timeoutTimer = new Timer();
            timeoutTimer.schedule(
                new TimerTask() {
                    @Override
                    public void run() {
                        compass.stop();
                        callback.onError("Timeout getting compass reading");
                    }
                },
                3000
            );

            compass.setCallback(
                new CompassCallback() {
                    @Override
                    public void onReading(
                        double heading,
                        double accuracy,
                        double magneticHeading,
                        long timestamp
                    ) {
                        timeoutTimer.cancel();
                        compass.stop();
                        callback.onReading(
                            heading,
                            accuracy,
                            magneticHeading,
                            timestamp
                        );
                    }

                    @Override
                    public void onError(String error) {
                        timeoutTimer.cancel();
                        compass.stop();
                        callback.onError(error);
                    }
                }
            );
        } catch (Exception e) {
            callback.onError(
                "Failed to get compass reading: " + e.getMessage()
            );
        }
    }

    private void startThrottleTimer() {
        mThrottleTimer = new Timer();
        mThrottleTimer.scheduleAtFixedRate(
            new TimerTask() {
                @Override
                public void run() {
                    checkAndSendReading();
                }
            },
            mUpdateThrottle,
            mUpdateThrottle
        );
    }

    private void stopThrottleTimer() {
        if (mThrottleTimer != null) {
            mThrottleTimer.cancel();
            mThrottleTimer = null;
        }
    }

    private void checkAndSendReading() {
        if (Double.isNaN(mFilteredHeading) || mUserCallback == null) {
            return;
        }

        // Check if data is fresh
        long currentTime = System.currentTimeMillis();
        long dataAge = currentTime - mLatestTimestamp;
        if (dataAge > 500) {
            // Skip data older than 500ms
            return;
        }

        // Check threshold (like iOS minChangeThreshold)
        double change = Math.abs(mFilteredHeading - mLastHeading);
        if (change > 180) change = 360 - change; // Handle 360/0 boundary

        if (change >= mMinChangeThreshold) {
            mUserCallback.onReading(
                mFilteredHeading,
                mLatestAccuracy,
                mLatestMagneticHeading,
                mLatestTimestamp
            );
            mLastHeading = mFilteredHeading;
            mLastCallbackTime = currentTime;
        }
    }
}
