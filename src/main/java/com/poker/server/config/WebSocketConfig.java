package com.poker.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker // Включаем STOMP поверх WebSocket
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Простой in-memory брокер для топиков и очередей
        config.enableSimpleBroker("/topic", "/queue");
        // Префикс для сообщений, которые идут ОТ клиента К серверу
        config.setApplicationDestinationPrefixes("/app");
        // Префикс для личных сообщений (привязка к сессии пользователя)
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Точка подключения для клиентов.
        // React будет подключаться к ws://localhost:8080/ws
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*") // Разрешаем подключения откуда угодно (для разработки)
                .withSockJS(); // Fallback для старых браузеров
    }
}