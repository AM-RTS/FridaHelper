package com.amrts.fridahelper.app

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

/**
 * Main activity with ViewPager2 + TabLayout for smooth swipe-based tab switching.
 * Supports dark mode toggle via toolbar icon, persisted in SharedPreferences.
 * No hook logic here — everything is delegated to the ViewModel and Fragments.
 */
class MainActivity : AppCompatActivity() {

    private val tabTitleResIds = intArrayOf(
        R.string.tab_java_hook,
        R.string.tab_native_hook
    )

    private var themeMenuItem: MenuItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        val tabLayout: TabLayout = findViewById(R.id.tab_layout)
        val viewPager: ViewPager2 = findViewById(R.id.view_pager)

        viewPager.adapter = HookPagerAdapter(this)

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.setText(tabTitleResIds[position])
        }.attach()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        themeMenuItem = menu.findItem(R.id.action_toggle_theme)
        themeMenuItem?.let { updateThemeIcon(it) }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_toggle_theme) {
            val newMode = ThemeManager.cycleMode(this)
            val label = ThemeManager.getModeLabel(this, newMode)
            Snackbar.make(
                findViewById(R.id.view_pager),
                getString(R.string.theme_switched, label),
                Snackbar.LENGTH_SHORT
            ).show()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun updateThemeIcon(item: MenuItem) {
        val mode = ThemeManager.getSavedMode(this)
        val iconRes = when (mode) {
            ThemeManager.MODE_LIGHT -> R.drawable.ic_theme_light
            ThemeManager.MODE_DARK -> R.drawable.ic_theme_dark
            else -> R.drawable.ic_theme_auto
        }
        item.setIcon(iconRes)
        item.title = "${getString(R.string.action_toggle_theme)} (${ThemeManager.getModeLabel(this, mode)})"
    }
}
