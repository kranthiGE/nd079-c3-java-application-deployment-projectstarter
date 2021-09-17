package com.udacity.catpoint.security.service;

import com.udacity.catpoint.security.application.StatusListener;
import com.udacity.catpoint.security.data.AlarmStatus;
import com.udacity.catpoint.security.data.ArmingStatus;
import com.udacity.catpoint.security.data.SecurityRepository;
import com.udacity.catpoint.security.data.Sensor;
import com.udacity.catpoint.security.data.SensorType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.util.Set;
import java.util.stream.Collectors;

import com.udacity.catpoint.image.service.ImageService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SecurityServiceTest {
    
    private SecurityService securityService;

    private Sensor sensor;

    @Mock
    private ImageService imageService;

    @Mock
    private SecurityRepository securityRepository;

    @Mock
    private StatusListener statusListener;

    @BeforeEach
    void init(){
        securityService = new SecurityService(securityRepository, imageService);
        sensor = new Sensor("TouchSensor", SensorType.DOOR);
    }

    @Test
    @DisplayName("1. If alarm is armed and a sensor becomes activated, put the system into pending alarm status.")
    public void whenArmedAndSensorActivated_alarmShouldBeSetToPending(){
        // when arming status is ARMED_HOME
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        // need to set alarm status to a default value of NO_ALARM
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);
        // and sensor status becomes active, which means code should set it to active 
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository).setAlarmStatus(AlarmStatus.PENDING_ALARM);
    }

    @Test
    @DisplayName("2. If alarm is armed and a sensor becomes activated and the system is already pending alarm, set the alarm status to alarm.")
    public void whenArmedAndSensorActivatedAndSystemInPendingAlarm_alarmShouldBeSetToAlarm(){
        // when armed status is ARMED_HOME
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    @Test
    @DisplayName("3. If pending alarm and all sensors are inactive, return to no alarm state.")
    public void whenAlarmPendingAndSensorAreInactive_alarmShouldBeSetToNoAlarm(){
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        securityService.changeSensorActivationStatus(sensor, false);
        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("4. If alarm is active, change in sensor state should not affect the alarm state.")
    public void whenSensorStateChanged_noChangeToAlarmState(boolean sensorStatus){
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
        securityService.changeSensorActivationStatus(sensor, sensorStatus);
        // can't test by getAlarmStatus() as it will be null
        // so using never() to determine that setAlarmStatus is never called
        verify(securityRepository, never()).setAlarmStatus(AlarmStatus.ALARM);
    }

    @Test
    @DisplayName("5. If a sensor is activated while already active and the system is in pending state, change it to alarm state.")
    public void whenSensorAllreadyActive_AndSystemInPending_changeStateToAlarm(){
        sensor.setActive(true);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    @ParameterizedTest
    @EnumSource(value = AlarmStatus.class, names = {"PENDING_ALARM", "ALARM"})
    @DisplayName("6. If a sensor is deactivated while already inactive, make no changes to the alarm state.")
    public void whenSensorIsDiactiveWhileInactive_dontChangeStateOfAlarm(AlarmStatus alarmStatus){
        sensor.setActive(false);
        when(securityRepository.getAlarmStatus()).thenReturn(alarmStatus);
        securityService.changeSensorActivationStatus(sensor, false);
        // can't test by getAlarmStatus() as it will be null
        // so using never() to determine that setAlarmStatus is never called
        verify(securityRepository, never()).setAlarmStatus(alarmStatus);
    }

    @Test
    @DisplayName("7. If the image service identifies an image containing a cat while the system is armed-home, put the system into alarm status.")
    public void whenImageIsCatAndArmed_changeStateToAlarm(){
        // image service has cat image
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);
        // arming status is ARMED_HOME
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);

        securityService.processImage(new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB));
        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    @Test
    @DisplayName("8. If the image service identifies an image that does not contain a cat, change the status to no alarm as long as the sensors are not active.")
    public void whenImageIsNotCatAndSensorDeactivated_changeStatetoNoAlarm(){
        // pass sensors in deactivated state
        when(securityService.getSensors()).thenReturn(
            getSensorTestDataSet(false)
        );
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(false);

        securityService.processImage(new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB));
        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    private Set<Sensor> getSensorTestDataSet(boolean isActive) {
        return Set.of(
            new Sensor("s1", SensorType.DOOR),
            new Sensor("s2", SensorType.WINDOW)
        )
        .stream()
        .peek(s -> s.setActive(isActive))
        .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("9. If the system is disarmed, set the status to no alarm.")
    public void whenSystemIsDisarmed_verifyStateAsNoAlarm(){
        securityService.setArmingStatus(ArmingStatus.DISARMED);
        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    @Test
    @DisplayName("10. If the system is armed, reset all sensors to inactive.")
    public void whenSystemIsArmed_verifySensorsToBeInActive(){
        // pass sensors in deactivated state
        when(securityService.getSensors()).thenReturn(
            getSensorTestDataSet(true)
        );
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);
        assertEquals(2, securityService.getSensors().size());
        securityService.getSensors()
                        .stream()
                        .forEach(
                            s -> assertFalse(s.getActive())
                        );

    }

    @Test
    @DisplayName("11. If the system is armed-home while the camera shows a cat, set the alarm status to alarm.")
    public void whenSystemIsArmedAndCurrentImageIsCat_changeStateToAlarm(){
        // image service has cat image
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        securityService.processImage(new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB));
        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    @Test
    @DisplayName("Testing Addition of Status Listener")
    public void whenStatusListenerAdded_verifyTheSizeOfListeners(){
        securityService.addStatusListener(statusListener);
        assertEquals(1, securityService.getStatusListeners().size());
    }

    @Test
    @DisplayName("Testing removal of a Status Listener")
    public void whenStatusListenerRemoved_verifyTheSizeOfListeners(){
        securityService.addStatusListener(statusListener);
        securityService.removeStatusListener(statusListener);
        assertEquals(0, securityService.getStatusListeners().size());
    }

    @Test
    @DisplayName("Verify if sensor is added to database")
    public void whenSensorAdded_verifyIfAddedToDatabase(){
        securityService.addSensor(sensor);
        verify(securityRepository).addSensor(sensor);
    }

    @Test
    @DisplayName("Verify if sensor is removed from database")
    public void whenSensorRemoved_verifyInDatabase(){
        securityService.removeSensor(sensor);
        verify(securityRepository).removeSensor(sensor);
    }

    @Test
    @DisplayName("If the input image processed has been identified to be a Cat then catDetected to be called on every status listener")
    public void statusListenerToBeNotified_whenCatImageIsProcessed(){
        securityService.addStatusListener(statusListener);
        securityService.processImage(new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB));
        verify(statusListener).catDetected(anyBoolean());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("If sensor is activated, while Disarmed and alarm in pending state, make no change to alarm state")
    public void whenDisarmedAndAlarmInPendingAndSensorIsActivated_dontChangeAlarmState(boolean sensorState){
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.DISARMED);
        securityService.changeSensorActivationStatus(sensor, sensorState);
        verify(securityRepository, never()).setAlarmStatus(any());
    }

    @Test
    public void whenGetAlarmStatusInvoked_fetchValueFromDatabase(){
        securityService.getAlarmStatus();
        verify(securityRepository).getAlarmStatus();
    }

    @Test
    @DisplayName("If a sensor is deactivated while already inactive, throw exception for NO_ALARM state.")
    public void whenSensorIsDiactiveWhileInactive_throwExceptionForNoAlarmState(){
        sensor.setActive(false);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);
        try{
            securityService.changeSensorActivationStatus(sensor, false);
        } catch(IllegalArgumentException ex){
            assertTrue(ex.getMessage().contains("Unexpected value: NO_ALARM"));
        }
        verify(securityRepository, never()).updateSensor(sensor);
    }

    
}
