# FinSense — Financial Intelligent Decision System

FinSense (project topic: **Financial Intelligent Decision System**) is a Java desktop application for tracking a personal financial profile, recurring commitments, transactions and savings goals. Its purchase advisor explains a recommendation using transparent rules and a frozen financial snapshot. The app also includes a behaviour questionnaire, purchase history, a read-only what-if simulator, a dashboard, spending insights and CSV export.

## Requirements

- JDK 21 or newer.
- MySQL 8 running locally.
- Copy `config/db.properties.example` to `config/db.properties` and set the local connection values. The app initializes the `finsense` schema. Tests use the separate `finsense_test` database.

## Run

- Windows terminal: `mvnw.cmd javafx:run`
- VS Code / Antigravity: open the project and use the **Run FinSense** launch configuration.
- IntelliJ IDEA: open `pom.xml`, then run `com.finsense.Main` or the Maven `javafx:run` goal.
- Explorer: run `run.bat`.

## Test

Run the complete suite with `mvnw.cmd clean test`. The suite uses JUnit and the isolated test database; configure test database access in the local MySQL settings before running it.

## Viva demo account

On the login screen choose **Open the viva demo account**. The demo user is **rebuilt from scratch on every click**, so each demonstration starts from the same data: six months of income and spending, rent and a car EMI, three savings goals, four budgets, one unusually large dining expense, a completed questionnaire, and five purchase evaluations (three bought and rated).

- Username: `demo_viva`
- Password: `DemoPass2026`

Anything changed in the demo account is discarded the next time it is opened.

## Intelligence features

| Feature | Where | How it works |
|---|---|---|
| Learns from feedback | Purchase history → *Worth it / Neutral / Regret* | `LearnedPreferenceRule` adjusts the score by up to ±15 from your ratings in the same category (≥2 ratings) or same need/want (≥3). |
| Spending forecast | Insights, Dashboard | `ForecastService`: least-squares trend over up to 6 complete months (average when fewer than 3). |
| Unusual spending | Insights, Dashboard | `AnomalyService`: robust z-score (median / MAD) per category, last 60 days vs. the past year. |
| Smart timing | Advisor, Simulator | For WAIT/AVOID, `DecisionEngine.affordableFrom` finds the first month the item fits without touching the emergency reserve. |
| Budgets | Budgets screen | Monthly limit per category; `BudgetRule` lowers the score when a purchase would break one. |
| Monthly report | Insights → *Export monthly report* | `ReportService` writes a printable HTML file (open in a browser, Print → Save as PDF). |

## Screenshots of every screen

`mvnw.cmd test -Dtest=ScreenshotTour -Dfinsense.shots=target/shots` renders each screen with the demo data into PNG files. It uses the `finsense_test` database, never the real one.

## Project layout

```text
src/main/java/com/finsense/       Java launcher, models, JDBC DAOs, services and JavaFX controllers
src/main/resources/fxml/          JavaFX screen layouts
src/main/resources/css/           Shared application and authentication styles
src/test/java/com/finsense/       Unit, database integration and FXML loading tests
docs/                             Architecture diagrams and viva notes
```

See [architecture and viva notes](docs/architecture-and-viva.md) for module responsibilities, diagrams and concept-to-file examples.
