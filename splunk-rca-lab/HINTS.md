# Hints & answers (spoilers)

Hint ladder per scenario: open one level at a time.

## s1: noisy neighbour
<details><summary>Hint 1</summary>Count errors per service.</details>
<details><summary>Hint 2</summary>

```spl
index=main sourcetype=rca_s1 severity=ERROR | stats count by source
```
</details>
<details><summary>Answer</summary>

`notify-svc`: `SMTP connection refused host=smtp.internal.corp:25` (~860 errors vs a handful elsewhere).
```spl
index=main sourcetype=rca_s1 severity=ERROR | stats count by source, message | sort -count
```
</details>

## s2: needle by field
<details><summary>Hint 1</summary>Look at status 500s grouped by something that identifies a customer.</details>
<details><summary>Hint 2</summary>

```spl
index=main sourcetype=rca_s2 status=500 | top tenantId
```
</details>
<details><summary>Answer</summary>

Tenant `t-4471`, `/checkout` only: `NullPointerException: shippingAddress is null` in order-svc.
```spl
index=main sourcetype=rca_s2 tenantId=t-4471 | stats count by path, status
```
</details>

## s3: when did it break
<details><summary>Hint 1</summary>Chart errors over time, then look for a deploy near the jump.</details>
<details><summary>Hint 2</summary>

```spl
index=main sourcetype=rca_s3 severity=ERROR | timechart span=10m count
index=main sourcetype=rca_s3 source=deployer
```
</details>
<details><summary>Answer</summary>

`order-svc` version `2.8.1` deploy; afterwards `Failed to load config key=discount.rules` + gateway 500s. The inventory and payment deploys are harmless; compare each deploy's time to the error jump.
</details>

## s4: follow the trace
<details><summary>Hint 1</summary>Gateway 504s don't say why. Take one traceId and read every service's lines for it.</details>
<details><summary>Hint 2</summary>

```spl
index=main sourcetype=rca_s4 "Upstream timeout" | head 1
index=main sourcetype=rca_s4 "<traceId>" | table _time source severity message | sort _time
```
</details>
<details><summary>Answer</summary>

`SKU-9913` hot row: `Deadlock detected on stock row` in inventory-svc, only after the midpoint. Confirm: `index=main sourcetype=rca_s4 severity=ERROR "Deadlock" | stats count by sku`. Short `Lock wait ... waited=20ms` WARNs are a decoy.
</details>

## s5: hidden in text
<details><summary>Hint 1</summary>The provider and the milliseconds are only in the message text. Extract them.</details>
<details><summary>Hint 2</summary>

```spl
index=main sourcetype=rca_s5 Timeout | rex "provider=(?<provider>\S+) after (?<ms>\d+)ms" | stats count by provider
```
</details>
<details><summary>Answer</summary>

Provider `paynet-eu`, timeouts of 5–30s starting ~2h into the window.
```spl
index=main sourcetype=rca_s5 Timeout | rex "provider=(?<provider>\S+) after (?<ms>\d+)ms" | where ms>5000 | timechart span=15m count by provider
```
</details>

## s6: red herring
<details><summary>Hint 1</summary>The top ERROR is retries that succeed. Look for ERRORs that appear few times, or normalise the IDs out of the message.</details>
<details><summary>Hint 2</summary>

```spl
index=main sourcetype=rca_s6 severity=ERROR | stats count by logger
```
</details>
<details><summary>Answer</summary>

`Retrying S3 upload` (~1600) is harmless: a success line follows. Real issue: `c.e.OrderService` `Charge succeeded but order update failed`, preceded by WARN `could not serialize access due to concurrent update`, starting ~4h in.
```spl
index=main sourcetype=rca_s6 "Charge succeeded" | rex "orderId=(?<orderId>\S+)" | timechart span=15m count
```
</details>
