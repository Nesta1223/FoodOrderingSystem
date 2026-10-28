# Order & Payment Service

A REST API for food ordering and payment processing, built with Java 17 and Spring Boot 3.2. Users order from open restaurants, the server calculates totals and locks in menu prices at checkout, and orders are paid and refunded under strict rules that keep financial records consistent. The business logic lives in a service layer with transactional boundaries, request validation, row locking for concurrent payments, and a uniform JSON error format. The API is documented with OpenAPI/Swagger.

## Tech Stack

- Java 17
- Spring Boot 3.2 (Web, Data JPA, Validation)
- PostgreSQL 15
- Hibernate / Spring Data JPA
- Springdoc OpenAPI 2 (Swagger UI)
- Lombok
- Maven
- Docker Compose (runs PostgreSQL)

## How to Run

Prerequisites: JDK 17+, Maven 3.9+, Docker.

```bash
# 1. Start PostgreSQL
docker-compose up -d

# 2. Run the app
mvn spring-boot:run
```

- API base URL: http://localhost:8080/api
- Swagger UI: http://localhost:8080/swagger-ui.html

Tables are created automatically (`ddl-auto: update`). The API has no user endpoints, so two demo users are seeded on startup (ids `1` and `2` on a fresh database).

### Quick walkthrough

```bash
# Create a restaurant and a menu item
curl -X POST localhost:8080/api/restaurants -H "Content-Type: application/json" \
  -d '{"name":"Jay Fai","category":"FOOD"}'
curl -X POST localhost:8080/api/restaurants/1/menu -H "Content-Type: application/json" \
  -d '{"name":"Crab Omelette","price":1000.00}'

# Place an order. Only ids and quantities are sent; the server calculates the total
curl -X POST localhost:8080/api/orders -H "Content-Type: application/json" \
  -d '{"userId":1,"restaurantId":1,"items":[{"menuItemId":1,"quantity":2}]}'

# Pay the exact total
curl -X POST localhost:8080/api/payments -H "Content-Type: application/json" \
  -d '{"orderId":1,"amount":2000.00,"method":"WALLET"}'

# Refund it
curl -X POST localhost:8080/api/payments/1/refund

# Limit the restaurant to 09:00-21:00. Orders outside those hours are rejected with "Restaurant is closed"
curl -X PUT localhost:8080/api/restaurants/1/opening-hours -H "Content-Type: application/json" \
  -d '{"openingHours":"OFFICE_HOURS"}'
```

## API Overview

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/orders` | Create an order (server-side total, price locking) |
| GET | `/api/orders/{id}` | Get an order with its items |
| GET | `/api/orders?userId={userId}` | List a user's orders, newest first |
| PATCH | `/api/orders/{id}/status` | Advance order status |
| DELETE | `/api/orders/{id}` | Cancel an order (PENDING only) |
| POST | `/api/payments` | Pay for an order |
| GET | `/api/payments/{orderId}` | Get the latest payment for an order |
| POST | `/api/payments/{id}/refund` | Refund a successful payment |
| POST | `/api/restaurants` | Create a restaurant (optional `openingHours` preset, default `ALL_DAY`) |
| GET | `/api/restaurants` | List restaurants that are open right now |
| GET | `/api/restaurants/{id}/opening-hours` | Get the opening-hours preset with its open and close time |
| PUT | `/api/restaurants/{id}/opening-hours` | Set the opening-hours preset |
| PUT | `/api/restaurants/{id}/pause` | Pause orders for 1–1440 minutes (repeat to extend) |
| DELETE | `/api/restaurants/{id}/pause` | End a pause early |
| POST | `/api/restaurants/{id}/menu` | Add a menu item |
| GET | `/api/restaurants/{id}/menu` | Get a restaurant's menu |
| PATCH | `/api/menu/{id}/availability` | Toggle a menu item's availability |

### Error format

Every error returns the same JSON shape:

```json
{
  "error": "Resource not found",
  "message": "Order with id 999 not found",
  "timestamp": "2026-10-27T11:00:00"
}
```

| Status | When |
|--------|------|
| 400 | Business rule violation, invalid enum value, malformed JSON, or validation failure (adds a `fieldErrors` map) |
| 404 | Entity not found |

## Business Rules & Design Decisions

### Server-side total calculation
The client sends only `menuItemId` and `quantity`. The server computes `totalAmount = Σ(quantity × priceAtOrder)` with `BigDecimal`. A client cannot change what it pays by editing the request, because the request has no price or total field.

### Price locking
When an order is created, each item's current `MenuItem.price` is copied into `OrderItem.priceAtOrder`, and that column is not updatable. If the restaurant changes a price later, existing orders keep the price the customer agreed to, so order history and payment amounts always match.

### Immutable payment records
A refund never deletes or rewrites a payment. Only `status` changes from `SUCCESS` to `REFUNDED`, and the original `amount`, `transactionRef` and `paidAt` are kept as an audit trail. Each successful payment gets a UUID `transactionRef` (unique in the database).

### Payment guards
- An order can have at most one `SUCCESS` payment ("Order already paid").
- `amount` must equal the order total exactly. It is compared with `BigDecimal.compareTo`, so `100` and `100.00` are equal.
- Cancelled orders cannot be paid.
- Payments, cancellations and status changes take a `SELECT ... FOR UPDATE` lock on the order row. Two requests arriving at the same time therefore cannot both pass the "already paid" check, and a cancel cannot interleave with a payment.

### Order lifecycle
```
PENDING ──► CONFIRMED ──► PREPARING ──► DELIVERED
   │
   └──► CANCELLED   (only from PENDING, and only if not paid; refund first)
```
Status changes only move forward. Invalid transitions are rejected with 400.

### Opening hours
"Open" is **calculated, never stored**, because a stored flag goes stale as soon as the clock passes a closing time. A restaurant is open when it is **not paused** and the current time is inside its opening-hours preset.

Opening hours are an `OpeningHours` enum, stored as text in the `restaurants.opening_hours` column. Each preset carries its own open and close time and applies to **every day of the week**:

| Preset | Open | Close |
|--------|------|-------|
| `ALL_DAY` (default) | always | always |
| `MORNING` | 06:00 | 14:00 |
| `OFFICE_HOURS` | 09:00 | 21:00 |
| `EVENING` | 17:00 | 23:00 |
| `LATE_NIGHT` | 20:00 | 04:00 (next day) |

- If the close time is not after the open time, the window runs past midnight (`LATE_NIGHT`), so it also covers the early morning.
- The open time is inclusive and the close time is exclusive.
- Times are evaluated in the `app.timezone` zone (default `Asia/Bangkok`), not the server's zone.
- Trade-off: presets keep the model simple, but a restaurant can't be closed on specific weekdays or have a lunch and dinner split. Adding a preset is one line in the enum.

### Pausing a restaurant
Opening hours can't know about a power cut or an overloaded kitchen, so owners can pause orders manually with `PUT /api/restaurants/{id}/pause` (`durationMinutes` 1–1440, optional `reason`).
- The pause is stored as `paused_until`, a **timestamp, not a boolean**. When it passes, the restaurant follows its opening hours again with no cleanup, so a forgotten pause cannot close a restaurant forever.
- The maximum is 24 hours; a longer pause has to be renewed on purpose.
- Both calls are idempotent: repeating `PUT` only moves the end of the pause, and `DELETE` on a restaurant that isn't paused does nothing. A client retrying after a timeout cannot flip the state by accident.
- A pause can only close a restaurant. It never opens one outside its opening hours.
- `RestaurantResponse` returns `acceptingOrders` (false while paused), `openNow` (the computed result), and `pausedUntil` / `pauseReason` while a pause is active.

### Ordering guards
- The restaurant must be open now (see above).
- Every menu item must exist, belong to that restaurant, and be available.

### Other conventions
- Money fields use `BigDecimal` / `DECIMAL(10,2)`, never `double`.
- Enums are stored as strings (`@Enumerated(EnumType.STRING)`), so reordering an enum cannot corrupt data.
- Response DTOs are flat and never expose JPA entities.
- Constructor injection everywhere, and `@Transactional` on every write.

## Architecture

```
            ┌──────────────────────────────┐
  HTTP ───► │  Controller layer            │  Request validation (@Valid), HTTP status codes,
            │  Order / Payment / Restaurant│  Swagger docs. No business logic.
            └──────────────┬───────────────┘
                           │ DTOs
            ┌──────────────▼───────────────┐
            │  Service layer               │  Business rules, @Transactional boundaries,
            │  Order / Payment / Restaurant│  entity ↔ DTO mapping, row locking
            └──────────────┬───────────────┘
                           │ Entities
            ┌──────────────▼───────────────┐
            │  Repository layer            │  Spring Data JPA interfaces
            │  (JpaRepository)             │
            └──────────────┬───────────────┘
                           │ SQL (Hibernate)
            ┌──────────────▼───────────────┐
            │  PostgreSQL 15 (Docker)      │
            └──────────────────────────────┘

  GlobalExceptionHandler (@RestControllerAdvice) turns BusinessException → 400,
  ResourceNotFoundException → 404 and validation errors → 400 with field details.
```

## Project Structure

```
src/main/java/com/pbgold/orderpayment/
├── controller/   REST endpoints
├── service/      Business logic
├── repository/   Data access
├── model/        JPA entities and enums
├── dto/          Request and response objects
├── exception/    Custom exceptions and global handler
└── config/       Demo data seeding
```
