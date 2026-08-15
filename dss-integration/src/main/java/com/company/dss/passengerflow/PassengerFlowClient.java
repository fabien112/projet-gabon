package com.company.dss.passengerflow;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
    private static final int PAGE_SIZE = 500;
    private static final int MAX_PAGES = 100;

    private final DssClient dssClient;

    public List<String> discoverCompteuseChannelIds() {
        return discoverCompteuseChannels().stream()
                .map(CompteuseChannel::channelId)
                .toList();
    }

    /**
     * Découvre toutes les caméras compteuses (canaux vidéo {@code $1$}).
     * Filtre case-insensitive sur le nom (Compteuse / People Count / Passenger).
     */
    public List<CompteuseChannel> discoverCompteuseChannels() {
        List<CompteuseChannel> channels = discoverMainVideoChannels().stream()
                .filter(c -> isCompteuseVideoChannel(c.name(), c.channelId()))
                .toList();
        log.info(">>> [FLOW] {} caméra(s) compteuse(s) découverte(s)", channels.size());
        return channels;
    }

    /**
     * Tous les canaux vidéo principaux DSS ({@code $1$}), pour l'ajout manuel en Config.
     */
    public List<CompteuseChannel> discoverMainVideoChannels() {
        JsonNode tree = dssClient.post(DssApiPaths.TREE_DEVICES, Map.of(), JsonNode.class);
        List<CompteuseChannel> channels = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        JsonNode devices = tree.path("data").path("devices");
        if (!devices.isArray()) {
            return channels;
        }
        for (JsonNode device : devices) {
            String deviceName = device.path("name").asText("");
            JsonNode units = device.path("units");
            if (!units.isArray()) {
                continue;
            }
            for (JsonNode unit : units) {
                JsonNode unitChannels = unit.path("channels");
                if (!unitChannels.isArray()) {
                    continue;
                }
                for (JsonNode channel : unitChannels) {
                    String name = channel.path("channelName").asText("");
                    String code = channel.path("channelCode").asText("");
                    if (!isMainVideoChannelCode(code) || !seen.add(code)) {
                        continue;
                    }
                    channels.add(new CompteuseChannel(code, name, deviceName));
                }
            }
        }
        log.info(">>> [FLOW] {} canal(aux) vidéo principal(aux) découvert(s)", channels.size());
        return channels;
    }

    /**
     * Historique d'une journée pour tous les canaux demandés (pagination complète).
     * Chaque ligne a un compte validé : {@code total = in + out}.
     */
    public List<Map<String, Object>> fetchHistoryForDate(LocalDate date, List<String> channelIds) {
        if (channelIds == null || channelIds.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        int page = 1;
        Integer totalCount = null;

        while (page <= MAX_PAGES) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("countType", "1");
            body.put("page", String.valueOf(page));
            body.put("pageSize", String.valueOf(PAGE_SIZE));
            body.put("countDate", date.toString());
            body.put("channelIds", channelIds);
            body.put("displayServerTimezoneOffset", "0");
            body.put("displayDeviceTimezoneOffset", "0");

            JsonNode response = dssClient.post(DssApiPaths.PASSENGER_FLOW_HISTORY, body, JsonNode.class);
            JsonNode data = response.path("data");
            JsonNode pageData = data.path("pageData");
            if (totalCount == null) {
                totalCount = parseTotalCount(data.path("totalCount"));
            }

            if (!pageData.isArray() || pageData.isEmpty()) {
                break;
            }

            for (JsonNode row : pageData) {
                Map<String, Object> mapped = PassengerFlowHistoryMapper.mapRow(row);
                if (!PassengerFlowHistoryMapper.isCountValid(mapped)) {
                    throw new IllegalStateException(
                            "Compte invalide après mapping : " + mapped
                    );
                }
                rows.add(mapped);
            }

            int fetched = pageData.size();
            boolean lastPageBySize = fetched < PAGE_SIZE;
            boolean lastPageByTotal = totalCount != null && totalCount >= 0 && rows.size() >= totalCount;
            if (lastPageBySize || lastPageByTotal) {
                break;
            }
            page++;
        }

        if (page > MAX_PAGES) {
            log.warn(">>> [FLOW] Pagination stoppée à {} pages ({} lignes) pour {}",
                    MAX_PAGES, rows.size(), date);
        }

        Set<String> camerasInResponse = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            String channelId = String.valueOf(row.getOrDefault("channelId", ""));
            if (StringUtils.hasText(channelId)) {
                camerasInResponse.add(channelId);
            }
        }
        log.debug(">>> [FLOW] Historique {} — pages≈{} — lignes={} — caméras présentes={}/{}",
                date, page, rows.size(), camerasInResponse.size(), channelIds.size());

        return rows;
    }

    public LocalDate todayInGabon() {
        return LocalDate.now(GABON);
    }

    public static boolean isCompteuseVideoChannel(String channelName, String channelCode) {
        if (!StringUtils.hasText(channelCode) || !isMainVideoChannelCode(channelCode)) {
            return false;
        }
        if (!StringUtils.hasText(channelName)) {
            return false;
        }
        String upper = channelName.toUpperCase(Locale.ROOT);
        return upper.contains("COMPTEUSE")
                || upper.contains("PEOPLE COUNT")
                || upper.contains("PEOPLECOUNT")
                || upper.contains("PASSENGER");
    }

    /**
     * Canal vidéo principal DSS : {@code {device}$1${unit}${ch}}.
     * Exclut les sous-canaux {@code $3$…} où {@code $1$} apparaît comme sous-chaîne
     * (ex. {@code 1000004$3$1$0}).
     */
    public static boolean isMainVideoChannelCode(String channelCode) {
        return StringUtils.hasText(channelCode)
                && channelCode.matches("^[^$]+\\$1\\$\\d+\\$\\d+$");
    }

    static Integer parseTotalCount(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return Math.max(node.asInt(), 0);
        }
        String text = node.asText("").trim();
        if (text.isEmpty() || "-0".equals(text)) {
            return 0;
        }
        try {
            return Math.max(Integer.parseInt(text), 0);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
