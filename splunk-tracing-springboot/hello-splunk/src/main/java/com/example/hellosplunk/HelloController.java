package com.example.hellosplunk;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    private static final Logger log =
            LoggerFactory.getLogger(HelloController.class);

    @GetMapping("/hello")
    public Map<String, Object> hello(
            @RequestParam(defaultValue = "World") String name) {

        String requestId = UUID.randomUUID().toString();

        log.info(
                "hello request requestId={} name={} timestamp={}",
                requestId,
                name,
                Instant.now()
        );

        return Map.of(
                "message", "Hello, " + name + "!",
                "requestId", requestId
        );
    }

    @GetMapping("/hello/error")
    public Map<String, String> error() {
        String requestId = UUID.randomUUID().toString();

        log.error(
                "simulated application error requestId={} errorType={}",
                requestId,
                "DemoException"
        );

        return Map.of(
                "message", "An error was logged",
                "requestId", requestId
        );
    }
}
