package com.poker.server.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class Player {
    private String sessionId; // Уникальный ID сессии WebSocket
    private String name;
    private int balance = 1000;
    private int bet = 0;
    private boolean active = true;      // Участвует в текущей раздаче (не сбросил)
    private boolean hasActed = false;   // Делал ли ход в текущем круге торгов
    private List<Card> hand = new ArrayList<>();
    private boolean connected = true;   // Физически подключен к серверу

    public Player(String sessionId, String name) {
        this.sessionId = sessionId;
        this.name = name;
    }

    public void clearHand() {
        this.hand.clear();
    }

    public void addCard(Card card) {
        this.hand.add(card);
    }
}
