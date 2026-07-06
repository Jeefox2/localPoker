# localPoker — Poker server (Spring Boot + STOMP/WebSocket)

Коротко
- Небольшой встраиваемый poker server на Spring Boot с поддержкой STOMP-over-WebSocket (SockJS fallback).
- Сервер хранит состояние стола в памяти (`PokerTable`) и использует один инстанс `PokerGameService` как «единый источник правды».

Быстрый старт (Windows PowerShell)
- Сборка (без тестов):
```powershell
mvn -DskipTests package
```
- Запуск в dev режиме:
```powershell
mvn spring-boot:run
```
- Пакет и запуск jar:
```powershell
mvn package; java -jar target\\*.jar
```
- Запуск тестов:
```powershell
mvn test
```

Куда смотреть
- Точка входа: `src/main/java/com/poker/server/PokerApplication.java`
- WebSocket/STOMP конфигурация: `src/main/java/com/poker/server/config/WebSocketConfig.java`
- Контроллер STOMP сообщений: `src/main/java/com/poker/server/controller/PokerWebSocketController.java`
- Логика игры: `src/main/java/com/poker/server/service/PokerGameService.java`
- Рейтинг рук: `src/main/java/com/poker/server/service/HandEvaluatorService.java`
- DTO: `src/main/java/com/poker/server/dto`
- Модели: `src/main/java/com/poker/server/model`

WebSocket / STOMP контракт
- Endpoint: `ws://<host>:<port>/ws` (SockJS fallback настроен).
- Клиент -> сервер:
  - Join: отправить имя на `/app/join` (payload: строка — имя).
  - Action: отправлять JSON `PlayerActionRequest` на `/app/action`.
- Сервер -> клиент:
  - Публичный: `/topic/game` (GameStateUpdate) — содержит: pot, currentMaxBet, tableCards (публичные), players[], currentPlayerSessionId, currentStage, message, turnStartTime.
  - Приватный: `/topic/private-{sessionId}` (PlayerHandUpdate) — содержит приватные карты и поле `handName` (название комбинации для UI).
  - Showdown: `/topic/showdown` (ShowdownUpdate) — рассылается при вскрытии; включает карты всех участников, названия комбинаций, имена победителей и сообщение о победе.

Особенности реализации (важное для внесения изменений)
- Вся логика изменения состояния таблицы реализована в `PokerGameService` и синхронизирована (`synchronized` методы). Изменения состояния должны идти только через этот сервис.
- Таймер хода: каждый ход ограничен 30 сек (константа `PokerTable.TURN_TIME_LIMIT_MS`), при истечении — авто-FOLD через callback. Поле `turnStartTime` передаётся в `GameStateUpdate`.
- Showdown: результат создаётся в `PokerGameService` и сохраняется в `pendingShowdown`; контроллер рассылает `ShowdownUpdate` всем и через 5 сек запускает новый раунд.
- `Card` реализован как `record` (immutable). Не добавляйте новые поля в `Card` — используйте обёртки при необходимости.
- Проект использует Lombok (`@Data`, `@AllArgsConstructor`) — убедитесь, что обработчик аннотаций включен в IDE.

Тестовый STOMP-клиент
- Файл `src/main/resources/static/test-client.html` содержит простой статический STOMP+SockJS клиент для локального тестирования. Откройте `http://localhost:8080/test-client.html` после запуска сервера.

Конфигурация и сборка
- Java 21, Spring Boot 4.1.0 (см. `pom.xml`).
- Lombok подключён как аннотация-процессор в `pom.xml`.

Git и CI
- Перед пушем убедитесь, что в `.gitignore` указаны `target/` и другие артефакты (в проекте уже есть `.gitignore`).
- Рекомендую запускать `mvn test` перед push в CI.

Как помочь / contributing
- Для добавления нового типа ставки: обновите `model/PlayerAction` (enum), DTO `PlayerActionRequest`, `PokerWebSocketController.handleAction()` и `PokerGameService.handlePlayerAction()` (валидация и логика). Запустите тесты и WebSocket клиента.

Контакты
- Репозиторий: https://github.com/Jeefox2/localPoker.git

License
- Нет явной лицензии в репозитории — добавьте `LICENSE` если требуется открытая лицензия.
