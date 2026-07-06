package com.poker.server.service;

import com.poker.server.model.Card;
import org.springframework.stereotype.Service;
import java.util.*;

@Service // Эта аннотация говорит Spring: "Создай объект этого класса и управляй им"
public class HandEvaluatorService {

    public enum HandRank {
        HIGH_CARD(0), PAIR(1), TWO_PAIRS(2), SET(3), STRAIGHT(4),
        FLUSH(5), FULL_HOUSE(6), QUAD(7), STRAIGHT_FLUSH(8);

        public final int priority;
        HandRank(int priority) { this.priority = priority; }
    }

    public static class HandResult implements Comparable<HandResult> {
        private final String name;
        private final HandRank rank;
        private final List<Integer> tieBreakers;

        public HandResult(String name, HandRank rank, List<Integer> tieBreakers) {
            this.name = name;
            this.rank = rank;
            this.tieBreakers = tieBreakers;
        }

        public String getName() { return name; }
        public HandRank getRank() { return rank; }

        @Override
        public int compareTo(HandResult o) {
            if (this.rank != o.rank) {
                return Integer.compare(this.rank.priority, o.rank.priority);
            }
            for (int i = 0; i < Math.min(this.tieBreakers.size(), o.tieBreakers.size()); i++) {
                int compare = Integer.compare(this.tieBreakers.get(i), o.tieBreakers.get(i));
                if (compare != 0) return compare;
            }
            return 0;
        }

        @Override
        public String toString() {
            return name + " (параметры сравнения: " + tieBreakers + ")";
        }
    }

    // Убрали static! Теперь это метод экземпляра сервиса
    public HandResult evaluate(List<Card> hand, List<Card> table) {
        List<Card> allCards = new ArrayList<>(hand);
        allCards.addAll(table);
        allCards.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        HandResult straightFlush = checkStraightFlush(allCards);
        if (straightFlush != null) return straightFlush;

        Map<Integer, Integer> counts = new HashMap<>();
        for (Card c : allCards) {
            counts.put(c.getValue(), counts.getOrDefault(c.getValue(), 0) + 1);
        }

        if (counts.containsValue(4)) {
            int quadRank = findRankByCount(counts, 4);
            int kicker = allCards.stream().map(Card::getValue).filter(v -> v != quadRank).max(Integer::compare).orElse(0);
            return new HandResult("Каре", HandRank.QUAD, List.of(quadRank, kicker));
        }

        if (isFullHouse(counts)) {
            int setRank = findRankByCount(counts, 3);
            int pairRank = counts.entrySet().stream()
                    .filter(e -> e.getValue() >= 2 && e.getKey() != setRank)
                    .map(Map.Entry::getKey)
                    .max(Integer::compare).orElse(0);
            return new HandResult("Фулл-Хаус", HandRank.FULL_HOUSE, List.of(setRank, pairRank));
        }

        String flushSuit = getFlushSuit(allCards);
        if (flushSuit != null) {
            List<Integer> flushCards = allCards.stream()
                    .filter(c -> c.suit().equals(flushSuit))
                    .map(Card::getValue)
                    .limit(5)
                    .toList();
            return new HandResult("Флеш", HandRank.FLUSH, flushCards);
        }

        int straightHigh = getStraightHighCard(allCards);
        if (straightHigh > 0) {
            return new HandResult("Стрит", HandRank.STRAIGHT, List.of(straightHigh));
        }

        if (counts.containsValue(3)) {
            int setRank = findRankByCount(counts, 3);
            List<Integer> kickers = allCards.stream()
                    .map(Card::getValue)
                    .filter(v -> v != setRank)
                    .limit(2)
                    .toList();
            List<Integer> tieBreakers = new ArrayList<>();
            tieBreakers.add(setRank);
            tieBreakers.addAll(kickers);
            return new HandResult("Сет", HandRank.SET, tieBreakers);
        }

        if (isTwoPairs(counts)) {
            List<Integer> pairs = counts.entrySet().stream()
                    .filter(e -> e.getValue() == 2)
                    .map(Map.Entry::getKey)
                    .sorted(Comparator.reverseOrder())
                    .toList();
            int highPair = pairs.get(0);
            int lowPair = pairs.get(1);
            int kicker = allCards.stream()
                    .map(Card::getValue)
                    .filter(v -> v != highPair && v != lowPair)
                    .max(Integer::compare).orElse(0);
            return new HandResult("Две пары", HandRank.TWO_PAIRS, List.of(highPair, lowPair, kicker));
        }

        if (counts.containsValue(2)) {
            int pairRank = findRankByCount(counts, 2);
            List<Integer> kickers = allCards.stream()
                    .map(Card::getValue)
                    .filter(v -> v != pairRank)
                    .limit(3)
                    .toList();
            List<Integer> tieBreakers = new ArrayList<>();
            tieBreakers.add(pairRank);
            tieBreakers.addAll(kickers);
            return new HandResult("Пара", HandRank.PAIR, tieBreakers);
        }

        List<Integer> top5 = allCards.stream().map(Card::getValue).limit(5).toList();
        return new HandResult("Старшая карта", HandRank.HIGH_CARD, top5);
    }

    // Все вспомогательные методы тоже теряют static
    private boolean isFullHouse(Map<Integer, Integer> counts) {
        boolean hasThree = counts.containsValue(3);
        long setsCount = counts.values().stream().filter(v -> v == 3).count();
        boolean hasTwo = counts.containsValue(2);
        return (hasThree && hasTwo) || (setsCount >= 2);
    }

    private boolean isTwoPairs(Map<Integer, Integer> counts) {
        return counts.values().stream().filter(v -> v == 2).count() >= 2;
    }

    private String getFlushSuit(List<Card> allCards) {
        Map<String, Integer> suitCounts = new HashMap<>();
        for (Card c : allCards) {
            suitCounts.put(c.suit(), suitCounts.getOrDefault(c.suit(), 0) + 1);
        }
        for (Map.Entry<String, Integer> entry : suitCounts.entrySet()) {
            if (entry.getValue() >= 5) return entry.getKey();
        }
        return null;
    }

    private HandResult checkStraightFlush(List<Card> allCards) {
        String flushSuit = getFlushSuit(allCards);
        if (flushSuit == null) return null;

        List<Card> suitCards = allCards.stream()
                .filter(c -> c.suit().equals(flushSuit))
                .toList();

        int straightHigh = getStraightHighCard(suitCards);
        if (straightHigh > 0) {
            String name = (straightHigh == 14) ? "Роял-Флеш" : "Стрит-Флеш";
            return new HandResult(name, HandRank.STRAIGHT_FLUSH, List.of(straightHigh));
        }
        return null;
    }

    private int getStraightHighCard(List<Card> allCards) {
        List<Integer> values = allCards.stream()
                .map(Card::getValue)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();

        for (int i = 0; i <= values.size() - 5; i++) {
            if (values.get(i) - values.get(i + 4) == 4) {
                return values.get(i);
            }
        }
        if (new HashSet<>(values).containsAll(List.of(14, 2, 3, 4, 5))) {
            return 5;
        }
        return 0;
    }

    private int findRankByCount(Map<Integer, Integer> counts, int count) {
        return counts.entrySet().stream()
                .filter(e -> e.getValue() == count)
                .map(Map.Entry::getKey)
                .max(Integer::compare)
                .orElse(0);
    }
}