package com.test.testapp.permission

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionManager(
    private val caller: ActivityResultCaller,
    private val context: AppCompatActivity
) {

    private var currentStep: PermissionStep = PermissionStep.REQUEST_NOTIFICATION
    private var notificationPermissionLauncher: ActivityResultLauncher<String>? = null
    private var overlayPermissionLauncher: ActivityResultLauncher<Intent>? = null
    private var onPermissionsGranted: (() -> Unit)? = null

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher = caller.registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                handlePermissionResult(isGranted)
            }
        }

        overlayPermissionLauncher = caller.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            handlePermissionResult(Settings.canDrawOverlays(context))
        }
    }

    fun requestAllPermissions(onPermissionsGranted: () -> Unit) {
        this.onPermissionsGranted = onPermissionsGranted
        nextStep()
    }

    fun permissionsGranted(): Boolean {
        val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        return notificationsGranted && Settings.canDrawOverlays(context)
    }

    private fun nextStep() {
        when (currentStep) {
            PermissionStep.REQUEST_NOTIFICATION -> checkAndRequestNotificationPermission()
            PermissionStep.REQUEST_OVERLAY -> checkAndRequestOverlayPermission()
            PermissionStep.ALL_GRANTED -> allPermissionsGranted()
        }
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
                    ?: return
            } else {
                currentStep = PermissionStep.REQUEST_OVERLAY
                nextStep()
            }
        } else {
            currentStep = PermissionStep.REQUEST_OVERLAY
            nextStep()
        }
    }

    private fun checkAndRequestOverlayPermission() {
        if (!Settings.canDrawOverlays(context)) {
            requestOverlayPermission()
        } else {
            currentStep = PermissionStep.ALL_GRANTED
            nextStep()
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )

        if (overlayPermissionLauncher != null) {
            overlayPermissionLauncher?.launch(intent)
        } else {
            context.startActivity(intent)
        }
    }

    private fun handlePermissionResult(isGranted: Boolean) {
        if (isGranted) {
            currentStep = when (currentStep) {
                PermissionStep.REQUEST_NOTIFICATION -> PermissionStep.REQUEST_OVERLAY
                PermissionStep.REQUEST_OVERLAY -> PermissionStep.ALL_GRANTED
                PermissionStep.ALL_GRANTED -> PermissionStep.ALL_GRANTED
            }
            nextStep()
        } else {
            handleDeniedPermission()
        }
    }

    private fun handleDeniedPermission() {
        when (currentStep) {
            PermissionStep.REQUEST_NOTIFICATION -> {
                if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    showPermissionDialog("Уведомления необходимы для работы приложения. Разрешите их") {
                        checkAndRequestNotificationPermission()
                    }
                } else {
                    showPermissionDialog("Вы запретили уведомления навсегда. Перейдите в настройки, чтобы разрешить их") {
                        openAppSettings()
                    }
                }
            }

            PermissionStep.REQUEST_OVERLAY -> {
                showPermissionDialog("Разрешение \"Показ поверх других приложений\" необходимо для работы") {
                    requestOverlayPermission()
                }
            }

            else -> Unit
        }
    }

    private fun allPermissionsGranted() {
        if (permissionsGranted()) {
            onPermissionsGranted?.invoke()
        }
    }

    private fun showPermissionDialog(message: String, onPositive: () -> Unit) {
        context.runOnUiThread {
            AlertDialog.Builder(context)
                .setTitle("Требуется разрешение")
                .setMessage(message)
                .setPositiveButton("ОК") { _, _ -> onPositive() }
                .setCancelable(false)
                .show()
        }
    }

    private fun shouldShowRequestPermissionRationale(permission: String): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(context, permission)
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    }
}