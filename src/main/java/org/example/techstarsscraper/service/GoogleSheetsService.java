package org.example.techstarsscraper.service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;
import lombok.extern.log4j.Log4j2;
import org.example.techstarsscraper.model.Job;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Log4j2
@ConditionalOnProperty(prefix = "scrape.google-sheets.upload", name = "enabled", havingValue = "true")
public class GoogleSheetsService {

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES =
            Collections.singletonList(SheetsScopes.SPREADSHEETS);
    private final String spreadsheetId;
    private final String credentialsFilePath;
    private final String tokensDirectoryPath;
    private final String applicationName;

    public GoogleSheetsService(
            @Value("${scrape.google-sheets.upload.spreadsheet-id}") String spreadsheetId,
            @Value("${scrape.google-sheets.upload.credentials.file-path}") String credentialsFilePath,
            @Value("${scrape.google-sheets.upload.tokens.directory-path}") String tokensDirectoryPath,
            @Value("${spring.application.name:techstars-scraper}") String applicationName) {

        if (spreadsheetId == null || spreadsheetId.isBlank()) {
            throw new IllegalArgumentException(
                    "Google Sheets spreadsheet-id is missing or empty. Please set 'google-sheets.upload.spreadsheet-id'.");
        }

        this.spreadsheetId = spreadsheetId;
        this.credentialsFilePath = credentialsFilePath;
        this.tokensDirectoryPath = tokensDirectoryPath;
        this.applicationName = applicationName;
    }

    private Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT)
            throws IOException {

        InputStream in = GoogleSheetsService.class.getResourceAsStream(credentialsFilePath);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + credentialsFilePath);
        }
        GoogleClientSecrets clientSecrets =
                GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(tokensDirectoryPath)))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    public void appendJobsToSheet(List<Job> jobs)
            throws IOException, GeneralSecurityException {

        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();

        Sheets service = new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(applicationName)
                .build();

        String range = "Sheet1!A1:Z1";
        ValueRange existingHeader = service.spreadsheets().values()
                .get(spreadsheetId, range)
                .execute();

        List<List<Object>> values = new ArrayList<>();

        List<Object> headers = List.of("ID", "Job URL", "Position", "Organization URL", "Logo URL",
                "Organization Title", "Labor Function", "Location Raw", "City", "State", "Country", "Posted Date", "Tags", "Description");

        if (existingHeader.getValues() == null || existingHeader.getValues().isEmpty()
                || !existingHeader.getValues().get(0).equals(headers)) {
            values.add(headers);
        }

        for (Job job : jobs) {
            values.add(List.of(
                    job.getId() != null ? job.getId().toString() : "",
                    job.getJobPageUrl() != null ? job.getJobPageUrl() : "",
                    job.getPositionName() != null ? job.getPositionName() : "",
                    job.getOrganizationUrl() != null ? job.getOrganizationUrl() : "",
                    job.getLogoUrl() != null ? job.getLogoUrl() : "",
                    job.getOrganizationTitle() != null ? job.getOrganizationTitle() : "",
                    job.getLaborFunction() != null ? job.getLaborFunction() : "",
                    job.getLocationRaw() != null ? job.getLocationRaw() : "",
                    job.getLocationCity() != null ? job.getLocationCity() : "",
                    job.getLocationState() != null ? job.getLocationState() : "",
                    job.getLocationCountry() != null ? job.getLocationCountry() : "",
                    job.getPostedDateUnix() != null ? job.getPostedDateUnix().toString() : "",
                    job.getTags() != null ? job.getTags() : "",
                    job.getDescriptionHtml() != null ? job.getDescriptionHtml() : ""
            ));
        }

        String rangeAppend = "Sheet1!A1";
        ValueRange body = new ValueRange().setValues(values);
        service.spreadsheets().values()
                .append(spreadsheetId, rangeAppend, body)
                .setValueInputOption("RAW")
                .execute();
        log.info("Uploaded {} jobs to Google Sheets", jobs.size());
    }
}


