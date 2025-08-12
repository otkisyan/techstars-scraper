package org.example.techstarsscraper.service;

import lombok.extern.log4j.Log4j2;
import org.example.techstarsscraper.dto.LocationInfo;
import org.example.techstarsscraper.model.Job;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Log4j2
public class JobDetailFetcher {

    private final String userAgent;
    private final int timeoutMs;

    public JobDetailFetcher(@Value("${scrape.user-agent}") String USER_AGENT,
                            @Value("${scrape.timeout-ms}") int TIMEOUT_MS) {
        this.userAgent = USER_AGENT;
        this.timeoutMs = TIMEOUT_MS;
    }

    public Job fetch(String jobUrl) throws IOException {
        log.info("Fetching job page: {}", jobUrl);
        Document doc = Jsoup.connect(jobUrl)
                .userAgent(userAgent)
                .timeout(timeoutMs)
                .get();

        Element content = extractContentElement(doc);

        String positionName = extractPositionName(content);
        String logoUrl = extractLogoUrl(content, jobUrl);
        String organizationTitle = extractOrganizationTitle(content, logoUrl);
        String organizationUrl = extractOrganizationUrl(content, jobUrl);
        LocationInfo locationInfo = extractLocationInfo(content);
        String laborFunction = extractLaborFunction(content);
        Long postedDateUnix = extractPostedDateUnix(doc, content);

        Element career = doc.selectFirst("[data-testid=careerPage], div[data-testid=careerPage]");
        String descriptionHtml = (career != null) ? career.html().trim() : "";

        Job job = Job.builder()
                .jobPageUrl(jobUrl)
                .positionName(positionName)
                .organizationUrl(organizationUrl)
                .logoUrl(logoUrl)
                .organizationTitle(organizationTitle)
                .laborFunction(laborFunction)
                .locationRaw(locationInfo.raw())
                .locationCity(locationInfo.city())
                .locationState(locationInfo.state())
                .locationCountry(locationInfo.country())
                .postedDateUnix(postedDateUnix)
                .descriptionHtml(descriptionHtml)
                .build();

        log.info("Parsed job: {} (position='{}')", jobUrl, positionName);
        return job;
    }

    private Element extractContentElement(Document doc) {
        Element content = doc.selectFirst("[data-testid=content]");
        if (content == null) {
            log.warn("[data-testid=content] not found, fallback to body");
            content = doc.body();
        }
        return content;
    }

    private String extractPositionName(Element content) {
        if (content == null) return null;
        Element positionEl = content.selectFirst("h1, h2");
        return (positionEl != null) ? positionEl.text().trim() : null;
    }

    private String extractLogoUrl(Element content, String baseUrl) {
        if (content == null) return null;
        Element logoImg = content.selectFirst("img[data-testid=image], img[alt]");
        return (logoImg != null) ? toAbsoluteUrl(logoImg, "src", baseUrl) : null;
    }

    private String extractOrganizationTitle(Element content, String logoUrl) {
        if (content == null) return null;
        if (logoUrl != null) {
            Element logoImg = content.selectFirst("img[data-testid=image], img[alt]");
            if (logoImg != null) {
                Element parent = logoImg.parent();
                for (int i = 0; i < 3 && parent != null; i++) {
                    Optional<Element> orgTextEl = parent.select("p, span, div").stream()
                            .filter(e -> {
                                String txt = e.text().trim();
                                return !txt.isEmpty() && txt.length() <= 100;
                            })
                            .findFirst();
                    if (orgTextEl.isPresent()) {
                        return orgTextEl.get().text().trim();
                    }
                    parent = parent.parent();
                }
            }
        }
        Element compLink = content.selectFirst("a[href*=\"/companies/\"]");
        return (compLink != null) ? compLink.text().trim() : null;
    }

    private String extractOrganizationUrl(Element content, String baseUrl) {
        if (content == null) return null;
        Element applyLink = content.selectFirst("a[type=button][data-testid=button]");
        if (applyLink == null) {
            applyLink = content.selectFirst("a[type=button][data-testid=button-apply-now]");
        }
        if (applyLink != null) {
            return toAbsoluteUrl(applyLink, "href", baseUrl);
        }
        return null;
    }

    private String extractLaborFunction(Element content) {
        if (content == null) return null;

        Element locationElement = findLocationElement(content);
        if (locationElement == null) return null;

        Element laborFunctionEl = locationElement.previousElementSibling();
        if (laborFunctionEl != null) {
            String text = laborFunctionEl.text().trim();
            return text.isEmpty() ? null : text;
        }
        return null;
    }

    private LocationInfo extractLocationInfo(Element content) {
        if (content == null) return new LocationInfo(null, null, null, null);

        Element locationElement = findLocationElement(content);
        String locationRaw = null;

        if (locationElement != null) {
            locationRaw = locationElement.text().trim();
        }

        String locationCity = null, locationState = null, locationCountry = null;
        if (locationRaw != null) {
            String[] parts = Arrays.stream(locationRaw.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toArray(String[]::new);

            if (parts.length == 1) {
                locationCountry = parts[0];
            } else if (parts.length == 2) {
                locationCity = parts[0];
                locationCountry = parts[1];
            } else if (parts.length == 3) {
                locationCity = parts[0];
                locationState = parts[1];
                locationCountry = parts[2];
            } else if (parts.length > 3) {
                locationCity = parts[0];
                locationState = parts[1];
                locationCountry = String.join(", ", Arrays.copyOfRange(parts, 2, parts.length));
            }
        }

        return new LocationInfo(locationRaw, locationCity, locationState, locationCountry);
    }

    private Long extractPostedDateUnix(Document doc, Element content) {
        Element postedEl = findPostedDateElement(content);
        String postedRaw = null;

        if (postedEl != null) {
            postedRaw = postedEl.text();
        } else if (doc != null) {
            Element dateEl = doc.selectFirst(":matchesOwn([A-Za-z]{3,9}\\s+\\d{1,2},\\s+\\d{4})");
            if (dateEl != null) {
                postedRaw = dateEl.text();
            }
        }
        return parseDateToEpoch(postedRaw);
    }

    private Element findLocationElement(Element content) {
        Element postedEl = findPostedDateElement(content);
        if (postedEl == null) return null;

        Element sibling = postedEl.previousElementSibling();
        while (sibling != null) {
            String text = sibling.text().trim();
            if (!text.matches(".*\\d.*")) {
                return sibling;
            }
            sibling = sibling.previousElementSibling();
        }
        return null;
    }

    private Element findPostedDateElement(Element content) {
        if (content == null) return null;
        return content.selectFirst("div:matchesOwn((?i)posted)");
    }

    private String toAbsoluteUrl(Element el, String attr, String baseUrl) {
        String absUrl = el.absUrl(attr);
        if (absUrl != null && !absUrl.isEmpty()) return absUrl;

        String val = el.attr(attr);
        if (val != null && !val.isEmpty()) {
            if (val.startsWith("/")) {
                try {
                    java.net.URL base = new java.net.URL(baseUrl);
                    return base.getProtocol() + "://" + base.getHost() + val;
                } catch (Exception ignored) {}
            }
            return val;
        }
        return null;
    }

    private Long parseDateToEpoch(String dateRaw) {
        if (dateRaw == null) return null;
        Pattern datePattern = Pattern.compile("([A-Za-z]{3,9}\\s+\\d{1,2},\\s+\\d{4})");
        Matcher matcher = datePattern.matcher(dateRaw);
        if (matcher.find()) {
            String dateStr = matcher.group(1);
            List<DateTimeFormatter> formatters = Arrays.asList(
                    DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.ENGLISH),
                    DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.ENGLISH)
            );
            for (DateTimeFormatter fmt : formatters) {
                try {
                    LocalDate ld = LocalDate.parse(dateStr, fmt);
                    return ld.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
                } catch (DateTimeParseException ignored) {}
            }
        }
        return null;
    }
}