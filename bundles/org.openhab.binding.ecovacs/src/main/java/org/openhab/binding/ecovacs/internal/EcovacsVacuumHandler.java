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

import static org.openhab.binding.ecovacs.internal.EcovacsBindingConstants.*;

import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.ecovacs.internal.api.EcovacsApi;
import org.openhab.binding.ecovacs.internal.api.EcovacsApiException;
import org.openhab.binding.ecovacs.internal.api.EcovacsDevice;
import org.openhab.binding.ecovacs.internal.api.commands.AbstractNoResponseCommand;
import org.openhab.binding.ecovacs.internal.api.commands.GetBatteryInfoCommand;
import org.openhab.binding.ecovacs.internal.api.commands.GetChargeStateCommand;
import org.openhab.binding.ecovacs.internal.api.commands.GetCleanStateCommand;
import org.openhab.binding.ecovacs.internal.api.commands.GetComponentLifeSpanCommand;
import org.openhab.binding.ecovacs.internal.api.commands.GetErrorCommand;
import org.openhab.binding.ecovacs.internal.api.commands.GetMoppingWaterAmountCommand;
import org.openhab.binding.ecovacs.internal.api.commands.GetNetworkInfoCommand;
import org.openhab.binding.ecovacs.internal.api.commands.GetSuctionPowerCommand;
import org.openhab.binding.ecovacs.internal.api.commands.GetTotalStatsCommand;
import org.openhab.binding.ecovacs.internal.api.commands.GetTotalStatsCommand.TotalStats;
import org.openhab.binding.ecovacs.internal.api.commands.GetVolumeCommand;
import org.openhab.binding.ecovacs.internal.api.commands.GetWaterSystemPresentCommand;
import org.openhab.binding.ecovacs.internal.api.commands.GoChargingCommand;
import org.openhab.binding.ecovacs.internal.api.commands.PauseCleaningCommand;
import org.openhab.binding.ecovacs.internal.api.commands.ResumeCleaningCommand;
import org.openhab.binding.ecovacs.internal.api.commands.SetMoppingWaterAmountCommand;
import org.openhab.binding.ecovacs.internal.api.commands.SetSuctionPowerCommand;
import org.openhab.binding.ecovacs.internal.api.commands.SetVolumeCommand;
import org.openhab.binding.ecovacs.internal.api.commands.StartAutoCleaningCommand;
import org.openhab.binding.ecovacs.internal.api.commands.StopCleaningCommand;
import org.openhab.binding.ecovacs.internal.api.model.ChargeMode;
import org.openhab.binding.ecovacs.internal.api.model.CleanLogRecord;
import org.openhab.binding.ecovacs.internal.api.model.CleanMode;
import org.openhab.binding.ecovacs.internal.api.model.Component;
import org.openhab.binding.ecovacs.internal.api.model.DeviceCapability;
import org.openhab.binding.ecovacs.internal.api.model.MoppingWaterAmount;
import org.openhab.binding.ecovacs.internal.api.model.NetworkInfo;
import org.openhab.binding.ecovacs.internal.api.model.SuctionPower;
import org.openhab.core.i18n.LocaleProvider;
import org.openhab.core.i18n.TranslationProvider;
import org.openhab.core.io.net.http.HttpUtil;
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
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link EcovacsVacuumHandler} is responsible for handling data and commands from/to vacuum cleaners.
 *
 * @author Danny Baumann - Initial contribution
 */
@NonNullByDefault
public class EcovacsVacuumHandler extends BaseThingHandler implements EcovacsDevice.EventListener {

    private final Logger logger = LoggerFactory.getLogger(EcovacsVacuumHandler.class);

    private final TranslationProvider i18Provider;
    private final LocaleProvider localeProvider;
    private final Bundle bundle;
    private @Nullable ScheduledFuture<?> reconnectFuture;
    private @Nullable ScheduledFuture<?> pollFuture;
    private @Nullable EcovacsDevice device;

    private @Nullable Boolean lastWasCharging;
    private @Nullable CleanMode lastCleanMode;
    private Optional<String> lastCleanMapUrl = Optional.empty();

    public EcovacsVacuumHandler(Thing thing, TranslationProvider i18Provider, LocaleProvider localeProvider) {
        super(thing);
        this.i18Provider = i18Provider;
        this.localeProvider = localeProvider;
        bundle = FrameworkUtil.getBundle(getClass());
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
            if (channel.equals(CHANNEL_ID_COMMAND) && command instanceof StringType) {
                AbstractNoResponseCommand cmd = determineDeviceCommand(device, command.toString());
                if (cmd != null) {
                    device.sendCommand(cmd);
                    return;
                }
            } else if (channel.equals(CHANNEL_ID_VOICE_VOLUME) && command instanceof DecimalType) {
                int volumePercent = ((DecimalType) command).intValue();
                device.sendCommand(new SetVolumeCommand((volumePercent + 5) / 10));
                return;
            } else if (channel.equals(CHANNEL_ID_SUCTION_POWER) && command instanceof StringType) {
                SuctionPower power = findMappedEnumValue(SUCTION_POWER_MAPPING, command.toString());
                if (power != null) {
                    device.sendCommand(new SetSuctionPowerCommand(power));
                    return;
                }
            } else if (channel.equals(CHANNEL_ID_WATER_AMOUNT) && command instanceof StringType) {
                MoppingWaterAmount amount = findMappedEnumValue(WATER_AMOUNT_MAPPING, command.toString());
                if (amount != null) {
                    device.sendCommand(new SetMoppingWaterAmountCommand(amount));
                    return;
                }
            }
            logger.debug("{}: Ignoring unsupported device command {} for channel {}", getDeviceSerial(), command,
                    channel);
        } catch (EcovacsApiException e) {
            logger.debug("{}: Handling device command {} failed", getDeviceSerial(), command, e);
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
            device.stopListeningForEvents();
        }
        ScheduledFuture<?> reconnectFuture = this.reconnectFuture;
        if (reconnectFuture != null) {
            reconnectFuture.cancel(true);
        }
        stopPolling();
    }

    @Override
    public void channelLinked(ChannelUID channelUID) {
        try {
            switch (channelUID.getId()) {
                case CHANNEL_ID_BATTERY_LEVEL:
                    fetchInitialBatteryStatus();
                    break;
                case CHANNEL_ID_STATE:
                case CHANNEL_ID_COMMAND:
                    fetchInitialStateAndCommandValues();
                    break;
                case CHANNEL_ID_WATER_PLATE_PRESENT:
                    fetchInitialWaterSystemValues();
                    break;
                case CHANNEL_ID_ERROR_CODE:
                case CHANNEL_ID_ERROR_DESCRIPTION:
                    fetchInitialErrorCode();
                default:
                    startPolling(5); // add some delay in case multiple channels are linked at once
                    break;
            }
        } catch (EcovacsApiException e) {
            logger.debug("{}: Fetching initial data for channel {} failed", getDeviceSerial(), channelUID.getId(), e);
        }
    }

    @Override
    public void onBatteryLevelUpdated(EcovacsDevice device, int newLevelPercent) {
        updateState(CHANNEL_ID_BATTERY_LEVEL, new DecimalType(newLevelPercent));
    }

    @Override
    public void onChargingStateUpdated(EcovacsDevice device, boolean charging) {
        lastWasCharging = charging;
        updateStateAndCommandChannels();
    }

    @Override
    public void onCleaningModeUpdated(EcovacsDevice device, CleanMode newMode) {
        lastCleanMode = newMode;
        updateStateAndCommandChannels();
        if (newMode == CleanMode.RETURNING) {
            startPolling(30);
        } else if (newMode == CleanMode.IDLE) {
            updateState(CHANNEL_ID_CLEANED_AREA, UnDefType.UNDEF);
            updateState(CHANNEL_ID_CLEANING_TIME, UnDefType.UNDEF);
        }
    }

    @Override
    public void onCleaningPowerUpdated(EcovacsDevice device, SuctionPower newPower) {
    }

    @Override
    public void onCleaningStatsUpdated(EcovacsDevice device, int cleanedArea, int cleaningTimeSeconds) {
        updateState(CHANNEL_ID_CLEANED_AREA, new QuantityType<>(cleanedArea, SIUnits.SQUARE_METRE));
        updateState(CHANNEL_ID_CLEANING_TIME, new QuantityType<>(cleaningTimeSeconds, Units.SECOND));
    }

    @Override
    public void onWaterSystemUpdated(EcovacsDevice device, boolean present, MoppingWaterAmount amount) {
        updateState(CHANNEL_ID_WATER_PLATE_PRESENT, OnOffType.from(present));
        updateState(CHANNEL_ID_WATER_AMOUNT, new StringType(WATER_AMOUNT_MAPPING.get(amount)));
    }

    @Override
    public void onErrorReported(EcovacsDevice device, int errorCode) {
        updateState(CHANNEL_ID_ERROR_CODE, new DecimalType(errorCode));
        final Locale locale = localeProvider.getLocale();
        String errorDesc = i18Provider.getText(bundle, "ecovacs.vacuum.error-code." + errorCode, null, locale);
        if (errorDesc == null) {
            errorDesc = i18Provider.getText(bundle, "ecovacs.vacuum.error-code.unknown", "", locale, errorCode);
        }
        updateState(CHANNEL_ID_ERROR_DESCRIPTION, new StringType(errorDesc));
    }

    @Override
    public void onEventStreamFailure(final EcovacsDevice device, Throwable error) {
        logger.debug("{}: Device connection failed, reconnecting", getDeviceSerial(), error);
        device.stopListeningForEvents();
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
        stopPolling();
        scheduleReconnection();
    }

    private void fetchInitialBatteryStatus() throws EcovacsApiException {
        doWithDevice(device -> {
            Integer batteryPercent = device.sendCommand(new GetBatteryInfoCommand());
            onBatteryLevelUpdated(device, batteryPercent);
        });
    }

    private void fetchInitialStateAndCommandValues() throws EcovacsApiException {
        doWithDevice(device -> {
            lastWasCharging = device.sendCommand(new GetChargeStateCommand()) == ChargeMode.CHARGING;
            lastCleanMode = device.sendCommand(new GetCleanStateCommand());
            updateStateAndCommandChannels();
        });
    }

    private void fetchInitialWaterSystemValues() throws EcovacsApiException {
        doWithDevice(device -> {
            if (!device.hasCapability(DeviceCapability.MOPPING_SYSTEM)) {
                return;
            }
            boolean present = device.sendCommand(new GetWaterSystemPresentCommand());
            MoppingWaterAmount amount = device.sendCommand(new GetMoppingWaterAmountCommand());
            onWaterSystemUpdated(device, present, amount);
        });
    }

    private void fetchInitialErrorCode() throws EcovacsApiException {
        doWithDevice(device -> {
            Optional<Integer> errorOpt = device.sendCommand(new GetErrorCommand());
            if (errorOpt.isPresent()) {
                onErrorReported(device, errorOpt.get());
            }
        });
    }

    private void removeUnsupportedChannels(EcovacsDevice device) {
        ThingBuilder builder = editThing();
        boolean hasChanges = false;

        if (!device.hasCapability(DeviceCapability.MOPPING_SYSTEM)) {
            hasChanges |= removeUnsupportedChannel(builder, CHANNEL_ID_WATER_AMOUNT);
            hasChanges |= removeUnsupportedChannel(builder, CHANNEL_ID_WATER_PLATE_PRESENT);
        }
        if (!device.hasCapability(DeviceCapability.CLEAN_SPEED_CONTROL)) {
            hasChanges |= removeUnsupportedChannel(builder, CHANNEL_ID_SUCTION_POWER);
        }
        if (!device.hasCapability(DeviceCapability.MAIN_BRUSH)) {
            hasChanges |= removeUnsupportedChannel(builder, CHANNEL_ID_MAIN_BRUSH_LIFETIME);
        }
        if (!device.hasCapability(DeviceCapability.VOICE_REPORTING)) {
            hasChanges |= removeUnsupportedChannel(builder, CHANNEL_ID_VOICE_VOLUME);
        }
        if (!device.hasCapability(DeviceCapability.MAPPING)) {
            hasChanges |= removeUnsupportedChannel(builder, CHANNEL_ID_LAST_CLEAN_MAP);
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
            device.listenForEvents(this);
            logger.debug("{}: Device connected", getDeviceSerial());
            updateStatus(ThingStatus.ONLINE);
            fetchInitialBatteryStatus();
            fetchInitialStateAndCommandValues();
            fetchInitialWaterSystemValues(); // nop if unsupported
            fetchInitialErrorCode();
            startPolling(0);
        });
    }

    private void pollData() {
        logger.debug("{}: Polling data", getDeviceSerial());
        doWithDevice(device -> {
            TotalStats totalStats = device.sendCommand(new GetTotalStatsCommand());
            updateState(CHANNEL_ID_TOTAL_CLEANED_AREA, new QuantityType<>(totalStats.totalArea, SIUnits.SQUARE_METRE));
            updateState(CHANNEL_ID_TOTAL_CLEANING_TIME, new QuantityType<>(totalStats.totalRuntime, Units.SECOND));
            updateState(CHANNEL_ID_TOTAL_CLEAN_RUNS, new DecimalType(totalStats.cleanRuns));

            List<CleanLogRecord> cleanLogRecords = device.getCleanLogs();
            if (!cleanLogRecords.isEmpty()) {
                CleanLogRecord record = cleanLogRecords.get(0);
                updateState(CHANNEL_ID_LAST_CLEAN_START,
                        new DateTimeType(record.timestamp.toInstant().atZone(ZoneId.systemDefault())));
                updateState(CHANNEL_ID_LAST_CLEAN_DURATION, new QuantityType<>(record.cleaningDuration, Units.SECOND));
                updateState(CHANNEL_ID_LAST_CLEAN_AREA, new QuantityType<>(record.cleanedArea, SIUnits.SQUARE_METRE));
                updateState(CHANNEL_ID_LAST_CLEAN_MODE, new StringType(CLEAN_MODE_MAPPING.get(record.mode)));
                if (device.hasCapability(DeviceCapability.MAPPING) && !lastCleanMapUrl.equals(record.mapImageUrl)) {
                    // HttpUtil expects the server to return the correct MIME type, but Ecovacs' server doesn't obey
                    State mapState = record.mapImageUrl.flatMap(url -> {
                        @Nullable
                        RawType mapData = HttpUtil.downloadData(record.mapImageUrl.get(), null, false, -1);
                        if (mapData != null) {
                            mapData = new RawType(mapData.getBytes(), "image/png");
                        }
                        return Optional.ofNullable((State) mapData);
                    }).orElse(UnDefType.NULL);
                    updateState(CHANNEL_ID_LAST_CLEAN_MAP, mapState);
                    lastCleanMapUrl = record.mapImageUrl;
                }
            }

            if (device.hasCapability(DeviceCapability.CLEAN_SPEED_CONTROL)) {
                SuctionPower power = device.sendCommand(new GetSuctionPowerCommand());
                updateState(CHANNEL_ID_SUCTION_POWER, new StringType(SUCTION_POWER_MAPPING.get(power)));
            }

            if (device.hasCapability(DeviceCapability.MOPPING_SYSTEM)) {
                MoppingWaterAmount waterAmount = device.sendCommand(new GetMoppingWaterAmountCommand());
                updateState(CHANNEL_ID_WATER_AMOUNT, new StringType(WATER_AMOUNT_MAPPING.get(waterAmount)));
            }

            NetworkInfo netInfo = device.sendCommand(new GetNetworkInfoCommand());
            if (netInfo.wifiRssi != 0) {
                updateState(CHANNEL_ID_WIFI_RSSI, new QuantityType<>(netInfo.wifiRssi, Units.DECIBEL_MILLIWATTS));
            }

            int sideBrushPercent = device.sendCommand(new GetComponentLifeSpanCommand(Component.SIDE_BRUSH));
            updateState(CHANNEL_ID_SIDE_BRUSH_LIFETIME, new QuantityType<>(sideBrushPercent, Units.PERCENT));
            int filterPercent = device.sendCommand(new GetComponentLifeSpanCommand(Component.DUST_CASE_HEAP));
            updateState(CHANNEL_ID_DUST_FILTER_LIFETIME, new QuantityType<>(filterPercent, Units.PERCENT));

            if (device.hasCapability(DeviceCapability.MAIN_BRUSH)) {
                int mainBrushPercent = device.sendCommand(new GetComponentLifeSpanCommand(Component.BRUSH));
                updateState(CHANNEL_ID_MAIN_BRUSH_LIFETIME, new QuantityType<>(mainBrushPercent, Units.PERCENT));
            }
            if (device.hasCapability(DeviceCapability.VOICE_REPORTING)) {
                int level = device.sendCommand(new GetVolumeCommand());
                updateState(CHANNEL_ID_VOICE_VOLUME, new PercentType(level * 10));
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
        updateState(CHANNEL_ID_STATE, new StringType(determineStateChannelValue(charging, cleanMode)));
        updateState(CHANNEL_ID_COMMAND, commandState != null ? new StringType(commandState) : UnDefType.NULL);
    }

    private String determineStateChannelValue(boolean charging, CleanMode cleanMode) {
        if (charging && cleanMode != CleanMode.RETURNING) {
            return "charging";
        }
        String result = CLEAN_MODE_MAPPING.get(cleanMode);
        return result != null ? result : "idle";
    }

    private @Nullable String determineCommandChannelValue(boolean charging, CleanMode cleanMode) {
        if (charging) {
            return CMD_CHARGE;
        }
        switch (cleanMode) {
            case AUTO:
                return CMD_AUTO_CLEAN;
            case PAUSE:
                return CMD_PAUSE;
            case STOP:
                return CMD_STOP;
            case RETURNING:
                return CMD_CHARGE;
            default:
                break;
        }
        return null;
    }

    private @Nullable AbstractNoResponseCommand determineDeviceCommand(EcovacsDevice device, String command) {
        switch (command) {
            case CMD_AUTO_CLEAN:
                return new StartAutoCleaningCommand();
            case CMD_PAUSE:
                return new PauseCleaningCommand();
            case CMD_RESUME:
                return new ResumeCleaningCommand();
            case CMD_STOP:
                return new StopCleaningCommand();
            case CMD_CHARGE:
                return new GoChargingCommand();
        }
        return null;
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
            logger.debug("{}: Failed communicating to device, reconnecting", getDeviceSerial(), e);
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
