package com.mukesh.android_agent_adk_litertlm_on_device_ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class PermissionHandler(
    private val activity: ComponentActivity,
    private val onAccessGranted: () -> Unit
) {
    private var waitingForAllFilesAccess = false

    private val requestReadExternalStorage = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onAccessGranted()
        } else {
            Log.e(TAG, "Storage permission was denied; cannot read the model from shared storage")
        }
    }

    fun requestAccess() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                if (Environment.isExternalStorageManager()) {
                    onAccessGranted()
                } else {
                    waitingForAllFilesAccess = true
                    activity.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:${activity.packageName}")
                        )
                    )
                }
            }

            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED -> onAccessGranted()

            else -> requestReadExternalStorage.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun onResume() {
        if (waitingForAllFilesAccess &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            Environment.isExternalStorageManager()
        ) {
            waitingForAllFilesAccess = false
            onAccessGranted()
        }
    }

    private companion object {
        const val TAG = "PermissionHandler"
    }
}
