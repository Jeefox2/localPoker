package com.poker.server.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class GameStateUpdate {
    private int pot;
    private int currentMaxBet;
    private List<Map<String, String>> tableCards;
    private List<PlayerInfo> players;
    private String currentPlayerSessionId;
    private String currentStage;
    private String message;
    private long turnStartTime;

    @Data
    @AllArgsConstructor
    public static class PlayerInfo {
        private String sessionId;
        private String name;
        private int balance;
        private int bet;
        private boolean active;
        private boolean connected;
    }
}