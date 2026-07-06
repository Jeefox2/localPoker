package com.poker.server.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class PokerTable {
    private String id;
    private List<Player> players = new ArrayList<>();
    private List<Card> deck = new ArrayList<>();
    private List<Card> tableCards = new ArrayList<>();
    private int pot = 0;
    private int currentMaxBet = 0;
    private int dealerIndex = 0;
    private int currentPlayerIndex = 0;
    private GameStage currentStage = GameStage.WAITING;
    private boolean gameStarted = false;

    // Время начала текущего хода (для таймера)
    private long turnStartTime = 0;

    // Лимит времени на ход (30 секунд)
    public static final int TURN_TIME_LIMIT_MS = 30_000;

    public final int SB_AMOUNT = 10;
    public final int BB_AMOUNT = 20;

    public enum GameStage {
        WAITING, PREFLOP, FLOP, TURN, RIVER, SHOWDOWN
    }

    public Player getCurrentPlayer() {
        if (players.isEmpty()) return null;
        return players.get(currentPlayerIndex);
    }
}