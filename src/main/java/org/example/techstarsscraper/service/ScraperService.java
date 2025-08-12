package org.example.techstarsscraper.service;

import io.github.bonigarcia.wdm.WebDriverManager;
import jakarta.transaction.Transactional;
import lombok.extern.log4j.Log4j2;
import org.example.techstarsscraper.model.Job;
import org.example.techstarsscraper.repository.JobRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Log4j2
public class ScraperService {

    private final JobRepository jobRepository;
    private final JobDetailFetcher jobDetailFetcher;
    private final GoogleSheetsService googleSheetsService;
    private final String baseScrapeUrl;
    private final String userAgent;
    private final boolean googleSheetsUploadEnabled;

    public ScraperService(JobRepository jobRepository,
                          JobDetailFetcher jobDetailFetcher,
                          @Nullable GoogleSheetsService googleSheetsService,
                          @Value("${scrape.base-url}") String baseScrapeUrl,
                          @Value("${scrape.user-agent}") String userAgent,
                          @Value("${scrape.google-sheets.upload.enabled}") boolean googleSheetsUploadEnabled) {
        this.jobRepository = jobRepository;
        this.jobDetailFetcher = jobDetailFetcher;
        this.googleSheetsService = googleSheetsService;
        this.baseScrapeUrl = baseScrapeUrl;
        this.userAgent = userAgent;
        this.googleSheetsUploadEnabled = googleSheetsUploadEnabled;
    }

    @Transactional
    public List<Job> scrapeByFunction(String jobFunction) throws IOException, GeneralSecurityException {
        String url = buildListUrl(jobFunction);
        // I will only fetch some of the job listings, not all
        Document doc = fetchDocument(url, 1, 5);
        Map<String, List<String>> jobTagsMap = extractJobTagsMap(doc);
        List<Job> savedJobs = fetchAndSaveJobs(jobTagsMap);

        if (!savedJobs.isEmpty() && googleSheetsUploadEnabled && googleSheetsService != null) {
            googleSheetsService.appendJobsToSheet(savedJobs);
        }

        return savedJobs;
    }

    public List<Job> findAll() {
        return jobRepository.findAll();
    }

    private String buildListUrl(String jobFunction) {
        if (jobFunction == null || jobFunction.isBlank()) return baseScrapeUrl;
        String encoded = URLEncoder.encode(jobFunction, StandardCharsets.UTF_8);
        return baseScrapeUrl + "?filter=" + encoded;
    }

    private Document fetchDocument(String url, int loadMoreClicks, int maxScrolls) {
        WebDriver driver = createWebDriver();
        try {
            driver.get(url);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

            By jobSelector = By.cssSelector("[itemtype='https://schema.org/JobPosting']");
            By loadMoreSelector = By.cssSelector("button[data-testid='load-more']");

            waitForJobPostingElement(wait, jobSelector);
            hideOnetrustPolicyBanner(driver);

            for (int i = 0; i < loadMoreClicks; i++) {
                if (!clickLoadMore(driver, wait, jobSelector, loadMoreSelector)) {
                    break;
                }
            }

            for (int s = 0; s < maxScrolls; s++) {
                if (!scrollDownAndWaitForNewElements(driver, wait, jobSelector)) {
                    break;
                }
            }

            String pageSource = driver.getPageSource();
            if (pageSource == null) {
                throw new IllegalStateException("Page source is null for URL: " + url);
            }
            return Jsoup.parse(pageSource, url);
        } finally {
            driver.quit();
        }
    }

    private WebDriver createWebDriver() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();

        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--user-agent=" + userAgent);
        return new ChromeDriver(options);
    }

    private void waitForJobPostingElement(WebDriverWait wait, By jobSelector) {
        wait.until(ExpectedConditions.presenceOfElementLocated(jobSelector));
    }

    private void hideOnetrustPolicyBanner(WebDriver driver) {
        ((JavascriptExecutor) driver).executeScript(
                "var elem = document.getElementById('onetrust-policy-text');" +
                        "if (elem) { elem.style.display='none'; }"
        );
    }

    private boolean clickLoadMore(WebDriver driver, WebDriverWait wait, By jobSelector, By loadMoreSelector) {
        int beforeCount = driver.findElements(jobSelector).size();
        wait.until(ExpectedConditions.elementToBeClickable(loadMoreSelector));
        WebElement loadMoreBtn = driver.findElement(loadMoreSelector);
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", loadMoreBtn);
        loadMoreBtn.click();

        try {
            return wait.until(driverInstance -> {
                int afterCount = driverInstance.findElements(jobSelector).size();
                return afterCount > beforeCount;
            });
        } catch (TimeoutException e) {
            return false;
        }
    }

    private boolean scrollDownAndWaitForNewElements(WebDriver driver, WebDriverWait wait, By jobSelector) {
        int previousCount = driver.findElements(jobSelector).size();
        ((JavascriptExecutor) driver).executeScript("window.scrollBy(0, document.body.scrollHeight);");

        try {
            return wait.until(driverInstance -> {
                int afterCount = driverInstance.findElements(jobSelector).size();
                return afterCount > previousCount;
            });
        } catch (TimeoutException e) {
            return false;
        }
    }


    private Map<String, List<String>> extractJobTagsMap(Document doc) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (doc == null) return result;

        Elements jobElements = doc.select("[itemtype='https://schema.org/JobPosting']");
        for (Element jobEl : jobElements) {
            Optional<String> jobUrlOpt = extractJobUrl(jobEl);
            if (jobUrlOpt.isPresent()) {
                String cleanLink = cleanLink(jobUrlOpt.get());
                List<String> tags = extractTags(jobEl);
                result.put(cleanLink, tags);
                log.info("Job link found: {} | Tags: {}", cleanLink, tags);
            }
        }
        return result;
    }

    private Optional<String> extractJobUrl(Element jobEl) {
        if (jobEl == null) return Optional.empty();

        Elements linkEls = jobEl.select("a[href]");
        for (Element a : linkEls) {
            String href = a.absUrl("href");
            if (href != null && !href.isBlank()
                    && href.contains("/jobs/")
                    && href.contains("jobs.techstars.com")) {
                return Optional.of(href);
            }
        }
        return Optional.empty();
    }


    private List<String> extractTags(Element jobEl) {
        if (jobEl == null) return Collections.emptyList();
        return jobEl.select("[data-testid=tag]")
                .stream()
                .map(Element::text)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private String cleanLink(String link) {
        if (link == null) return null;
        int q = link.indexOf('?');
        return (q >= 0) ? link.substring(0, q) : link;
    }

    private List<Job> fetchAndSaveJobs(Map<String, List<String>> jobTagsMap) {
        List<Job> saved = new ArrayList<>();
        if (jobTagsMap == null || jobTagsMap.isEmpty()) return saved;

        for (Map.Entry<String, List<String>> entry : jobTagsMap.entrySet()) {
            String jobUrl = entry.getKey();
            List<String> tags = entry.getValue();

            try {
                Job job = jobDetailFetcher.fetch(jobUrl);
                if (job == null) {
                    log.debug("JobDetailFetcher returned null for URL: {}", jobUrl);
                    continue;
                }

                boolean alreadyPresent = !jobRepository.findByJobPageUrl(job.getJobPageUrl()).isEmpty();
                if (!alreadyPresent) {
                    job.setTags(String.join(", ", tags));
                    Job savedJob = jobRepository.save(job);
                    saved.add(savedJob);
                    log.info("Saved job: {} (source url {})", savedJob.getId(), jobUrl);
                } else {
                    log.debug("Job already exists in repository: {}", job.getJobPageUrl());
                }

            } catch (Exception e) {
                log.warn("Failed to fetch/save job at {}: {}", jobUrl, e.getMessage());
                log.debug("Stacktrace:", e);
            }
        }
        return saved;
    }
}
