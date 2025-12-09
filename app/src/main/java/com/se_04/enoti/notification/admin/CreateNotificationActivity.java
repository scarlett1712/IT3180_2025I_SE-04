package com.se_04.enoti.notification.admin;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textfield.TextInputEditText;
import com.se_04.enoti.R;
import com.se_04.enoti.account.UserItem;
import com.se_04.enoti.residents.ResidentAdapter;
import com.se_04.enoti.residents.ResidentItem;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.BaseActivity;
import com.se_04.enoti.utils.UserManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.*;

public class CreateNotificationActivity extends BaseActivity {

    private Spinner spinnerNotificationType, spinnerReceiverFloor, spinnerReceiverRoom;
    private SearchView searchView;
    private RecyclerView recyclerResidents;
    private ResidentAdapter adapter;
    private ImageButton btnReceiverList;
    private TextInputEditText edtNotificationTitle, edtExpirationDate, edtNotificationContent;
    private Button btnSendNow, btnSendLater;
    private TextView txtSelectedResidents;
    private CheckBox chkSendAll;

    // View bao quanh phần chọn người nhận để ẩn đi khi Edit
    private LinearLayout layoutFilterContainer;

    public static final String ACTION_NOTIFICATION_CREATED = "com.se_04.enoti.NOTIFICATION_CREATED";
    private static final String API_URL = ApiConfig.BASE_URL + "/api/residents";
    private static final String NOTIFICATION_API_URL = ApiConfig.BASE_URL + "/api/create_notification";

    private final List<ResidentItem> allResidents = new ArrayList<>();
    private final List<ResidentItem> filteredResidents = new ArrayList<>();
    private Set<ResidentItem> selectedResidents = new HashSet<>();
    private boolean isResidentListVisible = false;

    // 🔥 Biến kiểm tra chế độ Edit
    private boolean isEditMode = false;
    private long editingNotificationId = -1;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_notification);

        initViews();
        setupToolbar();

        // Kiểm tra Intent xem là Edit hay Create
        isEditMode = getIntent().getBooleanExtra("IS_EDIT_MODE", false);

        if (isEditMode) {
            setupEditMode();
        } else {
            setupCreateMode();
        }
    }

    private void initViews() {
        spinnerNotificationType = findViewById(R.id.spinnerNotificationType);
        spinnerReceiverFloor = findViewById(R.id.spinnerReceiverFloor);
        spinnerReceiverRoom = findViewById(R.id.spinnerReceiverRoom);
        searchView = findViewById(R.id.search_view);
        recyclerResidents = findViewById(R.id.recyclerResidents);
        btnReceiverList = findViewById(R.id.btnReceiverList);
        edtNotificationTitle = findViewById(R.id.edtNotificationTitle);
        edtExpirationDate = findViewById(R.id.edtExpirationDate);
        edtNotificationContent = findViewById(R.id.edtNotificationContent);
        btnSendNow = findViewById(R.id.btnSendNow);
        btnSendLater = findViewById(R.id.btnSendLater);
        txtSelectedResidents = findViewById(R.id.txtSelectedResidents);
        chkSendAll = findViewById(R.id.chkSendAll);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar_feedback);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(isEditMode ? "Chỉnh sửa thông báo" : "Tạo thông báo");
            toolbar.setTitleTextColor(ContextCompat.getColor(this, android.R.color.white));
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    // -------------------------------------------------------------
    // 🔥 CẤU HÌNH CHO CHẾ ĐỘ SỬA (EDIT MODE)
    // -------------------------------------------------------------
    private void setupEditMode() {
        // 1. Lấy dữ liệu cũ từ Intent
        editingNotificationId = getIntent().getLongExtra("notification_id", -1);
        String oldTitle = getIntent().getStringExtra("title");
        String oldContent = getIntent().getStringExtra("content");
        String oldType = getIntent().getStringExtra("type");

        // 2. Điền dữ liệu vào form
        edtNotificationTitle.setText(oldTitle);
        edtNotificationContent.setText(oldContent);
        setupNotificationType(oldType);

        // 3. Ẩn các phần không cần thiết khi sửa (người nhận, gửi sau)
        hideReceiverSection();
        btnSendLater.setVisibility(View.GONE);

        // 4. Đổi nút "Gửi" thành "Lưu"
        btnSendNow.setText("Lưu thay đổi");
        btnSendNow.setOnClickListener(v -> updateNotification());
    }

    private void hideReceiverSection() {
        // Ẩn các view chọn người nhận thủ công để tránh lỗi null nếu ID XML thay đổi
        if (chkSendAll != null) chkSendAll.setVisibility(View.GONE);
        if (txtSelectedResidents != null) txtSelectedResidents.setVisibility(View.GONE);
        if (btnReceiverList != null) btnReceiverList.setVisibility(View.GONE);
        if (searchView != null) searchView.setVisibility(View.GONE);
        if (spinnerReceiverFloor != null) spinnerReceiverFloor.setVisibility(View.GONE);
        if (spinnerReceiverRoom != null) spinnerReceiverRoom.setVisibility(View.GONE);
    }

    // -------------------------------------------------------------
    // 🔥 CẤU HÌNH CHO CHẾ ĐỘ TẠO MỚI (CREATE MODE)
    // -------------------------------------------------------------
    private void setupCreateMode() {
        setupNotificationType(null); // Default type

        adapter = new ResidentAdapter(filteredResidents, ResidentAdapter.MODE_SELECT_FOR_NOTIFICATION, selected -> {
            selectedResidents = selected;
            updateSelectedResidentsDisplay();
        });
        recyclerResidents.setLayoutManager(new LinearLayoutManager(this));
        recyclerResidents.setAdapter(adapter);
        recyclerResidents.setVisibility(View.GONE);

        setupFloorAndRoom();
        setupSearch();
        setupReceiverListToggle();
        setupExpirationDatePicker();
        setupSendAllCheckbox();

        btnSendNow.setOnClickListener(v -> sendNow());
        btnSendLater.setOnClickListener(v -> showSendOptionsBottomSheet());

        fetchResidentsFromAPI();
    }

    // -------------------------------------------------------------
    // LOGIC CHUNG & HELPER
    // -------------------------------------------------------------

    private void setupNotificationType(@Nullable String selectedType) {
        String[] types = {"Hành chính", "Kỹ thuật & bảo trì", "Tài chính", "Sự kiện & cộng đồng", "Khẩn cấp"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, types);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerNotificationType.setAdapter(adapter);

        // Nếu đang sửa, chọn đúng loại cũ
        if (selectedType != null) {
            String translatedType = convertEnglishToVi(selectedType);
            for (int i = 0; i < types.length; i++) {
                if (types[i].equals(translatedType)) {
                    spinnerNotificationType.setSelection(i);
                    break;
                }
            }
        }
    }

    private void sendNow() { sendNotification(null); }
    private void sendLater(Date scheduledTime) { sendNotification(scheduledTime); }

    // 🔥 API POST: TẠO MỚI
    private void sendNotification(Date scheduledTime) {
        if (selectedResidents.isEmpty()) {
            Toast.makeText(this, "Không chọn cư dân nào — sẽ gửi cho tất cả!", Toast.LENGTH_SHORT).show();
            selectedResidents = new HashSet<>(allResidents);
        }

        String title = edtNotificationTitle.getText().toString().trim();
        String content = edtNotificationContent.getText().toString().trim();
        String expiredDateRaw = edtExpirationDate.getText().toString().trim();
        String typeVi = spinnerNotificationType.getSelectedItem().toString();
        String type = convertTypeToEnglish(typeVi);

        if (title.isEmpty() || content.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập tiêu đề và nội dung!", Toast.LENGTH_SHORT).show();
            return;
        }

        String expiredDate = null;
        if (!TextUtils.isEmpty(expiredDateRaw)) {
            try {
                SimpleDateFormat inputFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                expiredDate = outputFormat.format(inputFormat.parse(expiredDateRaw));
            } catch (Exception e) {}
        }

        String formattedScheduledTime = null;
        if (scheduledTime != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            formattedScheduledTime = sdf.format(scheduledTime);
        }

        try {
            JSONArray userIds = new JSONArray();
            for (ResidentItem r : selectedResidents) userIds.put(r.getUserId());

            UserItem currentUser = UserManager.getInstance(this).getCurrentUser();
            int senderId = (currentUser != null) ? Integer.parseInt(currentUser.getId()) : 1;

            JSONObject body = new JSONObject();
            body.put("title", title);
            body.put("content", content);
            body.put("type", type);
            body.put("sender_id", senderId);
            body.put("expired_date", expiredDate);
            body.put("target_user_ids", userIds);
            if (scheduledTime != null) body.put("scheduled_time", formattedScheduledTime);

            RequestQueue queue = Volley.newRequestQueue(this);
            JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, NOTIFICATION_API_URL, body,
                    response -> {
                        Toast.makeText(this, "Gửi thông báo thành công!", Toast.LENGTH_LONG).show();
                        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(ACTION_NOTIFICATION_CREATED));
                        finish();
                    },
                    error -> Toast.makeText(this, "Lỗi khi gửi thông báo!", Toast.LENGTH_SHORT).show());
            queue.add(request);
        } catch (Exception e) { e.printStackTrace(); }
    }

    // 🔥 API PUT: CẬP NHẬT
    private void updateNotification() {
        String title = edtNotificationTitle.getText().toString().trim();
        String content = edtNotificationContent.getText().toString().trim();
        String typeVi = spinnerNotificationType.getSelectedItem().toString();
        String type = convertTypeToEnglish(typeVi);

        if (title.isEmpty() || content.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập đủ thông tin!", Toast.LENGTH_SHORT).show();
            return;
        }

        String url = ApiConfig.BASE_URL + "/api/notification/update/" + editingNotificationId;

        JSONObject body = new JSONObject();
        try {
            body.put("title", title);
            body.put("content", content);
            body.put("type", type);
        } catch (Exception e) { e.printStackTrace(); }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.PUT, url, body,
                response -> {
                    Toast.makeText(this, "Cập nhật thành công!", Toast.LENGTH_SHORT).show();
                    LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(ACTION_NOTIFICATION_CREATED));
                    finish();
                },
                error -> {
                    Toast.makeText(this, "Lỗi cập nhật", Toast.LENGTH_SHORT).show();
                    error.printStackTrace();
                }
        );
        Volley.newRequestQueue(this).add(request);
    }

    private String convertTypeToEnglish(String typeVi) {
        switch (typeVi) {
            case "Hành chính": return "Administrative";
            case "Kỹ thuật & bảo trì": return "Maintenance";
            case "Tài chính": return "Finance";
            case "Sự kiện & cộng đồng": return "Event";
            case "Khẩn cấp": return "Emergency";
            default: return "Administrative";
        }
    }

    private String convertEnglishToVi(String typeEn) {
        if (typeEn == null) return "Hành chính";
        switch (typeEn) {
            case "Administrative": return "Hành chính";
            case "Maintenance": return "Kỹ thuật & bảo trì";
            case "Finance": return "Tài chính";
            case "Event": return "Sự kiện & cộng đồng";
            case "Emergency": return "Khẩn cấp";
            default: return "Hành chính";
        }
    }

    // --- CÁC HÀM CŨ ĐỂ QUẢN LÝ DANH SÁCH CƯ DÂN ---

    private void fetchResidentsFromAPI() {
        RequestQueue queue = Volley.newRequestQueue(this);
        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, API_URL, null,
                this::parseResidentsFromResponse,
                error -> Toast.makeText(this, "Không thể tải danh sách cư dân!", Toast.LENGTH_SHORT).show());
        queue.add(request);
    }

    private void parseResidentsFromResponse(JSONArray response) {
        try {
            allResidents.clear();
            for (int i = 0; i < response.length(); i++) {
                JSONObject obj = response.getJSONObject(i);
                int roleId = obj.optInt("role_id", 0);
                if (roleId != 1) continue;

                allResidents.add(new ResidentItem(
                        obj.optInt("user_item_id"), obj.optInt("user_id"),
                        obj.optString("full_name"), obj.optString("gender"),
                        obj.optString("dob"), obj.optString("email"),
                        obj.optString("phone"), obj.optString("relationship_with_the_head_of_household"),
                        obj.optString("family_id"), obj.optBoolean("is_living"),
                        obj.optString("apartment_number"), obj.optString("identity_card", ""),
                        obj.optString("home_town", "")
                ));
            }
            filteredResidents.clear();
            filteredResidents.addAll(allResidents);
            adapter.updateList(filteredResidents);
            setupFloorAndRoom();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void setupFloorAndRoom() {
        List<String> floors = new ArrayList<>(Collections.singletonList("Tất cả tầng"));
        for (ResidentItem r : allResidents) if (!floors.contains(r.getFloor())) floors.add(r.getFloor());

        List<String> rooms = new ArrayList<>(Collections.singletonList("Tất cả phòng"));
        for (ResidentItem r : allResidents) if (!rooms.contains(r.getRoom())) rooms.add(r.getRoom());

        spinnerReceiverFloor.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, floors));
        spinnerReceiverRoom.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, rooms));

        AdapterView.OnItemSelectedListener filterListener = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { filterResidents(searchView.getQuery().toString()); }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        };
        spinnerReceiverFloor.setOnItemSelectedListener(filterListener);
        spinnerReceiverRoom.setOnItemSelectedListener(filterListener);
    }

    private void setupSearch() {
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String q) { filterResidents(q); return true; }
            @Override public boolean onQueryTextChange(String n) { filterResidents(n); return true; }
        });
    }

    private void filterResidents(String query) {
        filteredResidents.clear();
        String selectedFloor = spinnerReceiverFloor.getSelectedItem() != null ? spinnerReceiverFloor.getSelectedItem().toString() : "Tất cả tầng";
        String selectedRoom = spinnerReceiverRoom.getSelectedItem() != null ? spinnerReceiverRoom.getSelectedItem().toString() : "Tất cả phòng";

        for (ResidentItem r : allResidents) {
            boolean matchesName = TextUtils.isEmpty(query) || r.getName().toLowerCase().contains(query.toLowerCase());
            boolean matchesFloor = selectedFloor.equals("Tất cả tầng") || r.getFloor().equals(selectedFloor);
            boolean matchesRoom = selectedRoom.equals("Tất cả phòng") || r.getRoom().equals(selectedRoom);
            if (matchesName && matchesFloor && matchesRoom) filteredResidents.add(r);
        }
        adapter.updateList(filteredResidents);
    }

    private void setupReceiverListToggle() {
        btnReceiverList.setOnClickListener(v -> {
            isResidentListVisible = !isResidentListVisible;
            recyclerResidents.setVisibility(isResidentListVisible ? View.VISIBLE : View.GONE);
        });
    }

    private void setupExpirationDatePicker() {
        edtExpirationDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(this, (view, y, m, d) -> edtExpirationDate.setText(String.format(Locale.getDefault(), "%02d/%02d/%d", d, m + 1, y)),
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });
    }

    private void setupSendAllCheckbox() {
        chkSendAll.setOnCheckedChangeListener((bv, isChecked) -> {
            if (isChecked) {
                selectedResidents.clear(); selectedResidents.addAll(allResidents);
                adapter.selectAllResidents(true); recyclerResidents.setEnabled(false);
            } else {
                selectedResidents.clear(); adapter.selectAllResidents(false); recyclerResidents.setEnabled(true);
            }
            updateSelectedResidentsDisplay();
        });
    }

    private void updateSelectedResidentsDisplay() {
        if (selectedResidents.isEmpty()) txtSelectedResidents.setText("Chưa chọn cư dân nào");
        else {
            StringBuilder sb = new StringBuilder("Đã chọn: ");
            for (ResidentItem r : selectedResidents) sb.append(r.getName()).append(", ");
            txtSelectedResidents.setText(sb.substring(0, sb.length() - 2));
        }
    }

    private void showSendOptionsBottomSheet() {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View sheetView = LayoutInflater.from(this).inflate(R.layout.bottomsheet_schedule_send, null);

        TextView txtSendTime = sheetView.findViewById(R.id.txtSendTime);
        Button btnConfirmSend = sheetView.findViewById(R.id.btnConfirmSend);
        Button btnSelectDate = sheetView.findViewById(R.id.btnSelectDate);
        Button btnSelectTime = sheetView.findViewById(R.id.btnSelectTime);

        Calendar c = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
        txtSendTime.setText(sdf.format(c.getTime()));

        btnSelectDate.setOnClickListener(v -> {
            new DatePickerDialog(this, (view, y, m, d) -> {
                c.set(Calendar.YEAR, y); c.set(Calendar.MONTH, m); c.set(Calendar.DAY_OF_MONTH, d);
                txtSendTime.setText(sdf.format(c.getTime()));
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });

        btnSelectTime.setOnClickListener(v -> {
            new TimePickerDialog(this, (view, h, m) -> {
                c.set(Calendar.HOUR_OF_DAY, h); c.set(Calendar.MINUTE, m);
                txtSendTime.setText(sdf.format(c.getTime()));
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show();
        });

        btnConfirmSend.setOnClickListener(v -> {
            sendLater(c.getTime());
            bottomSheet.dismiss();
        });
        bottomSheet.setContentView(sheetView);
        bottomSheet.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        MaterialToolbar toolbar = findViewById(R.id.toolbar_feedback);
        if (toolbar != null) {
            TypedValue typedValue = new TypedValue();
            getTheme().resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true);
            toolbar.setBackgroundColor(typedValue.data);
            toolbar.setTitleTextColor(getResources().getColor(android.R.color.white, getTheme()));
        }
    }
}