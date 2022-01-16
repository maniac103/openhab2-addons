/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.ecovacs.internal;

import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.pott.sucks.api.EcovacsApi;
import dev.pott.sucks.api.EcovacsApiException;
import dev.pott.sucks.api.EcovacsDevice;
import dev.pott.sucks.api.commands.GoChargingCommand;
import dev.pott.sucks.api.commands.StartAutoCleaningCommand;
import dev.pott.sucks.api.commands.StopCleaningCommand;
import dev.pott.sucks.cleaner.CleanMode;
import dev.pott.sucks.cleaner.ErrorDescription;
import dev.pott.sucks.cleaner.MoppingWaterAmount;
import dev.pott.sucks.cleaner.SuctionPower;

/**
 * The {@link EcovacsDeviceHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Danny Baumann - Initial contribution
 */
@NonNullByDefault
public class EcovacsDeviceHandler extends BaseThingHandler implements EcovacsDevice.StateChangeListener {

    private final Logger logger = LoggerFactory.getLogger(EcovacsDeviceHandler.class);

    private @Nullable ScheduledFuture<?> reconnectFuture;
    private @Nullable EcovacsDevice device;

    private int lastBatteryLevel;
    private @Nullable Boolean lastWasCharging;
    private @Nullable CleanMode lastCleanMode;
    private @Nullable Boolean lastWaterPlatePresent;
    private @Nullable MoppingWaterAmount lastMoppingWaterAmount;

    public EcovacsDeviceHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        String channel = channelUID.getId();

        if (channel.equals(EcovacsBindingConstants.CHANNEL_ID_COMMAND) && command instanceof StringType) {
            try {
                handleDeviceCommand(command.toString());
            } catch (EcovacsApiException e) {
                logger.debug("Handling device command " + command + " failed", e);
            }
        }
    }

    @Override
    public void initialize() {
        updateStatus(ThingStatus.UNKNOWN);
        scheduler.execute(() -> {
            final Bridge bridge = getBridge();
            final EcovacsApiHandler handler = bridge != null ? (EcovacsApiHandler) bridge.getHandler() : null;
            final EcovacsApi api = handler != null ? handler.getApi() : null;

            if (api == null) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED);
                return;
            }

            try {
                String serial = getThing().getUID().getId();
                Optional<EcovacsDevice> device = api.getDevices().stream()
                        .filter(d -> serial.equals(d.getSerialNumber())).findFirst();
                if (device.isPresent()) {
                    this.device = device.get();
                    connectToDevice();
                } else {
                    updateStatus(ThingStatus.OFFLINE);
                }
            } catch (EcovacsApiException e) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
            }
        });
    }

    @Override
    public void dispose() {
        super.dispose();
        EcovacsDevice device = this.device;
        if (device != null) {
            device.disconnect();
        }
        ScheduledFuture<?> reconnectFuture = this.reconnectFuture;
        if (reconnectFuture != null) {
            reconnectFuture.cancel(true);
        }
    }

    @Override
    public void channelLinked(ChannelUID channelUID) {
        EcovacsDevice device = this.device;
        if (device == null) {
            return;
        }

        switch (channelUID.getId()) {
            case EcovacsBindingConstants.CHANNEL_ID_BATTERY_LEVEL:
                onBatteryLevelChanged(device, lastBatteryLevel);
                break;
            case EcovacsBindingConstants.CHANNEL_ID_STATE:
            case EcovacsBindingConstants.CHANNEL_ID_COMMAND:
                updateStateAndCommandChannels();
                break;
            case EcovacsBindingConstants.CHANNEL_ID_WATER_PLATE_PRESENT: {
                final MoppingWaterAmount amount = lastMoppingWaterAmount;
                if (amount != null) {
                    onWaterSystemChanged(device, lastWaterPlatePresent, amount);
                }
                break;
            }
        }
    }

    @Override
    public void onBatteryLevelChanged(EcovacsDevice device, int newLevelPercent) {
        lastBatteryLevel = newLevelPercent;
        updateState(EcovacsBindingConstants.CHANNEL_ID_BATTERY_LEVEL, new DecimalType(newLevelPercent));
    }

    @Override
    public void onChargingStateChanged(EcovacsDevice device, boolean charging) {
        lastWasCharging = charging;
        updateStateAndCommandChannels();
    }

    @Override
    public void onCleaningModeChanged(EcovacsDevice device, CleanMode newMode) {
        lastCleanMode = newMode;
        updateStateAndCommandChannels();
    }

    @Override
    public void onCleaningPowerChanged(EcovacsDevice device, SuctionPower newPower) {
    }

    @Override
    public void onCleaningStatsChanged(EcovacsDevice device, int cleanedArea, int cleaningTimeSeconds) {
        updateState(EcovacsBindingConstants.CHANNEL_ID_CLEANED_AREA,
                new QuantityType<>(cleanedArea, SIUnits.SQUARE_METRE));
        updateState(EcovacsBindingConstants.CHANNEL_ID_CLEANING_TIME,
                new QuantityType<>(cleaningTimeSeconds, Units.SECOND));
    }

    @Override
    public void onWaterSystemChanged(EcovacsDevice device, boolean present, MoppingWaterAmount amount) {
        lastWaterPlatePresent = present;
        lastMoppingWaterAmount = amount;
        updateState(EcovacsBindingConstants.CHANNEL_ID_WATER_PLATE_PRESENT, OnOffType.from(present));
    }

    @Override
    public void onErrorReported(EcovacsDevice device, ErrorDescription error) {
        // TODO
    }

    @Override
    public void onDeviceConnectionFailed(final EcovacsDevice device, Throwable error) {
        logger.debug(getThing().getUID() + ": Device connection failed, reconnecting", error);
        device.disconnect();
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
        scheduleReconnection();
    }

    private void scheduleReconnection() {
        if (reconnectFuture == null) {
            reconnectFuture = scheduler.schedule(() -> {
                reconnectFuture = null;
                connectToDevice();
            }, 5, TimeUnit.SECONDS);
        }
    }

    private void connectToDevice() {
        EcovacsDevice device = this.device;
        if (device == null) {
            return;
        }
        try {
            device.connect(this);
            updateStatus(ThingStatus.ONLINE);
        } catch (EcovacsApiException e) {
            logger.debug(getThing().getUID() + ": Could not establish device connection, reconnecting", e);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
            scheduleReconnection();
        }
    }

    private void updateStateAndCommandChannels() {
        Boolean charging = this.lastWasCharging;
        CleanMode cleanMode = this.lastCleanMode;
        if (charging == null || cleanMode == null) {
            return;
        }
        String commandState = determineCommandChannelValue(charging, cleanMode);
        updateState(EcovacsBindingConstants.CHANNEL_ID_STATE,
                new StringType(determineStateChannelValue(charging, cleanMode)));
        updateState(EcovacsBindingConstants.CHANNEL_ID_COMMAND,
                commandState != null ? new StringType(commandState) : UnDefType.NULL);
    }

    private String determineStateChannelValue(boolean charging, CleanMode cleanMode) {
        if (charging) {
            return "charging";
        }
        switch (cleanMode) {
            case AUTO:
                return "auto";
            case EDGE:
                return "edge";
            case SPOT:
                return "spot";
            case SPOT_AREA:
                return "spotArea";
            case CUSTOM_AREA:
                return "customArea";
            case SINGLE_ROOM:
                return "singleRoom";
            case PAUSE:
                return "pause";
            case STOP:
                return "stop";
            case RETURNING:
                return "returning";
            case IDLE:
                break;
        }
        return "";
    }

    private @Nullable String determineCommandChannelValue(boolean charging, CleanMode cleanMode) {
        if (charging) {
            return EcovacsBindingConstants.CMD_CHARGE;
        }
        switch (cleanMode) {
            case AUTO:
                return EcovacsBindingConstants.CMD_AUTO_CLEAN;
            case PAUSE:
                return EcovacsBindingConstants.CMD_PAUSE;
            case STOP:
                return EcovacsBindingConstants.CMD_STOP;
            case RETURNING:
                return EcovacsBindingConstants.CMD_CHARGE;
            default:
                break;
        }
        return null;
    }

    private void handleDeviceCommand(String command) throws EcovacsApiException {
        final EcovacsDevice device = this.device;
        if (device == null) {
            logger.debug("Ignoring command {} for {}, no active connection", command, getThing().getUID());
            return;
        }

        switch (command) {
            case EcovacsBindingConstants.CMD_AUTO_CLEAN:
                device.sendCommand(new StartAutoCleaningCommand());
                break;
            case EcovacsBindingConstants.CMD_STOP:
                device.sendCommand(new StopCleaningCommand());
                break;
            case EcovacsBindingConstants.CMD_CHARGE:
                device.sendCommand(new GoChargingCommand());
                break;
        }
    }
}
