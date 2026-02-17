package com.amrts.fridahelper.app;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

/**
 * Main activity with ViewPager2 + TabLayout for smooth swipe-based tab switching.
 * Supports dark mode toggle via toolbar icon, persisted in SharedPreferences.
 * No hook logic here — everything is delegated to the ViewModel and Fragments.
 */
public class MainActivity extends AppCompatActivity {

    private final int[] tabTitleResIds = {
            R.string.tab_java_hook,
            R.string.tab_native_hook
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        TabLayout tabLayout = findViewById(R.id.tab_layout);
        ViewPager2 viewPager = findViewById(R.id.view_pager);

        viewPager.setAdapter(new HookPagerAdapter(this));

        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(tabTitleResIds[position])
        ).attach();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        updateThemeIcon(menu.findItem(R.id.action_toggle_theme));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_toggle_theme) {
            int newMode = ThemeManager.cycleMode(this);
            String label = ThemeManager.getModeLabel(newMode);
            Toast.makeText(this, getString(R.string.theme_switched, label), Toast.LENGTH_SHORT).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Updates the toolbar icon to reflect the current theme mode.
     */
    private void updateThemeIcon(MenuItem item) {
        int mode = ThemeManager.getSavedMode(this);
        int iconRes;
        switch (mode) {
            case ThemeManager.MODE_LIGHT:
                iconRes = R.drawable.ic_theme_light;
                break;
            case ThemeManager.MODE_DARK:
                iconRes = R.drawable.ic_theme_dark;
                break;
            case ThemeManager.MODE_SYSTEM:
            default:
                iconRes = R.drawable.ic_theme_auto;
                break;
        }
        item.setIcon(iconRes);
    }
}
