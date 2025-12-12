package com.se_04.enoti.home.accountant;

import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.se_04.enoti.R;
import com.se_04.enoti.account.AccountFragment;
import com.se_04.enoti.finance.admin.ManageFinanceFragment; // Kế toán dùng chung fragment quản lý tài chính với Admin?
import com.se_04.enoti.utils.BaseActivity;

public class MainActivity_Accountant extends BaseActivity {

    private static final String TAG = "MainActivity_Accountant";
    private static final String SELECTED_ITEM_ID_KEY = "selectedItemIdKey";
    private static final String ACTIVE_FRAGMENT_TAG_KEY = "activeFragmentTagKey";

    // Khai báo các Fragment
    private HomeFragment_Accountant homeFragmentAccountant;
    private ManageFinanceFragment manageFinanceFragment; // Fragment quản lý thu chi
    private AccountFragment accountFragment; // Fragment thông tin cá nhân

    private Fragment activeFragment;
    private FragmentManager fragmentManager;
    private int currentSelectedItemId = R.id.nav_home; // Mặc định chọn Home

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        setContentView(R.layout.activity_main_menu_accountant); // Đảm bảo bạn có layout này

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        fragmentManager = getSupportFragmentManager();

        if (savedInstanceState == null) {
            // Khởi tạo Fragment lần đầu
            homeFragmentAccountant = new HomeFragment_Accountant();
            manageFinanceFragment = new ManageFinanceFragment();
            accountFragment = new AccountFragment();

            FragmentTransaction transaction = fragmentManager.beginTransaction();

            // Thêm tất cả Fragment nhưng ẩn đi, trừ Fragment mặc định (Home)
            transaction.add(R.id.fragment_container, accountFragment, "account").hide(accountFragment);
            transaction.add(R.id.fragment_container, manageFinanceFragment, "finance").hide(manageFinanceFragment);
            transaction.add(R.id.fragment_container, homeFragmentAccountant, "home");

            transaction.commitNow();

            activeFragment = homeFragmentAccountant;
            currentSelectedItemId = R.id.nav_home;
            Log.d(TAG, "Initial setup: HomeFragment_Accountant set as active.");

        } else {
            // Khôi phục trạng thái sau khi xoay màn hình hoặc config change
            currentSelectedItemId = savedInstanceState.getInt(SELECTED_ITEM_ID_KEY, R.id.nav_home);
            String activeTag = savedInstanceState.getString(ACTIVE_FRAGMENT_TAG_KEY, "home");

            homeFragmentAccountant = (HomeFragment_Accountant) fragmentManager.findFragmentByTag("home");
            manageFinanceFragment = (ManageFinanceFragment) fragmentManager.findFragmentByTag("finance");
            accountFragment = (AccountFragment) fragmentManager.findFragmentByTag("account");

            // Fallback nếu Fragment bị null
            if (homeFragmentAccountant == null) homeFragmentAccountant = new HomeFragment_Accountant();
            if (manageFinanceFragment == null) manageFinanceFragment = new ManageFinanceFragment();
            if (accountFragment == null) accountFragment = new AccountFragment();

            switch (activeTag) {
                case "finance": activeFragment = manageFinanceFragment; break;
                case "account": activeFragment = accountFragment; break;
                case "home":
                default: activeFragment = homeFragmentAccountant; break;
            }
            Log.d(TAG, "Restored state: Active fragment tag: " + activeTag);
        }

        // Xử lý sự kiện click menu Bottom Navigation
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            Log.d(TAG, "BottomNav item selected: ID " + itemId);

            Fragment targetFragment = null;
            String targetTag = "";

            if (itemId == R.id.nav_home) {
                targetFragment = homeFragmentAccountant;
                targetTag = "home";
            } else if (itemId == R.id.nav_manage_finance) {
                targetFragment = manageFinanceFragment;
                targetTag = "finance";
            } else if (itemId == R.id.nav_profile) {
                targetFragment = accountFragment;
                targetTag = "account";
            }

            if (targetFragment != null) {
                if (targetFragment == activeFragment) {
                    return true; // Nếu đang ở tab đó thì không làm gì cả
                }

                FragmentTransaction transaction = fragmentManager.beginTransaction();
                if (!targetFragment.isAdded()) {
                    transaction.add(R.id.fragment_container, targetFragment, targetTag);
                }
                transaction.hide(activeFragment).show(targetFragment);

                activeFragment = targetFragment;
                currentSelectedItemId = itemId;
                transaction.commit();
            }
            return true;
        });

        // Đảm bảo menu hiển thị đúng item đang chọn
        bottomNav.setSelectedItemId(currentSelectedItemId);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(SELECTED_ITEM_ID_KEY, currentSelectedItemId);
        if (activeFragment != null && activeFragment.getTag() != null) {
            outState.putString(ACTIVE_FRAGMENT_TAG_KEY, activeFragment.getTag());
        } else {
            outState.putString(ACTIVE_FRAGMENT_TAG_KEY, "home");
        }
    }

    // Các hàm chuyển tab nhanh (nếu cần gọi từ bên trong Fragment)
    public void switchToFinanceTab() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setSelectedItemId(R.id.nav_manage_finance);
    }

    public void switchToProfileTab() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setSelectedItemId(R.id.nav_profile);
    }
}