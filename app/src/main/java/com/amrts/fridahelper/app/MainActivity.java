package com.amrts.fridahelper.app;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.tabs.TabLayout;

/**
 * Main activity with a TabLayout switching between JavaHookFragment and NativeHookFragment.
 * No hook logic here — everything is delegated to the ViewModel and Fragments.
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TabLayout tabLayout = findViewById(R.id.tab_layout);
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_java_hook));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_native_hook));

        if (savedInstanceState == null) {
            showFragment(new JavaHookFragment());
        }

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                Fragment fragment;
                if (tab.getPosition() == 0) {
                    fragment = new JavaHookFragment();
                } else {
                    fragment = new NativeHookFragment();
                }
                showFragment(fragment);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) { }

            @Override
            public void onTabReselected(TabLayout.Tab tab) { }
        });
    }

    private void showFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }
}
