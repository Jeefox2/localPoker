package com.poker.server.controller;

import com.poker.server.dto.*;
import com.poker.server.model.Card;
import com.poker.server.model.Player;
import com.poker.server.model.PlayerAction;
import com.poker.server.model.PokerTable;
import com.poker.server.service.PokerGameService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.*;

@Controller
public class PokerWebSocketController {
    private final PokerGameService gameService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public PokerWebSocketController(PokerGameService gameService, SimpMessagingTemplate messagingTemplate) {
        this.gameService = gameService;
        this.messagingTemplate = messagingTemplate;
    }

    @PostConstruct
    public void init() {
        gameService.setOnAutoFoldCallback(() -> {
            Player currentPlayer = gameService.getTable().getCurrentPlayer();
            if (currentPlayer != null) {
                gameService.handlePlayerAction(
                        currentPlayer.getSessionId(),
                        PlayerAction.FOLD,
                        0
                );
                broadcastGameState("⏰ " + currentPlayer.getName() + " не успел сделать ход — авто-FOLD");
                sendPrivateCardsToAll();
            }
        });
    }

    @MessageMapping("/action")
    public void handleAction(@Payload PlayerActionRequest request, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = getSessionId(headerAccessor);
        Player player = gameService.getPlayerBySessionId(sessionId);
        if (player == null) return;

        PokerTable.GameStage stageBefore = gameService.getTable().getCurrentStage();
        String result = gameService.handlePlayerAction(sessionId, request.getAction(), request.getAmount());

        if (!result.equals("OK")) {
            messagingTemplate.convertAndSend(
                    "/topic/private-" + sessionId,
                    new ErrorMessage(result));
            return;
        }

        // Проверяем, есть ли результат вскрытия
        ShowdownUpdate showdown = gameService.consumePendingShowdown();
        if (showdown != null) {
            // Рассылаем состояние SHOWDOWN всем
            broadcastGameState("Вскрытие! " + showdown.getWinnerMessage());
            // Рассылаем вскрытие всем
            messagingTemplate.convertAndSend("/topic/showdown", showdown);

            // Через 5 секунд начинаем новый раунд
            scheduler.schedule(() -> {
                try {
                    gameService.startNewRound();
                    broadcastGameState("Новый раунд начался!");
                    sendPrivateCardsToAll();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, 5, TimeUnit.SECONDS);
            return;
        }

        broadcastGameState("Игрок " + player.getName() + " выполнил: " + request.getAction());

        PokerTable.GameStage stageAfter = gameService.getTable().getCurrentStage();
        if (stageBefore != stageAfter) {
            sendPrivateCardsToAll();
        }
    }

    @MessageMapping("/join")
    public void handleJoin(@Payload String playerName, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = getSessionId(headerAccessor);
        Player player = gameService.addPlayer(sessionId, playerName);

        PokerTable table = gameService.getTable();

        long playersWithChips = table.getPlayers().stream()
                .filter(p -> p.getBalance() > 0)
                .count();

        if (playersWithChips >= 3 && !table.isGameStarted()) {
            gameService.startNewRound();
            table.setGameStarted(true);
            broadcastGameState("Игра началась!");
            sendPrivateCardsToAll();
        } else if (playersWithChips < 3) {
            broadcastGameState("Игрок " + playerName + " подключился. Ожидаем остальных...");
        } else {
            broadcastGameState("Игрок " + playerName + " подключился.");
        }
    }

    private void broadcastGameState(String logMessage) {
        PokerTable table = gameService.getTable();

        // УБРАЛИ вычисление комбинаций — они приватные!
        List<GameStateUpdate.PlayerInfo> playersInfo = table.getPlayers().stream()
                .map(p -> new GameStateUpdate.PlayerInfo(
                        p.getSessionId(),
                        p.getName(),
                        p.getBalance(),
                        p.getBet(),
                        p.isActive(),
                        p.isConnected()))
                .toList();

        List<Map<String, String>> tableCards = table.getTableCards().stream()
                .map(this::cardToMap)
                .toList();

        String currentPlayerId = table.getCurrentPlayer() != null
                ? table.getCurrentPlayer().getSessionId() : null;

        GameStateUpdate update = new GameStateUpdate(
                table.getPot(),
                table.getCurrentMaxBet(),
                tableCards,
                playersInfo,
                currentPlayerId,
                table.getCurrentStage() != null ? table.getCurrentStage().name() : "WAITING",
                logMessage,
                table.getTurnStartTime()
        );

        messagingTemplate.convertAndSend("/topic/game", update);
    }

    private void sendPrivateCardsToAll() {
        PokerTable table = gameService.getTable();
        for (Player player : table.getPlayers()) {
            List<Map<String, String>> cards = player.getHand().stream()
                    .map(this::cardToMap)
                    .toList();

            // НОВОЕ: вычисляем комбинацию для этого игрока
            String handName = null;
            if (!player.getHand().isEmpty()) {
                handName = gameService.evaluateHandName(
                        player.getHand(),
                        table.getTableCards()
                );
            }

            messagingTemplate.convertAndSend(
                    "/topic/private-" + player.getSessionId(),
                    new PlayerHandUpdate(cards, handName));
        }
    }

    private Map<String, String> cardToMap(Card card) {
        Map<String, String> map = new HashMap<>();
        map.put("rank", card.getRank());
        map.put("suit", card.getSuit());
        return map;
    }

    private String getSessionId(SimpMessageHeaderAccessor headerAccessor) {
        return Objects.requireNonNull(headerAccessor.getSessionId());
    }
}