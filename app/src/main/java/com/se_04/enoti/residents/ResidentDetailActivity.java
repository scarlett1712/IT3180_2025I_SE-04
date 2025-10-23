package com.se_04.enoti.residents;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.se_04.enoti.R;

public class ResidentDetailActivity extends AppCompatActivity {

    private TextView txtName, txtGender, txtDob, txtEmail, txtPhone,
            txtRelationship, txtLiving, txtFamilyID;
    private ImageView imgResident;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resident_detail);

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
        txtLiving = findViewById(R.id.txtResidentLiving);
        txtFamilyID = findViewById(R.id.txtResidentFamilyID);
        imgResident = findViewById(R.id.imgResident);

        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            txtName.setText(bundle.getString("name"));
            txtGender.setText(bundle.getString("gender"));
            txtDob.setText(bundle.getString("dob"));
            txtEmail.setText(bundle.getString("email"));
            txtPhone.setText(bundle.getString("phone"));
            txtRelationship.setText(bundle.getString("relationship"));
            txtFamilyID.setText(bundle.getString("familyID"));
            txtLiving.setText(bundle.getBoolean("isLiving") ? "Đang sinh sống" : "Không sinh sống");
        }
    }
}
