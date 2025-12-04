package com.se_04.enoti.residents;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.se_04.enoti.R;
import com.se_04.enoti.account.UserItem;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.UserManager;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ManageResidentFragment extends Fragment {

    private RecyclerView recyclerViewResidents;
    private SearchView searchView;
    private Spinner spinnerFilterFloor, spinnerFilterRoom;
    private FloatingActionButton btnExportExcel, btnAddResident;

    private ResidentAdapter adapter;
    private List<ResidentItem> fullList;
    private List<ResidentItem> filteredList;

    private final Set<ResidentItem> selectedResidents = new HashSet<>();
    private final List<String> allFloors = new ArrayList<>();
    private final List<String> allRooms = new ArrayList<>();

    private static final int REQUEST_WRITE_PERMISSION = 1001;

    // ⚙️ API endpoint
    private static final String API_URL = ApiConfig.BASE_URL + "/api/residents";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_manage_resident, container, false);

        TextView txtWelcome = view.findViewById(R.id.txtWelcome);
        TextView txtGreeting = view.findViewById(R.id.txtGreeting);
        searchView = view.findViewById(R.id.search_view);
        spinnerFilterFloor = view.findViewById(R.id.spinnerFilterFloor);
        spinnerFilterRoom = view.findViewById(R.id.spinnerFilterRoom);
        recyclerViewResidents = view.findViewById(R.id.recyclerViewResidents);
        btnExportExcel = view.findViewById(R.id.btnExportExcel);
        btnAddResident = view.findViewById(R.id.btnAddResident);

        // Hiển thị lời chào
        UserItem currentUser = UserManager.getInstance(requireContext()).getCurrentUser();
        String username = (currentUser != null) ? currentUser.getName() : "Người dùng";
        txtWelcome.setText("Xin chào " + username + "!");

        // Chào theo buổi
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        String timeOfDay;
        if (hour >= 5 && hour < 11) timeOfDay = "sáng";
        else if (hour >= 11 && hour < 14) timeOfDay = "trưa";
        else if (hour >= 14 && hour < 18) timeOfDay = "chiều";
        else timeOfDay = "tối";
        txtGreeting.setText(getString(R.string.greeting, timeOfDay));

        // RecyclerView setup
        recyclerViewResidents.setLayoutManager(new LinearLayoutManager(requireContext()));
        fullList = new ArrayList<>();
        filteredList = new ArrayList<>();
        adapter = new ResidentAdapter(filteredList, ResidentAdapter.MODE_VIEW_DETAIL, selected -> {});
        recyclerViewResidents.setAdapter(adapter);

        setupFloorSpinner();
        setupListeners();
        fetchResidentsFromAPI();

        return view;
    }

    private void setupListeners() {
        spinnerFilterFloor.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateRoomSpinner();
                applyFilters();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerFilterRoom.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyFilters();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) { applyFilters(); return true; }
            @Override public boolean onQueryTextChange(String newText) { applyFilters(); return true; }
        });

        btnExportExcel.setOnClickListener(v -> {
            exportResidentsToXLS(filteredList);
        });

        btnAddResident.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), CreateResidentActivity.class);
            startActivityForResult(intent, 101);
        });
    }

    private void fetchResidentsFromAPI() {
        RequestQueue queue = Volley.newRequestQueue(requireContext());
        JsonArrayRequest request = new JsonArrayRequest(
                Request.Method.GET, API_URL, null,
                response -> parseResidents(response),
                error -> Toast.makeText(getContext(), "Lỗi kết nối", Toast.LENGTH_SHORT).show()
        );
        queue.add(request);
    }

    private void parseResidents(JSONArray response) {
        try {
            fullList.clear();
            for (int i = 0; i < response.length(); i++) {
                JSONObject obj = response.getJSONObject(i);

                // 🔥 FIX: Sử dụng đúng Constructor mới của ResidentItem
                // Thêm identity_card và home_town (dùng optString để tránh lỗi nếu null)
                fullList.add(new ResidentItem(
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
                        obj.optString("apartment_number"),
                        obj.optString("identity_card", ""), // 🔥 CCCD (Mặc định rỗng)
                        obj.optString("home_town", "")      // 🔥 Quê quán (Mặc định rỗng)
                ));
            }

            // Sắp xếp cư dân
            Collections.sort(fullList, (a, b) -> {
                int roomA = extractRoomNumber(a.getRoom());
                int roomB = extractRoomNumber(b.getRoom());
                if (roomA != roomB) return Integer.compare(roomA, roomB);
                return a.getName().compareToIgnoreCase(b.getName());
            });

            filteredList.clear();
            filteredList.addAll(fullList);
            adapter.updateList(filteredList);
            setupFloorSpinner();

        } catch (Exception e) {
            e.printStackTrace();
            // Toast giúp debug nếu parse lỗi
            // Toast.makeText(getContext(), "Lỗi xử lý dữ liệu: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private int extractRoomNumber(String room) {
        try { return Integer.parseInt(room.replaceAll("\\D+", "")); }
        catch (Exception e) { return 0; }
    }

    private void setupFloorSpinner() {
        allFloors.clear();
        allFloors.add("Tất cả tầng");
        for (ResidentItem item : fullList) {
            String floor = item.getFloor();
            if (!allFloors.contains(floor)) allFloors.add(floor);
        }

        ArrayAdapter<String> floorAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, allFloors);
        floorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFilterFloor.setAdapter(floorAdapter);
        spinnerFilterRoom.setEnabled(false);
    }

    private void updateRoomSpinner() {
        String selectedFloor = spinnerFilterFloor.getSelectedItem().toString();
        allRooms.clear();

        if (selectedFloor.equals("Tất cả tầng")) {
            spinnerFilterRoom.setEnabled(false);
            allRooms.add("Tất cả phòng");
        } else {
            spinnerFilterRoom.setEnabled(true);
            allRooms.add("Tất cả phòng");
            for (ResidentItem item : fullList) {
                if (item.getFloor().equals(selectedFloor) && !allRooms.contains(item.getRoom())) {
                    allRooms.add(item.getRoom());
                }
            }
        }

        ArrayAdapter<String> roomAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, allRooms);
        roomAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFilterRoom.setAdapter(roomAdapter);
    }

    private void applyFilters() {
        String searchQuery = searchView.getQuery().toString().toLowerCase(Locale.ROOT);
        Object selectedFloorObj = spinnerFilterFloor.getSelectedItem();
        Object selectedRoomObj = spinnerFilterRoom.getSelectedItem();

        String selectedFloor = (selectedFloorObj != null) ? selectedFloorObj.toString() : "Tất cả tầng";
        String selectedRoom = (selectedRoomObj != null && spinnerFilterRoom.isEnabled())
                ? selectedRoomObj.toString() : "Tất cả phòng";

        filteredList.clear();

        for (ResidentItem item : fullList) {
            boolean matchesSearch = item.getName().toLowerCase(Locale.ROOT).contains(searchQuery);
            boolean matchesFloor = selectedFloor.equals("Tất cả tầng") || item.getFloor().equals(selectedFloor);
            boolean matchesRoom = selectedRoom.equals("Tất cả phòng") || item.getRoom().equals(selectedRoom);
            if (matchesSearch && matchesFloor && matchesRoom) filteredList.add(item);
        }

        adapter.updateList(filteredList);
    }

    private void exportResidentsToXLS(List<ResidentItem> residents) {
        try {
            org.apache.poi.hssf.usermodel.HSSFWorkbook workbook = new org.apache.poi.hssf.usermodel.HSSFWorkbook();
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Cư dân");

            // Thêm cột CCCD và Quê quán vào Excel
            String[] headers = {"Họ tên", "Giới tính", "Ngày sinh", "Điện thoại", "Email", "Phòng", "Chủ hộ", "Quan hệ", "CCCD", "Quê quán"};
            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }

            java.util.Map<String, String> headMap = new java.util.HashMap<>();
            for (ResidentItem r : fullList) {
                if ("Bản thân".equalsIgnoreCase(r.getRelationship())) {
                    headMap.put(r.getFamilyId(), r.getName());
                }
            }

            int rowNum = 1;
            for (ResidentItem r : residents) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);

                String headName = headMap.getOrDefault(r.getFamilyId(), "Chưa xác định");

                row.createCell(0).setCellValue(r.getName());
                row.createCell(1).setCellValue(r.getGender());
                row.createCell(2).setCellValue(r.getDob());
                row.createCell(3).setCellValue(r.getPhone());
                row.createCell(4).setCellValue(r.getEmail());
                row.createCell(5).setCellValue(r.getRoom());
                row.createCell(6).setCellValue(headName);
                row.createCell(7).setCellValue(r.getRelationship());
                // 🔥 Xuất thêm 2 cột mới
                row.createCell(8).setCellValue(r.getIdentityCard());
                row.createCell(9).setCellValue(r.getHomeTown());
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.setColumnWidth(i, 6000);
            }

            String fileName = "DanhSachCuDan_" +
                    new java.text.SimpleDateFormat("ddMMyyyy_HHmm", java.util.Locale.getDefault()).format(new java.util.Date()) +
                    ".xls";

            android.content.ContentValues values = new android.content.ContentValues();
            values.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName);
            values.put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/vnd.ms-excel");
            values.put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/Enoti");

            android.content.ContentResolver resolver = requireContext().getContentResolver();
            android.net.Uri uri;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            } else {
                java.io.File dir = new java.io.File(android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS), "Enoti");
                if (!dir.exists()) dir.mkdirs();
                java.io.File file = new java.io.File(dir, fileName);
                uri = android.net.Uri.fromFile(file);
            }

            if (uri != null) {
                java.io.OutputStream outputStream = resolver.openOutputStream(uri);
                workbook.write(outputStream);
                outputStream.close();
                workbook.close();
                android.widget.Toast.makeText(requireContext(), "Đã lưu: " + fileName, android.widget.Toast.LENGTH_LONG).show();
            } else {
                android.widget.Toast.makeText(requireContext(), "Không thể tạo file Excel!", android.widget.Toast.LENGTH_SHORT).show();
            }

        } catch (Exception e) {
            e.printStackTrace();
            android.widget.Toast.makeText(requireContext(), "Lỗi khi xuất file Excel!", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_WRITE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                exportResidentsToXLS(filteredList);
            } else {
                Toast.makeText(requireContext(), "Không có quyền ghi tệp!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 101 && resultCode == AppCompatActivity.RESULT_OK) {
            fetchResidentsFromAPI();
        }
    }
}