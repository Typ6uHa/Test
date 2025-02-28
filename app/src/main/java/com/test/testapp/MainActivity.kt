package com.test.testapp

import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.test.testapp.permission.PermissionManager

class MainActivity : AppCompatActivity() {

    private lateinit var permissionManager: PermissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(FrameLayout(this))

        // можно через di, но думаю тут будет слишком
        permissionManager = PermissionManager(this, this)

        if (permissionManager.permissionsGranted()) {
            startMyForegroundService()
        } else {
            permissionManager.requestAllPermissions {
                startMyForegroundService()
            }
        }
    }

    private fun startMyForegroundService() {
        val serviceIntent = Intent(this, ForegroundService::class.java)
        startForegroundService(serviceIntent)
        finish()
    }
}
