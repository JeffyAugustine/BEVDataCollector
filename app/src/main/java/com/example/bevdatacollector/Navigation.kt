package com.example.bevdatacollector

sealed class Screen(val route: String) {
    object Calibration : Screen("calibration")
    object Capture : Screen("capture")
    object Process : Screen("process")
}