package com.company.dss.passengerflow;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Mappe une ligne d'historique DSS vers le format métier (aligné Excel DSS).
 * <p>
 * Règle de compte valide : {@code total = enterNumber + exitNumber},
 * avec {@code in}/{@code out}/{@code occupancy} issus des champs natifs DSS.
 */
public final class PassengerFlowHistoryMapper {

    private PassengerFlowHistoryMapper() {
    }

    public static Map<String, Object> mapRow(JsonNode row) {
        int enter = intField(row, "enterNumber");
        int exit = intField(row, "exitNumber");
        int stay = intField(row, "stayNumber");
        int total = enter + exit;

        // Si DSS expose un totalNumber, il doit coincider avec in+out.
        if (row.has("totalNumber") && !row.get("totalNumber").isNull()) {
            int dssTotal = intField(row, "totalNumber");
            if (dssTotal != total) {
                throw new IllegalArgumentException(
                        "Compte DSS incohérent pour canal "
                                + row.path("channelId").asText()
                                + " @ " + row.path("startTime").asText()
                                + " : totalNumber=" + dssTotal
                                + " mais enter+exit=" + total
                );
            }
        }

        Map<String, Object> mapped = new LinkedHashMap<>();
        mapped.put("camera", row.path("channelName").asText(""));
        mapped.put("channelId", row.path("channelId").asText(""));
        mapped.put("startTime", row.path("startTime").asText(""));
        mapped.put("endTime", row.path("endTime").asText(""));
        mapped.put("total", total);
        mapped.put("in", enter);
        mapped.put("out", exit);
        mapped.put("occupancy", stay);
        return mapped;
    }

    /** Vérifie qu'une ligne déjà mappée respecte total = in + out. */
    public static boolean isCountValid(Map<String, Object> row) {
        if (row == null) {
            return false;
        }
        int in = toInt(row.get("in"));
        int out = toInt(row.get("out"));
        int total = toInt(row.get("total"));
        return total == in + out && in >= 0 && out >= 0;
    }

    static int intField(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return 0;
        }
        if (value.isNumber()) {
            return value.asInt();
        }
        String text = value.asText("").trim();
        if (text.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static int toInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
