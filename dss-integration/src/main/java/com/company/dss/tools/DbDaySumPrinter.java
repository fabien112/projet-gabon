package com.company.dss.tools;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/** Somme entrées/sorties par jour (debug local). */
public class DbDaySumPrinter {

    public static void main(String[] args) throws Exception {
        String url = args.length > 0 ? args[0]
                : "jdbc:h2:file:C:/Users/smar.BATIPLUS/Documents/DAHUA/DAHUA/dist-client/data/dss;MODE=MySQL;AUTO_SERVER=TRUE";
        String day = args.length > 1 ? args[1] : "2026-09-16";
        try (Connection c = DriverManager.getConnection(url, "SA", "")) {
            String sql = """
                    select h.slot_date, count(*) as slots,
                           sum(h.entries) as total_in, sum(h.exits) as total_out
                    from people_counting_hourly h
                    where h.slot_date = ?
                    group by h.slot_date
                    """;
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, day);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        System.out.println("date=" + rs.getString("slot_date")
                                + " slots=" + rs.getInt("slots")
                                + " entries=" + rs.getLong("total_in")
                                + " exits=" + rs.getLong("total_out"));
                    } else {
                        System.out.println("no rows for " + day);
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement("""
                    select c.name, c.channel_id, sum(h.entries) as ein
                    from people_counting_hourly h
                    join camera c on c.id = h.camera_id
                    where h.slot_date = ?
                    group by c.name, c.channel_id
                    order by ein desc
                    """)) {
                ps.setString(1, day);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        System.out.println("  " + rs.getString("name") + " | in=" + rs.getLong("ein"));
                    }
                }
            }
        }
    }
}
