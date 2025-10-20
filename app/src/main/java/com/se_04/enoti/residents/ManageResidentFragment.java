package com.se_04.enoti.residents;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.se_04.enoti.R;
import com.se_04.enoti.utils.UserManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class ManageResidentFragment extends Fragment {

    private RecyclerView recyclerViewResidents;
    private SearchView searchView;
    private Spinner spinnerFilterFloor, spinnerFilterRoom;
    private FloatingActionButton btnExportExcel;

    private ResidentAdapter adapter;
    private List<ResidentItem> fullList;
    private List<ResidentItem> filteredList;

    private final List<String> allFloors = new ArrayList<>();
    private final List<String> allRooms = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_manage_resident, container, false);

        // Ánh xạ view
        TextView txtWelcome = view.findViewById(R.id.txtWelcome);
        TextView txtGreeting = view.findViewById(R.id.txtGreeting);
        searchView = view.findViewById(R.id.search_view);
        spinnerFilterFloor = view.findViewById(R.id.spinnerFilterFloor);
        spinnerFilterRoom = view.findViewById(R.id.spinnerFilterRoom);
        recyclerViewResidents = view.findViewById(R.id.recyclerViewResidents);
        btnExportExcel = view.findViewById(R.id.btnExportExcel);

        // Hiển thị lời chào
        String username = UserManager.getInstance(requireContext()).getUsername();
        txtWelcome.setText(getString(R.string.welcome, username));

        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        String timeOfDay;
        if (hour >= 5 && hour < 11) timeOfDay = "sáng";
        else if (hour >= 11 && hour < 14) timeOfDay = "trưa";
        else if (hour >= 14 && hour < 18) timeOfDay = "chiều";
        else timeOfDay = "tối";
        txtGreeting.setText(getString(R.string.greeting, timeOfDay));

        // Dữ liệu mẫu
        setupSampleData();

        // Thiết lập RecyclerView
        recyclerViewResidents.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ResidentAdapter(filteredList);
        recyclerViewResidents.setAdapter(adapter);

        // Thiết lập spinner tầng
        setupFloorSpinner();

        // Sự kiện chọn tầng
        spinnerFilterFloor.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateRoomSpinner();
                applyFilters();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        // Sự kiện chọn phòng
        spinnerFilterRoom.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyFilters();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        // Tìm kiếm
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

        // Nút Export Excel
        btnExportExcel.setOnClickListener(v -> {
            // TODO: Thêm chức năng export Excel nếu cần
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

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null) adapter.notifyDataSetChanged();
    }
}
