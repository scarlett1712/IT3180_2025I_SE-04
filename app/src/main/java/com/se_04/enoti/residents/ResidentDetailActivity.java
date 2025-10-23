package com.se_04.enoti.residents;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.se_04.enoti.R;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.File;

public class ResidentDetailActivity extends AppCompatActivity {

    private TextView txtName, txtGender, txtDob, txtEmail, txtPhone,
            txtRelationship, txtLiving, txtRoom;
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
        txtRoom = findViewById(R.id.txtRoom);
        imgResident = findViewById(R.id.imgResident);

        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            txtName.setText(bundle.getString("name"));
            txtGender.setText(bundle.getString("gender"));
            txtDob.setText(bundle.getString("dob"));
            txtEmail.setText(bundle.getString("email"));
            txtPhone.setText(bundle.getString("phone"));
            txtRelationship.setText(bundle.getString("relationship"));
            txtRoom.setText(bundle.getString("room"));
            txtLiving.setText(bundle.getBoolean("is_living") ? "Đang sinh sống" : "Không sinh sống");

            // 🔥 LOAD ẢNH TỪ LOCAL STORAGE
            String userId = String.valueOf(bundle.getInt("user_id", 0));
            loadResidentAvatar(userId);
        }
    }

    private void loadResidentAvatar(String userId) {
        try {
            File avatarFile = getAvatarFile(userId);
            if (avatarFile.exists()) {
                Bitmap bitmap = BitmapFactory.decodeFile(avatarFile.getAbsolutePath());
                imgResident.setImageBitmap(bitmap);
            } else {
                // Set ảnh mặc định theo giới tính
                String gender = getIntent().getExtras().getString("gender", "");
                setDefaultAvatar(gender);
            }
        } catch (Exception e) {
            e.printStackTrace();
            String gender = getIntent().getExtras().getString("gender", "");
            setDefaultAvatar(gender);
        }
    }

    private void setDefaultAvatar(String gender) {
        if (gender != null && gender.toLowerCase().contains("nữ")) {
            imgResident.setImageResource(R.drawable.ic_person_female);
        } else {
            imgResident.setImageResource(R.drawable.ic_person);
        }
    }

    private File getAvatarFile(String userId) {
        File avatarDir = new File(getFilesDir(), "avatars");
        return new File(avatarDir, userId + ".jpg");
    }
}