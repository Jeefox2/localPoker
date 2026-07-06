package com.poker.server.service;

import com.poker.server.dto.ShowdownUpdate;
import com.poker.server.model.Card;
import com.poker.server.model.Player;
import com.poker.server.model.PlayerAction;
import com.poker.server.model.PokerTable;
import com.poker.server.model.PokerTable.GameStage;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

@Service
public class PokerGameService {
    private final HandEvaluatorService handEvaluator;
    private final PokerTable table = new PokerTable();

    // Showdown
    private ShowdownUpdate pendingShowdown = null;

    // Таймер хода
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> turnTimerTask = null;
    private Runnable onAutoFoldCallback = null;

    public PokerGameService(HandEvaluatorService handEvaluator) {
        this.handEvaluator = handEvaluator;
    }

    public void setOnAutoFoldCallback(Runnable callback) {
        this.onAutoFoldCallback = callback;
    }

    // ========== УПРАВЛЕНИЕ ИГРОКАМИ ==========
    public synchronized Player addPlayer(String sessionId, String name) {
        Player player = new Player(sessionId, name);
        table.getPlayers().add(player);
        return player;
    }

    // НОВОЕ: публичный метод для получения названия комбинации
    public String evaluateHandName(List<Card> hand, List<Card> tableCards) {
        if (hand == null || hand.isEmpty()) {
            return null;
        }
        HandEvaluatorService.HandResult result = handEvaluator.evaluate(hand, tableCards);
        return result.getName();
    }

    public Player getPlayerBySessionId(String sessionId) {
        return table.getPlayers().stream()
                .filter(p -> p.getSessionId().equals(sessionId))
                .findFirst()
                .orElse(null);
    }

    public PokerTable getTable() {
        return table;
    }

    public synchronized ShowdownUpdate consumePendingShowdown() {
        ShowdownUpdate update = pendingShowdown;
        pendingShowdown = null;
        return update;
    }

    // ========== РАЗДАЧА И РАУНДЫ ==========
    public synchronized void startNewRound() {
        cancelTurnTimer(); // Отменяем предыдущий таймер

        table.getTableCards().clear();
        table.setPot(0);
        table.setCurrentMaxBet(0);
        table.setCurrentStage(GameStage.PREFLOP);

        long playersWithChips = table.getPlayers().stream()
                .filter(p -> p.getBalance() > 0)
                .count();

        if (playersWithChips < 2) {
            return;
        }

        int newDealerIndex = getNextPlayerWithChips(table.getDealerIndex());
        table.setDealerIndex(newDealerIndex);

        createDeck();

        for (Player p : table.getPlayers()) {
            if (p.getBalance() > 0) {
                p.setActive(true);
                p.setBet(0);
                p.setHasActed(false);
                p.clearHand();
            } else {
                p.setActive(false);
                p.setBet(0);
                p.setHasActed(false);
                p.clearHand();
            }
        }

        dealCards();
        postBlinds();

        int bbIndex = getNextPlayerWithChips(table.getDealerIndex());
        bbIndex = getNextPlayerWithChips(bbIndex);
        table.setCurrentPlayerIndex(getNextPlayerWithChips(bbIndex));

        // Запускаем таймер для первого игрока
        startTurnTimer();
    }

    private int getNextPlayerWithChips(int currentIndex) {
        int size = table.getPlayers().size();
        for (int i = 1; i <= size; i++) {
            int nextIndex = (currentIndex + i) % size;
            if (table.getPlayers().get(nextIndex).getBalance() > 0) {
                return nextIndex;
            }
        }
        return currentIndex;
    }

    private void createDeck() {
        table.getDeck().clear();
        String[] suits = {"Червы", "Бубны", "Трефы", "Пики"};
        String[] ranks = {"2", "3", "4", "5", "6", "7", "8", "9", "10", "Валет", "Дама", "Король", "Туз"};
        for (String s : suits) {
            for (String r : ranks) {
                table.getDeck().add(new Card(s, r));
            }
        }
        Collections.shuffle(table.getDeck());
    }

    private void dealCards() {
        for (Player player : table.getPlayers()) {
            if (player.getBalance() > 0) {
                player.clearHand();
                Card c1 = drawCard();
                Card c2 = drawCard();
                player.addCard(c1);
                player.addCard(c2);
            }
        }
    }

    private Card drawCard() {
        if (table.getDeck().isEmpty()) {
            createDeck();
        }
        return table.getDeck().removeFirst();
    }

    private void postBlinds() {
        int sbIndex = getNextPlayerWithChips(table.getDealerIndex());
        int bbIndex = getNextPlayerWithChips(sbIndex);

        Player sbPlayer = table.getPlayers().get(sbIndex);
        Player bbPlayer = table.getPlayers().get(bbIndex);

        int sbAmount = Math.min(table.getSB_AMOUNT(), sbPlayer.getBalance());
        sbPlayer.setBalance(sbPlayer.getBalance() - sbAmount);
        sbPlayer.setBet(sbAmount);
        contributeToPot(sbAmount);

        int bbAmount = Math.min(table.getBB_AMOUNT(), bbPlayer.getBalance());
        bbPlayer.setBalance(bbPlayer.getBalance() - bbAmount);
        bbPlayer.setBet(bbAmount);
        contributeToPot(bbAmount);

        updateMaxBet(bbAmount, bbPlayer);
    }

    private void contributeToPot(int amount) {
        table.setPot(table.getPot() + amount);
    }

    private void updateMaxBet(int totalBet, Player raiser) {
        table.setCurrentMaxBet(totalBet);
        for (Player p : table.getPlayers()) {
            if (p != raiser) {
                p.setHasActed(false);
            }
        }
    }

    // ========== ОБРАБОТКА ДЕЙСТВИЙ ИГРОКОВ ==========
    public synchronized String handlePlayerAction(String sessionId, PlayerAction action, int amount) {
        Player player = getPlayerBySessionId(sessionId);
        if (player == null) return "Игрок не найден";

        if (player.getBalance() <= 0 && action != PlayerAction.FOLD) {
            return "У вас нет фишек для игры";
        }

        if (table.getCurrentPlayer() != player) {
            return "Сейчас не ваш ход";
        }

        String validationError = validateAction(player, action, amount);
        if (validationError != null) {
            return validationError;
        }

        // Отменяем таймер перед обработкой действия
        cancelTurnTimer();

        player.setHasActed(true);
        switch (action) {
            case FOLD -> {
                player.setActive(false);
                player.clearHand();
            }
            case CHECK -> {}
            case CALL -> {
                int needToPay = table.getCurrentMaxBet() - player.getBet();
                if (needToPay > player.getBalance()) needToPay = player.getBalance();
                player.setBalance(player.getBalance() - needToPay);
                player.setBet(player.getBet() + needToPay);
                contributeToPot(needToPay);
            }
            case RAISE -> {
                int amountToSubtract = amount - player.getBet();
                player.setBalance(player.getBalance() - amountToSubtract);
                player.setBet(amount);
                contributeToPot(amountToSubtract);
                updateMaxBet(amount, player);
            }
            case ALL_IN -> {
                int allInAmount = player.getBalance();
                contributeToPot(allInAmount);
                int newTotalBet = player.getBet() + allInAmount;
                player.setBet(newTotalBet);
                player.setBalance(0);
                if (newTotalBet > table.getCurrentMaxBet()) {
                    updateMaxBet(newTotalBet, player);
                } else {
                    player.setHasActed(true);
                }
            }
        }

        nextTurn();
        return "OK";
    }

    private String validateAction(Player player, PlayerAction action, int amount) {
        int currentMaxBet = table.getCurrentMaxBet();
        if (action == PlayerAction.RAISE) {
            int minRequiredBet = (currentMaxBet == 0) ? 20 : (currentMaxBet * 2);
            if (amount < minRequiredBet) {
                return "Минимальная ставка: " + minRequiredBet;
            }
            int amountToSubtract = amount - player.getBet();
            if (amountToSubtract > player.getBalance()) {
                return "Недостаточно фишек";
            }
        }
        if (action == PlayerAction.CHECK) {
            if (currentMaxBet > player.getBet()) {
                return "Нельзя сделать CHECK, когда есть ставка";
            }
        }
        return null;
    }

    // ========== ЛОГИКА ХОДОВ ==========
    private void nextTurn() {
        // 1. Считаем ВСЕХ активных игроков (включая All-In)
        long activeCount = table.getPlayers().stream()
                .filter(Player::isActive)
                .count();

        // 2. Если остался ОДИН активный — все остальные сбросили, он забирает банк
        if (activeCount == 1) {
            Player winner = table.getPlayers().stream()
                    .filter(Player::isActive)
                    .findFirst()
                    .orElse(null);
            if (winner != null) {
                winner.setBalance(winner.getBalance() + table.getPot());
                table.setPot(0);
            }
            startNewRound();
            return;
        }

        // 3. Считаем активных игроков С ФИШКАМИ
        long activeWithChipsCount = table.getPlayers().stream()
                .filter(p -> p.isActive() && p.getBalance() > 0)
                .count();

        // 4. Если все активные в All-In — досдаём карты до SHOWDOWN
        if (activeWithChipsCount <= 1) {
            while (table.getCurrentStage() != GameStage.SHOWDOWN) {
                advanceRound();
            }
            // НЕ запускаем таймер — после SHOWDOWN контроллер сам всё обработает
            return;
        }

        // 5. Если текущий игрок не имеет фишек, ищем следующего
        if (table.getCurrentPlayer() != null && table.getCurrentPlayer().getBalance() == 0) {
            int nextIndex = getNextPlayerWithChips(table.getCurrentPlayerIndex());
            table.setCurrentPlayerIndex(nextIndex);
        }

        // 6. Если круг торгов завершен — переходим к следующей улице
        if (isBettingRoundFinished()) {
            advanceRound();
            table.setCurrentPlayerIndex(getFirstActivePlayerWithChipsIndex());
        } else {
            int nextIndex = getNextPlayerWithChips(table.getCurrentPlayerIndex());
            table.setCurrentPlayerIndex(nextIndex);
        }

        // 7. Запускаем таймер для следующего игрока
        startTurnTimer();
    }

    private boolean isBettingRoundFinished() {
        for (Player p : table.getPlayers()) {
            if (p.isActive() && p.getBalance() > 0) {
                if (p.getBet() != table.getCurrentMaxBet() || !p.isHasActed()) {
                    return false;
                }
            }
        }
        return true;
    }

    private int getFirstActivePlayerWithChipsIndex() {
        for (int i = 0; i < table.getPlayers().size(); i++) {
            Player p = table.getPlayers().get(i);
            if (p.isActive() && p.getBalance() > 0) {
                return i;
            }
        }
        return -1;
    }

    private void advanceRound() {
        if (table.getCurrentStage() == GameStage.SHOWDOWN) {
            return;
        }

        for (Player p : table.getPlayers()) {
            p.setBet(0);
            p.setHasActed(false);
        }
        table.setCurrentMaxBet(0);

        switch (table.getCurrentStage()) {
            case PREFLOP -> {
                dealCommunityCards(3);
                table.setCurrentStage(GameStage.FLOP);
            }
            case FLOP -> {
                dealCommunityCards(1);
                table.setCurrentStage(GameStage.TURN);
            }
            case TURN -> {
                dealCommunityCards(1);
                table.setCurrentStage(GameStage.RIVER);
            }
            case RIVER -> {
                table.setCurrentStage(GameStage.SHOWDOWN);
                determineWinner();
            }
        }
    }

    private void dealCommunityCards(int count) {
        for (int i = 0; i < count; i++) {
            table.getTableCards().add(drawCard());
        }
    }

    // ========== ВСКРЫТИЕ ==========
    private void determineWinner() {
        List<Player> winners = new ArrayList<>();
        HandEvaluatorService.HandResult bestHand = null;
        Map<String, String> playerHands = new HashMap<>();

        for (Player player : table.getPlayers()) {
            if (!player.isActive()) continue;
            HandEvaluatorService.HandResult result = handEvaluator.evaluate(player.getHand(), table.getTableCards());
            playerHands.put(player.getName(), result.getName());

            if (bestHand == null) {
                bestHand = result;
                winners.add(player);
            } else {
                int comparison = result.compareTo(bestHand);
                if (comparison > 0) {
                    bestHand = result;
                    winners.clear();
                    winners.add(player);
                } else if (comparison == 0) {
                    winners.add(player);
                }
            }
        }

        // Карты всех активных игроков
        Map<String, List<Map<String, String>>> playersCards = new HashMap<>();
        for (Player player : table.getPlayers()) {
            if (!player.isActive()) continue;
            List<Map<String, String>> cards = player.getHand().stream()
                    .map(this::cardToMap)
                    .toList();
            playersCards.put(player.getName(), cards);
        }

        List<String> winnerNames = winners.stream()
                .map(Player::getName)
                .toList();

        String winnerMessage;
        if (winners.size() == 1) {
            Player winner = winners.get(0);
            winnerMessage = String.format("🏆 Победил %s с комбинацией %s!",
                    winner.getName(), bestHand.getName());
        } else {
            String winnerNamesStr = String.join(", ", winnerNames);
            winnerMessage = String.format("🤝 Ничья между: %s! Банк разделён. Комбинация: %s",
                    winnerNamesStr, bestHand.getName());
        }

        // Сохраняем результат вскрытия
        pendingShowdown = new ShowdownUpdate(
                playersCards,
                playerHands,
                table.getTableCards().stream().map(this::cardToMap).toList(),
                winnerNames,
                winnerMessage
        );

        // Раздаём банк
        if (!winners.isEmpty()) {
            int share = table.getPot() / winners.size();
            int remainder = table.getPot() % winners.size();
            for (int i = 0; i < winners.size(); i++) {
                int finalShare = (i == 0) ? (share + remainder) : share;
                Player winner = winners.get(i);
                winner.setBalance(winner.getBalance() + finalShare);
            }
            table.setPot(0);
        }

        // ВАЖНО: НЕ вызываем startNewRound() здесь!
        // Это сделает контроллер после рассылки ShowdownUpdate и паузы 5 секунд
    }

    // ========== ТАЙМЕР ХОДА ==========
    private void startTurnTimer() {
        cancelTurnTimer();
        table.setTurnStartTime(System.currentTimeMillis());

        turnTimerTask = scheduler.schedule(() -> {
            try {
                autoFoldCurrentPlayer();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, PokerTable.TURN_TIME_LIMIT_MS, TimeUnit.MILLISECONDS);
    }

    private void cancelTurnTimer() {
        if (turnTimerTask != null && !turnTimerTask.isDone()) {
            turnTimerTask.cancel(false);
            turnTimerTask = null;
        }
    }

    private void autoFoldCurrentPlayer() {
        Player currentPlayer = table.getCurrentPlayer();
        if (currentPlayer != null && currentPlayer.isActive() && currentPlayer.getBalance() > 0) {
            if (onAutoFoldCallback != null) {
                onAutoFoldCallback.run();
            }
        }
    }

    private Map<String, String> cardToMap(Card card) {
        Map<String, String> map = new HashMap<>();
        map.put("rank", card.getRank());
        map.put("suit", card.getSuit());
        return map;
    }
}