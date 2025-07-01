package org.nativescript.compass;

public interface CompassCallback {
    void onReading(
        double heading,
        double accuracy,
        double magneticHeading,
        long timestamp
    );
    void onError(String error);
}
