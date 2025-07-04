package com.growspace.testapp.pages

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.growspace.testapp.pages.check_uwb.CheckAzimuthElevationPage
import com.growspace.testapp.pages.rtls.DeviceCoordinateViewModel
import com.growspace.testapp.pages.rtls.RTLSPage

@SuppressLint("UnrememberedMutableState")
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val viewModel: DeviceCoordinateViewModel = viewModel()

    NavHost(navController, startDestination = "home") {
        composable("home") { HomeScreen(navController) }
        composable("ranging") { RangingPage() }
        composable("rtls") { RTLSPage(navController, viewModel = viewModel) }
        composable("uwbSetting") { UwbSettingPage(viewModel = viewModel) }
        composable("check") { CheckAzimuthElevationPage() }
    }
}