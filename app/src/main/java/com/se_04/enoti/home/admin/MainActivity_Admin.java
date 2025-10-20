package com.se_04.enoti.home.admin; // Your package name

import android.os.Bundle;
import android.util.Log; // For debugging

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
// Removed EdgeToEdge and WindowInsetsCompat as they are not directly related to this fix
// and might require more specific setup if you re-add them.

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.se_04.enoti.account.AccountFragment;
import com.se_04.enoti.notification.admin.CreateNotificationActivity;
import com.se_04.enoti.feedback.FeedbackFragment;
import com.se_04.enoti.R;
import com.se_04.enoti.notification.admin.ManageNotificationFragment;
import com.se_04.enoti.residents.ManageResidentFragment;

public class MainActivity_Admin extends AppCompatActivity {

    private static final String TAG = "MainActivity_Admin"; // For Logcat
    private static final String SELECTED_ITEM_ID_KEY = "selectedItemIdKey";
    private static final String ACTIVE_FRAGMENT_TAG_KEY = "activeFragmentTagKey";

    // Declare your fragments. Make them class members.
    private HomeFragment_Admin homeFragmentAdmin;
    private ManageResidentFragment manageResidentFragment;
    private ManageNotificationFragment manageNotificationFragment;
    private FeedbackFragment feedbackFragment;
    private AccountFragment accountFragment;

    private Fragment activeFragment; // To keep track of the currently visible fragment
    private FragmentManager fragmentManager;
    private int currentSelectedItemId = R.id.nav_home; // Default selected item

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        // EdgeToEdge.enable(this); // You can re-enable this if needed
        setContentView(R.layout.activity_main_menu_admin);

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        fragmentManager = getSupportFragmentManager();

        if (savedInstanceState == null) {
            // First time creation: initialize fragments and add them
            homeFragmentAdmin = new HomeFragment_Admin();
            manageResidentFragment = new ManageResidentFragment();
            manageNotificationFragment = new ManageNotificationFragment();
            feedbackFragment = new FeedbackFragment();
            accountFragment = new AccountFragment();

            // Start a transaction
            FragmentTransaction transaction = fragmentManager.beginTransaction();

            // Add all fragments, hide non-default ones. Use tags to find them later.
            // Add the default fragment last so it's "on top" if others are mistakenly shown.
            transaction.add(R.id.fragment_container, accountFragment, "account").hide(accountFragment);
            transaction.add(R.id.fragment_container, feedbackFragment, "feedback").hide(feedbackFragment);
            transaction.add(R.id.fragment_container, manageNotificationFragment, "managenotifications").hide(manageNotificationFragment);
            transaction.add(R.id.fragment_container, manageResidentFragment, "manageresidents").hide(manageResidentFragment);
            transaction.add(R.id.fragment_container, homeFragmentAdmin, "home"); // Default, so show it

            transaction.commitNow(); // Use commitNow if you immediately need to reference these fragments

            activeFragment = homeFragmentAdmin; // Set initial active fragment
            currentSelectedItemId = R.id.nav_home;
            Log.d(TAG, "Initial setup: HomeFragment_User set as active.");

        } else {
            // Re-creation: retrieve fragments by tag and restore active state
            currentSelectedItemId = savedInstanceState.getInt(SELECTED_ITEM_ID_KEY, R.id.nav_home);
            String activeTag = savedInstanceState.getString(ACTIVE_FRAGMENT_TAG_KEY, "home");

            homeFragmentAdmin = (HomeFragment_Admin) fragmentManager.findFragmentByTag("home");
            manageResidentFragment = (ManageResidentFragment) fragmentManager.findFragmentByTag("manageresidents");
            manageNotificationFragment = (ManageNotificationFragment) fragmentManager.findFragmentByTag("managenotifications");
            feedbackFragment = (FeedbackFragment) fragmentManager.findFragmentByTag("feedback");
            accountFragment = (AccountFragment) fragmentManager.findFragmentByTag("account");

            // Fallback if fragments are somehow null (shouldn't happen if added correctly)
            if (homeFragmentAdmin == null) homeFragmentAdmin = new HomeFragment_Admin();
            if (manageResidentFragment == null) manageResidentFragment = new ManageResidentFragment();
            if (manageNotificationFragment == null) manageNotificationFragment = new ManageNotificationFragment();
            if (feedbackFragment == null) feedbackFragment = new FeedbackFragment();
            if (accountFragment == null) accountFragment = new AccountFragment();


            switch (activeTag) {
                case "manageresidents": activeFragment = manageResidentFragment; break;
                case "managenotifications": activeFragment = manageNotificationFragment; break;
                case "feedback": activeFragment = feedbackFragment; break;
                case "account": activeFragment = accountFragment; break;
                case "home": // Fallthrough to default
                default: activeFragment = homeFragmentAdmin; break;
            }
            Log.d(TAG, "Restored state: Active fragment tag: " + activeTag);
        }

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            Log.d(TAG, "BottomNav item selected: ID " + itemId + ", Title: " + item.getTitle());

            Fragment targetFragment = null;
            String targetTag = "";

            if (itemId == R.id.nav_home) {
                targetFragment = homeFragmentAdmin;
                targetTag = "home";
            } else if (itemId == R.id.nav_manage_residents){
                targetFragment = manageResidentFragment;
                targetTag = "manageresidents";
            } else if (itemId == R.id.nav_manage_noti) {
                targetFragment = manageNotificationFragment;
                targetTag = "managenotifications";
            } else if (itemId == R.id.nav_feedback) {
                targetFragment = feedbackFragment;
                targetTag = "feedback";
            } else if (itemId == R.id.nav_account) {
                targetFragment = accountFragment;
                targetTag = "account";
            }

            if (targetFragment != null) {
                if (targetFragment == activeFragment) {
                    Log.d(TAG, "Target fragment is already active. No change.");
                    // Optional: You could implement a "scroll to top" or refresh here if the same tab is clicked.
                    return true; // Consume the event
                }

                FragmentTransaction transaction = fragmentManager.beginTransaction();
                if (!targetFragment.isAdded()) {
                    // This case should ideally not happen if all fragments are added initially
                    // and restored correctly. It's a fallback.
                    Log.w(TAG, "Target fragment " + targetTag + " was not added. Adding it now.");
                    transaction.add(R.id.fragment_container, targetFragment, targetTag);
                }
                transaction.hide(activeFragment).show(targetFragment);
                activeFragment = targetFragment; // Update the active fragment
                currentSelectedItemId = itemId; // Update the selected item ID
                transaction.commit();
                Log.d(TAG, "Switched to fragment: " + targetTag);
            } else {
                Log.w(TAG, "No target fragment found for itemId: " + itemId);
            }
            return true; // Event consumed
        });

        // Ensure the BottomNavigationView visually reflects the current state,
        // especially after configuration changes.
        bottomNav.setSelectedItemId(currentSelectedItemId);
        Log.d(TAG, "Set BottomNav selected item ID to: " + currentSelectedItemId);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(SELECTED_ITEM_ID_KEY, currentSelectedItemId);
        if (activeFragment != null && activeFragment.getTag() != null) {
            outState.putString(ACTIVE_FRAGMENT_TAG_KEY, activeFragment.getTag());
            Log.d(TAG, "Saving instance state: SelectedItemID=" + currentSelectedItemId + ", ActiveFragmentTag=" + activeFragment.getTag());
        } else {
            Log.w(TAG, "Saving instance state: Active fragment or its tag is null. Saving default home tag.");
            outState.putString(ACTIVE_FRAGMENT_TAG_KEY, "home"); // Fallback
        }
    }

    public void switchToManageResidentsTab(){
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setSelectedItemId(R.id.nav_manage_residents);
    }

    public void switchToManageNotificationsTab(){
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setSelectedItemId(R.id.nav_manage_noti);
    }
}
