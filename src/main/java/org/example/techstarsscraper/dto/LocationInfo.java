package org.example.techstarsscraper.dto;

public record LocationInfo(
        String raw,
        String city,
        String state,
        String country) {
}
