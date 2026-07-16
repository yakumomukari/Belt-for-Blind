package com.beltforblind.motor

import android.content.Context

class BleMotorController(context: Context) :
    MotorControlGateway by AndroidBleMotorController(context)
