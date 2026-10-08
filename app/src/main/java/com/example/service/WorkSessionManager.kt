package com.example.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.example.data.model.WorkSessionConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object WorkSessionManager {

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    private val _isWaitingForQuietHours = MutableStateFlow(false)
    val isWaitingForQuietHours: StateFlow<Boolean> = _isWaitingForQuietHours.asStateFlow()

    private val _quietNoticeMessage = MutableStateFlow<String?>(null)
    val quietNoticeMessage: StateFlow<String?> = _quietNoticeMessage.asStateFlow()

    private val _sessionConfig = MutableStateFlow(WorkSessionConfig())
    val sessionConfig: StateFlow<WorkSessionConfig> = _sessionConfig.asStateFlow()

    fun updateConfig(config: WorkSessionConfig) {
        _sessionConfig.value = config
    }

    fun setSessionActive(active: Boolean) {
        _isSessionActive.value = active
        if (!active) {
            _isWaitingForQuietHours.value = false
            _quietNoticeMessage.value = null
        }
    }

    fun setWaitingQuietHours(waiting: Boolean, message: String? = null) {
        _isWaitingForQuietHours.value = waiting
        _quietNoticeMessage.value = message
    }

    fun clearNoticeMessage() {
        _quietNoticeMessage.value = null
    }

    fun startSession(context: Context) {
        val config = _sessionConfig.value
        val intent = Intent(context, WorkSessionService::class.java).apply {
            action = WorkSessionService.ACTION_START
            putExtra(WorkSessionService.EXTRA_INTERVAL_MINUTES, config.intervalMinutes)
            putExtra(WorkSessionService.EXTRA_TIMEOUT_MINUTES, config.timeoutMinutes)
            putExtra(WorkSessionService.EXTRA_QUIET_ENABLED, config.quietHoursEnabled)
            putExtra(WorkSessionService.EXTRA_QUIET_START_H, config.quietStartHour)
            putExtra(WorkSessionService.EXTRA_QUIET_START_M, config.quietStartMinute)
            putExtra(WorkSessionService.EXTRA_QUIET_END_H, config.quietEndHour)
            putExtra(WorkSessionService.EXTRA_QUIET_END_M, config.quietEndMinute)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopSession(context: Context) {
        val intent = Intent(context, WorkSessionService::class.java).apply {
            action = WorkSessionService.ACTION_STOP
        }
        context.startService(intent)
        setSessionActive(false)
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        } else {
            true
        }
    }

    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }
}
