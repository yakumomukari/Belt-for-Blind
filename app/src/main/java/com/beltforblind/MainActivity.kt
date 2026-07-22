package com.beltforblind

import android.os.Bundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.beltforblind.ui.app.BeltForBlindApp
import com.beltforblind.ui.theme.BeltTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        setContent {
            BeltTheme {
                BeltForBlindApp()
            }
        }
    }
}
