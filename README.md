# Techstars Job Scraper

A Spring Boot application for scraping job postings from [jobs.techstars.com](https://jobs.techstars.com) filtered by a specific **job function**.  
The scraper collects detailed job information and stores it in a PostgreSQL database.

### [Installation](INSTALL.md)

## Description

This application allows you to fetch job postings from the Techstars Jobs platform by specifying a **job function**.  
It scrapes the list of jobs, visits each posting, and extracts key details such as position name, company information, location, tags, posting date, and job description (HTML included).

---

## Features

- Scrape job listings by **job function**
- Extract detailed job data:
    - Job page URL
    - Position name
    - Organization URL
    - Logo URL
    - Organization title
    - Labor function
    - Location (raw, city, state, country)
    - Posted date (Unix timestamp)
    - Description (HTML format)
    - Tags (comma-separated)
- Store results in **PostgreSQL**
- Optionally upload results to Google Sheets
- REST API for triggering scraping and retrieving stored jobs
- Configurable User-Agent, base URL, and timeout.

---

## Endpoints

Scrape jobs by *job function* using the `filter` query param on `jobs.techstars.com/jobs` page. Set it up on the website, copy it and pass to the scrape API.

### 1. Trigger a Scrape

Example scraping "Software Engineering jobs"
```
curl -X POST "http://localhost:8080/api/scrape?function=eyJqb2JfZnVuY3Rpb25zIjpbIlNvZnR3YXJlIEVuZ2luZWVyaW5nIl19
```

Response:
```json
{
  "saved": 12
}
```
### 2. Get All Stored Jobs

```http 
curl http://localhost:8080/api/jobs
```
Response: 

```json
   {
        "id": 1,
        "jobPageUrl": "https://jobs.techstars.com/companies/xxxx/jobs/xxxxx#content",
        "positionName": "Software Engineer",
        "organizationUrl": "https://www.linkedin.com/jobs/view/xxxxxxx",
        "logoUrl": "https://cdn.getro.com/companies/xxxxx",
        "organizationTitle": "xxxxx",
        "laborFunction": "Software Engineering",
        "locationRaw": "New York, NY, USA",
        "locationCity": "New York",
        "locationState": "NY",
        "locationCountry": "USA",
        "postedDateUnix": 1754870400,
        "descriptionHtml": "....",
        "tags": "Health, Hospital & Health Care, 20 - 30 employees"
    }
```