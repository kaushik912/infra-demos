#!/usr/bin/env python3
"""Synthetic incident generator for the Splunk RCA lab. stdlib only.

Each scenario emits ~25-35k events into index=main with sourcetype=rca_sN,
backdated across the last 6 hours, with one root cause buried in the noise.
Seeded, so every run produces the same story (only the clock window moves).

  python3 generate.py --dry-run                 # write NDJSON to ./out, no Splunk
  SPLUNK_HEC_TOKEN=... python3 generate.py      # push all scenarios
  SPLUNK_HEC_TOKEN=... python3 generate.py -s 3 # push scenario 3 only
"""
import argparse, json, os, random, ssl, time, urllib.request
from datetime import datetime, timezone

WINDOW = 6 * 3600
SERVICES = ["api-gateway", "order-svc", "payment-svc", "inventory-svc", "notify-svc"]
R = random.Random()
START = END = 0.0


def hexid(bits):
    return f"{R.getrandbits(bits):0{bits // 4}x}"


def host(svc):
    return f"{svc}-{R.choice(['a1', 'b2', 'c3'])}"


def E(t, svc, sev, logger, msg, tr=None, sp=None, **kw):
    body = {"severity": sev, "logger": logger, "message": msg, **kw}
    if tr:
        body["properties"] = {"traceId": tr, "spanId": sp or hexid(64)}
    return {"t": t, "source": svc, "host": host(svc), "body": body}


def health(per_min=4):
    out = []
    step = 60 / per_min
    for svc in SERVICES:
        t = START + R.random() * step
        while t < END:
            out.append(E(t, svc, "INFO", "c.e.HealthController", "GET /health 200"))
            t += step
    return out


def request(t, tenant=None, sku=None, path=None, status=200, notify_fail=False):
    """One normal request crossing gateway -> order -> inventory -> payment -> notify."""
    tr = hexid(128)
    tenant = tenant or f"t-{R.randint(1000, 1099)}"
    sku = sku or f"SKU-{R.randint(1000, 9000)}"
    path = path or R.choice(["/checkout", "/cart", "/orders"])
    dur = int(R.lognormvariate(5.2, 0.4))
    ev = [
        E(t, "api-gateway", "INFO", "c.e.GatewayController", f"GET {path}", tr, tenantId=tenant, path=path),
        E(t + .005, "order-svc", "INFO", "c.e.OrderService", "Creating order", tr, tenantId=tenant),
        E(t + .020, "inventory-svc", "INFO", "c.e.StockService", f"Reserved stock sku={sku}", tr, sku=sku),
        E(t + .060, "payment-svc", "INFO", "c.e.PaymentService", f"Charge authorised amount={R.randint(5, 400)}.{R.randint(0, 99):02d}", tr),
    ]
    if notify_fail:
        ev.append(E(t + .08, "notify-svc", "ERROR", "c.e.MailSender", "SMTP connection refused host=smtp.internal.corp:25", tr))
    else:
        ev.append(E(t + .08, "notify-svc", "INFO", "c.e.MailSender", "Queued confirmation email", tr))
    sev = "INFO" if status < 500 else "ERROR"
    ev.append(E(t + dur / 1000, "api-gateway", sev, "c.e.GatewayController", f"Completed {status} in {dur}ms", tr,
                tenantId=tenant, path=path, status=status, durationMs=dur))
    return ev


def at():
    return START + R.random() * (END - START)


def sprinkle_errors(n_req, rate=0.002):
    """Low-level background errors so the baseline isn't spotless."""
    out = []
    msgs = ["Connection reset by peer", "Read timed out after 2000ms, retrying", "Pool wait exceeded 500ms"]
    for _ in range(int(n_req * rate * 5)):
        out.append(E(at(), R.choice(SERVICES), R.choice(["WARN", "ERROR"]), "c.e.Client", R.choice(msgs), hexid(128)))
    return out


# ---------------------------------------------------------------- scenarios
def s1():
    """Noisy neighbour: notify-svc SMTP errors dominate."""
    ev = health()
    for _ in range(4000):
        ev += request(at(), notify_fail=R.random() < 0.22)
    return ev + sprinkle_errors(4000)


def s2():
    """Needle by field: one tenant, one path, always 500."""
    ev = health()
    for _ in range(4000):
        t = at()
        if R.random() < 0.03:
            tr = request(t, tenant="t-4471", path="/checkout", status=500)
            tr.insert(2, E(t + .01, "order-svc", "ERROR", "c.e.OrderService",
                           "NullPointerException: shippingAddress is null", tr[0]["body"]["properties"]["traceId"],
                           tenantId="t-4471"))
            ev += tr
        elif R.random() < 0.0015:
            ev += request(t, status=500)
        else:
            ev += request(t)
    return ev


def s3():
    """When did it break: only one of three deploys precedes the errors."""
    ev = health()
    bad = START + WINDOW * 0.63
    deploys = [(START + WINDOW * 0.17, "inventory-svc", "3.1.4"),
               (bad, "order-svc", "2.8.1"),
               (START + WINDOW * 0.75, "payment-svc", "2.3.0")]
    for t, svc, v in deploys:
        ev.append(E(t, "deployer", "INFO", "c.e.ci.Deployer", f"Deploying {svc} version={v} by=ci-bot"))
        ev.append(E(t + 40, "deployer", "INFO", "c.e.ci.Deployer", f"Deploy complete {svc} version={v}"))
    for _ in range(4500):
        t = at()
        r = request(t, status=500 if (t > bad and R.random() < 0.15) else 200)
        if t > bad and R.random() < 0.6:
            r.insert(2, E(t + .01, "order-svc", "ERROR", "c.e.ConfigLoader",
                          "Failed to load config key=discount.rules: value missing, using fallback", r[0]["body"]["properties"]["traceId"]))
        ev += r
    return ev + sprinkle_errors(4500)


def s4():
    """Follow the trace: gateway 504s are caused by a hot-row deadlock in inventory-svc."""
    ev = health()
    mid = START + WINDOW * 0.5
    for _ in range(4500):
        t = at()
        if t > mid and R.random() < 0.035:
            sku = "SKU-9913"
            r = request(t, sku=sku, path="/checkout")[:3]
            tr = r[0]["body"]["properties"]["traceId"]
            r.append(E(t + .03, "inventory-svc", "WARN", "c.e.StockService", f"Lock wait on stock row sku={sku} waited=4800ms", tr, sku=sku))
            r.append(E(t + 5, "inventory-svc", "ERROR", "c.e.StockService", f"Deadlock detected on stock row sku={sku}, transaction rolled back", tr, sku=sku))
            r.append(E(t + 30, "api-gateway", "ERROR", "c.e.GatewayController", "Upstream timeout 504 path=/checkout", tr,
                       status=504, path="/checkout", durationMs=30000))
            ev += r
        else:
            ev += request(t)
            if R.random() < 0.05:  # harmless short lock waits everywhere = decoy
                ev.append(E(t + .03, "inventory-svc", "WARN", "c.e.StockService",
                            f"Lock wait on stock row sku=SKU-{R.randint(1000, 9000)} waited={R.randint(5, 40)}ms", hexid(128)))
    return ev


def fmt(t):
    return datetime.fromtimestamp(t, timezone.utc).strftime("%Y-%m-%d %H:%M:%S,") + f"{int(t % 1 * 1000):03d}"


def s5():
    """Hidden in text: plain-text logs, root cause only visible via rex on the message."""
    ev = health()
    bad = START + WINDOW * 0.33
    provs = ["stripe", "adyen", "paypal", "paynet-eu"]
    for _ in range(9000):
        t, tr, p = at(), hexid(128), R.choice(provs)
        if p == "paynet-eu" and t > bad and R.random() < 0.45:
            ms = R.randint(5000, 30500)
            line = f"{fmt(t)} WARN  [payment-svc] c.e.PaymentClient - Timeout calling provider={p} after {ms}ms traceId={tr}"
        elif R.random() < 0.01:
            line = f"{fmt(t)} WARN  [payment-svc] c.e.PaymentClient - Slow response provider={p} after {R.randint(1000, 2000)}ms traceId={tr}"
        else:
            line = f"{fmt(t)} INFO  [payment-svc] c.e.PaymentClient - Charge authorised provider={p} amount={R.randint(5, 400)}.{R.randint(0, 99):02d} durationMs={R.randint(120, 900)} traceId={tr}"
        ev.append({"t": t, "source": "payment-svc", "host": host("payment-svc"), "body": line})
    return ev


def s6():
    """Red herring: loudest ERROR is harmless S3 retries; the real one is rare and varies per line."""
    ev = health()
    bad = START + WINDOW * 0.66
    for _ in range(4000):
        t = at()
        r = request(t)
        tr = r[0]["body"]["properties"]["traceId"]
        if R.random() < 0.4:
            r.append(E(t + .1, "order-svc", "ERROR", "c.e.ReceiptUploader", "Retrying S3 upload attempt=1/3 failed: SlowDown", tr))
            r.append(E(t + .6, "order-svc", "INFO", "c.e.ReceiptUploader", "S3 upload succeeded attempt=2", tr))
        if t > bad and R.random() < 0.02:
            r.append(E(t + .04, "order-svc", "WARN", "c.e.OrderRepository", "could not serialize access due to concurrent update", tr))
            r.append(E(t + .05, "order-svc", "ERROR", "c.e.OrderService",
                       f"Charge succeeded but order update failed orderId=O-{R.randint(100000, 999999)} chargeId=ch_{hexid(48)}", tr))
        ev += r
    return ev + sprinkle_errors(4000)


SCENARIOS = {1: s1, 2: s2, 3: s3, 4: s4, 5: s5, 6: s6}


# ---------------------------------------------------------------- output
def envelope(e, n):
    return {"time": round(e["t"], 3), "host": e["host"], "source": e["source"],
            "sourcetype": f"rca_s{n}", "index": "main", "event": e["body"]}


def push(token, url, batch):
    ctx = ssl._create_unverified_context()
    req = urllib.request.Request(url, data="".join(json.dumps(b) for b in batch).encode(),
                                 headers={"Authorization": f"Splunk {token}"})
    urllib.request.urlopen(req, context=ctx).read()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("-s", "--scenario", type=int, choices=SCENARIOS)
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--url", default="https://localhost:8088/services/collector/event")
    a = ap.parse_args()
    token = os.environ.get("SPLUNK_HEC_TOKEN")
    if not a.dry_run and not token:
        ap.error("set SPLUNK_HEC_TOKEN or use --dry-run")
    global START, END
    END = time.time()
    START = END - WINDOW
    for n in ([a.scenario] if a.scenario else SCENARIOS):
        R.seed(1000 + n)
        evs = sorted(SCENARIOS[n](), key=lambda e: e["t"])
        envs = [envelope(e, n) for e in evs]
        if a.dry_run:
            os.makedirs("out", exist_ok=True)
            with open(f"out/s{n}.ndjson", "w") as f:
                f.writelines(json.dumps(x) + "\n" for x in envs)
        else:
            for i in range(0, len(envs), 1000):
                push(token, a.url, envs[i:i + 1000])
        print(f"s{n}: {len(envs)} events -> {'out/' if a.dry_run else 'splunk '}sourcetype=rca_s{n}")


if __name__ == "__main__":
    main()
