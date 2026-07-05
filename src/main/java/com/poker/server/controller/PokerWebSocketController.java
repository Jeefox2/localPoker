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

import java.util.*;

@Controller
public class PokerWebSocketController {
    private final PokerGameService gameService;
    private final SimpMessagingTemplate messagingTemplate;

    public PokerWebSocketController(PokerGameService gameService, SimpMessagingTemplate messagingTemplate) {
        this.gameService = gameService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/action")
    public void handleAction(@Payload PlayerActionRequest request, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = getSessionId(headerAccessor);
        Player player = gameService.getPlayerBySessionId(sessionId);
        if (player == null) return;

        // Запоминаем текущую стадию до действия
        PokerTable.GameStage stageBefore = gameService.getTable().getCurrentStage();

        // Вызываем движок
        String result = gameService.handlePlayerAction(sessionId, request.getAction(), request.getAmount());

        if (!result.equals("OK")) {
            // Ошибка — шлем только этому игроку на его личный топик
            messagingTemplate.convertAndSend(
                    "/topic/private-" + sessionId,
                    new ErrorMessage(result));
            return;
        }

        // Успех — рассылаем обновленное состояние всем
        broadcastGameState("Игрок " + player.getName() + " выполнил: " + request.getAction());

        // Если перешли к новой улице — рассылаем новые карты
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

        // Считаем игроков с фишками
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

        List<GameStateUpdate.PlayerInfo> playersInfo = table.getPlayers().stream()
                .map(p -> new GameStateUpdate.PlayerInfo(
                        p.getSessionId(), p.getName(), p.getBalance(),
                        p.getBet(), p.isActive(), p.isConnected()))
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
                logMessage
        );

        messagingTemplate.convertAndSend("/topic/game", update);
    }

    private void sendPrivateCardsToAll() {
        PokerTable table = gameService.getTable();
        for (Player player : table.getPlayers()) {
            List<Map<String, String>> cards = player.getHand().stream()
                    .map(this::cardToMap)
                    .toList();
            // Шлем на персональный топик этого игрока
            messagingTemplate.convertAndSend(
                    "/topic/private-" + player.getSessionId(),
                    new PlayerHandUpdate(cards));
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