package com.beltforblind.ui.theme

import androidx.compose.ui.graphics.Color

object BeltColors {
    val PrimaryPurple = Color(0xFF7144C7)
    val PrimaryPurpleDark = Color(0xFF5932A8)
    val PurpleContainer = Color(0xFFF1EAFB)
    val Background = Color(0xFFFAF8FC)
    val Surface = Color(0xFFFFFFFF)
    val TextPrimary = Color(0xFF202028)
    val TextSecondary = Color(0xFF74717D)
    val Divider = Color(0xFFECE8F0)
    val Success = Color(0xFF2FA85F)
    val SuccessContainer = Color(0xFFE8F6ED)
    val Warning = Color(0xFFD89722)
    val WarningContainer = Color(0xFFFFF4DC)
    val Error = Color(0xFFD64A55)
    val ErrorContainer = Color(0xFFFCEAEC)
    val Disabled = Color(0xFFC8C4CC)

    // Temporary aliases keep existing screens stable until their dedicated UI stages.
    val SportGreen = Success
    val SportGreenDark = Color(0xFF237A38)
    val StopRed = Error
    val GpsWarning = Warning
    val SportPanel = TextPrimary
    val SportPanelSecondary = Color(0xFF34343D)
    val SportPanelText = Surface
    val SportPanelLabel = Color(0xFFC8C4CC)
}
