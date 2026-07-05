package com.poker.server.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class GameStateUpdate {
    private int pot;                          // Банк
    private int currentMaxBet;                // Максимальная ставка на улице
    private List<Map<String, String>> tableCards; // Карты на столе (общие)
    private List<PlayerInfo> players;         // Все игроки с их ставками и статусами
    private String currentPlayerSessionId;    // ID того, чей сейчас ход
    private String currentStage;              // PREFLOP, FLOP, TURN, RIVER, SHOWDOWN
    private String message;                   // Текстовое сообщение (лог)

    @Data
    @AllArgsConstructor
    public static class PlayerInfo {
        private String sessionId;
        private String name;
        private int balance;
        private int bet;
        private boolean active;       // Ещё в раздаче (не сбросил)
        private boolean connected;    // Физически подключен
    }
}