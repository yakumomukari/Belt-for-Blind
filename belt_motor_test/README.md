# Belt motor test firmware

ESP32 firmware for manually testing the eight vibration motors from the Android Debug page.
The original UART test commands remain available.

## Motor pins

| Motor | GPIO |
| --- | --- |
| 1 | 4 |
| 2 | 13 |
| 3 | 14 |
| 4 | 18 |
| 5 | 19 |
| 6 | 21 |
| 7 | 22 |
| 8 | 23 |

PWM runs at 1 kHz with 8-bit duty resolution. Each channel can use an independent duty from 0 through 255. The legacy single-motor command uses duty 128.

## BLE protocol

- Device name: `BeltMotor`
- Service UUID: `8f8a0001-8f4b-4f5b-9f2b-5e7a1f000001`
- Write characteristic UUID: `8f8a0002-8f4b-4f5b-9f2b-5e7a1f000001`
- Legacy payload: one ASCII byte
- `1` through `8`: activate that motor at duty 128 and stop the other motors
- `0`: stop every motor
- Vector payload: 9 binary bytes, `A1 I1 I2 I3 I4 I5 I6 I7 I8`
- `I1` through `I8`: independent unsigned 8-bit PWM duties for motors 1 through 8
- A vector containing eight zero duties stops every motor

The 9-byte vector fits in the default BLE ATT payload and is used by the Android Debug arc to blend two adjacent motors. The original ASCII protocol remains available for UART and older app controls.

The firmware stops every motor when the BLE client disconnects. An active motor command also expires after 700 ms, so a lost stop command, stalled phone process, or interrupted background task cannot leave motors running indefinitely. Sending another active legacy or vector command refreshes the watchdog.

## ATGM336H GPS

Default wiring:

| GPS module | ESP32 |
| --- | --- |
| TXD | GPIO16 (UART2 RX) |
| RXD | GPIO17 (UART2 TX) |
| VCC | 3.3 V |
| GND | GND |

The firmware reads NMEA 0183 at 9600 baud, validates the checksum, uses
`GGA` for position/fix quality/satellite count/HDOP, and uses `RMC` for
speed. The module outputs WGS-84 coordinates. The Android app converts them
to GCJ-02 before drawing or saving them on the AMap base map.

GPS notifications use characteristic
`8f8a0003-8f4b-4f5b-9f2b-5e7a1f000001`. Its fixed 18-byte little-endian
payload is:

| Offset | Size | Value |
| --- | --- | --- |
| 0 | 1 | protocol version, currently 1 |
| 1 | 1 | flags; bit 0 means valid fix |
| 2 | 2 | packet sequence |
| 4 | 4 | signed latitude degrees times 1e7 |
| 8 | 4 | signed longitude degrees times 1e7 |
| 12 | 2 | speed in cm/s; 0xffff means unavailable |
| 14 | 2 | HDOP times 100; 0xffff means unavailable |
| 16 | 1 | satellite count |
| 17 | 1 | NMEA fix quality |

The Android app prefers a fresh valid belt GPS sample. If no valid belt
sample arrives for 6 seconds, recording and navigation automatically fall
back to phone positioning.

## Build and flash

The repository path contains non-ASCII characters. ESP-IDF 6.0.2 can corrupt that path while generating Kconfig files on Windows, so use the included ASCII-path build wrapper from an ESP-IDF PowerShell terminal:

```powershell
.\build-windows.ps1
.\build-windows.ps1 -Flash -Monitor
```

The first command only builds the firmware. The second command rebuilds, flashes, and opens the serial monitor. If exactly one serial port is detected, the script selects it automatically. When multiple ports are connected, specify the ESP32 port explicitly, for example `-Port COM3`.

The script synchronizes only the firmware source files to an ASCII-only directory under `%TEMP%`. If the ESP-IDF installation path contains spaces or non-ASCII characters, it also creates a temporary drive alias for ESP-IDF. The final build directory is printed after a successful command. Do not flash the stale binary under the old project `build/` directory.

## Hardware safety

Do not connect a motor directly to an ESP32 GPIO. Use a transistor or MOSFET driver, flyback protection where required, an appropriate external motor power supply, and a shared ground with the ESP32.
