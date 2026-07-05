package com.poker.server.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class PlayerHandUpdate {
    private List<Map<String, String>> cards; // Карты конкретного игрока
}