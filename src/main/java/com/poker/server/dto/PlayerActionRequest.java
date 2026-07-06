package com.poker.server.dto;

import com.poker.server.model.PlayerAction;
import lombok.Data;

@Data
public class PlayerActionRequest {
    private PlayerAction action; // FOLD, CALL, RAISE, CHECK, ALL_IN
    private int amount;          // Нужно только для RAISE
}