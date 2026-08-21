package com.example.homelabmonitor.data.repository

import android.content.Context
import com.example.homelabmonitor.HomelabMonitorApplication

fun Context.appContainer(): AppContainer =
    (applicationContext as HomelabMonitorApplication).container
