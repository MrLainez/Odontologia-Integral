# Credenciales Default

Estas credenciales son solo para desarrollo local y fase de pruebas.
No deben usarse en produccion.

## Personal administrativo

### Administrador

- Correo: `admin@odonto.local`
- Contrasena: `admin123`
- Rol: `ADMIN`

### Recepcion

- Correo: `recepcion@odonto.local`
- Contrasena: `recepcion123`
- Rol: `RECEPCION`

### Odontologo

- Correo: `odontologo@odonto.local`
- Contrasena: `odontologo123`
- Rol: `ODONTOLOGO`

## Paciente de prueba

- Correo: `prueba4@correo.com`
- Contrasena: `123456`
- Nota: este paciente puede existir como dato legado de desarrollo. Para nuevos pacientes usa una contrasena de al menos 8 caracteres con letras y numeros, por ejemplo `Paciente123`.

## Acceso

Todos inician desde:

`http://localhost:8080/`

El sistema redirige segun el rol:

- `ADMIN` y `RECEPCION` -> `recepcion.html`
- `ODONTOLOGO` -> `odontologo.html`
- Paciente -> Portal interno del paciente
