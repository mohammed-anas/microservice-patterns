# Service discovery

Order calls inventory and payment by service name. Eureka stores each replica as its container IP on port 8080. The load balancer picks one of those IPs.

Run commands from the project root. Give a stopped or killed replica about 20 seconds before expecting it to leave the registry. Heartbeats are every 5 seconds, the lease expires after 15 seconds, and Eureka evicts every 5 seconds. `docker stop` also unregisters on the way down, so that case is faster.

The demo command retries for up to a minute. Use the one-shot call below when you want to see an error.

## Commands

Start and stop the lab:

```bash
docker compose up --build -d
docker compose ps
docker compose down
```

One call, including the HTTP status when it fails:

```bash
docker run --rm --network ecommerce_ecommerce curlimages/curl:8.17.0 -sS -m 8 -w '\nhttp_status:%{http_code}\n' http://order-service:8080/orders
```

Repeat that call and print only the container and IP fields:

```bash
for n in 1 2 3 4 5 6; do
  docker run --rm --network ecommerce_ecommerce curlimages/curl:8.17.0 -sS -m 8 http://order-service:8080/orders \
    | python3 -c 'import json,sys; d=json.load(sys.stdin); print({k: (d[k]["container"], d[k]["ip"]) for k in ("order","inventory","payment")})'
done
```

IP of one container:

```bash
docker inspect -f '{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}' ecommerce-payment-service-1
```

IP of every lab container:

```bash
docker inspect -f '{{.Name}}  {{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}' $(docker ps -q --filter name=ecommerce-)
```

Who Eureka currently lists (`ip:8080` and status):

```bash
curl -s http://localhost:8761/eureka/apps -H 'Accept: application/json' | python3 -c '
import json,sys
apps=json.load(sys.stdin)["applications"]["application"]
if isinstance(apps, dict): apps=[apps]
for app in apps:
    inst=app["instance"]
    if isinstance(inst, dict): inst=[inst]
    for i in inst:
        print(f"{app[\"name\"]:20} {i[\"ipAddr\"]}:8080  {i[\"status\"]:6}  {i[\"instanceId\"]}")
'
```

Dashboard: [http://localhost:8761](http://localhost:8761)

Logs while you run a call:

```bash
docker compose logs -f --tail 30 order-service
```

Call one replica by the IP you just looked up. This skips discovery and always hits that container:

```bash
docker run --rm --network ecommerce_ecommerce curlimages/curl:8.17.0 -sS -m 8 http://172.25.0.6:8080/payments
```

Scale. Do this with `up -d`. Publishing host port 8080 would make the second replica fail to start.

```bash
docker compose up -d --scale payment-service=3 --scale inventory-service=2 --scale order-service=2
```

## Experiments

Record the registry and one successful `/orders` response before each experiment, then compare.

1. **Several calls, same port, different IP.** Run the repeat loop. `order` stays on one container while you have a single order replica. `payment` and `inventory` move across the IPs from the registry. Every `port` value stays `8080`.

2. **Stop one payment replica.** `docker stop ecommerce-payment-service-1`. Refresh the registry until that IP is gone. Call `/orders` again. Payment answers from a remaining replica. Start it with `docker start ecommerce-payment-service-1` and wait until a new instance id appears. The id changes because it is generated at startup.

3. **Stop every payment replica.** `docker compose stop payment-service`, wait until `PAYMENT-SERVICE` is gone, then run the one-shot call. Order still starts the request, inventory may be reached, and the payment lookup fails because the registry has no payment instance. `docker compose start payment-service` brings them back. Wait for `UP` before calling again.

4. **Stop inventory only.** `docker compose stop inventory-service` and call `/orders`. The failure is on inventory this time, even though payment is still registered. Start inventory again with `docker compose start inventory-service`.

5. **Graceful stop versus kill.** Stop one payment container with `docker stop` and time how fast it disappears. Start it, then `docker kill` a payment container. Kill does not let the process unregister, so the IP stays `UP` until the 15 second lease expires and the next eviction runs. Call `/orders` during that window and watch for a failure followed by success on the live replica.

6. **Freeze one replica.** `docker pause ecommerce-payment-service-1`. The process cannot send heartbeats and cannot answer. Repeat the one-shot call. Some calls hit the paused IP and stop at the 8 second curl limit. Others hit the running replica and return immediately. `docker unpause ecommerce-payment-service-1` when you are done. The paused IP leaves the registry only after the lease expires.

7. **Scale up and down.** `docker compose up -d --scale payment-service=3`. The registry grows by one IP, still on port 8080, and the repeat loop starts using it. Then `--scale payment-service=1` and confirm a single payment IP remains.

8. **Two order replicas.** `docker compose up -d --scale order-service=2`. Repeat `/orders`. The `order.ip` value changes, because Docker DNS picks an order container. Each of those orders still chooses payment and inventory from Eureka. Compare that with calling a payment IP directly: the direct call never changes container.

9. **Eureka goes away.** Copy the registry, then `docker stop ecommerce-eureka-server-1`. Call `/orders` right away. Order can still use the list it fetched in the last 5 seconds, so a few calls may succeed. Keep calling. New lookups fail once that cache is stale, and the dashboard on port 8761 does not load. `docker start ecommerce-eureka-server-1`, wait until the dashboard is back, and call until all replicas are `UP` again. Instances register themselves. Eureka does not go find them.

10. **Disconnect one container from the network.** `docker network disconnect ecommerce_ecommerce ecommerce-payment-service-2`. It can no longer renew, so the same lease timer removes it. Calls should stick to the replicas still on the network. Reconnect with `docker network connect ecommerce_ecommerce ecommerce-payment-service-2`. If the process was stuck retrying, `docker restart ecommerce-payment-service-2` makes it register again.

11. **Read the registry while a call is in flight.** In one terminal run `docker compose logs -f --tail 20 order-service`. In another, run experiment 3 or 5. The order log shows the missing service name when the load balancer has an empty list, and a connection error when it still holds a dead IP.