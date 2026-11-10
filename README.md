# FinSense — Personal Finance Decision & Spending Advisor

FinSense is a Java desktop application for tracking a personal financial profile, recurring commitments, transactions and savings goals. Its purchase advisor explains a recommendation using transparent rules and a frozen financial snapshot. The app also includes a behaviour questionnaire, purchase history, a read-only what-if simulator, a dashboard, spending insights and CSV export.

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

On the login screen choose **Load viva demo account**. This creates or signs into the reusable demo user and seeds its profile, recurring rent and car EMI, sample transactions, savings goals, behaviour questionnaire and evaluated purchases once.

- Username: `demo_viva`
- Password: `DemoPass2026`

The seed is idempotent: subsequent clicks sign into the same account without duplicating the sample data. The EMI ends soon relative to the current date so the simulator can demonstrate a changing commitment timeline.

## Project layout

```text
src/main/java/com/finsense/       Java launcher, models, JDBC DAOs, services and JavaFX controllers
src/main/resources/fxml/          JavaFX screen layouts
src/main/resources/css/           Shared application and authentication styles
src/test/java/com/finsense/       Unit, database integration and FXML loading tests
docs/                             Architecture diagrams and viva notes
```

See [architecture and viva notes](docs/architecture-and-viva.md) for module responsibilities, diagrams and concept-to-file examples.
