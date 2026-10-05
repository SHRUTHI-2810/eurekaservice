# Microservices Ecosystem Demo

Sample project matching this resume line:

> Built a distributed microservices ecosystem (Spring Cloud Netflix Eureka + Spring Config Server) with
> Git-based configuration — improved service reliability by 40% via Retry Pattern and failover mechanisms.
> Enabled zero-downtime config updates using Spring Cloud Bus and distributed tracing, eliminating manual
> restart cycles.

## What's in here

| Module | Port | Role |
|---|---|---|
| `eureka-server` | 8761 | Service registry / discovery (Netflix Eureka) |
| `config-server` | 8888 | Git-backed centralized config (Spring Cloud Config) |
| `order-service` | 8081 | REST **controller layer**, calls payment-service with Retry Pattern + failover |
| `payment-service` | 8082 | Downstream service called by order-service |
| `config-repo/` | — | Local git repo the Config Server serves config from |

Stack: Spring Boot 3.2.5 / Spring Cloud 2023.0.1 (Java 17), Spring Retry, Resilience4j
(via `spring-cloud-starter-circuitbreaker-resilience4j`) + Feign for failover, Spring Cloud Bus
over RabbitMQ, Micrometer Tracing + Zipkin for distributed tracing.

## The controller layer

`order-service/src/main/java/com/example/orderservice/controller/OrderController.java` is the
REST controller layer you asked about:

- `GET /api/orders/greeting` — returns a value pulled live from Config Server, marked
  `@RefreshScope` so it can change without a restart (see "Zero-downtime config" below).
- `POST /api/orders` — places an order, delegates to `OrderService.placeOrder(...)`, which calls
  `payment-service` through a Feign client wrapped in `@Retryable` (Retry Pattern) with a
  fallback (`PaymentClientFallback`) for failover.
- `GET /api/orders/{id}` — fetches a previously placed order.

The retry + failover logic itself lives one layer down, in `OrderService`, and the Feign client
+ its fallback live in `order-service/.../client/`. That separation (controller → service →
client) is the pattern to point to in an interview: the controller stays thin, resilience is a
service/client concern.

## Prerequisites

- JDK 17+
- Maven 3.8+
- Docker (for RabbitMQ + Zipkin) — optional but needed to demo Bus refresh and tracing end-to-end

## Run order

```bash
# 1. Supporting infra (RabbitMQ for Spring Cloud Bus, Zipkin for tracing)
docker compose up -d

# 2. Build everything
mvn -q -DskipTests clean install

# 3. Eureka first (everything else registers with it)
cd eureka-server && mvn spring-boot:run
# -> http://localhost:8761

# 4. Config Server (reads from ../config-repo, a local git repo already
#    initialized for you with sample order-service.yml / payment-service.yml)
cd config-server && mvn spring-boot:run
# -> http://localhost:8888/order-service/default  (sanity check)

# 5. The two business services
cd payment-service && mvn spring-boot:run
cd order-service && mvn spring-boot:run
```

Give each one ~10-20s to register with Eureka before starting the next; check
`http://localhost:8761` to see all four instances listed once everything is up.

> The config-server's `application.yml` points at
> `file://${user.home}/microservices-demo/config-repo`. If you unzip this project somewhere
> other than your home directory, either move it there or edit that `uri` to an absolute path
> (or point it at a real GitHub/GitLab repo — that's the intended production setup; the local
> git repo is just so this runs standalone).

## Demo 1 — place an order

```bash
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{"product":"Keyboard","amount":49.99}'
```

Response includes `"paymentStatus":"SUCCESS"` — order-service called payment-service via Eureka
+ Feign and got a normal response.

## Demo 2 — Retry Pattern + failover

Turn on simulated failures in payment-service **without touching code or restarting it** — edit
`config-repo/payment-service.yml`:

```yaml
app:
  simulate-failures: true
```

Then:

```bash
cd config-repo && git add -A && git commit -m "simulate payment outage"
# push the refresh to every bus-connected instance:
curl -X POST http://localhost:8081/actuator/busrefresh
```

Now repeat the `POST /api/orders` call. You'll see (via logs) `OrderService.placeOrder` retry 3
times with backoff (500ms → 1000ms → 2000ms), then fail over via `@Recover`, returning
`"paymentStatus":"PAYMENT_UNAVAILABLE_FAILOVER_APPLIED"` instead of an error. Stop
`payment-service` entirely instead (rather than just flagging failures) and you'll additionally
see Resilience4j's circuit breaker open and `PaymentClientFallback` kick in
(`"FAILOVER_QUEUED"`) once it trips — that's the Feign-level failover mechanism, separate from
the retry loop in `OrderService`.

Set `simulate-failures` back to `false` and re-run the bus-refresh command to restore normal
behavior.

## Demo 3 — zero-downtime config update via Spring Cloud Bus

```bash
curl http://localhost:8081/api/orders/greeting
```

Edit `config-repo/order-service.yml`, change `app.greeting`, then:

```bash
cd config-repo && git add -A && git commit -m "update greeting"
curl -X POST http://localhost:8081/actuator/busrefresh
curl http://localhost:8081/api/orders/greeting   # new value, no restart
```

Spring Cloud Bus publishes the refresh event over RabbitMQ; every instance subscribed to the bus
(just order-service here, but this scales to N replicas) picks it up and rebuilds its
`@RefreshScope` beans. This is the "eliminating manual restart cycles" part of the bullet.

## Demo 4 — distributed tracing

Hit the order endpoint a few times, then open `http://localhost:9411` (Zipkin UI) and search for
service `order-service`. You'll see a trace spanning the `POST /api/orders` call in order-service
through the Feign call into payment-service, with a shared trace ID — this is what lets you
follow one request across service boundaries instead of grepping separate log files.

## Notes on scope

This is a minimal, runnable reference implementation sized for demoing/explaining the resume
bullet in an interview — not a production system. In particular: the "database" is an in-memory
`ConcurrentHashMap` in `OrderService`, there's no auth between services, and the git config repo
is local rather than a real remote (swap the `uri` in `config-server/application.yml` to point at
GitHub/GitLab and it works the same way).
