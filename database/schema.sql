CREATE DATABASE IF NOT EXISTS odonto_gral
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE odonto_gral;

CREATE TABLE IF NOT EXISTS pacientes (
  id INT AUTO_INCREMENT PRIMARY KEY,
  nombre VARCHAR(120) NOT NULL,
  telefono VARCHAR(20) NOT NULL,
  email VARCHAR(160) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  fecha_registro DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE pacientes
  ADD COLUMN IF NOT EXISTS activo BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE pacientes
  MODIFY COLUMN password_hash VARCHAR(255) NOT NULL;

CREATE TABLE IF NOT EXISTS odontologos (
  id INT AUTO_INCREMENT PRIMARY KEY,
  nombre VARCHAR(120) NOT NULL,
  activo BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT IGNORE INTO odontologos (id, nombre, activo)
VALUES (1, 'Odontologo General', TRUE);

CREATE TABLE IF NOT EXISTS usuarios_admin (
  id INT AUTO_INCREMENT PRIMARY KEY,
  nombre VARCHAR(120) NOT NULL,
  email VARCHAR(160) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  rol VARCHAR(30) NOT NULL,
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT IGNORE INTO usuarios_admin (id, nombre, email, password_hash, rol, activo)
VALUES
  (1, 'Administrador General', 'admin@odonto.local', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'ADMIN', TRUE),
  (2, 'Recepcion Principal', 'recepcion@odonto.local', 'e7af7bf39dbc423a5e12298ae05f86bb3b227d8b9d7c3656b9990ffeb0015219', 'RECEPCION', TRUE),
  (3, 'Odontologo General', 'odontologo@odonto.local', '5579945e4980170ad7651d04f16216f1be4537e5c6304d7b8eafb1b111444a8d', 'ODONTOLOGO', TRUE);

ALTER TABLE usuarios_admin
  MODIFY COLUMN password_hash VARCHAR(255) NOT NULL;

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

CREATE TABLE IF NOT EXISTS solicitudes_cita (
  id INT AUTO_INCREMENT PRIMARY KEY,
  paciente_id INT NOT NULL,
  odontologo_id INT NULL,
  fecha DATE NOT NULL,
  hora TIME NOT NULL,
  motivo VARCHAR(180) NOT NULL,
  estatus VARCHAR(30) NOT NULL DEFAULT 'PENDIENTE',
  cita_id INT NULL,
  fecha_solicitud DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  fecha_resolucion DATETIME NULL,
  CONSTRAINT fk_solicitudes_cita_paciente
    FOREIGN KEY (paciente_id) REFERENCES pacientes(id),
  CONSTRAINT fk_solicitudes_cita_odontologo
    FOREIGN KEY (odontologo_id) REFERENCES odontologos(id),
  CONSTRAINT fk_solicitudes_cita_cita
    FOREIGN KEY (cita_id) REFERENCES citas(id),
  INDEX idx_solicitudes_cita_estatus_fecha (estatus, fecha, hora)
);

ALTER TABLE solicitudes_cita
  ADD COLUMN IF NOT EXISTS odontologo_id INT NULL;

CREATE TABLE IF NOT EXISTS expedientes (
  id INT AUTO_INCREMENT PRIMARY KEY,
  paciente_id INT NOT NULL UNIQUE,
  edad INT NULL,
  grupo_sanguineo VARCHAR(5) NULL,
  alergias VARCHAR(255) NULL,
  antecedentes TEXT NULL,
  fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_expedientes_paciente
    FOREIGN KEY (paciente_id) REFERENCES pacientes(id)
);

ALTER TABLE expedientes
  ADD COLUMN IF NOT EXISTS edad INT NULL;

ALTER TABLE expedientes
  ADD COLUMN IF NOT EXISTS grupo_sanguineo VARCHAR(5) NULL;

CREATE TABLE IF NOT EXISTS notas_evolucion (
  id INT AUTO_INCREMENT PRIMARY KEY,
  paciente_id INT NOT NULL,
  texto_nota TEXT NOT NULL,
  fecha_hora DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_notas_paciente
    FOREIGN KEY (paciente_id) REFERENCES pacientes(id),
  INDEX idx_notas_paciente_fecha (paciente_id, fecha_hora)
);

CREATE TABLE IF NOT EXISTS imagenes_clinicas (
  id INT AUTO_INCREMENT PRIMARY KEY,
  paciente_id INT NOT NULL,
  tipo VARCHAR(40) NOT NULL DEFAULT 'IMAGEN',
  descripcion VARCHAR(255) NULL,
  nombre_original VARCHAR(255) NOT NULL,
  nombre_archivo VARCHAR(255) NOT NULL,
  content_type VARCHAR(120) NOT NULL,
  ruta_archivo VARCHAR(500) NOT NULL,
  url_publica VARCHAR(500) NOT NULL,
  fecha_subida DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_imagenes_clinicas_paciente
    FOREIGN KEY (paciente_id) REFERENCES pacientes(id),
  INDEX idx_imagenes_clinicas_paciente_fecha (paciente_id, fecha_subida)
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

CREATE TABLE IF NOT EXISTS pagos (
  id INT AUTO_INCREMENT PRIMARY KEY,
  paciente_nombre VARCHAR(120) NOT NULL,
  concepto VARCHAR(160) NOT NULL,
  monto DECIMAL(10, 2) NOT NULL,
  metodo VARCHAR(40) NOT NULL,
  fecha_registro DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_pagos_fecha (fecha_registro)
);

CREATE TABLE IF NOT EXISTS horarios_atencion (
  dia_semana TINYINT NOT NULL PRIMARY KEY,
  activo BOOLEAN NOT NULL DEFAULT FALSE,
  hora_inicio TIME NOT NULL,
  hora_fin TIME NOT NULL
);

CREATE TABLE IF NOT EXISTS horarios_odontologo (
  odontologo_id INT NOT NULL,
  dia_semana TINYINT NOT NULL,
  activo BOOLEAN NOT NULL DEFAULT FALSE,
  hora_inicio TIME NOT NULL,
  hora_fin TIME NOT NULL,
  PRIMARY KEY (odontologo_id, dia_semana),
  CONSTRAINT fk_horarios_odontologo
    FOREIGN KEY (odontologo_id) REFERENCES odontologos(id)
    ON DELETE CASCADE
);

INSERT IGNORE INTO horarios_atencion (dia_semana, activo, hora_inicio, hora_fin)
VALUES
  (1, FALSE, '09:00:00', '18:00:00'),
  (2, TRUE, '09:00:00', '18:00:00'),
  (3, TRUE, '09:00:00', '18:00:00'),
  (4, TRUE, '09:00:00', '18:00:00'),
  (5, TRUE, '09:00:00', '18:00:00'),
  (6, TRUE, '09:00:00', '18:00:00'),
  (7, FALSE, '09:00:00', '18:00:00');

CREATE TABLE IF NOT EXISTS dias_feriados (
  id INT AUTO_INCREMENT PRIMARY KEY,
  fecha DATE NOT NULL UNIQUE,
  motivo VARCHAR(160) NOT NULL DEFAULT 'Cierre especial',
  fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_dias_feriados_fecha (fecha)
);
