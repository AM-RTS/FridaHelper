package com.amrts.fridahelper.app;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

/**
 * Main activity with ViewPager2 + TabLayout for smooth swipe-based tab switching.
 * Page 0 = Java Hook, Page 1 = Native Hook.
 * No hook logic here — everything is delegated to the ViewModel and Fragments.
 */
public class MainActivity extends AppCompatActivity {

    private final int[] tabTitleResIds = {
            R.string.tab_java_hook,
            R.string.tab_native_hook
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TabLayout tabLayout = findViewById(R.id.tab_layout);
        ViewPager2 viewPager = findViewById(R.id.view_pager);

        viewPager.setAdapter(new HookPagerAdapter(this));

        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(tabTitleResIds[position])
        ).attach();
    }
}
