CREATE DATABASE IF NOT EXISTS qr_attendance CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE qr_attendance;

DROP TABLE IF EXISTS auth_sessions;
DROP TABLE IF EXISTS attendance;
DROP TABLE IF EXISTS qr_sessions;
DROP TABLE IF EXISTS rooms;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  role ENUM('teacher', 'staff', 'admin') NOT NULL,
  employee_no VARCHAR(50) NOT NULL UNIQUE,
  first_name VARCHAR(100) NOT NULL,
  last_name VARCHAR(100) NOT NULL,
  email VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  status ENUM('ACTIVE', 'INACTIVE', 'SUSPENDED') NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_login DATETIME NULL
) ENGINE=InnoDB;

CREATE TABLE rooms (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(50) NOT NULL UNIQUE,
  name VARCHAR(150) NOT NULL,
  building VARCHAR(150) NOT NULL,
  `floor` VARCHAR(50) NOT NULL DEFAULT '',
  capacity INT UNSIGNED NOT NULL DEFAULT 0,
  status ENUM('AVAILABLE', 'OCCUPIED', 'INACTIVE', 'MAINTENANCE') NOT NULL DEFAULT 'AVAILABLE'
) ENGINE=InnoDB;

CREATE TABLE qr_sessions (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  teacher_id INT UNSIGNED NOT NULL,
  room_id INT UNSIGNED NOT NULL,
  token_hash CHAR(64) NOT NULL UNIQUE,
  created_at DATETIME NOT NULL,
  expires_at DATETIME NOT NULL,
  used_at DATETIME NULL,
  status ENUM('ACTIVE', 'USED', 'EXPIRED', 'CANCELLED') NOT NULL DEFAULT 'ACTIVE',
  FOREIGN KEY (teacher_id) REFERENCES users(id),
  FOREIGN KEY (room_id) REFERENCES rooms(id),
  INDEX idx_qr_teacher (teacher_id, status)
) ENGINE=InnoDB;

CREATE TABLE attendance (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  teacher_id INT UNSIGNED NOT NULL,
  room_id INT UNSIGNED NOT NULL,
  qr_session_id BIGINT UNSIGNED NULL,
  scanned_by INT UNSIGNED NULL,
  time_in DATETIME NOT NULL,
  time_out DATETIME NULL,
  duration_minutes INT UNSIGNED NULL,
  status ENUM('ACTIVE', 'COMPLETED', 'INCOMPLETE', 'CANCELLED') NOT NULL DEFAULT 'ACTIVE',
  active_teacher_id INT UNSIGNED GENERATED ALWAYS AS (IF(status = 'ACTIVE', teacher_id, NULL)) STORED,
  UNIQUE KEY idx_one_active_attendance (active_teacher_id),
  FOREIGN KEY (teacher_id) REFERENCES users(id),
  FOREIGN KEY (room_id) REFERENCES rooms(id),
  FOREIGN KEY (qr_session_id) REFERENCES qr_sessions(id),
  FOREIGN KEY (scanned_by) REFERENCES users(id),
  INDEX idx_attendance_time (time_in),
  INDEX idx_attendance_room (room_id)
) ENGINE=InnoDB;

CREATE TABLE auth_sessions (
  token_hash CHAR(64) PRIMARY KEY,
  user_id INT UNSIGNED NOT NULL,
  expires_at DATETIME NOT NULL,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

INSERT INTO users (role, employee_no, first_name, last_name, email, password_hash)
VALUES ('admin', 'ADMIN-001', 'System', 'Administrator', 'admin124@attendance.ph', '4166dc3dcc8a072c51aaaeeef2bf501e:d08681afbbf0adb15add0c6ea0024a9e84106008544f3edd7ebfd7f35370431d');

INSERT INTO rooms (code, name, building, `floor`, capacity) VALUES
  ('RM-101', 'Room 101', 'Main Building', '1st Floor', 40),
  ('RM-102', 'Room 102', 'Main Building', '1st Floor', 40),
  ('LAB-1', 'Computer Lab 1', 'Tech Building', '2nd Floor', 35),
  ('LAB-2', 'Science Lab', 'Tech Building', '2nd Floor', 30),
  ('AVR', 'AV Room', 'Annex', 'Ground Floor', 80);
