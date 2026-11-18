<div align="center">

# FinSense
### Financial Intelligent Decision System

**Spend wisely. Decide smartly. Save better.**

A Java desktop application that helps users understand their finances, evaluate potential purchases, and make more informed spending decisions.

<br/>

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)
![JavaFX](https://img.shields.io/badge/UI-JavaFX-2F6B9A?logo=openjdk&logoColor=white)
![Database](https://img.shields.io/badge/Database-MySQL_8-4479A1?logo=mysql&logoColor=white)
![Build](https://img.shields.io/badge/Build-Maven-C71A36?logo=apachemaven&logoColor=white)
![Tests](https://img.shields.io/badge/Tests-JUnit_5-25A162?logo=junit5&logoColor=white)

</div>

---

## ✨ Overview

FinSense goes beyond conventional expense tracking. It brings together a user's financial profile, transactions, recurring commitments, savings goals, and spending behaviour to help answer an everyday question:

> **“Can I afford this purchase, and is buying it a sensible decision right now?”**

Its central feature is a transparent, rule-based **Purchase Decision Advisor**. Instead of returning an unexplained score, it evaluates the user's financial situation, presents a recommendation, and explains the factors behind it.

FinSense also includes financial dashboards, spending insights, budgets, purchase history, a behavioural questionnaire, and a read-only purchase simulator.

## 🚀 Key Features

| Feature | What it does |
|---|---|
| **Personal Financial Profile** | Maintains income, savings, expenses, and recurring financial commitments. |
| **Behavioural Assessment** | Uses a questionnaire and spending signals to build an evolving spending-behaviour profile. |
| **Income & Expense Tracking** | Records, categorises, and reviews financial transactions. |
| **Savings Goals** | Creates targets and deadlines, tracks contributions, and shows progress. |
| **Purchase Decision Advisor** | Evaluates a planned purchase and returns an explainable recommendation. |
| **Purchase History** | Stores past evaluations and lets users record purchase outcomes and feedback. |
| **What-if Simulator** | Explores the potential financial impact of a purchase without recording it as a real expense. |
| **Budgets & Insights** | Tracks category budgets, highlights unusual spending, and estimates future spending. |
| **Reports & Export** | Exports transaction data and produces a printable monthly report. |
| **Viva Demo Mode** | Provides a repeatable sample account for demonstrating the application's features. |

## 🧠 How the Purchase Advisor Works

1. The user enters a product, category, and price.
2. FinSense gathers the user's financial information and builds a **financial snapshot**.
3. The decision engine evaluates the purchase using independent decision rules.
4. The application displays a recommendation with reasons and relevant financial context.
5. The evaluation and its snapshot are saved so the decision can be reviewed later.

### Possible recommendations

| Recommendation | Meaning |
|---|---|
| 🟢 **BUY** | The purchase appears financially reasonable. |
| 🟡 **CONSIDER** | The purchase may be manageable, but deserves further thought. |
| 🟠 **WAIT** | Postponing the purchase may lead to a healthier financial outcome. |
| 🔴 **AVOID** | The purchase could put financial stability or important goals under pressure. |

The advisor uses explainable rules rather than machine learning. Its result is decision support, not a guarantee or substitute for professional financial advice.

## 💡 Intelligence & Insights

| Capability | How it works |
|---|---|
| **Feedback-based preferences** | `LearnedPreferenceRule` adjusts a decision score using the user's purchase feedback when enough comparable ratings are available. |
| **Spending forecast** | `ForecastService` uses a least-squares trend over up to six complete months, falling back to an average when fewer than three months are available. |
| **Unusual-spending detection** | `AnomalyService` uses a robust z-score based on the median and median absolute deviation (MAD), comparing recent category spending with historical data. |
| **Smart purchase timing** | For WAIT/AVOID outcomes, `DecisionEngine.affordableFrom` estimates the first month the purchase fits without using the emergency reserve. |
| **Budget-aware decisions** | `BudgetRule` reduces the decision score when a proposed purchase would exceed a category's monthly budget. |
| **Monthly reporting** | `ReportService` generates a printable HTML report that can be saved as a PDF from a browser. |

## 🏗️ Architecture

FinSense follows a **layered architecture** to keep the user interface, business rules, and database operations separate.

```text
┌─────────────────────────────────────────────┐
│              Presentation Layer             │
│             JavaFX + FXML + CSS              │
├─────────────────────────────────────────────┤
│              Controller Layer               │
│        Handles screens and user actions      │
├─────────────────────────────────────────────┤
│               Service Layer                 │
│       Validation and application logic       │
├─────────────────────────────────────────────┤
│             Decision / Domain                │
│   DecisionEngine, rules, models, snapshots   │
├─────────────────────────────────────────────┤
│                 DAO Layer                   │
│       JDBC queries and transactions          │
├─────────────────────────────────────────────┤
│                MySQL 8                      │
│        Persistent application storage        │
└─────────────────────────────────────────────┘
```

**Purchase evaluation flow**

```text
User
  │
  ▼
JavaFX Screen / Controller
  │
  ▼
PurchaseService
  │
  ▼
DecisionEvaluator ─── gathers financial data
  │
  ▼
DecisionEngine ────── applies DecisionRule implementations
  │
  ▼
DecisionResult ────── recommendation + explanations
  │
  ▼
PurchaseDAO ───────── stores evaluation and snapshot
  │
  ▼
MySQL Database
```

The decision engine is kept separate from persistence. The simulator reuses evaluation and projection logic without creating real purchase or expense records.

For architecture diagrams, entity relationships, sequence diagrams, and detailed viva notes, see **[Architecture & Viva Notes](docs/architecture-and-viva.md)**.

## ☕ Java Concepts Demonstrated

| Concept | How it appears in FinSense |
|---|---|
| **Classes & Objects** | Models such as `User`, `Money`, and financial records represent application data. |
| **Encapsulation** | Model objects control access to their internal state. |
| **Inheritance** | Shared controller and DAO behaviour is centralised in base classes such as `BaseController` and `BaseDAO`. |
| **Abstraction & Interfaces** | `DecisionRule` defines a common contract for decision rules. |
| **Polymorphism** | The decision engine applies different rule implementations through the `DecisionRule` interface. |
| **Collections Framework** | Lists and other collections hold rules and financial records for processing. |
| **Stream API** | Processes and groups transaction amounts, including category summaries. |
| **Exception Handling** | Handles invalid operations and database failures. |
| **JDBC** | Connects Java services and DAOs to MySQL using SQL statements and transactions. |
| **JavaFX & FXML** | Builds the desktop interface and connects screens to their controllers. |
| **Lambda Expressions** | Supports concise event handling and functional collection operations. |
| **Java Records & Immutability** | Immutable value carriers such as `FinancialSnapshot` and `Money` help protect evaluated data. |

## 🛠️ Technology Stack

| Technology | Purpose |
|---|---|
| **Java 21+** | Core application and business logic |
| **JavaFX** | Desktop graphical user interface |
| **FXML & CSS** | Screen layout and styling |
| **JDBC** | Database connectivity |
| **MySQL 8** | Persistent data storage |
| **Maven** | Dependency and build management |
| **JUnit 5** | Automated testing |

## ⚙️ Getting Started

### Prerequisites

- JDK 21 or newer
- MySQL 8 running locally
- Git
- Maven (optional when using the included Maven Wrapper)

### 1. Clone the repository

```bash
git clone https://github.com/Vighnesh-SV/FinSense.git
cd FinSense
```

### 2. Configure the database

Copy the example configuration:

```text
config/db.properties.example
```

Create a local file named `config/db.properties` and set the MySQL connection values for your environment. The application initializes the `finsense` schema. Tests use a separate `finsense_test` database.

**Keep your local database credentials private. Do not commit `config/db.properties`.**

### 3. Run FinSense

**Windows terminal**

```bat
mvnw.cmd javafx:run
```

Other ways to launch:

- **VS Code / Antigravity:** Open the project and use the **Run FinSense** launch configuration.
- **IntelliJ IDEA:** Open `pom.xml`, then run `com.finsense.Main` or the Maven `javafx:run` goal.
- **Windows Explorer:** Run `run.bat`.

## 🧪 Run Tests

Run the complete test suite:

```bat
mvnw.cmd clean test
```

The suite uses JUnit and the isolated `finsense_test` database. Configure test database access in your local MySQL settings before running it.

## 🎓 Viva Demonstration

FinSense includes a ready-to-use demonstration account. On the login screen, select **Open the viva demo account**.

The demo data is rebuilt from scratch each time the demo account is opened, so each presentation starts from a consistent state. It includes:

- Six months of income and expense history
- Rent and car EMI commitments
- Three savings goals and four budgets
- An unusually large dining expense for insights
- A completed behavioural questionnaire
- Five purchase evaluations, including three marked as purchased and rated

**Demo credentials**

| Field | Value |
|---|---|
| Username | `demo_viva` |
| Password | `DemoPass2026` |

Changes made during a demo session are discarded the next time the demo account is opened.

## 🖼️ Capture Application Screenshots

The screenshot tour renders the application's screens using demo data:

```bat
mvnw.cmd test -Dtest=ScreenshotTour -Dfinsense.shots=target/shots
```

PNG screenshots are saved under `target/shots`. The tour uses the `finsense_test` database, not the real application database.

## 📁 Project Structure

```text
FinSense/
├── config/                         # Local database configuration
├── docs/
│   └── architecture-and-viva.md    # Diagrams, architecture and viva notes
├── src/
│   ├── main/
│   │   ├── java/com/finsense/
│   │   │   ├── model/              # Domain models and value objects
│   │   │   ├── dao/                # JDBC data access
│   │   │   ├── service/            # Application and business services
│   │   │   ├── decision/           # Purchase decision engine and rules
│   │   │   ├── controller/         # JavaFX controllers
│   │   │   └── util/               # Shared utilities
│   │   └── resources/
│   │       ├── fxml/               # JavaFX screen layouts
│   │       └── css/                # Application styles
│   └── test/java/com/finsense/     # Unit, integration and UI tests
├── pom.xml
├── mvnw
├── mvnw.cmd
└── README.md
```

## 📚 Documentation

- **[Architecture & Viva Notes](docs/architecture-and-viva.md)** — layered design, use-case diagram, class diagram, ER diagram, sequence diagrams, and viva questions.
- **[Source code](src/)** — implementation of the application.

## 🔐 Privacy & Data Integrity

- User-owned records are queried within the authenticated user's scope.
- SQL operations use JDBC data-access classes.
- Financial values use an immutable `Money` type backed by integer paise to avoid floating-point rounding issues.
- Multi-step database changes use transactions so related writes succeed or roll back together.
- The what-if simulator does not persist hypothetical purchases as real expenses.

## 👥 Project

**FinSense — Financial Intelligent Decision System**

Developed as a Java desktop academic project to demonstrate object-oriented programming, layered application design, database connectivity, and explainable financial decision support.

---

<div align="center">

**FinSense — Spend Wisely. Decide Smartly. Save Better.**

</div>
