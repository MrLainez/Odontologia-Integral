# Odontologia-Integral

Sistema integral de expedientes odontologicos con interfaz web y API REST en Kotlin/Javalin.

## Ejecutar localmente

1. Configura MariaDB con `database/schema.sql`.
2. Ajusta credenciales en `config.properties`.
3. Ejecuta:

```powershell
.\run.bat
```

Luego abre:

- Portal de pacientes: `http://localhost:8080/index.html`
- Agenda publica: `http://localhost:8080/agendar.html`
- Recepcion: `http://localhost:8080/recepcion.html`
- Odontologo: `http://localhost:8080/odontologo.html`
