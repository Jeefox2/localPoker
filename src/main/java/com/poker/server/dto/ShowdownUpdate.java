package com.poker.server.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class ShowdownUpdate {
    private Map<String, List<Map<String, String>>> playersCards; // Имя -> список карт
    private String winnerMessage; // "Победил X с комбинацией Y"
}