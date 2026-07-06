package com.poker.server.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class ShowdownUpdate {
    private Map<String, List<Map<String, String>>> playersCards; // Имя -> карты игрока
    private Map<String, String> playerHands;                     // Имя -> название комбинации
    private List<Map<String, String>> tableCards;                // Карты на столе
    private List<String> winnerNames;                            // Имена победителей
    private String winnerMessage;                                // "Победил X с комбинацией Y"
}