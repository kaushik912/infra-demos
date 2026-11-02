# SPL core cheat sheet (Tiers 1–3)

Pipeline: `filter | parse | project | order`. Widen with `stats count by`, narrow with a filter, repeat.

## Tier 1: find and narrow (before the first `|`)
```spl
index=main source=api-gateway earliest=-4h latest=now     // always index/source/time first
severity=ERROR                                            // field filter (JSON auto-extracts if sourcetype is JSON)
"timeout"   *conn*                                        // free text on _raw
severity=ERROR NOT "/health"                              // NOT out the noise
(severity=ERROR OR severity=WARN) source=service-*        // parens + wildcards
earliest=-15m@m latest=@m                                 // @m / @h snap to boundary
```

## Tier 2: see the shape
```spl
| table _time source severity message
| fields - _raw
| sort -_time          | head 20       | tail 20
| stats count by source                // THE command
| stats count by source, message | sort -count
| top limit=10 tenantId                // most common, with %
| rare tenantId                        // least common
| dedup properties.traceId             // one row per value
| timechart span=5m count              // when?
| timechart span=5m count by severity
```

## Tier 3: shape the data
```spl
| spath                                           // all JSON fields
| spath "properties.traceId"                      // just one (faster)
| rex "after (?<ms>\d+)ms"                        // regex capture from _raw
| rex field=message "orderId=(?<orderId>\S+)"
| eval slow=if(durationMs>1000,"Y","N")
| eval ms=tonumber(ms)
| where ms>5000                                   // post-extraction filter (field exists now)
| rename properties.traceId as traceId
```

## `search` vs `where`
- Base search / `search`: simple field=value, wildcards allowed, case-insensitive values.
- `where`: expressions (`>`, `<`, functions), case-sensitive strings, quote string literals.
- A field must exist before you filter on it; if you extract it with `rex`/`spath`, filter after.

## RCA loop
1. **When?** `timechart span=10m count by severity`
2. **What?** `severity=ERROR | stats count by source, logger, message | sort -count`
3. **Who/where?** `stats count by <tenantId|host|version|sku>` on the failing events
4. **Why?** one traceId: `"<id>" | table _time source severity message | sort _time`
