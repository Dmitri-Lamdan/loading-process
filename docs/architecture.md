# 3	Архитектура системы

## 3.1	Логическая архитектура

```mermaid
flowchart LR

    %% === Client Side ===
    subgraph CLIENT ["Client (React + PWA)"]
        UI("React UI")
        SW("Service Worker")
        IDB("IndexedDB Cache")
        SYNC("Sync Manager")
    end

    %% === User Devices ===
    subgraph DEVICES ["User Devices"]
        ATV("Android TV Box")
        WIN("Windows PC")
        LNX("Linux PC")
    end

    %% === Tailscale Funnel ===
    subgraph FUNNEL ["Tailscale Funnel"]
        TSCLIENT("Tailscale Client on Android")
        TSPUB("Public HTTPS Endpoint (ts.net)")
    end

    %% === Backend ===
    subgraph BACKEND ["Rust Backend on Android"]
        API("REST API")
        AUTH("Auth Service")
        SQLITE("SQLite Database")
        OBJ("Objects Service")
        VAL("Values Service")
        CAL("Calendar Integration")
        EVENTS("NATS Event Publisher")
    end

    %% === NATS Broker ===
    subgraph BROKER ["Message Broker"]
        NATS("NATS Server")
    end

    %% Device → Client
    ATV --> UI
    WIN --> UI
    LNX --> UI

    %% Client internals
    UI --> IDB
    UI --> SYNC
    SW --> IDB

    %% Client → Funnel → Backend
    SYNC --> TSPUB
    TSPUB --> TSCLIENT
    TSCLIENT --> API

    %% Backend internals
    API --> AUTH
    API --> SQLITE
    API --> OBJ
    API --> VAL
    API --> CAL
    API --> EVENTS

    %% NATS
    EVENTS --> NATS
    NATS --> API

```

## 3.2	Компоненты

-	Frontend (React + PWA клиентом)
-	Rust backend на Android (API Gateway / Backend API)
-	Objects Service
-	Values Service
-	Calendar Integration Service
- NATS как брокером сообщений
- SQLite (file-based) Storage


```mermaid
flowchart LR

    %% === Client Side (React + PWA) ===
    subgraph CLIENT["Client Application (React + PWA)"]
        UI["Component: React UI"]
        SW["Component: Service Worker (Offline Mode)"]
        IDB["Component: IndexedDB (Local Cache)"]
        SYNC["Component: Sync Manager"]
    end

    %% === Devices running the client ===
    subgraph DEVICES["Execution Environment (Browsers on Devices)"]
        ATV["Device: Android TV Box"]
        WIN["Device: Windows PC"]
        LNX["Device: Linux PC"]
    end

    %% === Tailscale Funnel Infrastructure ===
    subgraph FUNNEL["Secure Public Access (Tailscale Funnel)"]
        TSCLIENT["Component: Tailscale Client (Android Server)"]
        TSPUB["Public HTTPS Endpoint (ts.net)"]
    end

    %% === Backend Server on Android ===
    subgraph BACKEND["Backend Server (Rust on Android Device)"]
        API["Component: REST API (Axum / Actix)"]
        AUTH["Component: Auth Service"]
        SQLITE["Component: SQLite Database"]

        OBJ["Component: Objects Service"]
        VAL["Component: Values Service"]
        CAL["Component: Calendar Integration Service"]

        EVENTS["Component: Event Publisher (NATS Client)"]
    end

    %% === Message Broker (

```

### 3.2.2 Развёртывание в Андроид ТВ боксе

- Серверная часть развёртывается непосредственно на Android TV Box, который используется как компактная, энергоэффективная и постоянно доступная платформа для backend‑сервиса.
- Запуск backend‑приложения выполняется в среде Termux, обеспечивающей полноценный Linux‑пользовательский слой на Android 4.x.
- Rust‑backend включает REST API, сервисы обработки объектов и значений, интеграцию с календарём, локальную базу данных SQLite и публикацию событий в NATS‑брокер.
- SQLite хранится локально в файловой системе Android TV Box; требуется учитывать однописательную модель БД.
- Backend запускается как единичный процесс; параллельный доступ на запись недопустим (аналогично ограничению replicaCount: 1 в Kubernetes).
- Для безопасного внешнего доступа используется Tailscale — mesh‑VPN на базе WireGuard, обеспечивающий приватное сетевое окружение для устройства.
- Механизм Tailscale Funnel предоставляет публичный HTTPS‑endpoint без необходимости открывать порты, настраивать NAT или использовать VPS.
- После активации Funnel сервер получает стабильный URL вида https://<device>.ts.net, который используется клиентскими приложениями (React/PWA) для синхронизации данных и обращения к API.
- Android TV Box функционирует как автономный серверный узел: выполняет бизнес‑логику, хранит данные локально, обрабатывает запросы клиентов и публикует события в NATS.
- Благодаря Tailscale устройство остаётся доступным из интернета через защищённый туннель, обеспечивая минимальные требования к инфраструктуре и простоту развёртывания.


### 3.2.1 Use Cases

#### 3.2.1.1	UC 01: Просмотр списка объектов

 - **Актор**: Пользователь 
 - **Описание**: Пользователь открывает список объектов и видит карточки с ключевой информацией.  
 - **Результат**: Отображён актуальный список объектов.


#### 3.2.1.2	UC 02: Создание объекта
- **Актор**: Пользователь 
- **Описание**: Пользователь заполняет форму создания объекта. Результат: Новый объект сохранён в базе.

#### 3.2.1.3	UC 03: Редактирование объекта

- Актор: Пользователь 
- Описание: Пользователь изменяет параметры объекта. Результат: Объект обновлён, значения пересчитаны.

#### 3.2.1.4	UC 04: Расчёт значений

- **Актор**: Система 
- **Описание**: При изменении данных система пересчитывает текущие значения. Результат: Обновлённые данные сохранены.

## 3.3	Sequence Diagrams (текстовое описание)

### 3.3.1	Создание объекта

```mermaid
%%{init: {
  "theme": "default",
  "themeVariables": {
    "primaryColor": "#4CAF50",
    "primaryTextColor": "#ffffff",
    "actorBorder": "#4CAF50",
    "actorTextColor": "#4CAF50",
    "lineColor": "#4CAF50"
  }
}}%%

sequenceDiagram
    participant User
    participant Frontend
    participant Backend
    participant ValuesModule as Values Module
    participant DB

    User ->> Frontend: вводит данные
    Frontend ->> Backend: POST /objects
    Backend ->> ValuesModule: расчёт значений
    Backend ->> DB: сохранение объекта
    Backend ->> Frontend: 201 Created
    Frontend ->> User: отображение нового объекта
```

### 3.3.2	Получение списка объектов

```mermaid
%%{init: {
  "theme": "default",
  "themeVariables": {
    "primaryColor": "#4CAF50",
    "primaryTextColor": "#ffffff",
    "actorBorder": "#4CAF50",
    "actorTextColor": "#4CAF50",
    "lineColor": "#4CAF50"
  }
}}%%

sequenceDiagram
    participant User
    participant Frontend
    participant Backend
    participant DB

    User ->> Frontend: открывает список
    Frontend ->> Backend: GET /objects
    Backend ->> DB: запрос списка
    DB -->> Backend: данные
    Backend -->> Frontend: JSON список
    Frontend ->> User: отображение карточек
```

### 3.3.3 Редактирование объекта

```mermaid

%%{init: {
  "theme": "default",
  "themeVariables": {
    "primaryColor": "#4CAF50",
    "primaryTextColor": "#ffffff",
    "actorBorder": "#4CAF50",
    "actorTextColor": "#4CAF50",
    "lineColor": "#4CAF50"
  }
}}%%
sequenceDiagram
    title UC 03: Редактирование объекта (с альтернативными потоками)

    participant User
    participant Frontend
    participant Backend
    participant DB

    User ->> Frontend: открывает форму редактирования объекта
    Frontend ->> Backend: GET /object/{id}
    Backend ->> DB: запрос объекта
    DB -->> Backend: данные объекта
    Backend -->> Frontend: JSON объекта
    Frontend ->> User: отображение формы с текущими параметрами

    User ->> Frontend: изменяет параметры объекта
    Frontend ->> Backend: PUT /object/{id}\n(новые параметры)

    alt Успешная валидация
        Backend ->> Backend: пересчёт значений\n(даты, интервалы, прогресс)
        Backend ->> DB: обновление объекта
        DB -->> Backend: подтверждение
        Backend -->> Frontend: обновлённый объект
        Frontend ->> User: отображение обновлённых данных\n(значения пересчитаны)
    else Ошибка валидации
        Backend -->> Frontend: 400 Validation Error\n(некорректные параметры)
        Frontend ->> User: сообщение об ошибке\n(поля подсвечены)
    else Объект не найден
        Backend -->> Frontend: 404 Not Found
        Frontend ->> User: уведомление "Объект не найден"
    else Ошибка сервера
        Backend -->> Frontend: 500 Internal Server Error
        Frontend ->> User: сообщение "Сервис временно недоступен"
    else Конфликт версий (оптимистичная блокировка)
        Backend -->> Frontend: 409 Conflict\n(объект изменён другим пользователем)
        Frontend ->> User: предложение перезагрузить данные
    end
```

### 3.3.4	 Редактирование объекта
```mermaid

%%{init: {
  "theme": "default",
  "themeVariables": {
    "primaryColor": "#4CAF50",
    "primaryTextColor": "#ffffff",
    "actorBorder": "#4CAF50",
    "actorTextColor": "#4CAF50",
    "lineColor": "#4CAF50"
  }
}}%%
sequenceDiagram
    title UC: Автоматический пересчёт данных системой (с branch‑логикой)

    participant System
    participant Backend
    participant DB
    participant Frontend

    System ->> Backend: событие изменения данных\n(триггер обновления)
    Backend ->> DB: запрос текущих данных объекта
    DB -->> Backend: данные объекта

    alt Тип объекта: Календарный интервал
        Backend ->> Backend: пересчёт по времени\n(дата установки + интервал)
        Backend ->> DB: сохранение обновлённых данных
        DB -->> Backend: подтверждение
        Backend -->> Frontend: обновлённые данные

    else Тип объекта: Пробег (км / моточасы)
        Backend ->> Backend: пересчёт по пробегу\n(текущий пробег + интервал)
        Backend ->> DB: сохранение обновлённых данных
        DB -->> Backend: подтверждение
        Backend -->> Frontend: обновлённые данные

    else Тип объекта: Событие (ручной лог)
        Backend ->> Backend: пересчёт не требуется\n(лог фиксируется вручную)
        Backend ->> DB: сохранение изменений
        DB -->> Backend: подтверждение
        Backend -->> Frontend: обновлённые данные

    else Тип объекта: Комбинированный (время + пробег)
        Backend ->> Backend: пересчёт по двум алгоритмам\n(календарь + пробег)
        Backend ->> DB: сохранение обновлённых данных
        DB -->> Backend: подтверждение
        Backend -->> Frontend: обновлённые данные
    end

    Frontend ->> System: подтверждение обновления
```
