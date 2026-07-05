package com.poker.server.model;

import java.util.List;

public record Card(String suit, String rank) {

    private static final List<String> RANK_ORDER = List.of(
            "2", "3", "4", "5", "6", "7", "8", "9", "10", "Валет", "Дама", "Король", "Туз"
    );

    @Override
    public String toString() {
        return rank + " of " + suit;
    }

    public int getValue() {
        return RANK_ORDER.indexOf(rank) + 2;
    }

    // Для record геттеры создаются автоматически (rank() и suit()),
    // но если тебе удобнее getRank(), оставим их для совместимости с твоим кодом
    public String getRank() { return rank; }
    public String getSuit() { return suit; }
}
