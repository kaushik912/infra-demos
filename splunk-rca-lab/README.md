# Splunk RCA lab

Six synthetic incidents (needle-in-a-haystack) to build core SPL muscle memory. No advanced SPL needed.

- `generate.py`: seeded generator, pushes to Splunk HEC (stdlib only)
- `SCENARIOS.md`: the complaints (start here)
- `SPL_CHEATSHEET.md`: Tier 1–3 SPL
- `HINTS.md`: hint ladder + answers (spoilers)

## Run

```bash
docker start splunk                      # existing container; UI http://localhost:8000
export SPLUNK_HEC_TOKEN=<hec token>      # Settings → Data Inputs → HTTP Event Collector, token must allow index main
python3 generate.py                      # all 6 scenarios (~180k events)
python3 generate.py -s 3                 # just one
python3 generate.py --dry-run            # write ./out/*.ndjson only
```

Events are backdated across the last 6h, so re-run to refresh the window (old data stays; delete with `| delete` or search `earliest=-6h`).
Verify: `index=main sourcetype=rca_s* earliest=-6h | stats count by sourcetype`
