package com.se_04.enoti.notification.admin;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TimePicker;
import android.widget.Toast;
import android.widget.Button;
import android.widget.TextView;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textfield.TextInputEditText;
import com.se_04.enoti.R;
import com.se_04.enoti.residents.ResidentAdapter;
import com.se_04.enoti.residents.ResidentItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CreateNotificationActivity extends AppCompatActivity {

    private Spinner spinnerNotificationType, spinnerReceiverFloor, spinnerReceiverRoom;
    private SearchView searchView;
    private RecyclerView recyclerResidents;
    private ResidentAdapter adapter;
    private ImageButton btnReceiverList;
    private TextInputEditText edtNotificationTitle, edtExpirationDate, edtNotificationContent;
    private Button btnSendLater;
    private TextView txtSelectedResidents;

    private final List<ResidentItem> allResidents = new ArrayList<>();
    private final List<ResidentItem> filteredResidents = new ArrayList<>();
    private Set<ResidentItem> selectedResidents = new HashSet<>();

    private boolean isResidentListVisible = false;

    private static final String API_URL = "http://10.0.2.2:5000/api/residents"; // ⚙️ Thay IP của backend bạn

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_notification);

        // Ánh xạ view
        MaterialToolbar toolbar = findViewById(R.id.toolbar_feedback);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(true);
            getSupportActionBar().setTitle("Tạo thông báo");
        }

        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        spinnerNotificationType = findViewById(R.id.spinnerNotificationType);
        spinnerReceiverFloor = findViewById(R.id.spinnerReceiverFloor);
        spinnerReceiverRoom = findViewById(R.id.spinnerReceiverRoom);
        searchView = findViewById(R.id.search_view);
        recyclerResidents = findViewById(R.id.recyclerResidents);
        btnReceiverList = findViewById(R.id.btnReceiverList);
        edtNotificationTitle = findViewById(R.id.edtNotificationTitle);
        edtExpirationDate = findViewById(R.id.edtExpirationDate);
        edtNotificationContent = findViewById(R.id.edtNotificationContent);
        btnSendLater = findViewById(R.id.btnSendLater);
        txtSelectedResidents = findViewById(R.id.txtSelectedResidents);

        // Thiết lập RecyclerView
        adapter = new ResidentAdapter(filteredResidents, ResidentAdapter.MODE_SELECT_FOR_NOTIFICATION, selected -> {
            selectedResidents = selected;
            updateSelectedResidentsDisplay();
        });
        recyclerResidents.setLayoutManager(new LinearLayoutManager(this));
        recyclerResidents.setAdapter(adapter);
        recyclerResidents.setVisibility(View.GONE);

        setupNotificationType();
        setupFloorAndRoom();
        setupSearch();
        setupReceiverListToggle();
        setupExpirationDatePicker();

        btnSendLater.setOnClickListener(v -> showSendOptionsBottomSheet());

        // 🧠 Gọi API lấy danh sách cư dân thật
        fetchResidentsFromAPI();
    }

    /** 🧠 Lấy danh sách cư dân từ API */
    private void fetchResidentsFromAPI() {
        RequestQueue queue = Volley.newRequestQueue(this);

        JsonArrayRequest request = new JsonArrayRequest(
                Request.Method.GET,
                API_URL,
                null,
                this::parseResidentsFromResponse,
                error -> {
                    error.printStackTrace();
                    Toast.makeText(this, "Không thể tải danh sách cư dân!", Toast.LENGTH_SHORT).show();
                }
        );

        queue.add(request);
    }

    /** 🧠 Xử lý dữ liệu JSON trả về từ server */
    private void parseResidentsFromResponse(JSONArray response) {
        try {
            allResidents.clear();
            for (int i = 0; i < response.length(); i++) {
                JSONObject obj = response.getJSONObject(i);

                // Lọc chỉ lấy role_id = 1 (user)
                int roleId = obj.optInt("role_id", 0);
                if (roleId != 1) continue;

                allResidents.add(new ResidentItem(
                        obj.optInt("user_item_id"),
                        obj.optInt("user_id"),
                        obj.optString("full_name"),
                        obj.optString("gender"),
                        obj.optString("dob"),
                        obj.optString("email"),
                        obj.optString("phone"),
                        obj.optString("relationship_with_the_head_of_household"),
                        obj.optString("family_id"),
                        obj.optBoolean("is_living"),
                        obj.optString("apartment_number")
                ));
            }

            filteredResidents.clear();
            filteredResidents.addAll(allResidents);
            adapter.updateList(filteredResidents);

            setupFloorAndRoom(); // cập nhật spinner tầng/phòng
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupNotificationType() {
        String[] types = {"Thông báo chung", "Khẩn cấp", "Bảo trì"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, types);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerNotificationType.setAdapter(adapter);
    }

    private void setupFloorAndRoom() {
        List<String> floors = new ArrayList<>();
        floors.add("Tất cả tầng");
        for (ResidentItem r : allResidents) {
            if (!floors.contains(r.getFloor())) floors.add(r.getFloor());
        }

        List<String> rooms = new ArrayList<>();
        rooms.add("Tất cả phòng");
        for (ResidentItem r : allResidents) {
            if (!rooms.contains(r.getRoom())) rooms.add(r.getRoom());
        }

        ArrayAdapter<String> floorAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, floors);
        floorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerReceiverFloor.setAdapter(floorAdapter);

        ArrayAdapter<String> roomAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, rooms);
        roomAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerReceiverRoom.setAdapter(roomAdapter);

        AdapterView.OnItemSelectedListener filterListener = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                filterResidents(searchView.getQuery().toString());
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        };

        spinnerReceiverFloor.setOnItemSelectedListener(filterListener);
        spinnerReceiverRoom.setOnItemSelectedListener(filterListener);
    }

    private void setupSearch() {
        searchView.clearFocus();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) {
                filterResidents(query);
                return true;
            }

            @Override public boolean onQueryTextChange(String newText) {
                filterResidents(newText);
                return true;
            }
        });
    }

    private void setupReceiverListToggle() {
        btnReceiverList.setOnClickListener(v -> {
            isResidentListVisible = !isResidentListVisible;
            recyclerResidents.setVisibility(isResidentListVisible ? View.VISIBLE : View.GONE);
        });
    }

    private void filterResidents(String query) {
        filteredResidents.clear();

        String selectedFloor = spinnerReceiverFloor.getSelectedItem() != null
                ? spinnerReceiverFloor.getSelectedItem().toString()
                : "Tất cả tầng";
        String selectedRoom = spinnerReceiverRoom.getSelectedItem() != null
                ? spinnerReceiverRoom.getSelectedItem().toString()
                : "Tất cả phòng";

        for (ResidentItem r : allResidents) {
            boolean matchesName = TextUtils.isEmpty(query)
                    || r.getName().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
            boolean matchesFloor = selectedFloor.equals("Tất cả tầng") || r.getFloor().equals(selectedFloor);
            boolean matchesRoom = selectedRoom.equals("Tất cả phòng") || r.getRoom().equals(selectedRoom);

            if (matchesName && matchesFloor && matchesRoom) {
                filteredResidents.add(r);
            }
        }

        adapter.updateList(filteredResidents);
    }

    private void setupExpirationDatePicker() {
        edtExpirationDate.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog dialog = new DatePickerDialog(this, (DatePicker view, int y, int m, int d) -> {
                String selectedDate = String.format(Locale.getDefault(), "%02d/%02d/%d", d, m + 1, y);
                edtExpirationDate.setText(selectedDate);
            }, year, month, day);
            dialog.show();
        });
    }

    /** ---------------------- CẬP NHẬT DANH SÁCH CƯ DÂN ĐÃ CHỌN ---------------------- */
    private void updateSelectedResidentsDisplay() {
        if (selectedResidents.isEmpty()) {
            txtSelectedResidents.setText("Chưa chọn cư dân nào");
        } else {
            StringBuilder sb = new StringBuilder("Đã chọn: ");
            for (ResidentItem r : selectedResidents) {
                sb.append(r.getName()).append(", ");
            }
            txtSelectedResidents.setText(sb.substring(0, sb.length() - 2));
        }
    }

    /** ---------------------- BOTTOM SHEET GỬI THÔNG BÁO ---------------------- */
    private void showSendOptionsBottomSheet() {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View sheetView = LayoutInflater.from(this).inflate(R.layout.bottomsheet_schedule_send, null);

        TextView txtSendTime = sheetView.findViewById(R.id.txtSendTime);
        CheckBox chkRemind = sheetView.findViewById(R.id.checkRemind);
        LinearLayout layoutRemindTime = sheetView.findViewById(R.id.layoutRemindBefore);
        TextView txtRemindTime = sheetView.findViewById(R.id.txtRemindTime);
        Button btnConfirmSend = sheetView.findViewById(R.id.btnConfirmSend);

        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
        txtSendTime.setText(sdf.format(calendar.getTime()));

        txtSendTime.setOnClickListener(v -> pickDateTime(txtSendTime));

        chkRemind.setOnCheckedChangeListener((buttonView, isChecked) ->
                layoutRemindTime.setVisibility(isChecked ? View.VISIBLE : View.GONE)
        );

        txtRemindTime.setOnClickListener(v -> pickTime(txtRemindTime));

        btnConfirmSend.setOnClickListener(v -> {
            if (selectedResidents.isEmpty()) {
                Toast.makeText(this, "Vui lòng chọn ít nhất một cư dân để gửi!", Toast.LENGTH_SHORT).show();
                return;
            }

            String sendTime = txtSendTime.getText().toString();
            boolean remind = chkRemind.isChecked();
            String remindTime = remind ? txtRemindTime.getText().toString() : "Không";

            Toast.makeText(this,
                    "Đã gửi thông báo cho " + selectedResidents.size() + " cư dân!\nThời gian gửi: " + sendTime +
                            "\nNhắc nhở: " + (remind ? "Có (" + remindTime + ")" : "Không"),
                    Toast.LENGTH_LONG).show();

            bottomSheet.dismiss();
        });

        bottomSheet.setContentView(sheetView);
        bottomSheet.show();
    }

    private void pickDateTime(TextView textView) {
        Calendar calendar = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            new TimePickerDialog(this, (TimePicker tp, int hour, int minute) -> {
                calendar.set(year, month, dayOfMonth, hour, minute);
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
                textView.setText(sdf.format(calendar.getTime()));
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void pickTime(TextView textView) {
        Calendar calendar = Calendar.getInstance();
        new TimePickerDialog(this, (TimePicker tp, int hour, int minute) -> {
            textView.setText(String.format(Locale.getDefault(), "%02d:%02d:00", hour, minute));
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show();
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
