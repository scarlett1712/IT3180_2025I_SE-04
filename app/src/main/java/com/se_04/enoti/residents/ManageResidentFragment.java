package com.se_04.enoti.residents;

import android.Manifest;
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
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.se_04.enoti.R;
import com.se_04.enoti.utils.UserManager;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ManageResidentFragment extends Fragment {

    private RecyclerView recyclerViewResidents;
    private SearchView searchView;
    private Spinner spinnerFilterFloor, spinnerFilterRoom;
    private FloatingActionButton btnExportExcel;

    private ResidentAdapter adapter;
    private List<ResidentItem> fullList;
    private List<ResidentItem> filteredList;

    private final Set<ResidentItem> selectedResidents = new HashSet<>();

    private final List<String> allFloors = new ArrayList<>();
    private final List<String> allRooms = new ArrayList<>();

    private static final int REQUEST_WRITE_PERMISSION = 1001;

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

        String username = UserManager.getInstance(requireContext()).getUsername();
        txtWelcome.setText(getString(R.string.welcome, username));

        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        String timeOfDay = (hour >= 5 && hour < 11) ? "sáng"
                : (hour < 14) ? "trưa"
                : (hour < 18) ? "chiều" : "tối";
        txtGreeting.setText(getString(R.string.greeting, timeOfDay));

        setupSampleData();

        recyclerViewResidents.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ResidentAdapter(filteredList, ResidentAdapter.MODE_VIEW_DETAIL, selected -> {});
        recyclerViewResidents.setAdapter(adapter);

        setupFloorSpinner();

        spinnerFilterFloor.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateRoomSpinner();
                applyFilters();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        spinnerFilterRoom.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyFilters();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                applyFilters();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                applyFilters();
                return true;
            }
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

        return view;
    }

    private void setupSampleData() {
        fullList = new ArrayList<>();
        fullList.add(new ResidentItem("Nguyễn Văn A", "Tầng 1", "Phòng 101"));
        fullList.add(new ResidentItem("Trần Thị B", "Tầng 1", "Phòng 102"));
        fullList.add(new ResidentItem("Lê Văn C", "Tầng 2", "Phòng 201"));
        fullList.add(new ResidentItem("Phạm Minh D", "Tầng 3", "Phòng 301"));
        fullList.add(new ResidentItem("Ngô Thị E", "Tầng 3", "Phòng 302"));

        filteredList = new ArrayList<>(fullList);
    }

    private void setupFloorSpinner() {
        allFloors.clear();
        allFloors.add("Tất cả tầng");
        allFloors.add("Tầng 1");
        allFloors.add("Tầng 2");
        allFloors.add("Tầng 3");

        ArrayAdapter<String> floorAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, allFloors
        );
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

        ArrayAdapter<String> roomAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, allRooms
        );
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
            boolean matchesSearch = item.getName().toLowerCase(Locale.ROOT).contains(searchQuery);
            boolean matchesFloor = selectedFloor.equals("Tất cả tầng") || item.getFloor().equals(selectedFloor);
            boolean matchesRoom = selectedRoom.equals("Tất cả phòng") || item.getRoom().equals(selectedRoom);

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
            String[] headers = {"Họ tên", "Tầng", "Phòng"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
            }

            int rowIndex = 1;
            for (ResidentItem r : residents) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(r.getName());
                row.createCell(1).setCellValue(r.getFloor());
                row.createCell(2).setCellValue(r.getRoom());
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
}
