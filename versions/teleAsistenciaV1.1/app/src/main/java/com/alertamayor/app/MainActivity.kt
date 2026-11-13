package com.alertamayor.app

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.alertamayor.app.ui.navigation.AlertaMayorNavGraph
import com.alertamayor.app.ui.theme.AlertaMayorTheme

class MainActivity : ComponentActivity() {

    private val batteryOptimizationLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // No se requiere acción tras cerrar el diálogo de optimización de batería.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as AlertaMayorApp
        val coordinator = app.container.callCoordinator
        coordinator.connect()

        requestIgnoreBatteryOptimizations()

        // R-003 (ciclo 4): mantener la pantalla encendida mientras la app está en primer
        // plano. Es crítico en el teléfono supervisado (central de alerta): el botón rojo
        // de ALERTA debe estar siempre visible y pulsable sin tener que encender la pantalla.
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            // M-001 (ciclo 6): selector de tema. Se observa la preferencia del DataStore
            // y se aplica en caliente (collectAsState recomponer sin reiniciar la app).
            val themePref by app.container.settingsRepository.theme.collectAsState(initial = "system")
            val darkTheme = when (themePref) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            AlertaMayorTheme(darkTheme = darkTheme) {
                AlertaMayorNavGraph()
            }
        }
    }

    /**
     * Pide al usuario desactivar la optimización de batería para esta app.
     * Es crítico en Huawei/Samsung/Xiaomi, que suspenden agresivamente las apps
     * en segundo plano y cortarían la recepción de llamadas de emergencia.
     */
    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        try {
            val intent = android.content.Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            batteryOptimizationLauncher.launch(intent)
        } catch (_: Exception) {
            // Si no se puede abrir el diálogo, se ignora.
        }
    }
}
