package com.amrts.fridahelper.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.amrts.fridahelper.app.theme.FridaHelperTheme
import com.amrts.fridahelper.app.ui.FridaHelperApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            var themeMode by remember { mutableIntStateOf(ThemeManager.getSavedMode(this@MainActivity)) }
            FridaHelperTheme(themeMode = themeMode) {
                FridaHelperApp(
                    themeMode = themeMode,
                    onCycleTheme = {
                        themeMode = ThemeManager.cycleMode(this@MainActivity)
                    }
                )
            }
        }
    }
}
