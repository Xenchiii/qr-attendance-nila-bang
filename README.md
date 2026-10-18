# QR-Based Teacher Attendance and Room Monitoring System (Java)

Java 17 and Javalin backend, MySQL database, static frontend served from the same app.

1. Create the database and tables: `mysql -u root -p < schema.sql`
2. Build: `mvn package`
3. Run: `DB_URL=jdbc:mysql://localhost:3306/qr_attendance DB_USER=root DB_PASS=yourpassword java -jar target/qr-attendance.jar`
4. Open `http://localhost:8080`

Environment variables: `DB_URL`, `DB_USER`, `DB_PASS`, `PORT` (default 8080), `COOKIE_SECURE=true` when serving over HTTPS.

Seeded administrator: `admin124@attendance.ph` / `Admin124`. Change this password before going live.
Teachers can log in right after signing up. Staff accounts must be approved by the administrator.
# qr-attendance-nila-bang
