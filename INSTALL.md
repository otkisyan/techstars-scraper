# Installation

### Prerequisites

- JDK 17+
- Maven
- Docker and Docker Compose

### Installation

**Before you start:** Edit properties in `src/main/resources/application.yaml` or provide environment variables in `.env` for more security and customization or leave it as it is.

1. Clone the repo

```bash
> git clone https://github.com/otkisyan/techstars-scraper.git
> cd techstars-scraper
```

1. Compile and package the code

```bash
> mvn clean package
```

3. Run the Application

```bash
> docker-compose up -d 
```

### Endpoints:

* http://localhost:8080 - Application
* http://localhost:5432 - PostgreSQL (database: `$POSTGRES_DB`, username: `$POSTGRES_USER`, password: `$POSTGRES_PASSWORD`)

## Using Google Sheets Upload
The application can automatically append scraped jobs to a Google Sheet.


Steps to enable ([Google Sheets API Documentation](https://developers.google.com/workspace/sheets/api/quickstart/java)):
- Create a Google Cloud project and enable the Google Sheets API.
- Configure the OAuth consent screen
- Authorize credentials for a desktop application
- Download the credentials file.
- Place the credentials file inside `src/main/resources/credentials.json`
- Set environment variables in `.env` or edit properties in `application.yaml`:

```bash
GOOGLE_SHEETS_UPLOAD_ENABLED=true
GOOGLE_SHEETS_UPLOAD_SPREADSHEET_ID=<your_spreadsheet_id>
GOOGLE_SHEETS_CREDENTIALS_FILE_PATH=/credentials.json
GOOGLE_SHEETS_TOKENS_DIRECTORY_PATH=tokens
```

- `GOOGLE_SHEETS_UPLOAD_SPREADSHEET_ID` is the part of your Google Sheets URL between `/d/` and `/edit`.
- `GOOGLE_SHEETS_TOKENS_DIRECTORY_PATH` is where OAuth tokens will be stored.

**First-time authentication**

On the first run, check the application logs in the console.  You will see a message like:

```
Please open the following address in your browser:
https://accounts.google.com/o/oauth2/auth?...
```
Copy the link, paste it into your browser, grant access to your sheets. The access and refresh tokens will be saved to the `GOOGLE_SHEETS_TOKENS_DIRECTORY_PATH` directory.
Everything is ready.