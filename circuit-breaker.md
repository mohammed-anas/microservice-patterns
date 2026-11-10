# Circuit breaker

The breaker sits in order-service, around the same discovery calls as before. Eureka still chooses an IP on port 8080. Retry runs inside the breaker. The fallback runs outside it.

```
GET /orders
  -> fallback
    -> circuit breaker          one result per user call
      -> retry                  up to 3 tries, only on timeout, connection error, or HTTP 5xx
        -> load balancer        Eureka IP:8080
```

Payment and inventory each have their own breaker. One of them opening does not affect the other. Breaker state lives in the order process. It is not stored in Eureka. Two order replicas learn separately.

`GET /orders` with no query string is unchanged: container, IP, and port come back from a real replica. The query parameters below exist only to force a fault.

Run commands from the project root. Rebuild the three app images after a config change:

```bash
docker compose up --build -d order-service payment-service inventory-service
```

## What the numbers mean

Closed means calls go through. The breaker looks at the last **10** outcomes and does not judge until **6** of them exist. It opens when the failure rate is **at least 50%**, or when **at least 80%** of calls are slower than **2 seconds**.

Open means the next call is rejected in memory. Nothing is sent to that service, and retry does not run. Open lasts **15 seconds** (`waitDurationInOpenState`), then the state becomes half-open. In production that wait is usually 30-60 seconds, long enough for a restart.

Half-open lets **3** trial calls through. If those still fail at 50% or more, it opens again and the 15 second wait starts over. If they succeed, it closes. A read timeout is **3 seconds**, which is longer than the 2 second slow-call line, so a 2.5 second response counts as a slow success and a 4 second response counts as a failure.

Retry runs only while the breaker is closed or half-open. `maxAttempts: 3` is the first try plus two retries, with exponential backoff and jitter. A user call that gets HTTP 503 three times is still **one** failure in the window. An HTTP 400 is neither retried nor recorded.

## Commands

Healthy call, same fields as the discovery lab:

```bash
docker run --rm --network ecommerce_ecommerce curlimages/curl:8.17.0 -sS -m 8 http://order-service:8080/orders
```

One forced payment failure (HTTP 503 from every attempt):

```bash
docker run --rm --network ecommerce_ecommerce curlimages/curl:8.17.0 -sS -m 15 \
  "http://order-service:8080/orders?paymentFail=true"
```

Slow payment, still HTTP 200 if it finishes under 3 seconds:

```bash
docker run --rm --network ecommerce_ecommerce curlimages/curl:8.17.0 -sS -m 15 \
  "http://order-service:8080/orders?paymentDelayMs=2500"
```

Breaker state:

```bash
docker run --rm --network ecommerce_ecommerce curlimages/curl:8.17.0 -sS \
  http://order-service:8080/actuator/circuitbreakers
```

Recent transitions:

```bash
docker run --rm --network ecommerce_ecommerce curlimages/curl:8.17.0 -sS \
  http://order-service:8080/actuator/circuitbreakerevents
```

Retry and breaker logs:

```bash
docker compose logs -f --tail 50 order-service
```

Count how many times payment actually ran a lab fault:

```bash
docker compose logs --tail 100 payment-service | grep "lab fault"
```

The breaker memory is wiped when that order container restarts. To run an experiment again from CLOSED:

```bash
docker compose restart order-service
```

Wait until `docker compose ps` shows order-service healthy before the next call.

## Experiments

1. **Discovery still works.** Restart order-service so both breakers start at CLOSED. Call `/orders` with no query string. `payment` and `inventory` still show a real container and a `172.x` address on port 8080. Actuator shows both breakers `CLOSED`.

2. **Retry, then one failure.** `docker compose logs -f order-service` in one terminal. Run the `paymentFail=true` command once. Payment logs `lab fault` three times. Order logs two `retry` lines and then `retry exhausted`. The response is HTTP 200 with `payment.message` equal to `dependency_failed`. Inventory still has a real IP. The failure window stored one failure, not three.

3. **Closed to open.** Restart order-service. Run `paymentFail=true` six times. The sixth call is still attempted. Actuator then shows `payment-service` as `OPEN`, and the order log has `CLOSED_TO_OPEN` plus a failure rate of 100. Run the same command a seventh time. It returns in milliseconds with `payment.message` equal to `circuit_open`, and payment does not log a new `lab fault`. The open breaker never reached retry.

4. **The other service stays up.** While payment is OPEN, call `/orders` with no query string. Inventory returns a real container. Payment stays `circuit_open`. Actuator still shows `inventory-service` as `CLOSED`.

5. **A client error does not move the breaker.** Restart order-service. Ten times, call:

   ```bash
   docker run --rm --network ecommerce_ecommerce curlimages/curl:8.17.0 -sS -m 8 \
     "http://order-service:8080/orders?paymentClientError=true"
   ```

   Each response has `payment.message` equal to `client_error`. Payment logs `lab fault` once per call, not three times. The payment breaker stays `CLOSED`.

6. **Open to half-open to closed.** Open the payment breaker with experiment 3. Wait 15 seconds. Actuator shows `HALF_OPEN` and the log shows `OPEN_TO_HALF_OPEN` without you sending a call. Within 30 seconds, call `/orders` with no query string three times. Those are the probes. After the third success the log shows `HALF_OPEN_TO_CLOSED`, and further calls keep reaching payment. If you send no probe in those 30 seconds, the log shows `HALF_OPEN_TO_OPEN` and the 15 second wait starts again.

7. **Half-open that still fails.** Open the breaker again, wait for `HALF_OPEN`, then run `paymentFail=true` three times. The probes fail, the log shows `HALF_OPEN_TO_OPEN`, and the 15 second wait starts over. A call during that new wait is `circuit_open` again.

8. **Slow calls, not errors.** Restart order-service. Six times, call `paymentDelayMs=2500`. Each call returns `Payment processed` and takes about 2.5 seconds. There are no retry lines, because the call succeeded. After six slow successes the slow-call rate is 100, which is past 80, so the breaker opens on slowness. The next plain `/orders` is `circuit_open` even though payment would still return 200.

9. **Timeout is a failure, not a slow success.** Restart order-service. Call `paymentDelayMs=4000` once. The client gives up at 3 seconds, retries, and gives up again. `payment.message` is `dependency_failed`. That outcome fills the failure window, not the slow-call window. Do not use this call to study slowness. Use 2500 ms for that.

10. **Retry plus a live replica.** Scale payment to 2 if it is not already: `docker compose up -d --scale payment-service=2`. Restart order-service. `docker pause` one payment container. Call `/orders` several times. Some calls log a retry and then succeed on the other IP, and the breaker stays `CLOSED`, because the retried attempt worked. `docker unpause` that container when you finish. Then `docker compose stop payment-service`, wait until Eureka has dropped both instances (about 20 seconds), and call `/orders`. There is no retry storm for an empty instance list. Each call is one `dependency_failed`. After six of them the breaker opens and later calls are `circuit_open`. `docker compose start payment-service` to bring payment back. The breaker stays open until the wait expires. Starting the container does not close it.

11. **Each order replica has its own breaker.** `docker compose up -d --scale order-service=2`. Open the breaker by sending `paymentFail=true` until one response says `circuit_open`. Keep calling plain `/orders`. Some responses are still a real payment, because Docker DNS also sends you to the other order container, and that process is still `CLOSED`. Restarting only one order container clears only that container's window.
