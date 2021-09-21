package com.udacity.catpoint.security.application;

/**
 * Identifies a component that should be notified whenever the system status changes
 */
public interface SensorStatusListener {
    void sensorStatusChanged();
}
