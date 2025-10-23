package com.se_04.enoti.account;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.se_04.enoti.R;
import com.se_04.enoti.account_related.LogInActivity;
import com.se_04.enoti.utils.UserManager;

public class AccountFragment extends Fragment {

    private ImageView imgAvatar;
    private TextView txtFullName, txtApartment, email, phoneNumber, relationship, startDate;
    private Button btnEditProfile, btnSignOut;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_account, container, false);

        // Ánh xạ view
        imgAvatar = view.findViewById(R.id.imgAvatar);
        txtFullName = view.findViewById(R.id.txtFullName);
        txtApartment = view.findViewById(R.id.txtApartment);
        email = view.findViewById(R.id.email);
        phoneNumber = view.findViewById(R.id.phoneNumber);
        relationship = view.findViewById(R.id.relationship);
        startDate = view.findViewById(R.id.startDate);
        btnEditProfile = view.findViewById(R.id.btnEditProfile);
        btnSignOut = view.findViewById(R.id.btnSignOut);

        // 🔹 Lấy thông tin user từ UserManager
        UserManager userManager = UserManager.getInstance(requireContext());
        UserItem currentUser = userManager.getCurrentUser();

        // Nếu chưa có dữ liệu thì tạo demo user
        if (currentUser == null) {
            currentUser = new UserItem(
                    "U01",
                    "F01",
                    "a.nguyenvan@example.com",
                    "Nguyễn Văn A",
                    "12/03/1950",
                    Gender.MALE,
                    "Chủ hộ",
                    0,
                    Role.USER,
                    "0987654321"
            );
            userManager.saveCurrentUser(currentUser);
        }

        // Gán dữ liệu lên giao diện
        bindUserData(currentUser);

        // 🔹 Nút chỉnh sửa hồ sơ
        btnEditProfile.setOnClickListener(v -> {
            Intent editIntent = new Intent(requireContext(), EditProfileActivity.class);
            startActivity(editIntent);
        });

        // 🔹 Nút đăng xuất
        btnSignOut.setOnClickListener(v -> showLogoutConfirmation());

        return view;
    }

    private void bindUserData(UserItem user) {
        txtFullName.setText(user.getName());
        txtApartment.setText("Căn hộ: " + user.getRoom());
        email.setText("Email: " + user.getEmail());
        phoneNumber.setText("Số điện thoại: " + user.getPhone());
        relationship.setText("Quan hệ trong hộ: " + user.getRelationship());
        startDate.setText("Ngày sinh: " + user.getDob());

        imgAvatar.setImageResource(
                user.getGender() == Gender.MALE
                        ? R.drawable.ic_person
                        : R.drawable.ic_person_female
        );

        // 🔹 Nếu là Admin → ẩn các trường không cần thiết
        if (user.getRole() == Role.ADMIN) {
            txtApartment.setText("Quản trị viên");
            relationship.setVisibility(View.GONE);
        } else {
            txtApartment.setVisibility(View.VISIBLE);
            relationship.setVisibility(View.VISIBLE);
        }
    }

    private void showLogoutConfirmation() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Xác nhận đăng xuất")
                .setMessage("Bạn có chắc chắn muốn đăng xuất không?")
                .setPositiveButton("Đăng xuất", (dialog, which) -> logout())
                .setNegativeButton("Hủy", (dialog, which) -> dialog.dismiss())
                .setCancelable(false)
                .show();
    }

    private void logout() {
        // Xóa toàn bộ dữ liệu người dùng khỏi UserManager
        UserManager.getInstance(requireContext()).clearUser();

        // Xóa mọi dữ liệu khác nếu có SharedPreferences khác
        requireContext().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();

        // Quay lại màn hình đăng nhập, xóa ngăn xếp Activity
        Intent intent = new Intent(requireContext(), LogInActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

}
