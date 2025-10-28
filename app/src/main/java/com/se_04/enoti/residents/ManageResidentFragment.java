package com.se_04.enoti.residents;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
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
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
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

    // ⚙️ API endpoint — thay bằng IP máy chạy Node.js
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

        UserItem currentUser = UserManager.getInstance(requireContext()).getCurrentUser();
        String username = (currentUser != null) ? currentUser.getName() : "Người dùng";
        String message = "Xin chào " + username + "!";
        txtWelcome.setText(message);

        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);

        String timeOfDay;
        if (hour >= 5 && hour < 11) {
            timeOfDay = "sáng";
        } else if (hour >= 11 && hour < 14) {
            timeOfDay = "trưa";
        } else if (hour >= 14 && hour < 18) {
            timeOfDay = "chiều";
        } else {
            timeOfDay = "tối";
        }
        String greeting = getString(R.string.greeting, timeOfDay);
        txtGreeting.setText(greeting);

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
            if (ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireActivity(),
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_WRITE_PERMISSION);
            } else {
                exportResidentsToExcel(filteredList);
            }
        });

        btnAddResident.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), CreateResidentActivity.class);
            startActivityForResult(intent, 101);
        });
    }

    private void fetchResidentsFromAPI() {
        if (!isAdded()) return;

        RequestQueue queue = Volley.newRequestQueue(requireContext());

        JsonArrayRequest request = new JsonArrayRequest(
                Request.Method.GET,
                API_URL,
                null,
                response -> {
                    if (!isAdded()) return;
                    parseResidents(response);
                },
                error -> {
                    error.printStackTrace();
                    if (isAdded() && getContext() != null) {
                        Toast.makeText(getContext(), "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        queue.add(request);
    }

    private void parseResidents(JSONArray response) {
        try {
            fullList.clear();
            for (int i = 0; i < response.length(); i++) {
                JSONObject obj = response.getJSONObject(i);
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
                        obj.optString("apartment_number")
                ));
            }

            // 🧩 Sắp xếp theo số phòng → rồi theo tên (bỏ họ)
            Collections.sort(fullList, new Comparator<ResidentItem>() {
                @Override
                public int compare(ResidentItem a, ResidentItem b) {
                    // So sánh số phòng (ưu tiên số nhỏ hơn)
                    int roomA, roomB;
                    try {
                        roomA = Integer.parseInt(a.getRoom().replaceAll("\\D+", ""));
                        roomB = Integer.parseInt(b.getRoom().replaceAll("\\D+", ""));
                    } catch (Exception e) {
                        return a.getRoom().compareToIgnoreCase(b.getRoom());
                    }
                    if (roomA != roomB) return Integer.compare(roomA, roomB);

                    // Nếu cùng phòng → so sánh theo TÊN (không lấy họ)
                    String[] partsA = a.getName().trim().split("\\s+");
                    String[] partsB = b.getName().trim().split("\\s+");
                    String lastA = partsA[partsA.length - 1];
                    String lastB = partsB[partsB.length - 1];
                    int nameCompare = lastA.compareToIgnoreCase(lastB);
                    if (nameCompare != 0) return nameCompare;

                    // Nếu trùng tên → fallback so theo họ đầy đủ
                    return a.getName().compareToIgnoreCase(b.getName());
                }
            });

            filteredList.clear();
            filteredList.addAll(fullList);
            adapter.updateList(filteredList);
            setupFloorSpinner();

        } catch (Exception e) {
            e.printStackTrace();
        }
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
        String selectedFloor = spinnerFilterFloor.getSelectedItem().toString();
        String selectedRoom = spinnerFilterRoom.isEnabled()
                ? spinnerFilterRoom.getSelectedItem().toString() : "Tất cả phòng";

        filteredList.clear();

        for (ResidentItem item : fullList) {
            // 🔍 Cho phép tìm theo tên
            boolean matchesSearch = item.getName().toLowerCase(Locale.ROOT).contains(searchQuery);

            boolean matchesFloor =
                    selectedFloor.equals("Tất cả tầng") || item.getFloor().equals(selectedFloor);

            boolean matchesRoom =
                    selectedRoom.equals("Tất cả phòng") || item.getRoom().equals(selectedRoom);

            if (matchesSearch && matchesFloor && matchesRoom) {
                filteredList.add(item);
            }
        }

        adapter.updateList(filteredList);
    }

    private void exportResidentsToExcel(List<ResidentItem> residents) {
        try {
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("Cư dân");

            Row header = sheet.createRow(0);
            String[] headers = {"Họ tên", "Giới tính", "Ngày sinh", "Điện thoại", "Email", "Phòng", "Quan hệ"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
            }

            int rowIndex = 1;
            for (ResidentItem r : residents) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(r.getName());
                row.createCell(1).setCellValue(r.getGender());
                row.createCell(2).setCellValue(r.getDob());
                row.createCell(3).setCellValue(r.getPhone());
                row.createCell(4).setCellValue(r.getEmail());
                row.createCell(5).setCellValue(r.getRoom());
                row.createCell(6).setCellValue(r.getRelationship());
            }

            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Enoti");
            if (!dir.exists()) dir.mkdirs();

            File file = new File(dir, "DanhSachCuDan.xlsx");
            FileOutputStream fos = new FileOutputStream(file);
            workbook.write(fos);
            fos.close();
            workbook.close();

            Toast.makeText(requireContext(), "Đã lưu: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(requireContext(), "Lỗi khi xuất file!", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_WRITE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                exportResidentsToExcel(filteredList);
            } else {
                Toast.makeText(requireContext(), "Không có quyền ghi tệp!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 101 && resultCode == AppCompatActivity.RESULT_OK) {
            // 🔁 Gọi lại API để tải danh sách cư dân mới
            fetchResidentsFromAPI();
        }
    }

}
