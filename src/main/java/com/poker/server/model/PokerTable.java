package com.poker.server.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PokerTable {
    private boolean gameStarted = false;
    private String tableId;
    private List<Player> players = new ArrayList<>();
    private List<Card> deck = new ArrayList<>();
    private List<Card> tableCards = new ArrayList<>();

    private int pot = 0;
    private int currentMaxBet = 0;
    private int lastRaiseAmount = 20; // По умолчанию равен размеру Big Blind
    private int currentPlayerIndex = 0;
    private int dealerIndex = -1;
    private GameStage currentStage = GameStage.PREFLOP;

    // Настройки блайндов
    private final int SB_AMOUNT = 10;
    private final int BB_AMOUNT = 20;

    public enum GameStage {
        PREFLOP, FLOP, TURN, RIVER, SHOWDOWN
    }

    public PokerTable(String tableId) {
        this.tableId = tableId;
    }

    // Вспомогательный метод для получения текущего игрока
    public Player getCurrentPlayer() {
        if (players.isEmpty()) return null;
        return players.get(currentPlayerIndex);
    }
}
