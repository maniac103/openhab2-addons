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

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.RawType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.builder.ThingBuilder;
import org.openhab.core.types.Command;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.pott.sucks.api.EcovacsApi;
import dev.pott.sucks.api.EcovacsApiException;
import dev.pott.sucks.api.EcovacsDevice;
import dev.pott.sucks.api.commands.GetComponentLifeSpanCommand;
import dev.pott.sucks.api.commands.GetMoppingWaterAmountCommand;
import dev.pott.sucks.api.commands.GetNetworkInfoCommand;
import dev.pott.sucks.api.commands.GetSuctionPowerCommand;
import dev.pott.sucks.api.commands.GetTotalStatsCommand;
import dev.pott.sucks.api.commands.GetTotalStatsCommand.TotalStats;
import dev.pott.sucks.api.commands.GetVolumeCommand;
import dev.pott.sucks.api.commands.GoChargingCommand;
import dev.pott.sucks.api.commands.SetMoppingWaterAmountCommand;
import dev.pott.sucks.api.commands.SetSuctionPowerCommand;
import dev.pott.sucks.api.commands.SetVolumeCommand;
import dev.pott.sucks.api.commands.StartAutoCleaningCommand;
import dev.pott.sucks.api.commands.StopCleaningCommand;
import dev.pott.sucks.cleaner.CleanLogRecord;
import dev.pott.sucks.cleaner.CleanMode;
import dev.pott.sucks.cleaner.Component;
import dev.pott.sucks.cleaner.DeviceCapability;
import dev.pott.sucks.cleaner.ErrorDescription;
import dev.pott.sucks.cleaner.MoppingWaterAmount;
import dev.pott.sucks.cleaner.NetworkInfo;
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
    private @Nullable ScheduledFuture<?> pollFuture;
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
        final EcovacsDevice device = this.device;
        if (device == null) {
            logger.debug("{}: Ignoring command {}, no active connection", getDeviceSerial(), command);
            return;
        }
        String channel = channelUID.getId();

        try {
            if (channel.equals(EcovacsBindingConstants.CHANNEL_ID_COMMAND) && command instanceof StringType) {
                handleDeviceCommand(device, command.toString());
                return;
            } else if (channel.equals(EcovacsBindingConstants.CHANNEL_ID_VOICE_VOLUME) && command instanceof DecimalType) {
                int volumePercent = ((DecimalType) command).intValue();
                device.sendCommand(new SetVolumeCommand((volumePercent + 5) / 10));
                return;
            } else if (channel.equals(EcovacsBindingConstants.CHANNEL_ID_SUCTION_POWER) && command instanceof StringType) {
                SuctionPower power = findMappedEnumValue(EcovacsBindingConstants.SUCTION_POWER_MAPPING,
                        command.toString());
                if (power != null) {
                    device.sendCommand(new SetSuctionPowerCommand(power));
                    return;
                }
            } else if (channel.equals(EcovacsBindingConstants.CHANNEL_ID_WATER_AMOUNT) && command instanceof StringType) {
                MoppingWaterAmount amount = findMappedEnumValue(EcovacsBindingConstants.WATER_AMOUNT_MAPPING,
                        command.toString());
                if (amount != null) {
                    device.sendCommand(new SetMoppingWaterAmountCommand(amount));
                    return;
                }
            }
            logger.debug("{}: Ignoring unsupported device command {}", getDeviceSerial(), command);
        } catch (EcovacsApiException e) {
            logger.debug(getDeviceSerial() + ": Handling device command " + command + " failed", e);
        }
    }

    @Override
    public void initialize() {
        logger.debug("{}: Initializing handler", getDeviceSerial());
        scheduler.execute(() -> {
            final Bridge bridge = getBridge();
            final EcovacsApiHandler handler = bridge != null ? (EcovacsApiHandler) bridge.getHandler() : null;
            final EcovacsApi api = handler != null ? handler.getApi() : null;

            if (api == null) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED);
                return;
            }

            try {
                String serial = getDeviceSerial();
                Optional<EcovacsDevice> device = api.getDevices().stream()
                        .filter(d -> serial.equals(d.getSerialNumber())).findFirst();
                if (device.isPresent()) {
                    this.device = device.get();
                    removeUnsupportedChannels(device.get());
                    connectToDevice();
                } else {
                    logger.info("{}: Device not found in device list, setting offline", serial);
                    updateStatus(ThingStatus.OFFLINE);
                }
            } catch (EcovacsApiException e) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
            }
        });
    }

    @Override
    public void dispose() {
        logger.debug("{}: Disposing handler", getDeviceSerial());
        super.dispose();
        EcovacsDevice device = this.device;
        if (device != null) {
            device.disconnect();
        }
        ScheduledFuture<?> reconnectFuture = this.reconnectFuture;
        if (reconnectFuture != null) {
            reconnectFuture.cancel(true);
        }
        stopPolling();
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
            default:
                startPolling(5); // add some delay in case multiple channels are linked at once
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
        if (newMode == CleanMode.RETURNING) {
            startPolling(30);
        } else if (newMode == CleanMode.IDLE) {
            updateState(EcovacsBindingConstants.CHANNEL_ID_CLEANED_AREA, UnDefType.UNDEF);
            updateState(EcovacsBindingConstants.CHANNEL_ID_CLEANING_TIME, UnDefType.UNDEF);
        }
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
        logger.debug(getDeviceSerial() + ": Device connection failed, reconnecting", error);
        device.disconnect();
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
        stopPolling();
        scheduleReconnection();
    }

    private void removeUnsupportedChannels(EcovacsDevice device) {
        ThingBuilder builder = editThing();
        boolean hasChanges = false;

        if (!device.hasCapability(DeviceCapability.MOPPING_SYSTEM)) {
            hasChanges |= removeUnsupportedChannel(builder, EcovacsBindingConstants.CHANNEL_ID_WATER_AMOUNT);
            hasChanges |= removeUnsupportedChannel(builder, EcovacsBindingConstants.CHANNEL_ID_WATER_PLATE_PRESENT);
        }
        if (!device.hasCapability(DeviceCapability.CLEAN_SPEED_CONTROL)) {
            hasChanges |= removeUnsupportedChannel(builder, EcovacsBindingConstants.CHANNEL_ID_SUCTION_POWER);
        }
        if (!device.hasCapability(DeviceCapability.MAIN_BRUSH)) {
            hasChanges |= removeUnsupportedChannel(builder, EcovacsBindingConstants.CHANNEL_ID_MAIN_BRUSH_LIFETIME);
        }
        if (!device.hasCapability(DeviceCapability.VOICE_REPORTING)) {
            hasChanges |= removeUnsupportedChannel(builder, EcovacsBindingConstants.CHANNEL_ID_VOICE_VOLUME);
        }

        if (hasChanges) {
            updateThing(builder.build());
        }
    }

    private boolean removeUnsupportedChannel(ThingBuilder builder, String channelId) {
        ChannelUID channelUID = new ChannelUID(getThing().getUID(), channelId);
        if (getThing().getChannel(channelUID) == null) {
            return false;
        }
        logger.debug("{}: Removing unsupported channel {}", getDeviceSerial(), channelId);
        builder.withoutChannel(channelUID);
        return true;
    }

    private synchronized void startPolling(long initialDelaySeconds) {
        stopPolling();

        final EcovacsDeviceConfiguration config = getConfigAs(EcovacsDeviceConfiguration.class);
        logger.debug("{}: Scheduling next poll in {}s, refresh interval {}min", getDeviceSerial(), initialDelaySeconds,
                config.refresh);
        pollFuture = scheduler.scheduleWithFixedDelay(this::pollData, initialDelaySeconds, config.refresh * 60,
                TimeUnit.SECONDS);
    }

    private synchronized void stopPolling() {
        final ScheduledFuture<?> pollFuture = this.pollFuture;
        if (pollFuture != null) {
            pollFuture.cancel(true);
            this.pollFuture = null;
        }
    }

    private synchronized void scheduleReconnection() {
        if (reconnectFuture == null) {
            reconnectFuture = scheduler.schedule(() -> {
                reconnectFuture = null;
                connectToDevice();
            }, 5, TimeUnit.SECONDS);
        }
    }

    private void connectToDevice() {
        doWithDevice(device -> {
            device.connect(this);
            logger.debug("{}: Device connected", getDeviceSerial());
            updateStatus(ThingStatus.ONLINE);
            startPolling(0);
        });
    }

    private void pollData() {
        logger.debug("{}: Polling data", getDeviceSerial());
        doWithDevice(device -> {
            TotalStats totalStats = device.sendCommand(new GetTotalStatsCommand());
            updateState(EcovacsBindingConstants.CHANNEL_ID_TOTAL_CLEANED_AREA,
                    new QuantityType<>(totalStats.totalArea, SIUnits.SQUARE_METRE));
            updateState(EcovacsBindingConstants.CHANNEL_ID_TOTAL_CLEANING_TIME,
                    new QuantityType<>(totalStats.totalRuntime, Units.SECOND));
            updateState(EcovacsBindingConstants.CHANNEL_ID_TOTAL_CLEAN_RUNS, new DecimalType(totalStats.cleanRuns));

            List<CleanLogRecord> lastCleanRecord = device.getCleanLogs(1);
            if (!lastCleanRecord.isEmpty()) {
                CleanLogRecord record = lastCleanRecord.get(0);
                updateState(EcovacsBindingConstants.CHANNEL_ID_LAST_CLEAN_START,
                        new DateTimeType(record.timestamp.toInstant().atZone(ZoneId.systemDefault())));
                updateState(EcovacsBindingConstants.CHANNEL_ID_LAST_CLEAN_DURATION,
                        new QuantityType<>(record.cleaningDuration, Units.SECOND));
                updateState(EcovacsBindingConstants.CHANNEL_ID_LAST_CLEAN_AREA,
                        new QuantityType<>(record.cleanedArea, SIUnits.SQUARE_METRE));
                updateState(EcovacsBindingConstants.CHANNEL_ID_LAST_CLEAN_MODE,
                        new StringType(EcovacsBindingConstants.CLEAN_MODE_MAPPING.get(record.mode)));
                updateState(EcovacsBindingConstants.CHANNEL_ID_LAST_CLEAN_MAP,
                        new RawType(record.mapImagePngData, "image/png"));
            }

            if (device.hasCapability(DeviceCapability.CLEAN_SPEED_CONTROL)) {
                SuctionPower power = device.sendCommand(new GetSuctionPowerCommand());
                updateState(EcovacsBindingConstants.CHANNEL_ID_SUCTION_POWER,
                        new StringType(EcovacsBindingConstants.SUCTION_POWER_MAPPING.get(power)));
            }

            if (device.hasCapability(DeviceCapability.MOPPING_SYSTEM)) {
                MoppingWaterAmount waterAmount = device.sendCommand(new GetMoppingWaterAmountCommand());
                updateState(EcovacsBindingConstants.CHANNEL_ID_WATER_AMOUNT,
                        new StringType(EcovacsBindingConstants.WATER_AMOUNT_MAPPING.get(waterAmount)));
            }

            NetworkInfo netInfo = device.sendCommand(new GetNetworkInfoCommand());
            if (netInfo.wifiRssi != 0) {
                updateState(EcovacsBindingConstants.CHANNEL_ID_WIFI_RSSI,
                        new QuantityType<>(netInfo.wifiRssi, Units.DECIBEL_MILLIWATTS));
            }

            int sideBrushPercent = device.sendCommand(new GetComponentLifeSpanCommand(Component.SIDE_BRUSH));
            updateState(EcovacsBindingConstants.CHANNEL_ID_SIDE_BRUSH_LIFETIME,
                    new QuantityType<>(sideBrushPercent, Units.PERCENT));
            int filterPercent = device.sendCommand(new GetComponentLifeSpanCommand(Component.DUST_CASE_HEAP));
            updateState(EcovacsBindingConstants.CHANNEL_ID_DUST_FILTER_LIFETIME,
                    new QuantityType<>(filterPercent, Units.PERCENT));

            if (device.hasCapability(DeviceCapability.MAIN_BRUSH)) {
                int mainBrushPercent = device.sendCommand(new GetComponentLifeSpanCommand(Component.BRUSH));
                updateState(EcovacsBindingConstants.CHANNEL_ID_MAIN_BRUSH_LIFETIME,
                        new QuantityType<>(mainBrushPercent, Units.PERCENT));
            }
            if (device.hasCapability(DeviceCapability.VOICE_REPORTING)) {
                int level = device.sendCommand(new GetVolumeCommand());
                updateState(EcovacsBindingConstants.CHANNEL_ID_VOICE_VOLUME, new PercentType(level * 10));
            }
        });
        logger.debug("{}: Data polling completed", getDeviceSerial());
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
        if (charging && cleanMode != CleanMode.RETURNING) {
            return "charging";
        }
        String result = EcovacsBindingConstants.CLEAN_MODE_MAPPING.get(cleanMode);
        return result != null ? result : "";
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

    private void handleDeviceCommand(EcovacsDevice device, String command) throws EcovacsApiException {
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

    private interface WithDeviceAction {
        void run(EcovacsDevice device) throws EcovacsApiException;
    }

    private void doWithDevice(WithDeviceAction action) {
        EcovacsDevice device = this.device;
        if (device == null) {
            return;
        }
        try {
            action.run(device);
        } catch (EcovacsApiException e) {
            if (e.getCause() instanceof InterruptedException) {
                return;
            }
            logger.debug(getDeviceSerial() + ": Failed communicating to device, reconnecting", e);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
            stopPolling();
            scheduleReconnection();
        }
    }

    private <T> @Nullable T findMappedEnumValue(Map<T, String> mapping, String value) {
        return mapping.entrySet().stream().filter(entry -> entry.getValue().equals(value)).map(entry -> entry.getKey())
                .findFirst().get();
    }

    private String getDeviceSerial() {
        return getThing().getUID().getId();
    }
}
