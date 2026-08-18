package com.company.dss.tools;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class DbMinDatePrinter {

    public static void main(String[] args) throws Exception {
        String url = "jdbc:h2:file:./data/dss";
        try (Connection c = DriverManager.getConnection(url, "SA", "")) {
            try (PreparedStatement ps = c.prepareStatement("select min(slot_date) as min_d, max(slot_date) as max_d from people_counting_hourly")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String min = rs.getString("min_d");
                        String max = rs.getString("max_d");
                        System.out.println("minSlotDate=" + min);
                        System.out.println("maxSlotDate=" + max);
                    } else {
                        System.out.println("minSlotDate=null");
                        System.out.println("maxSlotDate=null");
                    }
                }
            }
        }
    }
}
