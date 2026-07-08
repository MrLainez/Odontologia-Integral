# Deploy de Odontologia Integral

Esta guia deja listo el proyecto para generar un paquete ejecutable y moverlo a un
servidor, por ejemplo una instancia EC2 detras de HTTPS.

## 1. Configuracion de produccion

En el servidor crea un `config.properties` privado usando como base:

```text
config.production.example.properties
```

Valores obligatorios para produccion:

- `APP_ENV=production`
- `APP_BASE_URL=https://tu-dominio.com`
- `AUTH_SECRET` largo, unico y privado
- `DB_URL`, `DB_USER` y `DB_PASSWORD` reales
- `INIT_DB_ENABLED=false`
- `UPLOADS_DIR` y `CLINICAL_IMAGES_DIR` en una ruta persistente con respaldo

`config.properties` esta ignorado por Git y no debe subirse al repositorio.

## 2. Generar paquete ejecutable

Desde la raiz del proyecto:

```powershell
.\gradlew.bat clean installDist
```

El paquete instalado queda en:

```text
build/install/OdontoGral
```

Ese directorio incluye:

- `bin/OdontoGral.bat`
- `bin/OdontoGral`
- `lib/` con dependencias
- `public/` con HTML, CSS y JS
- `database/schema.sql`
- archivos ejemplo de configuracion

## 3. Ejecutar el paquete

En Windows, desde `build/install/OdontoGral`:

```powershell
.\bin\OdontoGral.bat
```

En Linux, desde el directorio instalado:

```bash
./bin/OdontoGral
```

El proceso debe arrancar desde la carpeta raiz del paquete para que
`STATIC_FILES_DIR=public` encuentre los archivos web.

## 4. Recomendacion para AWS

- MariaDB: usar RDS con backups automaticos.
- App Kotlin/Javalin: EC2 o contenedor.
- HTTPS: usar Nginx, ALB o CloudFront con certificado TLS.
- Imagenes clinicas: usar volumen persistente respaldado; siguiente mejora recomendada:
  S3 privado con descarga protegida por la API.

## 5. Verificacion despues del deploy

Cuando la app este levantada, ejecuta el smoke test apuntando a la URL real:

```powershell
.\tests\api-smoke.ps1 -BaseUrl "https://tu-dominio.com" -AdminEmail "admin@odonto.local" -AdminPassword "admin123"
```

En produccion cambia las credenciales default antes de operar el sistema.
