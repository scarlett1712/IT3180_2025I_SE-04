package com.se_04.enoti.residents;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.se_04.enoti.R;
import com.se_04.enoti.account.UserItem;
import com.se_04.enoti.account.Gender;
import com.se_04.enoti.account.Role;

public class ResidentDetailActivity extends AppCompatActivity {

    private TextView txtName, txtGender, txtDob, txtEmail, txtPhone,
            txtRelationship, txtRole, txtLiving, txtFamilyID;
    private ImageView imgResident;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resident_detail);

        // Ánh xạ view
        MaterialToolbar toolbar = findViewById(R.id.toolbar_resident_detail);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(true);
            getSupportActionBar().setTitle("Chi tiết cư dân");
        }

        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        txtName = findViewById(R.id.txtResidentName);
        txtGender = findViewById(R.id.txtResidentGender);
        txtDob = findViewById(R.id.txtResidentDob);
        txtEmail = findViewById(R.id.txtResidentEmail);
        txtPhone = findViewById(R.id.txtResidentPhone);
        txtRelationship = findViewById(R.id.txtResidentRelationship);
        txtRole = findViewById(R.id.txtResidentRole);
        txtLiving = findViewById(R.id.txtResidentLiving);
        txtFamilyID = findViewById(R.id.txtResidentFamilyID);
        imgResident = findViewById(R.id.imgResident);

        // 🔹 Nhận dữ liệu từ Intent
        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            String name = bundle.getString("name");
            String gender = bundle.getString("gender");
            String dob = bundle.getString("dob");
            String email = bundle.getString("email");
            String phone = bundle.getString("phone");
            String relationship = bundle.getString("relationship");
            String role = bundle.getString("role");
            String familyID = bundle.getString("familyID");
            boolean isLiving = bundle.getBoolean("isLiving", true);

            // Gán dữ liệu
            txtName.setText(name);
            txtGender.setText(gender);
            txtDob.setText(dob);
            txtEmail.setText(email);
            txtPhone.setText(phone);
            txtRelationship.setText(relationship);
            txtRole.setText(role);
            txtFamilyID.setText(familyID);
            txtLiving.setText(isLiving ? "Đang cư trú" : "Tạm vắng");
        }
    }
}
