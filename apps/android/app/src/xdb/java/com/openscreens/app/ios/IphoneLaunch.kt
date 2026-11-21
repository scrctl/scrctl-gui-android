package com.openscreens.app.ios

import android.content.Context
import android.content.Intent

internal fun iphoneLauncher(context: Context): (() -> Unit)? =
    { context.startActivity(Intent(context, IphoneActivity::class.java)) }
