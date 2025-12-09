package com.se_04.enoti.notification.admin;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
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
import com.google.android.material.button.MaterialButton;
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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CreateNotificationActivity extends BaseActivity {

    // --- KHAI BÁO VIEW (Theo Layout Mới) ---
    // Thay Spinner bằng AutoCompleteTextView cho Exposed Dropdown Menu
    private AutoCompleteTextView spinnerNotificationType, spinnerReceiverFloor, spinnerReceiverRoom;

    private SearchView searchView;
    private RecyclerView recyclerResidents;
    private ResidentAdapter adapter;

    private TextInputEditText edtNotificationTitle, edtExpirationDate, edtNotificationContent;
    private MaterialButton btnSendNow, btnSendLater; // Dùng MaterialButton
    private TextView txtSelectedResidents;
    private CheckBox chkSendAll;

    // --- BIẾN HỆ THỐNG ---
    public static final String ACTION_NOTIFICATION_CREATED = "com.se_04.enoti.NOTIFICATION_CREATED";
    private static final String API_URL = ApiConfig.BASE_URL + "/api/residents";
    private static final String NOTIFICATION_API_URL = ApiConfig.BASE_URL + "/api/create_notification";

    private final List<ResidentItem> allResidents = new ArrayList<>();
    private final List<ResidentItem> filteredResidents = new ArrayList<>();
    private Set<ResidentItem> selectedResidents = new HashSet<>();

    private boolean isEditMode = false;
    private long editingNotificationId = -1;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_notification); // Đảm bảo đúng tên file layout XML

        initViews();
        setupToolbar();

        // Kiểm tra xem đang Tạo mới hay Sửa
        isEditMode = getIntent().getBooleanExtra("IS_EDIT_MODE", false);

        if (isEditMode) {
            setupEditMode();
        } else {
            setupCreateMode();
        }
    }

    private void initViews() {
        // Ánh xạ ID từ layout activity_create_notification.xml
        spinnerNotificationType = findViewById(R.id.spinnerNotificationType);
        spinnerReceiverFloor = findViewById(R.id.spinnerReceiverFloor);
        spinnerReceiverRoom = findViewById(R.id.spinnerReceiverRoom);

        searchView = findViewById(R.id.search_view);
        recyclerResidents = findViewById(R.id.recyclerResidents);

        edtNotificationTitle = findViewById(R.id.edtNotificationTitle);
        edtExpirationDate = findViewById(R.id.edtExpirationDate);
        edtNotificationContent = findViewById(R.id.edtNotificationContent);

        btnSendNow = findViewById(R.id.btnSendNow);
        btnSendLater = findViewById(R.id.btnSendLater);

        txtSelectedResidents = findViewById(R.id.txtSelectedResidents);
        chkSendAll = findViewById(R.id.chkSendAll);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(isEditMode ? "Chỉnh sửa thông báo" : "Tạo thông báo mới");
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    // =================================================================================
    // PHẦN 1: CẤU HÌNH CHẾ ĐỘ SỬA (EDIT MODE)
    // =================================================================================
    private void setupEditMode() {
        editingNotificationId = getIntent().getLongExtra("notification_id", -1);
        String oldTitle = getIntent().getStringExtra("title");
        String oldContent = getIntent().getStringExtra("content");
        String oldType = getIntent().getStringExtra("type");

        edtNotificationTitle.setText(oldTitle);
        edtNotificationContent.setText(oldContent);
        setupNotificationType(oldType);

        // Trong chế độ sửa, thường sẽ không cho sửa người nhận để tránh phức tạp logic
        // Ẩn Card người nhận và nút Gửi sau
        View cardRecipients = findViewById(R.id.cardRecipients);
        if (cardRecipients != null) cardRecipients.setVisibility(View.GONE);

        btnSendLater.setVisibility(View.GONE);

        btnSendNow.setText("Lưu thay đổi");
        btnSendNow.setOnClickListener(v -> updateNotification());
    }

    // =================================================================================
    // PHẦN 2: CẤU HÌNH CHẾ ĐỘ TẠO MỚI (CREATE MODE)
    // =================================================================================
    private void setupCreateMode() {
        setupNotificationType(null); // Mặc định

        // Setup RecyclerView
        adapter = new ResidentAdapter(filteredResidents, ResidentAdapter.MODE_SELECT_FOR_NOTIFICATION, selected -> {
            selectedResidents = selected;
            updateSelectedResidentsDisplay();
        });
        recyclerResidents.setLayoutManager(new LinearLayoutManager(this));
        recyclerResidents.setAdapter(adapter);
        recyclerResidents.setVisibility(View.GONE); // Mặc định ẩn cho gọn

        // Setup các chức năng khác
        setupSearch();
        setupExpirationDatePicker();
        setupSendAllCheckbox();

        // Nút bấm
        btnSendNow.setOnClickListener(v -> sendNow());
        btnSendLater.setOnClickListener(v -> showSendOptionsBottomSheet());

        // Tải dữ liệu cư dân
        fetchResidentsFromAPI();
    }

    // =================================================================================
    // CÁC HÀM XỬ LÝ LOGIC (HELPER METHODS)
    // =================================================================================

    // 1. Cấu hình Dropdown Loại thông báo (Dùng AutoCompleteTextView)
    private void setupNotificationType(@Nullable String selectedType) {
        String[] types = {"Hành chính", "Kỹ thuật & bảo trì", "Tài chính", "Sự kiện & cộng đồng", "Khẩn cấp"};

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, types);
        spinnerNotificationType.setAdapter(adapter);

        if (selectedType != null) {
            String translatedType = convertEnglishToVi(selectedType);
            // setText(..., false) để không kích hoạt filter
            spinnerNotificationType.setText(translatedType, false);
        } else {
            // Chọn mặc định cái đầu tiên
            spinnerNotificationType.setText(types[0], false);
        }
    }

    // 2. Tải danh sách cư dân từ API
    private void fetchResidentsFromAPI() {
        RequestQueue queue = Volley.newRequestQueue(this);
        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, API_URL, null,
                this::parseResidentsFromResponse,
                error -> {
                    Log.e("API_ERROR", "Lỗi tải cư dân: " + error.toString());
                    Toast.makeText(this, "Không thể tải danh sách cư dân", Toast.LENGTH_SHORT).show();
                });
        queue.add(request);
    }

    // 3. Parse JSON và setup Bộ lọc Tầng/Phòng
    private void parseResidentsFromResponse(JSONArray response) {
        try {
            allResidents.clear();
            for (int i = 0; i < response.length(); i++) {
                JSONObject obj = response.getJSONObject(i);
                // Chỉ lấy role_id = 1 (Cư dân)
                if (obj.optInt("role_id", 0) != 1) continue;

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

            // Khởi tạo danh sách lọc
            filteredResidents.clear();
            filteredResidents.addAll(allResidents);
            adapter.updateList(filteredResidents);

            // Setup bộ lọc sau khi có dữ liệu
            setupFloorAndRoom();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 4. Cấu hình Dropdown Tầng & Phòng
    private void setupFloorAndRoom() {
        // Lấy danh sách Tầng và Phòng duy nhất
        List<String> floors = new ArrayList<>(Collections.singletonList("Tất cả"));
        List<String> rooms = new ArrayList<>(Collections.singletonList("Tất cả"));

        for (ResidentItem r : allResidents) {
            if (!floors.contains(r.getFloor())) floors.add(r.getFloor());
            if (!rooms.contains(r.getRoom())) rooms.add(r.getRoom());
        }

        // Set Adapter cho Tầng
        ArrayAdapter<String> floorAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, floors);
        spinnerReceiverFloor.setAdapter(floorAdapter);
        spinnerReceiverFloor.setText(floors.get(0), false);

        // Set Adapter cho Phòng
        ArrayAdapter<String> roomAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, rooms);
        spinnerReceiverRoom.setAdapter(roomAdapter);
        spinnerReceiverRoom.setText(rooms.get(0), false);

        // Sự kiện khi chọn Tầng/Phòng -> Lọc lại danh sách
        AdapterView.OnItemClickListener filterListener = (parent, view, position, id) ->
                filterResidents(searchView.getQuery().toString());

        spinnerReceiverFloor.setOnItemClickListener(filterListener);
        spinnerReceiverRoom.setOnItemClickListener(filterListener);
    }

    // 5. Cấu hình Tìm kiếm
    private void setupSearch() {
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterResidents(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterResidents(newText);
                return true;
            }
        });
    }

    // 6. Logic Lọc cư dân
    private void filterResidents(String query) {
        filteredResidents.clear();

        // Lấy giá trị từ AutoCompleteTextView
        String selectedFloor = spinnerReceiverFloor.getText().toString();
        String selectedRoom = spinnerReceiverRoom.getText().toString();

        // Fix lỗi nếu text bị trống (người dùng xóa tay)
        if(selectedFloor.isEmpty()) selectedFloor = "Tất cả";
        if(selectedRoom.isEmpty()) selectedRoom = "Tất cả";

        for (ResidentItem r : allResidents) {
            boolean matchesName = TextUtils.isEmpty(query) || r.getName().toLowerCase().contains(query.toLowerCase());
            boolean matchesFloor = selectedFloor.equals("Tất cả") || r.getFloor().equals(selectedFloor);
            boolean matchesRoom = selectedRoom.equals("Tất cả") || r.getRoom().equals(selectedRoom);

            if (matchesName && matchesFloor && matchesRoom) {
                filteredResidents.add(r);
            }
        }
        adapter.updateList(filteredResidents);

        // Hiện danh sách nếu có kết quả và chưa chọn "Gửi tất cả"
        if (!chkSendAll.isChecked()) {
            recyclerResidents.setVisibility(filteredResidents.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    // 7. Checkbox Gửi tất cả
    private void setupSendAllCheckbox() {
        chkSendAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // Chọn hết
                selectedResidents.clear();
                selectedResidents.addAll(allResidents);
                adapter.selectAllResidents(true);

                // Ẩn/Mờ danh sách để báo hiệu không cần chọn tay
                recyclerResidents.setAlpha(0.5f);
                recyclerResidents.setEnabled(false);
            } else {
                // Bỏ chọn hết
                selectedResidents.clear();
                adapter.selectAllResidents(false);

                recyclerResidents.setAlpha(1.0f);
                recyclerResidents.setEnabled(true);
                recyclerResidents.setVisibility(View.VISIBLE);
            }
            updateSelectedResidentsDisplay();
        });
    }

    // 8. Hiển thị text cư dân đã chọn
    private void updateSelectedResidentsDisplay() {
        if (selectedResidents.isEmpty()) {
            txtSelectedResidents.setText("Chưa chọn cư dân nào");
        } else if (chkSendAll.isChecked()) {
            txtSelectedResidents.setText("Đã chọn: Tất cả cư dân (" + allResidents.size() + ")");
        } else {
            StringBuilder sb = new StringBuilder("Đã chọn: ");
            int count = 0;
            for (ResidentItem r : selectedResidents) {
                if (count > 2) { // Chỉ hiện tối đa 3 tên
                    sb.append("... và ").append(selectedResidents.size() - 3).append(" người khác");
                    break;
                }
                sb.append(r.getName()).append(", ");
                count++;
            }
            // Xóa dấu phẩy thừa nếu không vào nhánh ...
            String result = sb.toString();
            if (result.endsWith(", ")) {
                result = result.substring(0, result.length() - 2);
            }
            txtSelectedResidents.setText(result);
        }
    }

    // 9. Chọn ngày hết hạn
    private void setupExpirationDatePicker() {
        edtExpirationDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(this, (view, year, month, dayOfMonth) ->
                    edtExpirationDate.setText(String.format(Locale.getDefault(), "%02d/%02d/%d", dayOfMonth, month + 1, year)),
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });
    }

    // =================================================================================
    // PHẦN 3: GỬI THÔNG BÁO (SEND LOGIC)
    // =================================================================================

    // BottomSheet chọn giờ gửi sau (Sử dụng layout dialog_schedule_time.xml)
    private void showSendOptionsBottomSheet() {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View sheetView = LayoutInflater.from(this).inflate(R.layout.bottomsheet_schedule_send, null);

        TextView txtSendTime = sheetView.findViewById(R.id.txtSendTime);
        MaterialButton btnConfirmSend = sheetView.findViewById(R.id.btnConfirmSend);
        MaterialButton btnSelectDate = sheetView.findViewById(R.id.btnSelectDate);
        MaterialButton btnSelectTime = sheetView.findViewById(R.id.btnSelectTime);

        Calendar c = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
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

    private void sendNow() { sendNotification(null); }
    private void sendLater(Date scheduledTime) { sendNotification(scheduledTime); }

    // API POST tạo thông báo
    private void sendNotification(Date scheduledTime) {
        // Tự động chọn tất cả nếu chưa chọn ai
        if (selectedResidents.isEmpty()) {
            Toast.makeText(this, "Chưa chọn người nhận, hệ thống sẽ gửi cho TẤT CẢ cư dân!", Toast.LENGTH_SHORT).show();
            selectedResidents = new HashSet<>(allResidents);
        }

        String title = edtNotificationTitle.getText().toString().trim();
        String content = edtNotificationContent.getText().toString().trim();
        String expiredDateRaw = edtExpirationDate.getText().toString().trim();

        // Lấy text từ AutoCompleteTextView
        String typeVi = spinnerNotificationType.getText().toString();
        String type = convertTypeToEnglish(typeVi);

        if (title.isEmpty() || content.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập tiêu đề và nội dung!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Format ngày hết hạn
        String expiredDate = null;
        if (!TextUtils.isEmpty(expiredDateRaw)) {
            try {
                SimpleDateFormat inputFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                expiredDate = outputFormat.format(inputFormat.parse(expiredDateRaw));
            } catch (Exception e) { e.printStackTrace(); }
        }

        // Format giờ gửi
        String formattedScheduledTime = null;
        if (scheduledTime != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            formattedScheduledTime = sdf.format(scheduledTime);
        }

        // Tạo JSON Body
        try {
            JSONArray userIds = new JSONArray();
            for (ResidentItem r : selectedResidents) {
                userIds.put(r.getUserId());
            }

            UserItem currentUser = UserManager.getInstance(this).getCurrentUser();
            int senderId = (currentUser != null) ? Integer.parseInt(currentUser.getId()) : 1;

            JSONObject body = new JSONObject();
            body.put("title", title);
            body.put("content", content);
            body.put("type", type);
            body.put("sender_id", senderId);
            body.put("expired_date", expiredDate);
            body.put("target_user_ids", userIds);

            if (scheduledTime != null) {
                body.put("scheduled_time", formattedScheduledTime);
            }

            // Gửi Request
            RequestQueue queue = Volley.newRequestQueue(this);
            JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, NOTIFICATION_API_URL, body,
                    response -> {
                        Toast.makeText(this, "Gửi thông báo thành công!", Toast.LENGTH_LONG).show();
                        // Gửi broadcast để màn hình danh sách cập nhật lại
                        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(ACTION_NOTIFICATION_CREATED));
                        finish();
                    },
                    error -> {
                        Toast.makeText(this, "Lỗi khi gửi thông báo: " + error.toString(), Toast.LENGTH_SHORT).show();
                        error.printStackTrace();
                    });
            queue.add(request);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Lỗi tạo dữ liệu gửi đi", Toast.LENGTH_SHORT).show();
        }
    }

    // API PUT Cập nhật thông báo
    private void updateNotification() {
        String title = edtNotificationTitle.getText().toString().trim();
        String content = edtNotificationContent.getText().toString().trim();
        String typeVi = spinnerNotificationType.getText().toString();
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

    // Helper: Chuyển đổi Loại tin Việt - Anh
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
}