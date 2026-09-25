package com.demo.newrelic.widget;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class WidgetSeeder implements CommandLineRunner {

    private final WidgetRepository widgetRepository;

    public WidgetSeeder(WidgetRepository widgetRepository) {
        this.widgetRepository = widgetRepository;
    }

    @Override
    public void run(String... args) {
        widgetRepository.save(new Widget("blue-gear", "hardware"));
        widgetRepository.save(new Widget("red-gear", "hardware"));
        widgetRepository.save(new Widget("green-sprocket", "hardware"));
        widgetRepository.save(new Widget("cloud-widget", "software"));
        widgetRepository.save(new Widget("api-widget", "software"));
    }
}
