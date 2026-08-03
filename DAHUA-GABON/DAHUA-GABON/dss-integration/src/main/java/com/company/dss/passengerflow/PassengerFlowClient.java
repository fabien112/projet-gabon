package com.company.dss.passengerflow;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.company.dss.client.DssClient;
import com.company.dss.common.DssApiPaths;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Client HTTP pour l'historique People Counting (même source que l'export Excel DSS).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PassengerFlowClient {

    private static final ZoneId GABON = ZoneId.of("Africa/Libreville");

    private final DssClient dssClient;

    public List<String> discoverCompteuseChannelIds() {
        JsonNode tree = dssClient.post(DssApiPaths.TREE_DEVICES, Map.of(), JsonNode.class);
        List<String> ids = new ArrayList<>();
        JsonNode devices = tree.path("data").path("devices");
        if (!devices.isArray()) {
            return ids;
        }
        for (JsonNode device : devices) {
            JsonNode units = device.path("units");
            if (!units.isArray()) {
                continue;
            }
            for (JsonNode unit : units) {
                JsonNode channels = unit.path("channels");
                if (!channels.isArray()) {
                    continue;
                }
                for (JsonNode channel : channels) {
                    String name = channel.path("channelName").asText("");
                    String code = channel.path("channelCode").asText("");
                    // Canaux vidéo people-count (unitType 1 → $1$ dans le code)
                    if (name.contains("Compteuse") && code.contains("$1$")) {
                        ids.add(code);
                    }
                }
            }
        }
        return ids;
    }

    public List<Map<String, Object>> fetchHistoryForDate(LocalDate date, List<String> channelIds) {
        if (channelIds == null || channelIds.isEmpty()) {
            return List.of();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("countType", "1");
        body.put("page", "1");
        body.put("pageSize", "200");
        body.put("countDate", date.toString());
        body.put("channelIds", channelIds);
        body.put("displayServerTimezoneOffset", "0");
        body.put("displayDeviceTimezoneOffset", "0");

        JsonNode response = dssClient.post(DssApiPaths.PASSENGER_FLOW_HISTORY, body, JsonNode.class);
        JsonNode pageData = response.path("data").path("pageData");
        List<Map<String, Object>> rows = new ArrayList<>();
        if (!pageData.isArray()) {
            return rows;
        }
        for (JsonNode row : pageData) {
            int enter = row.path("enterNumber").asInt(0);
            int exit = row.path("exitNumber").asInt(0);
            int stay = row.path("stayNumber").asInt(0);
            Map<String, Object> mapped = new LinkedHashMap<>();
            mapped.put("camera", row.path("channelName").asText());
            mapped.put("channelId", row.path("channelId").asText());
            mapped.put("startTime", row.path("startTime").asText());
            mapped.put("endTime", row.path("endTime").asText());
            mapped.put("total", enter + exit);
            mapped.put("in", enter);
            mapped.put("out", exit);
            mapped.put("occupancy", stay);
            rows.add(mapped);
        }
        return rows;
    }

    public LocalDate todayInGabon() {
        return LocalDate.now(GABON);
    }
}
