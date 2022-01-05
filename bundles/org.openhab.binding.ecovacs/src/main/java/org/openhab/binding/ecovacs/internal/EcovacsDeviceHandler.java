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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.StringType;
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
import dev.pott.sucks.api.dto.request.commands.GoChargingCommand;
import dev.pott.sucks.api.dto.request.commands.StartCleaningCommand;
import dev.pott.sucks.api.dto.request.commands.StopCommand;
import dev.pott.sucks.cleaner.CleanMode;
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

    private @Nullable EcovacsConfiguration config;
    private @Nullable EcovacsDevice device;

    private int lastBatteryLevel;
    private @Nullable Boolean lastWasCharging;
    private @Nullable CleanMode lastCleanMode;

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
        config = getConfigAs(EcovacsConfiguration.class);

        updateStatus(ThingStatus.UNKNOWN);

        scheduler.execute(() -> {
            final Bridge bridge = getBridge();
            final EcovacsApiHandler handler = bridge != null ? (EcovacsApiHandler) bridge.getHandler() : null;
            final EcovacsApi api = handler != null ? handler.getApi() : null;

            if (api != null) {
                try {
                    String serial = getThing().getUID().getId();
                    Optional<EcovacsDevice> device = api.getDevices()
                            .stream()
                            .filter(d -> serial.equals(d.getSerialNumber()))
                            .findFirst();
                    if (device.isPresent()) {
                        this.device = device.get();
                        this.device.connect(this);
                        updateStatus(ThingStatus.ONLINE);
                    } else {
                        updateStatus(ThingStatus.OFFLINE);
                    }
                } catch (EcovacsApiException e) {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
                }
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED);
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

    private void updateStateAndCommandChannels() {
        if (lastWasCharging == null || lastCleanMode == null) {
            return;
        }
        String commandState = determineCommandChannelValue();
        updateState(EcovacsBindingConstants.CHANNEL_ID_STATE, new StringType(determineStateChannelValue()));
        updateState(EcovacsBindingConstants.CHANNEL_ID_COMMAND, commandState != null ? new StringType(commandState) : UnDefType.NULL);
    }

    private String determineStateChannelValue() {
        if (lastWasCharging) {
            return "charging";
        }
        switch (lastCleanMode) {
            case AUTO: return "auto";
            case EDGE: return "edge";
            case SPOT: return "spot";
            case SPOT_AREA: return "spotArea";
            case CUSTOM_AREA: return "customArea";
            case SINGLE_ROOM: return "singleRoom";
            case PAUSE: return "pause";
            case STOP: return "stop";
            case RETURNING: return "returning";
        }
        return "";
    }

    private @Nullable String determineCommandChannelValue() {
        if (lastWasCharging) {
            return EcovacsBindingConstants.CMD_CHARGE;
        }
        switch (lastCleanMode) {
            case AUTO: return EcovacsBindingConstants.CMD_AUTO_CLEAN;
            case PAUSE: return EcovacsBindingConstants.CMD_PAUSE;
            case STOP: return EcovacsBindingConstants.CMD_STOP;
            case RETURNING: return EcovacsBindingConstants.CMD_CHARGE;
            default: break;
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
                device.sendCommand(new StartCleaningCommand());
                break;
            case EcovacsBindingConstants.CMD_STOP:
                device.sendCommand(new StopCommand());
                break;
            case EcovacsBindingConstants.CMD_CHARGE:
                device.sendCommand(new GoChargingCommand());
                break;
        }
    }
}
