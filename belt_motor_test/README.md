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

PWM runs at 1 kHz with 8-bit duty resolution. The active motor uses duty 128, and selecting a motor stops the other seven motors.

## BLE protocol

- Device name: `BeltMotor`
- Service UUID: `8f8a0001-8f4b-4f5b-9f2b-5e7a1f000001`
- Write characteristic UUID: `8f8a0002-8f4b-4f5b-9f2b-5e7a1f000001`
- Payload: one ASCII byte
- `1` through `8`: activate that motor
- `0`: stop every motor

The firmware stops every motor when the BLE client disconnects.

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
