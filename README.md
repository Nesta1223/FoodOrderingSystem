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

# Restrict the restaurant to opening hours. Orders outside them are rejected with "Restaurant is closed"
curl -X PUT localhost:8080/api/restaurants/1/opening-hours -H "Content-Type: application/json" \
  -d '{"openingHours":[{"dayOfWeek":"MONDAY","openTime":"09:00","closeTime":"21:00"}]}'
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
| POST | `/api/restaurants` | Create a restaurant (optionally with opening hours) |
| GET | `/api/restaurants` | List restaurants that are open right now |
| GET | `/api/restaurants/{id}/opening-hours` | Get the weekly opening hours |
| PUT | `/api/restaurants/{id}/opening-hours` | Replace the whole schedule |
| PATCH | `/api/restaurants/{id}/accepting-orders` | Pause or resume orders (manual switch) |
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
"Open" is **calculated, not stored**. A restaurant is open when its manual switch (`acceptingOrders`) is on **and** the current time falls inside one of its stored opening periods (`opening_hours` table).
- A day can have several periods (lunch and dinner).
- If `closeTime` is not after `openTime`, the period runs past midnight (`18:00`–`02:00`) and also covers the next morning.
- `openTime` is inclusive, `closeTime` is exclusive.
- A restaurant with no schedule has no time restriction, so only the manual switch applies.
- Times are evaluated in the `app.timezone` zone (default `Asia/Bangkok`), not the server's zone.
- `RestaurantResponse` returns both `acceptingOrders` (the switch) and `openNow` (the computed result).

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
