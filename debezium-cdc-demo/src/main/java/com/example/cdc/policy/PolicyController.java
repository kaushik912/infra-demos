package com.example.cdc.policy;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/** Plain CRUD. Every write here becomes a Debezium change event via the Postgres WAL. */
@RestController
@RequestMapping("/api/policies")
public class PolicyController {

    public record PolicyRequest(String policyNumber, String policyHolder) {
    }

    private final PolicyRepository repository;

    public PolicyController(PolicyRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Policy> list() {
        return repository.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Policy create(@RequestBody PolicyRequest req) {
        LocalDateTime now = LocalDateTime.now();
        return repository.save(new Policy(req.policyNumber(), req.policyHolder(), now, now.plusYears(1)));
    }

    @PutMapping("/{id}")
    public Policy update(@PathVariable Long id, @RequestBody PolicyRequest req) {
        Policy policy = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy " + id + " not found"));
        policy.setPolicyNumber(req.policyNumber());
        policy.setPolicyHolder(req.policyHolder());
        return repository.save(policy);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy " + id + " not found");
        }
        repository.deleteById(id);
    }
}
