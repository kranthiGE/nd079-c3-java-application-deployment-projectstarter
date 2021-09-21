package com.udacity.catpoint.security.service;

import com.udacity.catpoint.image.service.ImageService;
import com.udacity.catpoint.security.application.SensorStatusListener;
import com.udacity.catpoint.security.application.StatusListener;
import com.udacity.catpoint.security.data.AlarmStatus;
import com.udacity.catpoint.security.data.ArmingStatus;
import com.udacity.catpoint.security.data.SecurityRepository;
import com.udacity.catpoint.security.data.Sensor;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service that receives information about changes to the security system. Responsible for
 * forwarding updates to the repository and making any decisions about changing the system state.
 *
 * This is the class that should contain most of the business logic for our system, and it is the
 * class you will be writing unit tests for.
 */
public class SecurityService {

    private ImageService imageService;
    private SecurityRepository securityRepository;
    private Set<StatusListener> statusListeners = new HashSet<>();
    private SensorStatusListener sensorStatusListener;
    private AtomicBoolean isCatDetected = new AtomicBoolean(false);

    public SecurityService(SecurityRepository securityRepository, ImageService imageService) {
        this.securityRepository = securityRepository;
        this.imageService = imageService;
    }

    /**
     * Sets the current arming status for the system. Changing the arming status
     * may update both the alarm status.
     * @param armingStatus
     */
    public void setArmingStatus(ArmingStatus armingStatus) {
        switch(armingStatus){
            case DISARMED:
                setAlarmStatus(AlarmStatus.NO_ALARM);// satisfies the requirement
                break;
            case ARMED_HOME:
            /* this covers the scenario:
            Put the system as disarmed, scan a picture until it detects a cat, 
            after that make it armed, it should make system in ALARM state
            */
                if(getCatStatus()){
                    setAlarmStatus(AlarmStatus.ALARM);
                }
                getSensors().stream()
                            .forEach(s -> changeSensorActivationStatus(s, false));
                break;
            default:
                //reset all sensors to inactive
                getSensors().stream()
                            .forEach(s -> changeSensorActivationStatus(s, false));
                break;
        }
        securityRepository.setArmingStatus(armingStatus);
        sensorStatusListener.sensorStatusChanged();
    }

    /**
     * Internal method that handles alarm status changes based on whether
     * the camera currently shows a cat.
     * @param cat True if a cat is detected, otherwise false.
     */
    private void catDetected(Boolean cat) {
        //setting cat detected flag
        isCatDetected.set(cat);
        if(cat && getArmingStatus() == ArmingStatus.ARMED_HOME) {
            setAlarmStatus(AlarmStatus.ALARM);
        } else if(!cat && areSensorsDeactivated()) {
            setAlarmStatus(AlarmStatus.NO_ALARM);
        }

        statusListeners.forEach(sl -> sl.catDetected(cat));
    }

    /**
     * Register the StatusListener for alarm system updates from within the SecurityService.
     * @param statusListener
     */
    public void addStatusListener(StatusListener statusListener) {
        statusListeners.add(statusListener);
    }

    /**
     * Register the StatusListener for sensor system updates from within the SecurityService.
     * @param statusListener
     */
    public void setSensorStatusListener(SensorStatusListener sensorStatusListener) {
        this.sensorStatusListener = sensorStatusListener;
    }

    public void removeStatusListener(StatusListener statusListener) {
        statusListeners.remove(statusListener);
    }

    public Set<StatusListener> getStatusListeners(){
        return statusListeners;
    }

    /**
     * Change the alarm status of the system and notify all listeners.
     * @param status
     */
    public void setAlarmStatus(AlarmStatus status) {
        securityRepository.setAlarmStatus(status);
        statusListeners.forEach(sl -> sl.notify(status));
    }

    /**
     * Internal method for updating the alarm status when a sensor has been activated.
     */
    private void handleSensorActivated() {
        if(securityRepository.getAlarmStatus().equals(AlarmStatus.NO_ALARM)) {
            setAlarmStatus(AlarmStatus.PENDING_ALARM);
        } else if(securityRepository.getAlarmStatus().equals(AlarmStatus.PENDING_ALARM)){
            setAlarmStatus(AlarmStatus.ALARM);
        }
    }

    /**
     * Internal method for updating the alarm status when a sensor has been deactivated
     */
    private void handleSensorDeactivated() {
        if(securityRepository.getAlarmStatus().equals(AlarmStatus.PENDING_ALARM)){
            setAlarmStatus(AlarmStatus.NO_ALARM);
        } else if(securityRepository.getAlarmStatus().equals(AlarmStatus.ALARM)){
            setAlarmStatus(AlarmStatus.PENDING_ALARM);
        }
    }

    public void handleAlarmStatusBySensorState(Boolean sensorStatus){
        if(sensorStatus){
            handleSensorActivated();
        } else {
            handleSensorDeactivated();
        }
    }

    /**
     * Change the activation status for the specified sensor and update alarm status if necessary.
     * @param sensor
     * @param active
     */
    public void changeSensorActivationStatus(Sensor sensor, Boolean active) {
        if(securityRepository.getArmingStatus() == ArmingStatus.DISARMED) {
            return; //no problem if the system is disarmed
        }
        if(securityRepository.getAlarmStatus().equals(AlarmStatus.ALARM)) {
            if(securityRepository.getArmingStatus() == ArmingStatus.ARMED_AWAY){
                handleSensorDeactivated();
            }
        } else {
            handleAlarmStatusBySensorState(active);
        }
        // if(!securityRepository.getAlarmStatus().equals(AlarmStatus.ALARM)){
        //     if(active){
        //         handleSensorActivated();
        //     } else {
        //         handleSensorDeactivated();
        //     }
        // }
        sensor.setActive(active);
        securityRepository.updateSensor(sensor);
    }

    /**
     * Send an image to the SecurityService for processing. The securityService will use its provided
     * ImageService to analyze the image for cats and update the alarm status accordingly.
     * @param currentCameraImage
     */
    public void processImage(BufferedImage currentCameraImage) {
        catDetected(imageService.imageContainsCat(currentCameraImage, 50.0f));
    }

    public AlarmStatus getAlarmStatus() {
        return securityRepository.getAlarmStatus();
    }

    public Set<Sensor> getSensors() {
        return (new ConcurrentSkipListSet<Sensor>(securityRepository.getSensors()));
    }

    public void addSensor(Sensor sensor) {
        securityRepository.addSensor(sensor);
    }

    public void removeSensor(Sensor sensor) {
        securityRepository.removeSensor(sensor);
    }

    private boolean areSensorsDeactivated(){
        return getSensors().stream()
                        .allMatch(s -> s.getActive() == false);
    }

    public ArmingStatus getArmingStatus() {
        return securityRepository.getArmingStatus();
    }

    public boolean getCatStatus(){
        return isCatDetected.get();
    }
}
