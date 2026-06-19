CREATE DATABASE IF NOT EXISTS odonto_gral
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE odonto_gral;

CREATE TABLE IF NOT EXISTS pacientes (
  id INT AUTO_INCREMENT PRIMARY KEY,
  nombre VARCHAR(120) NOT NULL,
  telefono VARCHAR(20) NOT NULL,
  email VARCHAR(160) NOT NULL UNIQUE,
  password_hash VARCHAR(64) NOT NULL,
  fecha_registro DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS odontologos (
  id INT AUTO_INCREMENT PRIMARY KEY,
  nombre VARCHAR(120) NOT NULL,
  activo BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT IGNORE INTO odontologos (id, nombre, activo)
VALUES (1, 'Odontologo General', TRUE);

CREATE TABLE IF NOT EXISTS citas (
  id INT AUTO_INCREMENT PRIMARY KEY,
  paciente_id INT NOT NULL,
  odontologo_id INT NOT NULL,
  fecha DATE NOT NULL,
  hora TIME NOT NULL,
  tratamiento VARCHAR(160) NOT NULL,
  estatus VARCHAR(30) NOT NULL DEFAULT 'CONFIRMADA',
  fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_citas_paciente
    FOREIGN KEY (paciente_id) REFERENCES pacientes(id),
  CONSTRAINT fk_citas_odontologo
    FOREIGN KEY (odontologo_id) REFERENCES odontologos(id),
  INDEX idx_citas_fecha_hora (fecha, hora)
);

CREATE TABLE IF NOT EXISTS expedientes (
  id INT AUTO_INCREMENT PRIMARY KEY,
  paciente_id INT NOT NULL UNIQUE,
  alergias VARCHAR(255) NULL,
  antecedentes TEXT NULL,
  fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_expedientes_paciente
    FOREIGN KEY (paciente_id) REFERENCES pacientes(id)
);

CREATE TABLE IF NOT EXISTS notas_evolucion (
  id INT AUTO_INCREMENT PRIMARY KEY,
  paciente_id INT NOT NULL,
  texto_nota TEXT NOT NULL,
  fecha_hora DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_notas_paciente
    FOREIGN KEY (paciente_id) REFERENCES pacientes(id),
  INDEX idx_notas_paciente_fecha (paciente_id, fecha_hora)
);

CREATE TABLE IF NOT EXISTS odontograma_piezas (
  id INT AUTO_INCREMENT PRIMARY KEY,
  paciente_id INT NOT NULL,
  numero_pieza INT NOT NULL,
  superficie VARCHAR(30) NOT NULL DEFAULT 'GENERAL',
  estado VARCHAR(40) NOT NULL DEFAULT 'SANO',
  fecha_actualizacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_odonto_gral_odontograma_piezas_paciente_id
    FOREIGN KEY (paciente_id) REFERENCES pacientes(id),
  UNIQUE KEY uk_odontograma_pieza_superficie (paciente_id, numero_pieza, superficie),
  INDEX idx_odontograma_paciente (paciente_id)
);
