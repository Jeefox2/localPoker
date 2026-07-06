# AGENTS.md — Быстрая справка для AI-агентов

Цель
- Кратко: помочь автоматическим кодовым агентам быстро понять архитектуру, точки расширения и практические команды для работы с этим проектом (Spring Boot + STOMP/WebSocket poker server).

Короткий план (чеклист для агента)
- Прочитать `src/main/java/com/poker/server/config/WebSocketConfig.java` и `controller/PokerWebSocketController.java` — это контракт сообщений.
- Понять модель данных: `model/Player.java`, `model/PokerTable.java`, `model/Card.java`.
- Логика игры и синхронизация: `service/PokerGameService.java`.
- Рейтинг рук: `service/HandEvaluatorService.java`.
- Для сборки/запуска: `pom.xml` (Java 21, spring-boot-maven-plugin).

Quickstart — команды (Windows PowerShell)
- Сборка: mvn -DskipTests package
- Запуск в dev: mvn spring-boot:run
- Упаковать и запустить jar: mvn package; java -jar target\*.jar
- Запуск тестов: mvn test

Ключевые зависимости
- **Spring Boot 4.1.0** — основной фреймворк (webmvc, websocket, test).
- **Java 21** — минимальная версия языка (поддержка records, виртуальных потоков).
- **Project Lombok** — генерирует getter'ы, setter'ы, конструкторы через аннотации (`@Data`, `@AllArgsConstructor`). Убедитесь, что обработчик аннотаций активен в IDE.
- **SockJS** — fallback для WebSocket для старых браузеров (конфигурируется в `WebSocketConfig.registerStompEndpoints()`).
- **Simple Message Broker** — встроенный in-memory STOMP брокер для топиков и очередей.

Конфигурация игры
- **Слепые ставки**: SB = 10, BB = 20 (жестко установлены в `model/PokerTable.java` как константы; измените поля `SB_AMOUNT` и `BB_AMOUNT` для новых значений).
- **Стартовый баланс игрока**: 1000 фишек (инициализирован в `model/Player.java` как `balance = 1000`).
- **Максимум игроков**: Ограничений нет (динамически растёт при подключении; игра начинается с 3+ игроков с фишками).
- **Раунд продолжается до**: завершения при наличии одного активного игрока (остальные сбросили) или достижении SHOWDOWN (если 2+ активных в наличии).

Большая картина (architecture)
- Веб-сервер: Spring Boot (REST minimal) + STOMP-over-WebSocket (SockJS) — конфигурация в `config/WebSocketConfig.java`.
- Входящие сообщения: клиенты отправляют на префикс `/app` (см. `@MessageMapping` в `controller/PokerWebSocketController.java`).
- Публикование: состояние игры отталкивается в топики — публичный `/topic/game` и приватные `/topic/private-{sessionId}` для приватных карт/ошибок, `/topic/showdown` для раскрытия карт при вскрытии.
- Состояние: один инстанс `PokerGameService` управляет «единой источником правды» — состояние хранится в `PokerTable` (in-memory). Логика игры синхронизирована внутрь сервиса.
- **Workflow вскрытия (showdown)**: 1) при определении победителя `PokerGameService.determineWinner()` создает `ShowdownUpdate` и сохраняет в `pendingShowdown`; 2) контроллер получает его через `consumePendingShowdown()` и отправляет на `/topic/showdown` всем; 3) через 5 секунд контроллер вызывает `startNewRound()` для начала следующего раунда.

Контракт сообщений (конкретика)
- Клиент -> сервер
  - Join: отправить имя игрока на `/app/join` (payload: строка — имя).
  - Action: отправлять `PlayerActionRequest` на `/app/action`.
    - DTO: `PlayerActionRequest { PlayerAction action; int amount; }` (см. `dto/PlayerActionRequest.java`).
- Сервер -> клиент
  - Публичный: `/topic/game` -> `GameStateUpdate` (pot, currentMaxBet, tableCards, players[], currentPlayerSessionId, currentStage, message, **turnStartTime**).
  - Приватный: `/topic/private-{sessionId}` -> `PlayerHandUpdate` (private cards, **handName** для названия комбинации) или `ErrorMessage`.
  - Showdown: `/topic/showdown` -> `ShowdownUpdate` (playersCards, playerHands, tableCards, winnerNames, winnerMessage) — отправляется после вскрытия, затем через 5 сек начинается новый раунд.

Файлы «когда что менять»
- Добавить/сменить действие игрока: `dto/PlayerActionRequest.java`, `model/PlayerAction.java`, `controller/PokerWebSocketController.java`, `service/PokerGameService.java`.
- Менять представление состояния: `dto/GameStateUpdate.java` и `controller/PokerWebSocketController.broadcastGameState()`.
- Логика распределения банка/победителей: `service/PokerGameService.determineWinner()` и `service/HandEvaluatorService.java`.

Проектные соглашения и ловушки
- **Синхронизация**: Вся игровая логика синхронизирована на уровне методов (используются `synchronized` методы в `PokerGameService`). Критические методы: `addPlayer()`, `startNewRound()`, `handlePlayerAction()`. Не вводите асинхронные вызовы, которые изменяют `PokerTable` вне `PokerGameService` без явной синхронизации.
- **Потокобезопасность**: Один инстанс `PokerTable` управляет состоянием для всех игроков. Все изменения состояния должны проходить через synchronized методы сервиса.
- **Lombok**: Проект использует Project Lombok — аннотации `@Data` генерируют getter'ы, setter'ы и конструктор. Убедитесь, что обработчик аннотаций Lombok включен в IDE.
- **Card как Java Record**: `Card` реализован как Java record (`public record Card(String suit, String rank)`) — это гарантирует неизменяемость и генерирует методы `suit()` и `rank()`. Для совместимости добавлены методы `getSuit()` и `getRank()`.
- **Размеры блайндов**: Жестко установлены в `model/PokerTable.java` как константы (`SB_AMOUNT = 10, BB_AMOUNT = 20`). Для изменения отредактируйте эти поля или сделайте их параметрами конструктора.
- **Таймер хода в PokerTable**: Поле `turnStartTime` содержит миллисекунды начала текущего хода (от System.currentTimeMillis()), константа `TURN_TIME_LIMIT_MS = 30_000` определяет лимит 30 секунд. При изменении лимита отредактируйте `TURN_TIME_LIMIT_MS`. Таймер управляется `PokerGameService` автоматически — не изменяйте его напрямую в `PokerTable`.
- **Стартовый баланс игрока**: По умолчанию 1000 фишек (см. `model/Player.java`). Для изменения модифицируйте инициализацию баланса или передавайте как параметр в конструктор.
- **Private topics**: формат имени — строка `"/topic/private-" + sessionId` (см. `PokerWebSocketController.sendPrivateCardsToAll`). Используйте sessionId как ключ — не clientId.
- **WebSocket endpoint**: `ws://<host>:<port>/ws` с SockJS и префиксами `/app`, `/topic`, `/user` (см. `WebSocketConfig`). Используется `setAllowedOriginPatterns("*")` для разработки.
- **Карты/масти**: в проекте используются строковые представления рангов/мастей на русском языке (Червы/Бубны/Трефы/Пики, 2-10/Валет/Дама/Король/Туз). Проверяйте парсинг/рендеринг при изменениях.
- **HandEvaluator**: реализован вручную с четкой иерархией рангов (см. enum `HandRank` в `HandEvaluatorService`). При изменении логики сравнения обновите метод `compareTo()` в `HandResult`. Метод `PokerGameService.evaluateHandName(hand, tableCards)` возвращает название комбинации (например, "Флеш") для отправки клиенту в `PlayerHandUpdate.handName`.
- **PlayerHandUpdate DTO**: Теперь включает `handName` — название комбинации игрока для отображения в UI рядом с картами. Вычисляется в `PokerWebSocketController.sendPrivateCardsToAll()` через вызов `gameService.evaluateHandName()`.
- **ShowdownUpdate DTO**: **Используется** — отправляется на `/topic/showdown` после вскрытия с полной информацией о картах всех игроков, комбинациях и победителях. Механизм: `PokerGameService` сохраняет результат в `pendingShowdown`, контроллер вызывает `consumePendingShowdown()` и рассылает всем клиентам.
- **Таймер хода**: каждый ход ограничен 30 секундами (`TURN_TIME_LIMIT_MS = 30_000`). При истечении времени игрок автоматически делает FOLD через `onAutoFoldCallback`. `GameStateUpdate` включает `turnStartTime` для отображения таймера на клиенте. Методы управления таймером: `startTurnTimer()`, `cancelTurnTimer()` — вызываются автоматически при смене хода.

Отладка и быстрые проверки
- Логи: запустить приложение и смотреть logback через консоль (Spring Boot выводит стартовые бинды и адрес `/ws`).
- Тестировать сокеты: использовать WebSocket/STOMP клиент (stompjs, wscat с stomp или Postman) — подключиться к `/ws` и отправлять на `/app/join` и `/app/action`.
- Для локальной отладки: поставьте точки входа в `PokerGameService` (startNewRound, handlePlayerAction, determineWinner, startTurnTimer).
- **Синхронизация и гонки**: Все изменения состояния должны происходить внутри synchronized методов. Если видите странные изменения баланса или состояния, проверьте, не вызываются ли асинхронные операции вне PokerGameService.
- **Отладка таймера**: Проверьте, что `turnStartTime` в `GameStateUpdate` отправляется корректно; для отключения таймера временно установите `TURN_TIME_LIMIT_MS = Long.MAX_VALUE`.
- **Отладка showdown**: Подпишитесь на `/topic/showdown` в клиенте; проверьте, что `ShowdownUpdate` содержит корректные имена игроков и комбинации через логирование в `PokerGameService.determineWinner()`.

Написание тестов
- **Фреймворк**: JUnit 5 (Jupiter) с Spring Boot Test + WebSocket Test.
- **Пример интеграционного теста**: `@SpringBootTest` + `TestRestTemplate` для REST или `WebSocketStompClient` для WebSocket/STOMP.
- **Unit-тесты сервиса**: Можно тестировать `PokerGameService` и `HandEvaluatorService` напрямую без WebSocket контекста.
- **Тестирование обработчиков**: Используйте Spring's `SimpMessageHeaderAccessor` и `SimpMessagingTemplate` mock'и для тестирования `PokerWebSocketController` методов.
- **Тестирование таймера**: Можно использовать `@EnableSchedulerLock` или мок scheduler для проверки вызова `autoFoldCurrentPlayer()` через 30 сек; используйте `Thread.sleep()` или TestScheduler из VirtualTime если доступен.
- **Тестирование showdown**: Имитируйте ситуацию с 1-2 активными игроками, проверьте, что `ShowdownUpdate` содержит корректные данные и отправляется в контроллер.
- Для интеграционного теста WebSocket клиента: подключитесь к встроенному серверу Spring, подпишитесь на топики (`/topic/game`, `/topic/showdown`), отправьте сообщения и проверьте ответы.

Примеры сообщений (JSON)
- Join (строка): отправить просто имя игрока как текст-пэйлоад на `/app/join`.
- PlayerActionRequest (пример для raise):
  ```json
  { "action": "RAISE", "amount": 100 }
  ```
  Для fold/check/call/all-in используйте `"amount": 0` или опускайте поле — контроллер ожидает `PlayerActionRequest` с полями `action` и `amount`.

Быстрый STOMP тест-клиент (локально)
- Является статическим HTML, который кладётся в `src/main/resources/static/test-client.html` и обслуживается Spring Boot на `http://localhost:8080/test-client.html`.
- Клиент использует SockJS + STOMP и демонстрирует: подключение, подписки на `/topic/game` и `/topic/private-{sessionId}`, отправку `/app/join` и `/app/action`.

Пример использования
1. Собрать и запустить сервер в одной консоли:
```powershell
mvn -DskipTests package; mvn spring-boot:run
```
2. Открыть в браузере: `http://localhost:8080/test-client.html` — в UI нажать "Connect", затем "Join" и отправлять действия.

Где положить изменения
- Если добавляете новые поля в DTO — обновите одновременно `dto/*` (добавить поле с getter/setter через @Data или Lombok) и `controller/PokerWebSocketController.java` (использование нового поля, сериализация/валидация).
- **Все модели используют Lombok**: Используйте `@Data` для POJO с автогенерацией getter/setter/equals/hashCode/toString. Используйте `@AllArgsConstructor` для конструктора со всеми полями.
- **Изменение таймера хода**: Отредактируйте `PokerTable.TURN_TIME_LIMIT_MS` для нового лимита; механизм останавливает и перезапускает таймер через методы `startTurnTimer()` и `cancelTurnTimer()` при каждом изменении хода. Обработчик истечения таймера — `autoFoldCurrentPlayer()` в `PokerGameService`.
- **Изменение showdown логики**: Если меняется формат `ShowdownUpdate`, обновляйте `dto/ShowdownUpdate.java` и места где создается (`PokerGameService.determineWinner()`) и отправляется (`PokerWebSocketController.handleAction()`). Проверьте JSON сериализацию.
- **Card как Record**: Не добавляйте поля в Card напрямую (это immutable record). Если нужны доп. метаданные (история, примечания), создайте обёртку: `class CardWithMetadata { Card card; Map metadata; }`.
- **Все async операции должны быть синхронизированы**: Если добавляете асинхронную задачу (ScheduledExecutorService, @Async), убедитесь, что изменения состояния `PokerTable` защищены `synchronized` блоками или вызовом synchronized методов сервиса. **Текущие scheduler'ы**: `PokerGameService` использует `scheduler` для управления таймером хода (вызывает `autoFoldCurrentPlayer` через 30 сек), `PokerWebSocketController` использует `scheduler` для задержки в 5 сек перед новым раундом после вскрытия.
- **Если меняете представление состояния**: Обновляйте `GameStateUpdate.java` и метод `controller/PokerWebSocketController.broadcastGameState()` параллельно, чтобы сохранить совместимость клиентов. Проверьте, что новые поля корректно сериализуются в JSON.
- **Логика распределения банка**: Если изменяете определение победителя или расчёт выигрыша, обновляйте `service/PokerGameService.determineWinner()` и `service/HandEvaluatorService.java` одновременно. Протестируйте на различных комбинациях рук.


Добавление фичи — пример (новый тип ставки)
1. Обновить `model/PlayerAction` (новое значение enum).
2. Обновить `dto/PlayerActionRequest` если нужно новое поле (например, добавить `List<Integer> options` для нескольких вариантов).
3. Обработать в `controller/PokerWebSocketController.handleAction` (валидация входящих данных) и в `service/PokerGameService.handlePlayerAction` (логика ставок, изменение баланса, обновление состояния).
4. Обновить `service/PokerGameService.validateAction` для новых правил.
5. В тестах имитировать последовательность сообщений WebSocket (подключение → join → действия) или напрямую вызывать методы сервиса.
6. Убедитесь, что при добавлении нового действия вызывается `broadcastGameState` для уведомления всех клиентов.

Пример: добавление действия DONATE (пожертвование фишек в банк без получения карт)
1. В `model/PlayerAction`: добавить `DONATE`.
2. В `service/PokerGameService.handlePlayerAction` в switch-case:
   ```java
   case DONATE -> {
       int donateAmount = Math.min(amount, player.getBalance());
       player.setBalance(player.getBalance() - donateAmount);
       contributeToPot(donateAmount);
   }
   ```
3. В `service/PokerGameService.validateAction` добавить проверку, что `amount > 0` для DONATE.
4. После добавления логики — протестировать с WebSocket клиентом.

Где смотреть при проблемах
- Проблемы синхронизации/конкуренции — `service/PokerGameService` и использование `synchronized`.
- Неправильные hand-rank — `service/HandEvaluatorService`.
- Неверные топики/форматы сообщений — `controller/PokerWebSocketController` и `dto/*`.

Известные ограничения и TODO
- **Persistence отсутствует**: Состояние игры хранится только в памяти. После перезагрузки сервера весь прогресс теряется. Для продакшена рассмотрите добавление БД (PostgreSQL, MongoDB и т.д.) и сохранение истории рук.
- **Масштабирование**: Текущая архитектура с одним `PokerTable` экземпляром подходит для одного сервера. Для множества параллельных столов реализуйте `PokerTableRepository` или factory.
- **Аутентификация**: Отсутствует. sessionId автоматически назначается Spring по WebSocket сессии. Для продакшена добавьте JWT или токены.
- **Обработка разъединения**: Игрок, отключившийся от WebSocket, остаётся в таблице. Требуется механизм для обнаружения и удаления отключенных игроков (heartbeat, timeout).

Ресурсы и точки входа
- `src/main/java/com/poker/server/PokerApplication.java` — стартап.
- `config/WebSocketConfig.java` — websocket/stomp contract.
- `controller/PokerWebSocketController.java` — входящие/исходящие сообщения.
- `service/PokerGameService.java`, `service/HandEvaluatorService.java` — основная логика.
- DTO: `src/main/java/com/poker/server/dto` — все сообщения, которые отправляются/принимаются.

Конец — кратко и действенно
- Сконцентрируйтесь на `WebSocketConfig` -> `PokerWebSocketController` -> `PokerGameService` -> `HandEvaluatorService` как на основной цепочке данных/правил.
- Всегда модифицируйте DTO и контроллер параллельно, чтобы сохранить совместимость клиентов.

(Этот файл был сгенерирован автоматически — используйте как живой краткий справочник для агентов.)

