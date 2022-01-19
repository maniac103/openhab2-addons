# Ecovacs Binding

This binding provides integration for vacuum cleaning / mopping robots made by Ecovacs (https://www.ecovacs.com/).
It discovers devices and communicates to them by using Ecovacs' cloud services.

## Supported Things

- Ecovacs cloud API (`ecovacsapi`)
- Vacuum cleaner (`vacuum`)

At this point, only the Deebot OZMO 950 is supported, but support for any device using MQTT for communication (Deebot 901 and newer) is planned to be added.

## Discovery

At first, you need to manually create the bridge thing for the cloud API. Once that is done, the supported devices will be automatically discovered and added to the inbox.

_Describe the available auto-discovery features here. Mention for what it works and what needs to be kept in mind when using it._

## Thing Configuration

For the cloud API thing, the following parameters must be configured:

| Config    | Description                                                                                                                   |
|-----------|-------------------------------------------------------------------------------------------------------------------------------|
| email     | The email address you used when registering the Ecovacs cloud account                                                         |
| password  | The cloud account password                                                                                                    |
| continent | The continent you are residing on, or 'World' if none matches. This is used to select the correct cloud server to connect to. |

For the vacuum things, there is no required configuration. Optionally, you can tweak the following parameters:

| Config  | Description                                                                            |
|---------|----------------------------------------------------------------------------------------|
| refresh | Refresh interval for polled data (see below), in minutes. By default set to 5 minutes. |

## Channels

_Here you should provide information about available channel types, what their meaning is and how they can be used._

_Note that it is planned to generate some part of this based on the XML files within ```src/main/resources/OH-INF/thing``` of your binding._

| Channel                          | Type                 | Description                                               | Read Only | Updated By | Remarks |
|----------------------------------|----------------------|-----------------------------------------------------------|-----------|------------|------   |
| actions#command                  | String               | Command to execute                                        | No        | Event      | [1]     |
| status#state                     | String               | Current operational state                                 | Yes       | Event      | [2]     |
| status#current-cleaning-time     | Number:Time          | Time spent in current cleaning run                        | Yes       | Event      | [3]     |
| status#current-cleaned-area      | Number:Area          | Area cleaned in current cleaning run                      | Yes       | Event      | [3]     |
| status#water-system-present      | Switch               | Whether the device is currently ready for mopping         | Yes       | Event      | [4]     |
| status#wifi-rssi                 | Number:Power         | The current signal strength of the device                 | Yes       | Polling    |         |
| consumables#main-brush-lifetime  | Number:Dimensionless | The remaining life time of the main brush in percent      | Yes       | Polling    | [5]     |
| consumables#side-brush-lifetime  | Number:Dimensionless | The remaining life time of the side brush in percent      | Yes       | Polling    |         |
| consumables#dust-filter-lifetime | Number:Dimensionless | The remaining life time of the dust bin filter in percent | Yes       | Polling    |         |
| last-clean#last-clean-start      | DateTime             | The start time of the last completed cleaning run         | Yes       | Polling    |         |
| last-clean#last-clean-duration   | Number:Time          | The duration of the last completed cleaning run           | Yes       | Polling    |         |
| last-clean#last-clean-area       | Number:Area          | The area cleaned in the last completed cleaning run       | Yes       | Polling    |         |
| last-clean#last-clean-mode       | String               | The mode used for the last completed cleaning run         | Yes       | Polling    | [6]     |
| last-clean#last-clean-map        | Image                | The map image of the last completed cleaning run          | Yes       | Polling    |         |
| total-stats#total-cleaning-time  | Number:Time          | The total time spent cleaning during the device life time | Yes       | Polling    |         |
| total-stats#total-cleaned-area   | Number:Area          | The total area cleaned during the device life time        | Yes       | Polling    |         |
| total-stats#total-clean-runs     | Number               | The total number of clean runs in the device life time    | Yes       | Polling    |         |
| settings#suction-power           | String               | The power level used during cleaning                      | No        | Polling    | [7]     |
| settings#voice-volume            | Dimmer               | The voice volume level in percent                         | No        | Polling    | [8]     |
| settings#water-amount            | String               | The amount of water to be used when mopping               | No        | Polling    | [9]     |

Remarks:
- [1] Possible actions include 'clean' (start auto cleaning), 'pause', 'stop' and 'charge' (go to charge station)
- [2] Possible states: 'auto', 'edge', 'spot', 'spotArea', 'customArea', 'singleRoom', 'pause', 'stop', 'returning' and 'charging'
- [3] Current cleaning status is only valid if the device is currently cleaning
- [4] Only present if device has a mopping system
- [5] Only present if device has a main brush
- [6] For possible modes, see list under [2]
- [7] Only present if device can control power level. Possible values vary by device: 'normal' and 'high' are always supported, 'silent' and 'higher' are supported for some models
- [8] Only present if device has voice reporting
- [9] Only present if device has a mopping system. Possible values include 'low', 'medium', 'high' and 'veryhigh'

## Full Example

TODO

