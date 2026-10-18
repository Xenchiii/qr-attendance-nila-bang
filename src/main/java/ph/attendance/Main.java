package ph.attendance;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        Db.init();
        Javalin app = Javalin.create(config -> config.staticFiles.add("/public", Location.CLASSPATH));
        AuthRoutes.mount(app);
        AttendanceRoutes.mount(app);
        AdminRoutes.mount(app);
        app.exception(Exception.class, (error, ctx) -> {
            error.printStackTrace();
            Http.fail(ctx, 500, "Something went wrong on the server");
        });
        app.start(Integer.parseInt(System.getenv().getOrDefault("PORT", "8080")));
    }
}
