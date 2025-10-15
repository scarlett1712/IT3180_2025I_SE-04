package com.se_04.enoti.finance;

import java.util.ArrayList;
import java.util.List;

public class FinanceRepository {
    private static FinanceRepository instance;
    private final List<FinanceItem> receipts;

    private FinanceRepository() {
        receipts = new ArrayList<>();
        seedData();
    }

    public static FinanceRepository getInstance(){
        if (instance == null){
            instance = new FinanceRepository();
        }
        return instance;
    }

    public List<FinanceItem> getReceipts(){ return receipts; }

    private void seedData(){
        receipts.add(new FinanceItem("Hóa đơn điện", "31/10/2025", "Hóa đơn hàng tháng", "Công ty nước sạch Hà Nội", 1800000L));
        receipts.add(new FinanceItem("Hóa đơn nước", "31/10/2025", "Hóa đơn hàng tháng", "Công ty nước sạch Hà Nội", 150000L));
        receipts.add(new FinanceItem("Quỹ vì người giàu", "5/11/2025", "Quỹ", "admin"));
    }
}

