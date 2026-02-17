package com.amrts.fridahelper.app;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

/**
 * ViewPager2 adapter for the two hook fragments.
 * Page 0 = JavaHookFragment, Page 1 = NativeHookFragment.
 */
public final class HookPagerAdapter extends FragmentStateAdapter {

    private static final int PAGE_COUNT = 2;

    public HookPagerAdapter(@NonNull FragmentActivity activity) {
        super(activity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 0) {
            return new JavaHookFragment();
        }
        return new NativeHookFragment();
    }

    @Override
    public int getItemCount() {
        return PAGE_COUNT;
    }
}
