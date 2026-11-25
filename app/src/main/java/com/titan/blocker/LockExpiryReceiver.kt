package com.titan.blocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class LockExpiryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        DeviceOwnerProtection.refresh(context)
    }
}
