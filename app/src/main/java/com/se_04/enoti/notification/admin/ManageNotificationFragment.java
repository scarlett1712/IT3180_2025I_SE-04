package com.se_04.enoti.notification.admin;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.se_04.enoti.R;
import com.se_04.enoti.notification.admin.CreateNotificationActivity;

public class ManageNotificationFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_manage_notification, container, false);

        FloatingActionButton btnAdd = view.findViewById(R.id.btnAddNotification);
        btnAdd.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), CreateNotificationActivity.class);
            startActivity(intent);
        });

        return view;
    }
}
