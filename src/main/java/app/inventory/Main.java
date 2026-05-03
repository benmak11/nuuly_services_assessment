package app.inventory;

import app.inventory.db.Database;
import io.javalin.Javalin;

public class Main {

    private static final int PORT = 7070;
    private static final String DB_PATH = "inventory.db";

    public static void main(String[] args) throws Exception {
        Database database = Database.file(DB_PATH);
        database.migrate();

        Javalin app = Javalin.create().start(PORT);

        Runtime.getRuntime().addShutdownHook(new Thread(app::stop));
    }
}
