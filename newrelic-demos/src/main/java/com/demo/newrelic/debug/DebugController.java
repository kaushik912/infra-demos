package com.demo.newrelic.debug;

import com.demo.newrelic.widget.Widget;
import com.demo.newrelic.widget.WidgetRepository;
import com.newrelic.api.agent.NewRelic;
import com.newrelic.api.agent.Trace;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * Four endpoints, each triggering a distinct New Relic APM debugging view:
 * slow transaction trace, error/exception trace, DB query span, and a
 * distributed trace to an external HTTP call.
 */
@RestController
@Tag(name = "Debug", description = "Endpoints that exercise New Relic APM's main debugging views")
public class DebugController {

    private final WidgetRepository widgetRepository;
    private final RestClient restClient = RestClient.create();

    public DebugController(WidgetRepository widgetRepository) {
        this.widgetRepository = widgetRepository;
    }

    @Operation(summary = "Slow transaction",
            description = "Sleeps for the given number of millis. In New Relic, this shows up "
                    + "under APM > Transactions as a slow trace once it crosses the apdex/trace threshold.")
    @GetMapping("/api/debug/slow")
    public String slow(@RequestParam(defaultValue = "1500") long millis) throws InterruptedException {
        // Custom attribute so the slow trace is easy to find/filter on in New Relic.
        NewRelic.addCustomParameter("demo.slow.millis", millis);
        Thread.sleep(millis);
        return "slept " + millis + "ms";
    }

    @Operation(summary = "Unhandled exception",
            description = "Always throws. New Relic's Java agent auto-captures unhandled exceptions "
                    + "into the Errors inbox with the full stack trace and transaction context.")
    @GetMapping("/api/debug/error")
    public String error() {
        // Custom error reporting example, in addition to the agent's automatic capture.
        NewRelic.noticeError("Simulated failure from /api/debug/error", false);
        throw new IllegalStateException("Simulated failure for New Relic error tracking demo");
    }

    @Operation(summary = "Database query span",
            description = "Runs a non-trivial JPQL LIKE query plus a per-row lookup loop (N+1 pattern). "
                    + "New Relic's Java agent instruments JPA/Hibernate automatically, so this shows up "
                    + "as separate DB spans in the transaction trace and in the Databases view.")
    @GetMapping("/api/debug/db-query")
    @Trace(dispatcher = true)
    public List<Widget> dbQuery(@RequestParam(defaultValue = "widget") String fragment) {
        List<Widget> matches = widgetRepository.searchByNameFragment(fragment);
        // Deliberate N+1: re-fetch each match by id instead of reusing the list above,
        // so the trace shows several small SELECTs instead of one query.
        return matches.stream()
                .map(w -> widgetRepository.findById(w.getId()).orElseThrow())
                .toList();
    }

    @Operation(summary = "External HTTP call",
            description = "Calls a public HTTP endpoint via RestClient. New Relic auto-instruments "
                    + "outbound HTTP clients, so this appears as an external service span and, if the "
                    + "callee also runs an APM agent, as a distributed trace link.")
    @GetMapping("/api/debug/external-call")
    public String externalCall() {
        return restClient.get()
                .uri("https://httpbin.org/delay/1")
                .retrieve()
                .body(String.class);
    }
}
