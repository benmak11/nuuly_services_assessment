# Nuuly Inventory Service

A small RESTful inventory management service. Tracks stock by SKU, accepts new
stock, and processes purchases against current inventory.

The API surface:

- `GET  /inventory/{skuId}` — read current quantity for a SKU
- `POST /inventory/{skuId}` — add stock (creates the SKU if it doesn't exist)
- `POST /inventory/{skuId}/purchase` — deduct stock if sufficient quantity is available

## Tech stack

| Layer       | Choice                                |
| ----------- |---------------------------------------|
| Language    | Java 21                               |
| Build       | Gradle 9 (via wrapper)                |
| Web         | [Javalin](https://javalin.io/) 7      |
| Persistence | SQLite (via `org.xerial:sqlite-jdbc`) |
| JSON        | Jackson                               |
| Tests       | JUnit 5                               |

SQLite was chosen as the embedded datastore because it has a mature, pure-JDBC
Java store (no native install required), strong ACID semantics, and a
single-writer model that makes the "insufficient inventory" race condition
straightforward to reason about.

## Prerequisites

- **JDK 21** on your `PATH` (verify with `java -version`).
  - The Gradle build uses a toolchain pinned to Java 21. If your default JDK is
    older, Gradle will attempt to provision a 21 toolchain automatically.
- No need to install Gradle — the included wrapper (`./gradlew`) downloads the
  correct version (9.0.0) on first run.
- No need to install SQLite — the `sqlite-jdbc` driver bundles native binaries
  for macOS, Linux, and Windows.

## Running locally

From the project root:

```bash
# Compile (first run will download dependencies)
./gradlew compileJava

# Run the service
./gradlew run
```

The service listens on **`http://localhost:7070`**.

On first start, a SQLite database file `inventory.db` is created in the working
directory and the `inventory` table is migrated automatically. Subsequent runs
reuse the same file, preserving data between restarts.

To stop the server, press `Ctrl+C`. A shutdown hook stops Javalin gracefully.

### Resetting local data

To wipe local state and start from a fresh database:

```bash
rm -f inventory.db inventory.db-wal inventory.db-shm
```

The `-wal` and `-shm` files are created by SQLite's WAL journal mode.

### Choosing a different port

The port is currently a constant (`7070`) in `app.inventory.Main`. If you need
to change it for local dev, edit the `PORT` field there.

## Running tests

```bash
./gradlew test
```

Test results are written to `build/reports/tests/test/index.html`.

## Testing the API with Bruno

A [Bruno](https://www.usebruno.com/) collection is included under
`NuulyInventoryServiceCollection/` for exercising the endpoints against a
running local instance. Open the directory in Bruno to load the collection,
which contains requests for reading a SKU, creating/adding stock, and
processing a purchase.

## Project layout

```
.
├── build.gradle                          # Gradle build config
├── settings.gradle
├── gradlew, gradlew.bat                  # Gradle wrapper
├── README.md
├── docs/
│   └── instructions.md                   # Assessment instructions
├── NuulyInventoryServiceCollection/      # Bruno API collection
└── src/
    ├── main/java/app/inventory/
    │   ├── Main.java                     # Entry point: migrates DB, starts Javalin
    │   ├── db/
    │   │   └── Database.java             # SQLite connection + schema migration
    │   ├── http/
    │   │   └── InventoryController.java  # Javalin route handlers
    │   ├── model/
    │   │   ├── InventoryItem.java
    │   │   └── InventoryQuantity.java
    │   └── repository/
    │       ├── InventoryRepository.java  # SQL access for the inventory table
    │       └── PurchaseResult.java
    └── test/java/app/inventory/
```

## Database schema

A single table is created on startup:

```sql
CREATE TABLE IF NOT EXISTS inventory (
    sku_id   TEXT PRIMARY KEY NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity >= 0)
);
```

Connections are opened with `PRAGMA foreign_keys = ON` and
`PRAGMA journal_mode = WAL` for better read concurrency.
