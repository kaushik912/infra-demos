# RCA scenarios

All in `index=main`, one `sourcetype` each. Time window: last 6h (`earliest=-6h`).
Find the root cause using only Tier 1–3 SPL (see `SPL_CHEATSHEET.md`). Don't open `HINTS.md` until you're stuck.

**Services:** `api-gateway`, `order-svc`, `payment-svc`, `inventory-svc`, `notify-svc` (+ `deployer` in s3). Health-check lines are noise.
**Fields (JSON scenarios):** `severity`, `logger`, `message`, `properties.traceId`, plus `tenantId`, `path`, `status`, `durationMs`, `sku` where relevant.

| # | sourcetype | Complaint | Practices |
|---|---|---|---|
| 1 | `rca_s1` | "Error alerts have been firing all day. Which service is the problem?" | scope, `stats count by`, `top` |
| 2 | `rca_s2` | "A customer says checkout always fails. Others seem fine." | `status`, `top`/`rare`, field filters |
| 3 | `rca_s3` | "Orders started breaking sometime this afternoon. When, and why?" | `timechart`, `NOT`, time narrowing |
| 4 | `rca_s4` | "Users get 504s at checkout in the second half of the window." | `spath`, traceId pivot, `sort _time` |
| 5 | `rca_s5` | "Payments feel slow. Logs are plain text, no JSON." | `rex`, `eval`, `where` |
| 6 | `rca_s6` | "Customers charged but orders not confirmed. Error count is huge, though." | red herrings, `rare`, `rex` normalising |

## How to work each one

1. Start: `index=main sourcetype=rca_sN earliest=-6h | head 20` — look at the data first.
2. Run the loop: **when** (`timechart`) → **what** (`stats count by logger, message`) → **who/where** (`stats count by <candidate field>`) → **why** (one traceId, `sort _time`).
3. Write your answer (cause + the SPL that proved it) before opening `HINTS.md`.
4. Finish with: how would you run this investigation on your work's logs?
