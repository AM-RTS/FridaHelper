package com.amrts.fridahelper.app

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * ViewPager2 adapter for the two hook fragments.
 * Page 0 = JavaHookFragment, Page 1 = NativeHookFragment.
 */
class HookPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> JavaHookFragment()
        else -> NativeHookFragment()
    }
}
