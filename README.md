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

## Pruebas rapidas de API

Con el servidor encendido y la base de datos inicializada, ejecuta:

```powershell
.\tests\api-smoke.ps1
```

La prueba revisa salud de base de datos, login, pacientes, citas, conflicto 409,
expediente, notas, historial y auditoria.

## Configuracion para produccion

No subas `config.properties` al repositorio. En servidor usa variables de entorno o un
`config.properties` privado basado en `config.example.properties`.

Valores minimos a revisar antes de desplegar:

- `APP_ENV=production`
- `APP_BASE_URL=https://tu-dominio.com`
- `AUTH_SECRET` con un valor largo y unico
- `DB_URL`, `DB_USER` y `DB_PASSWORD` reales de MariaDB
- `INIT_DB_ENABLED=false`
- `CLINICAL_IMAGES_DIR` apuntando a una carpeta persistente o volumen respaldado

En produccion el sistema exige HTTPS, secretos seguros y que el inicializador de base
de datos este apagado.

## Respaldo de datos

Para no perder informacion clinica se deben respaldar dos cosas:

- Base de datos MariaDB `odonto_gral`
- Carpeta configurada en `CLINICAL_IMAGES_DIR`

Recomendacion inicial para AWS: MariaDB en RDS con backups automaticos y las imagenes
clinicas en almacenamiento persistente respaldado. Idealmente, el siguiente paso es
migrar imagenes clinicas a S3 privado con acceso protegido por la API.
