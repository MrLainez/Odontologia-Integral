import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.HttpStatus
import io.javalin.http.staticfiles.Location
import com.fasterxml.jackson.annotation.JsonAlias
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.Connection
import java.sql.Date
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.SQLIntegrityConstraintViolationException
import java.sql.Time
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.Properties
import java.util.Base64
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

// Modelos simples que representan los JSON recibidos por la API.
data class RegistroPacienteRequest(
    val nombre: String = "",
    val telefono: String = "",
    val email: String = "",
    val password: String = ""
)

data class ActualizarPacienteRequest(
    val nombre: String = "",
    val telefono: String = "",
    val email: String = ""
)

data class CambiarPasswordPacienteRequest(
    val password: String = ""
)

data class LoginRequest(
    val email: String = "",
    val password: String = ""
)

data class AdminLoginRequest(
    val email: String = "",
    val password: String = ""
)

data class CrearAdminUsuarioRequest(
    val nombre: String = "",
    val email: String = "",
    val password: String = "",
    val rol: String = ""
)

data class CambiarPasswordAdminRequest(
    val password: String = ""
)

data class CambiarPasswordPropiaRequest(
    val passwordActual: String = "",
    val passwordNueva: String = ""
)

data class AgendarCitaRequest(
    val pacienteId: Int = 0,
    val odontologoId: Int = 0,
    val fecha: String = "",
    val hora: String = "",
    val tratamiento: String = ""
)

data class CrearCitaRequest(
    val pacienteId: Int = 0,
    val odontologoId: Int = 0,
    val fechaHora: String = "",
    val motivo: String = ""
)

data class AceptarSolicitudCitaRequest(
    val odontologoId: Int = 0
)

data class ReprogramarCitaRequest(
    val pacienteId: Int = 0,
    val fechaHora: String = ""
)

data class CrearSolicitudCitaRequest(
    val pacienteId: Int = 0,
    val odontologoId: Int = 0,
    val fechaHora: String = "",
    val motivo: String = ""
)

data class ActualizarEstatusCitaRequest(
    val estatus: String = ""
)

data class ActualizarPiezaRequest(
    val estado: String = "",
    val superficie: String? = null
)

data class CrearNotaRequest(
    @param:JsonAlias("texto_nota")
    val textoNota: String = ""
)

data class ActualizarExpedienteRequest(
    val edad: Int? = null,
    val grupoSanguineo: String? = null,
    val alergias: String? = null,
    val antecedentes: String? = null
)

data class CrearPagoRequest(
    val pacienteNombre: String = "",
    val concepto: String = "",
    val monto: Double = 0.0,
    val metodo: String = ""
)

data class HorarioAtencionRequest(
    val diaSemana: Int = 0,
    val activo: Boolean = false,
    val horaInicio: String = "",
    val horaFin: String = ""
)

data class ActualizarHorariosAtencionRequest(
    val horarios: List<HorarioAtencionRequest> = emptyList()
)

data class DiaFeriadoRequest(
    val fecha: String = "",
    val motivo: String = ""
)

data class ApiError(val error: String)

data class AuthPrincipal(
    val id: Int,
    val tipo: String,
    val rol: String
)

// Lee valores desde variables de entorno o desde config.properties.
object AppConfig {
    private const val CONFIG_FILE = "config.properties"
    private val insecureValues = setOf(
        "",
        "password",
        "root",
        "admin",
        "cambia-este-secreto-en-produccion",
        "cambia-este-token",
        "dev-init-token"
    )

    private val properties = Properties().apply {
        val configFile = File(CONFIG_FILE)

        if (configFile.exists()) {
            configFile.inputStream().use { load(it) }
        }
    }

    fun get(key: String, defaultValue: String): String {
        return System.getenv(key)
            ?: properties.getProperty(key)
            ?: defaultValue
    }

    fun getInt(key: String, defaultValue: Int): Int {
        return get(key, defaultValue.toString()).toIntOrNull() ?: defaultValue
    }

    fun isProduction(): Boolean {
        return get("APP_ENV", "development").equals("production", ignoreCase = true)
    }

    fun validateForStartup() {
        if (!isProduction()) return

        val errors = mutableListOf<String>()
        requireSecureValue(errors, "AUTH_SECRET", minLength = 32)
        requireSecureValue(errors, "DB_PASSWORD", minLength = 12)

        if (get("DB_USER", "").isBlank()) {
            errors.add("DB_USER es obligatorio en produccion.")
        }

        if (get("INIT_DB_ENABLED", "false").equals("true", ignoreCase = true)) {
            errors.add("INIT_DB_ENABLED debe estar en false en produccion.")
        }

        if (!get("APP_BASE_URL", "").startsWith("https://", ignoreCase = true)) {
            errors.add("APP_BASE_URL debe configurarse con HTTPS en produccion.")
        }

        if (errors.isNotEmpty()) {
            error("Configuracion insegura para produccion:\n- ${errors.joinToString("\n- ")}")
        }
    }

    private fun requireSecureValue(errors: MutableList<String>, key: String, minLength: Int) {
        val value = get(key, "").trim()
        val normalized = value.lowercase()

        if (value.length < minLength || normalized in insecureValues) {
            errors.add("$key debe tener al menos $minLength caracteres y no puede usar valores de ejemplo.")
        }
    }
}

// Punto único para abrir conexiones JDBC hacia MariaDB.
object Database {
    private val jdbcUrl = AppConfig.get("DB_URL", "jdbc:mariadb://localhost:3306/odonto_gral")
    private val user = AppConfig.get("DB_USER", "root")
    private val password = AppConfig.get("DB_PASSWORD", "")
    private val driver = AppConfig.get("DB_DRIVER", "org.mariadb.jdbc.Driver")

    init {
        Class.forName(driver)
    }

    fun connection(): Connection = DriverManager.getConnection(jdbcUrl, user, password)

    fun serverConnection(): Connection = DriverManager.getConnection(serverJdbcUrl(), user, password)

    fun configuredDatabaseName(): String {
        val databasePart = jdbcUrl.substringAfterLast("/", "")
        return databasePart.substringBefore("?").trim()
    }

    private fun serverJdbcUrl(): String {
        val prefix = jdbcUrl.substringBeforeLast("/", jdbcUrl)
        val databasePart = jdbcUrl.substringAfterLast("/", "")
        val query = databasePart.substringAfter("?", "")

        return if (databasePart.contains("?")) {
            "$prefix/?$query"
        } else {
            "$prefix/"
        }
    }
}

// Tokens firmados para proteger la API sin agregar dependencias externas.
object AuthService {
    private val secret = AppConfig.get("AUTH_SECRET", "cambia-este-secreto-en-produccion")
    private val ttlMillis = AppConfig.getInt("AUTH_TTL_HOURS", 8) * 60L * 60L * 1000L

    fun createToken(id: Int, type: String, role: String): String {
        val expiresAt = System.currentTimeMillis() + ttlMillis
        val payload = "${type.uppercase()}:$id:${role.uppercase()}:$expiresAt"
        val signature = sign(payload)
        return Base64.getUrlEncoder().withoutPadding().encodeToString("$payload:$signature".toByteArray())
    }

    fun parseToken(token: String): AuthPrincipal? {
        return try {
            val decoded = String(Base64.getUrlDecoder().decode(token))
            val parts = decoded.split(":")

            if (parts.size != 5) return null

            val payload = parts.take(4).joinToString(":")
            val signature = parts[4]
            val expiresAt = parts[3].toLongOrNull() ?: return null

            if (signature != sign(payload) || expiresAt < System.currentTimeMillis()) return null

            AuthPrincipal(
                id = parts[1].toIntOrNull() ?: return null,
                tipo = parts[0],
                rol = parts[2]
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun sign(payload: String): String {
        val source = "$payload:$secret"
        return MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}

// Operaciones de base de datos relacionadas con pacientes y login.
object PatientRepository {
    fun create(request: RegistroPacienteRequest): Int {
        val sql = """
            INSERT INTO pacientes (nombre, telefono, email, password_hash, fecha_registro)
            VALUES (?, ?, ?, ?, NOW())
        """.trimIndent()

        Database.connection().use { connection ->
            connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { statement ->
                statement.setString(1, request.nombre.trim())
                statement.setString(2, request.telefono.trim())
                statement.setString(3, request.email.trim().lowercase())
                statement.setString(4, hashPassword(request.password))
                statement.executeUpdate()

                statement.generatedKeys.use { keys ->
                    if (keys.next()) return keys.getInt(1)
                }
            }
        }

        error("No fue posible obtener el id del paciente registrado.")
    }

    fun findByCredentials(email: String, password: String): Map<String, Any?>? {
        val sql = """
            SELECT id, nombre, email, password_hash
            FROM pacientes
            WHERE email = ? AND activo = TRUE
            LIMIT 1
        """.trimIndent()

        Database.connection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, email.trim().lowercase())

                statement.executeQuery().use { result ->
                    if (!result.next()) return null
                    val passwordHash = result.getString("password_hash")

                    if (!PasswordService.verify(password, passwordHash)) return null

                    if (PasswordService.needsUpgrade(passwordHash)) {
                        updatePasswordHash(connection, result.getInt("id"), hashPassword(password))
                    }

                    return mapOf(
                        "id" to result.getInt("id"),
                        "nombre" to result.getString("nombre"),
                        "email" to result.getString("email")
                    )
                }
            }
        }
    }

    fun list(): List<Map<String, Any?>> {
        val sql = """
            SELECT id, nombre, telefono, email, activo, fecha_registro
            FROM pacientes
            ORDER BY activo DESC, fecha_registro DESC
        """.trimIndent()
        val patients = mutableListOf<Map<String, Any?>>()

        Database.connection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.executeQuery().use { result ->
                    while (result.next()) {
                        patients.add(
                            mapOf(
                                "id" to result.getInt("id"),
                                "nombre" to result.getString("nombre"),
                                "telefono" to result.getString("telefono"),
                                "email" to result.getString("email"),
                                "activo" to result.getBoolean("activo"),
                                "fechaRegistro" to result.getTimestamp("fecha_registro")?.toLocalDateTime()?.toString()
                            )
                        )
                    }
                }
            }
        }

        return patients
    }

    fun search(query: String): List<Map<String, Any?>> {
        val sql = """
            SELECT id, nombre, telefono, email, activo, fecha_registro
            FROM pacientes
            WHERE activo = TRUE
              AND (
                nombre LIKE ?
                OR email LIKE ?
                OR telefono LIKE ?
                OR CAST(id AS CHAR) = ?
              )
            ORDER BY nombre ASC
            LIMIT 12
        """.trimIndent()
        val term = "%${query.trim()}%"
        val patients = mutableListOf<Map<String, Any?>>()

        Database.connection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, term)
                statement.setString(2, term)
                statement.setString(3, term)
                statement.setString(4, query.trim())

                statement.executeQuery().use { result ->
                    while (result.next()) {
                        patients.add(
                            mapOf(
                                "id" to result.getInt("id"),
                                "nombre" to result.getString("nombre"),
                                "telefono" to result.getString("telefono"),
                                "email" to result.getString("email"),
                                "activo" to result.getBoolean("activo"),
                                "fechaRegistro" to result.getTimestamp("fecha_registro")?.toLocalDateTime()?.toString()
                            )
                        )
                    }
                }
            }
        }

        return patients
    }

    fun update(id: Int, request: ActualizarPacienteRequest) {
        val sql = """
            UPDATE pacientes
            SET nombre = ?, telefono = ?, email = ?
            WHERE id = ? AND activo = TRUE
        """.trimIndent()

        Database.connection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, request.nombre.trim())
                statement.setString(2, request.telefono.trim())
                statement.setString(3, request.email.trim().lowercase())
                statement.setInt(4, id)

                if (statement.executeUpdate() == 0) {
                    throw PatientNotFoundException()
                }
            }
        }
    }

    fun resetPassword(id: Int, password: String) {
        val sql = """
            UPDATE pacientes
            SET password_hash = ?
            WHERE id = ? AND activo = TRUE
        """.trimIndent()

        Database.connection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, hashPassword(password))
                statement.setInt(2, id)

                if (statement.executeUpdate() == 0) {
                    throw PatientNotFoundException()
                }
            }
        }
    }

    fun changeOwnPassword(id: Int, currentPassword: String, newPassword: String) {
        val sql = """
            SELECT password_hash
            FROM pacientes
            WHERE id = ? AND activo = TRUE
            LIMIT 1
        """.trimIndent()

        Database.connection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setInt(1, id)

                statement.executeQuery().use { result ->
                    if (!result.next()) throw PatientNotFoundException()

                    val passwordHash = result.getString("password_hash")
                    if (!PasswordService.verify(currentPassword, passwordHash)) {
                        throw InvalidCurrentPasswordException()
                    }
                }
            }
        }

        resetPassword(id, newPassword)
    }

    fun existsActive(connection: Connection, id: Int): Boolean {
        val sql = "SELECT 1 FROM pacientes WHERE id = ? AND activo = TRUE LIMIT 1"

        connection.prepareStatement(sql).use { statement ->
            statement.setInt(1, id)
            statement.executeQuery().use { result -> return result.next() }
        }
    }

    private fun updatePasswordHash(connection: Connection, id: Int, passwordHash: String) {
        val sql = "UPDATE pacientes SET password_hash = ? WHERE id = ?"

        connection.prepareStatement(sql).use { statement ->
            statement.setString(1, passwordHash)
            statement.setInt(2, id)
            statement.executeUpdate()
        }
    }

    fun deleteProfileAndClinicalHistory(id: Int) {
        Database.connection().use { connection ->
            connection.autoCommit = false

            try {
                if (!patientExists(connection, id)) {
                    throw PatientNotFoundException()
                }

                val clinicalImagePaths = findClinicalImagePaths(connection, id)

                deleteByPatientId(connection, "odontograma_piezas", id)
                deleteByPatientId(connection, "imagenes_clinicas", id)
                deleteByPatientId(connection, "notas_evolucion", id)
                deleteByPatientId(connection, "expedientes", id)
                deleteByPatientId(connection, "solicitudes_cita", id)
                deleteByPatientId(connection, "citas", id)
                deletePatient(connection, id)

                connection.commit()
                clinicalImagePaths.forEach { path -> File(path).delete() }
            } catch (error: Exception) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    }

    private fun findClinicalImagePaths(connection: Connection, patientId: Int): List<String> {
        val sql = "SELECT ruta_archivo FROM imagenes_clinicas WHERE paciente_id = ?"
        val paths = mutableListOf<String>()

        connection.prepareStatement(sql).use { statement ->
            statement.setInt(1, patientId)

            statement.executeQuery().use { result ->
                while (result.next()) {
                    paths.add(result.getString("ruta_archivo"))
                }
            }
        }

        return paths
    }

    private fun patientExists(connection: Connection, id: Int): Boolean {
        val sql = "SELECT COUNT(*) AS total FROM pacientes WHERE id = ?"

        connection.prepareStatement(sql).use { statement ->
            statement.setInt(1, id)

            statement.executeQuery().use { result ->
                result.next()
                return result.getInt("total") > 0
            }
        }
    }

    private fun deleteByPatientId(connection: Connection, tableName: String, patientId: Int) {
        val sql = "DELETE FROM $tableName WHERE paciente_id = ?"

        connection.prepareStatement(sql).use { statement ->
            statement.setInt(1, patientId)
            statement.executeUpdate()
        }
    }

    private fun deletePatient(connection: Connection, id: Int) {
        val sql = "DELETE FROM pacientes WHERE id = ?"

        connection.prepareStatement(sql).use { statement ->
            statement.setInt(1, id)

            if (statement.executeUpdate() == 0) {
                throw PatientNotFoundException()
            }
        }
    }
}

// Operaciones de base de datos para usuarios administrativos.
object AdminRepository {
    fun list(): List<Map<String, Any?>> {
        val sql = """
            SELECT id, nombre, email, rol, activo, fecha_creacion
            FROM usuarios_admin
            ORDER BY activo DESC, nombre ASC
        """.trimIndent()
        val users = mutableListOf<Map<String, Any?>>()

        Database.connection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.executeQuery().use { result ->
                    while (result.next()) {
                        users.add(
                            mapOf(
                                "id" to result.getInt("id"),
                                "nombre" to result.getString("nombre"),
                                "email" to result.getString("email"),
                                "rol" to result.getString("rol"),
                                "activo" to result.getBoolean("activo"),
                                "fechaCreacion" to result.getTimestamp("fecha_creacion")?.toLocalDateTime()?.toString()
                            )
                        )
                    }
                }
            }
        }

        return users
    }

    fun create(request: CrearAdminUsuarioRequest, role: AdminRole): Int {
        val sql = """
            INSERT INTO usuarios_admin (nombre, email, password_hash, rol, activo, fecha_creacion)
            VALUES (?, ?, ?, ?, TRUE, NOW())
        """.trimIndent()

        Database.connection().use { connection ->
            connection.autoCommit = false

            try {
                connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { statement ->
                    statement.setString(1, request.nombre.trim())
                    statement.setString(2, request.email.trim().lowercase())
                    statement.setString(3, hashPassword(request.password))
                    statement.setString(4, role.databaseValue)
                    statement.executeUpdate()

                    statement.generatedKeys.use { keys ->
                        if (keys.next()) {
                            val userId = keys.getInt(1)

                            if (role == AdminRole.DENTIST) {
                                DentistRepository.create(connection, request.nombre)
                            }

                            connection.commit()
                            return userId
                        }
                    }
                }
            } catch (error: Exception) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }

        error("No fue posible obtener el id del usuario administrativo.")
    }

    fun findByCredentials(email: String, password: String): Map<String, Any?>? {
        val sql = """
            SELECT id, nombre, email, rol, password_hash
            FROM usuarios_admin
            WHERE email = ? AND activo = TRUE
            LIMIT 1
        """.trimIndent()

        Database.connection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, email.trim().lowercase())

                statement.executeQuery().use { result ->
                    if (!result.next()) return null
                    val passwordHash = result.getString("password_hash")

                    if (!PasswordService.verify(password, passwordHash)) return null

                    if (PasswordService.needsUpgrade(passwordHash)) {
                        updatePasswordHash(connection, result.getInt("id"), hashPassword(password))
                    }

                    return mapOf(
                        "id" to result.getInt("id"),
                        "nombre" to result.getString("nombre"),
                        "email" to result.getString("email"),
                        "rol" to result.getString("rol")
                    )
                }
            }
        }
    }

    fun resetPassword(id: Int, password: String) {
        val sql = """
            UPDATE usuarios_admin
            SET password_hash = ?
            WHERE id = ? AND activo = TRUE
        """.trimIndent()

        Database.connection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, hashPassword(password))
                statement.setInt(2, id)

                if (statement.executeUpdate() == 0) {
                    throw AdminUserNotFoundException()
                }
            }
        }
    }

    fun changeOwnPassword(id: Int, currentPassword: String, newPassword: String) {
        val sql = """
            SELECT password_hash
            FROM usuarios_admin
            WHERE id = ? AND activo = TRUE
            LIMIT 1
        """.trimIndent()

        Database.connection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setInt(1, id)

                statement.executeQuery().use { result ->
                    if (!result.next()) throw AdminUserNotFoundException()

                    val passwordHash = result.getString("password_hash")
                    if (!PasswordService.verify(currentPassword, passwordHash)) {
                        throw InvalidCurrentPasswordException()
                    }
                }
            }
        }

        resetPassword(id, newPassword)
    }

    private fun updatePasswordHash(connection: Connection, id: Int, passwordHash: String) {
        val sql = "UPDATE usuarios_admin SET password_hash = ? WHERE id = ?"

        connection.prepareStatement(sql).use { statement ->
            statement.setString(1, passwordHash)
            statement.setInt(2, id)
            statement.executeUpdate()
        }
    }

    fun deactivate(id: Int) {
        val sql = """
            DELETE FROM usuarios_admin
            WHERE id = ?
        """.trimIndent()

        Database.connection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setInt(1, id)

                if (statement.executeUpdate() == 0) {
                    throw AdminUserNotFoundException()
                }
            }
        }
    }
}

// Configuracion de horarios de atencion que usa el calendario publico.
object BusinessHoursRepository {
    private val dayLabels = mapOf(
        1 to "Lunes",
        2 to "Martes",
        3 to "Miercoles",
        4 to "Jueves",
        5 to "Viernes",
        6 to "Sabado",
        7 to "Domingo"
    )

    fun list(): List<Map<String, Any?>> {
        ensureDefaults()

        val sql = """
            SELECT dia_semana, activo, hora_inicio, hora_fin
            FROM horarios_atencion
            ORDER BY dia_semana ASC
        """.trimIndent()
        val schedules = mutableListOf<Map<String, Any?>>()

        Database.connection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.executeQuery().use { result ->
                    while (result.next()) {
                        val day = result.getInt("dia_semana")
                        schedules.add(
                            mapOf(
                                "diaSemana" to day,
                                "diaNombre" to (dayLabels[day] ?: "Dia $day"),
                                "activo" to result.getBoolean("activo"),
                                "horaInicio" to result.getTime("hora_inicio")?.toLocalTime()?.toString(),
                                "horaFin" to result.getTime("hora_fin")?.toLocalTime()?.toString()
                            )
                        )
                    }
                }
            }
        }

        return schedules
    }

    fun update(requests: List<HorarioAtencionRequest>) {
        if (requests.size != 7) {
            throw IllegalArgumentException("Debes enviar la configuracion de los 7 dias.")
        }

        Database.connection().use { connection ->
            connection.autoCommit = false

            try {
                requests.forEach { request ->
                    validateSchedule(request)
                    upsertSchedule(connection, request)
                }
                connection.commit()
            } catch (error: Exception) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    }

    fun slotsForDate(date: LocalDate): List<LocalTime> {
        ensureDefaults()

        val sql = """
            SELECT activo, hora_inicio, hora_fin
            FROM horarios_atencion
            WHERE dia_semana = ?
            LIMIT 1
        """.trimIndent()
        val dayOfWeek = date.dayOfWeek.value

        Database.connection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setInt(1, dayOfWeek)

                statement.executeQuery().use { result ->
                    if (!result.next() || !result.getBoolean("activo")) return emptyList()

                    val start = result.getTime("hora_inicio")?.toLocalTime() ?: return emptyList()
                    val end = result.getTime("hora_fin")?.toLocalTime() ?: return emptyList()
                    return generateHalfHourSlots(start, end)
                }
            }
        }
    }

    private fun ensureDefaults() {
        val sql = """
            INSERT IGNORE INTO horarios_atencion (dia_semana, activo, hora_inicio, hora_fin)
            VALUES
              (1, FALSE, '09:00:00', '18:00:00'),
              (2, TRUE, '09:00:00', '18:00:00'),
              (3, TRUE, '09:00:00', '18:00:00'),
              (4, TRUE, '09:00:00', '18:00:00'),
              (5, TRUE, '09:00:00', '18:00:00'),
              (6, TRUE, '09:00:00', '18:00:00'),
              (7, FALSE, '09:00:00', '18:00:00')
        """.trimIndent()

        Database.connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(sql)
            }
        }
    }

    private fun upsertSchedule(connection: Connection, request: HorarioAtencionRequest) {
        val sql = """
            INSERT INTO horarios_atencion (dia_semana, activo, hora_inicio, hora_fin)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
              activo = VALUES(activo),
              hora_inicio = VALUES(hora_inicio),
              hora_fin = VALUES(hora_fin)
        """.trimIndent()

        connection.prepareStatement(sql).use { statement ->
            statement.setInt(1, request.diaSemana)
            statement.setBoolean(2, request.activo)
            statement.setTime(3, Time.valueOf(parseTime(request.horaInicio)))
            statement.setTime(4, Time.valueOf(parseTime(request.horaFin)))
            statement.executeUpdate()
        }
    }

    private fun validateSchedule(request: HorarioAtencionRequest) {
        if (request.diaSemana !in 1..7) {
            throw IllegalArgumentException("diaSemana debe estar entre 1 y 7.")
        }

        val start = parseTime(request.horaInicio)
        val end = parseTime(request.horaFin)

        if (!isHalfHourValue(start) || !isHalfHourValue(end)) {
            throw IllegalArgumentException("Los horarios deben estar en intervalos de 30 minutos.")
        }

        if (!start.isBefore(end)) {
            throw IllegalArgumentException("La hora de inicio debe ser menor que la hora de fin.")
        }
    }

    private fun generateHalfHourSlots(start: LocalTime, end: LocalTime): List<LocalTime> {
        val slots = mutableListOf<LocalTime>()
        var current = start

        while (current.isBefore(end)) {
            slots.add(current)
            current = current.plusMinutes(30)
        }

        return slots
    }

    private fun isHalfHourValue(time: LocalTime): Boolean {
        return time.minute == 0 || time.minute == 30
    }
}

object DentistRepository {
    fun create(connection: Connection, name: String): Int {
        val sql = """
            INSERT INTO odontologos (nombre, activo)
            VALUES (?, TRUE)
        """.trimIndent()

        connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { statement ->
            statement.setString(1, name.trim())
            statement.executeUpdate()

            statement.generatedKeys.use { keys ->
                if (keys.next()) return keys.getInt(1)
            }
        }

        error("No fue posible obtener el id del odontologo registrado.")
    }

    fun listActive(): List<Map<String, Any?>> {
        val sql = """
            SELECT id, nombre, activo
            FROM odontologos
            WHERE activo = TRUE
            ORDER BY nombre ASC
        """.trimIndent()
        val dentists = mutableListOf<Map<String, Any?>>()

        Database.connection().use { connection ->
            syncFromAdminUsers(connection)

            connection.prepareStatement(sql).use { statement ->
                statement.executeQuery().use { result ->
                    while (result.next()) {
                        dentists.add(
                            mapOf(
                                "id" to result.getInt("id"),
                                "nombre" to result.getString("nombre"),
                                "activo" to result.getBoolean("activo")
                            )
                        )
                    }
                }
            }
        }

        return dentists
    }

    fun existsActive(connection: Connection, id: Int): Boolean {
        val sql = "SELECT 1 FROM odontologos WHERE id = ? AND activo = TRUE LIMIT 1"

        connection.prepareStatement(sql).use { statement ->
            statement.setInt(1, id)
            statement.executeQuery().use { result -> return result.next() }
        }
    }

    private fun syncFromAdminUsers(connection: Connection) {
        val sql = """
            INSERT INTO odontologos (nombre, activo)
            SELECT ua.nombre, TRUE
            FROM usuarios_admin ua
            WHERE ua.rol = 'ODONTOLOGO'
              AND ua.activo = TRUE
              AND NOT EXISTS (
                SELECT 1
                FROM odontologos o
                WHERE LOWER(o.nombre) = LOWER(ua.nombre)
              )
        """.trimIndent()

        connection.prepareStatement(sql).use { statement ->
            statement.executeUpdate()
        }
    }
}

object DentistHoursRepository {
    private val dayLabels = mapOf(
        1 to "Lunes",
        2 to "Martes",
        3 to "Miercoles",
        4 to "Jueves",
        5 to "Viernes",
        6 to "Sabado",
        7 to "Domingo"
    )

    fun list(odontologistId: Int): List<Map<String, Any?>> {
        val customSchedules = findCustomSchedules(odontologistId).associateBy { it["diaSemana"] as Int }

        if (customSchedules.isEmpty()) {
            return BusinessHoursRepository.list().map { schedule ->
                schedule + ("heredado" to true)
            }
        }

        return (1..7).map { day ->
            customSchedules[day] ?: inheritedScheduleForDay(day)
        }
    }

    fun update(odontologistId: Int, requests: List<HorarioAtencionRequest>) {
        if (odontologistId <= 0) {
            throw IllegalArgumentException("El odontologo es obligatorio.")
        }

        if (requests.size != 7) {
            throw IllegalArgumentException("Debes enviar la configuracion de los 7 dias.")
        }

        Database.connection().use { connection ->
            connection.autoCommit = false

            try {
                requests.forEach { request ->
                    validateSchedule(request)
                    upsertSchedule(connection, odontologistId, request)
                }
                connection.commit()
            } catch (error: Exception) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    }

    fun slotsForDate(date: LocalDate, odontologistId: Int?): List<LocalTime> {
        if (odontologistId == null || odontologistId <= 0) {
            return BusinessHoursRepository.slotsForDate(date)
        }

        val dayOfWeek = date.dayOfWeek.value
        val sql = """
            SELECT activo, hora_inicio, hora_fin
            FROM horarios_odontologo
            WHERE odontologo_id = ? AND dia_semana = ?
            LIMIT 1
        """.trimIndent()

        Database.connection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setInt(1, odontologistId)
                statement.setInt(2, dayOfWeek)

                statement.executeQuery().use { result ->
                    if (!result.next()) return BusinessHoursRepository.slotsForDate(date)
                    if (!result.getBoolean("activo")) return emptyList()

                    val start = result.getTime("hora_inicio")?.toLocalTime() ?: return emptyList()
                    val end = result.getTime("hora_fin")?.toLocalTime() ?: return emptyList()
                    return generateHalfHourSlots(start, end)
                }
            }
        }
    }

    private fun findCustomSchedules(odontologistId: Int): List<Map<String, Any?>> {
        val sql = """
            SELECT dia_semana, activo, hora_inicio, hora_fin
            FROM horarios_odontologo
            WHERE odontologo_id = ?
            ORDER BY dia_semana ASC
        """.trimIndent()
        val schedules = mutableListOf<Map<String, Any?>>()

        Database.connection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setInt(1, odontologistId)

                statement.executeQuery().use { result ->
                    while (result.next()) {
                        val day = result.getInt("dia_semana")
                        schedules.add(
                            mapOf(
                                "diaSemana" to day,
                                "diaNombre" to (dayLabels[day] ?: "Dia $day"),
                                "activo" to result.getBoolean("activo"),
                                "horaInicio" to result.getTime("hora_inicio")?.toLocalTime()?.toString(),
                                "horaFin" to result.getTime("hora_fin")?.toLocalTime()?.toString(),
                                "heredado" to false
                            )
                        )
                    }
                }
            }
        }

        return schedules
    }

    private fun inheritedScheduleForDay(day: Int): Map<String, Any?> {
        return BusinessHoursRepository.list()
            .firstOrNull { it["diaSemana"] == day }
            ?.plus("heredado" to true)
            ?: mapOf(
                "diaSemana" to day,
                "diaNombre" to (dayLabels[day] ?: "Dia $day"),
                "activo" to false,
                "horaInicio" to "09:00",
                "horaFin" to "18:00",
                "heredado" to true
            )
    }

    private fun upsertSchedule(connection: Connection, odontologistId: Int, request: HorarioAtencionRequest) {
        val sql = """
            INSERT INTO horarios_odontologo (odontologo_id, dia_semana, activo, hora_inicio, hora_fin)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
              activo = VALUES(activo),
              hora_inicio = VALUES(hora_inicio),
              hora_fin = VALUES(hora_fin)
        """.trimIndent()

        connection.prepareStatement(sql).use { statement ->
            statement.setInt(1, odontologistId)
            statement.setInt(2, request.diaSemana)
            statement.setBoolean(3, request.activo)
            statement.setTime(4, Time.valueOf(parseTime(request.horaInicio)))
            statement.setTime(5, Time.valueOf(parseTime(request.horaFin)))
            statement.executeUpdate()
        }
    }

    private fun validateSchedule(request: HorarioAtencionRequest) {
        if (request.diaSemana !in 1..7) {
            throw IllegalArgumentException("diaSemana debe estar entre 1 y 7.")
        }

        val start = parseTime(request.horaInicio)
        val end = parseTime(request.horaFin)

        if ((start.minute != 0 && start.minute != 30) || (end.minute != 0 && end.minute != 30)) {
            throw IllegalArgumentException("Los horarios deben estar en intervalos de 30 minutos.")
        }

        if (!start.isBefore(end)) {
            throw IllegalArgumentException("La hora de inicio debe ser menor que la hora de fin.")
        }
    }

    private fun generateHalfHourSlots(start: LocalTime, end: LocalTime): List<LocalTime> {
        val slots = mutableListOf<LocalTime>()
        var current = start

        while (current.isBefore(end)) {
            slots.add(current)
            current = current.plusMinutes(30)
        }

        return slots
    }
}

// Fechas especiales donde el consultorio no atiende aunque el dia semanal este activo.
object HolidayRepository {
    fun list(): List<Map<String, Any?>> {
        val sql = """
            SELECT id, fecha, motivo, fecha_creacion
            FROM dias_feriados
            ORDER BY fecha DESC
        """.trimIndent()
        val holidays = mutableListOf<Map<String, Any?>>()

        Database.connection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.executeQuery().use { result ->
                    while (result.next()) {
                        holidays.add(
                            mapOf(
                                "id" to result.getInt("id"),
                                "fecha" to result.getDate("fecha")?.toLocalDate()?.toString(),
                                "motivo" to result.getString("motivo"),
                                "fechaCreacion" to result.getTimestamp("fecha_creacion")?.toLocalDateTime()?.toString()
                            )
                        )
                    }
                }
            }
        }

        return holidays
    }

    fun create(request: DiaFeriadoRequest): Int {
        val date = parseDate(request.fecha)
        val reason = request.motivo.trim().ifBlank { "Cierre especial" }
        val sql = """
            INSERT INTO dias_feriados (fecha, motivo, fecha_creacion)
            VALUES (?, ?, NOW())
            ON DUPLICATE KEY UPDATE motivo = VALUES(motivo)
        """.trimIndent()

        Database.connection().use { connection ->
            connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { statement ->
                statement.setDate(1, Date.valueOf(date))
                statement.setString(2, reason)
                statement.executeUpdate()

                statement.generatedKeys.use { keys ->
                    if (keys.next()) return keys.getInt(1)
                }
            }
        }

        return findByDate(date)?.get("id") as? Int ?: error("No fue posible obtener el id del cierre especial.")
    }

    fun delete(id: Int) {
        val sql = "DELETE FROM dias_feriados WHERE id = ?"

        Database.connection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setInt(1, id)

                if (statement.executeUpdate() == 0) {
                    throw HolidayNotFoundException()
                }
            }
        }
    }

    fun findByDate(date: LocalDate): Map<String, Any?>? {
        val sql = """
            SELECT id, fecha, motivo
            FROM dias_feriados
            WHERE fecha = ?
            LIMIT 1
        """.trimIndent()

        Database.connection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setDate(1, Date.valueOf(date))

                statement.executeQuery().use { result ->
                    if (!result.next()) return null

                    return mapOf(
                        "id" to result.getInt("id"),
                        "fecha" to result.getDate("fecha")?.toLocalDate()?.toString(),
                        "motivo" to result.getString("motivo")
                    )
                }
            }
        }
    }
}

// Operaciones de citas: crear, validar disponibilidad y cambiar estatus.
object AppointmentRepository {
    private val defaultOdontologistId = AppConfig.getInt("DEFAULT_ODONTOLOGO_ID", 1)

    fun schedule(request: AgendarCitaRequest): Int {
        val appointmentDate = parseDate(request.fecha)
        val appointmentTime = parseTime(request.hora)

        return scheduleInternal(
            pacienteId = request.pacienteId,
            odontologoId = request.odontologoId,
            date = appointmentDate,
            time = appointmentTime,
            treatment = request.tratamiento
        )
    }

    fun create(request: CrearCitaRequest): Int {
        val odontologistId = request.odontologoId.takeIf { it > 0 } ?: defaultOdontologistId
        return createWithDentist(request, odontologistId)
    }

    fun createWithDentist(request: CrearCitaRequest, odontologistId: Int): Int {
        val appointmentDateTime = parseDateTime(request.fechaHora)

        return scheduleInternal(
            pacienteId = request.pacienteId,
            odontologoId = odontologistId,
            date = appointmentDateTime.toLocalDate(),
            time = appointmentDateTime.toLocalTime(),
            treatment = request.motivo
        )
    }

    fun updateStatus(id: Int, status: AppointmentStatus) {
        val sql = """
            UPDATE citas
            SET estatus = ?
            WHERE id = ?
        """.trimIndent()

        Database.connection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, status.databaseValue)
                statement.setInt(2, id)

                if (statement.executeUpdate() == 0) {
                    throw AppointmentNotFoundException()
                }
            }
        }
    }

    fun listToday(): List<Map<String, Any?>> {
        return listByDate(LocalDate.now())
    }

    fun listByDate(date: LocalDate): List<Map<String, Any?>> {
        val sql = """
            SELECT c.id, c.fecha, c.hora, c.tratamiento, c.estatus,
                   p.id AS paciente_id, p.nombre AS paciente_nombre,
                   o.id AS odontologo_id, o.nombre AS odontologo_nombre
            FROM citas c
            INNER JOIN pacientes p ON p.id = c.paciente_id
            LEFT JOIN odontologos o ON o.id = c.odontologo_id
            WHERE c.fecha = ?
            ORDER BY c.hora ASC
        """.trimIndent()
        val appointments = mutableListOf<Map<String, Any?>>()

        Database.connection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setDate(1, Date.valueOf(date))

                statement.executeQuery().use { result ->
                    while (result.next()) {
                        appointments.add(
                            mapOf(
                                "id" to result.getInt("id"),
                                "fecha" to result.getDate("fecha")?.toLocalDate()?.toString(),
                                "hora" to result.getTime("hora")?.toLocalTime()?.toString(),
                                "tratamiento" to result.getString("tratamiento"),
                                "estatus" to result.getString("estatus"),
                                "pacienteId" to result.getInt("paciente_id"),
                                "pacienteNombre" to result.getString("paciente_nombre"),
                                "odontologoId" to result.getObject("odontologo_id"),
                                "odontologoNombre" to result.getString("odontologo_nombre")
                            )
                        )
                    }
                }
            }
        }

        return appointments
    }

    fun listByPatient(patientId: Int): List<Map<String, Any?>> {
        val sql = """
            SELECT c.id, c.fecha, c.hora, c.tratamiento, c.estatus, o.nombre AS odontologo_nombre
            FROM citas c
            LEFT JOIN odontologos o ON o.id = c.odontologo_id
            WHERE c.paciente_id = ?
              AND c.estatus <> 'CANCELADA'
              AND (c.fecha > CURDATE() OR (c.fecha = CURDATE() AND c.hora >= CURTIME()))
            ORDER BY c.fecha ASC, c.hora ASC
        """.trimIndent()
        val appointments = mutableListOf<Map<String, Any?>>()

        Database.connection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setInt(1, patientId)

                statement.executeQuery().use { result ->
                    while (result.next()) {
                        appointments.add(
                            mapOf(
                                "id" to result.getInt("id"),
                                "fecha" to result.getDate("fecha")?.toLocalDate()?.toString(),
                                "hora" to result.getTime("hora")?.toLocalTime()?.toString(),
                                "tratamiento" to result.getString("tratamiento"),
                                "estatus" to result.getString("estatus"),
                                "odontologoNombre" to (result.getString("odontologo_nombre") ?: "Por asignar")
                            )
                        )
                    }
                }
            }
        }

        return appointments
    }

    fun availability(date: LocalDate, excludedAppointmentId: Int? = null, odontologistId: Int? = null): List<Map<String, Any?>> {
        val busyTimes = findBusyTimes(date, excludedAppointmentId, odontologistId)
        val configuredTimes = DentistHoursRepository.slotsForDate(date, odontologistId)

        return configuredTimes.map { time ->
            mapOf(
                "hora" to formatDisplayTime(time),
                "valor" to time.toString(),
                "disponible" to !busyTimes.contains(time)
            )
        }
    }

    fun cancelByPatient(appointmentId: Int, patientId: Int) {
        Database.connection().use { connection ->
            val appointmentDateTime = findActiveAppointmentDateTime(connection, appointmentId, patientId)
                ?: throw AppointmentNotFoundException()

            if (appointmentDateTime.isBefore(LocalDateTime.now().plusHours(24))) {
                throw AppointmentPolicyException()
            }

            val sql = """
                UPDATE citas
                SET estatus = 'CANCELADA'
                WHERE id = ? AND paciente_id = ? AND estatus <> 'CANCELADA'
            """.trimIndent()

            connection.prepareStatement(sql).use { statement ->
                statement.setInt(1, appointmentId)
                statement.setInt(2, patientId)

                if (statement.executeUpdate() == 0) {
                    throw AppointmentNotFoundException()
                }
            }
        }
    }

    fun rescheduleByPatient(appointmentId: Int, request: ReprogramarCitaRequest) {
        val appointmentDateTime = parseDateTime(request.fechaHora)
        val newDate = appointmentDateTime.toLocalDate()
        val newTime = appointmentDateTime.toLocalTime()

        Database.connection().use { connection ->
            connection.autoCommit = false

            try {
                val currentDateTime = findActiveAppointmentDateTime(connection, appointmentId, request.pacienteId)
                    ?: throw AppointmentNotFoundException()

                if (currentDateTime.isBefore(LocalDateTime.now().plusHours(24))) {
                    throw AppointmentPolicyException()
                }

                if (isSlotTaken(connection, newDate, newTime, appointmentId)) {
                    connection.rollback()
                    throw AppointmentConflictException()
                }

                val sql = """
                    UPDATE citas
                    SET fecha = ?, hora = ?, estatus = 'CONFIRMADA'
                    WHERE id = ? AND paciente_id = ? AND estatus <> 'CANCELADA'
                """.trimIndent()

                connection.prepareStatement(sql).use { statement ->
                    statement.setDate(1, Date.valueOf(newDate))
                    statement.setTime(2, Time.valueOf(newTime))
                    statement.setInt(3, appointmentId)
                    statement.setInt(4, request.pacienteId)

                    if (statement.executeUpdate() == 0) {
                        throw AppointmentNotFoundException()
                    }
                }

                connection.commit()
            } catch (error: Exception) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    }

    private fun scheduleInternal(
        pacienteId: Int,
        odontologoId: Int,
        date: LocalDate,
        time: LocalTime,
        treatment: String
    ): Int {
        Database.connection().use { connection ->
            connection.autoCommit = false

            try {
                if (!PatientRepository.existsActive(connection, pacienteId)) {
                    throw PatientNotFoundException()
                }

                if (!DentistRepository.existsActive(connection, odontologoId)) {
                    throw DentistNotFoundException()
                }

                // Regla critica: no permitir dos citas activas en la misma fecha y hora.
                if (isSlotTaken(connection, date, time, odontologoId = odontologoId)) {
                    connection.rollback()
                    throw AppointmentConflictException()
                }

                val id = insertAppointment(connection, pacienteId, odontologoId, date, time, treatment)
                connection.commit()
                return id
            } catch (error: Exception) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    }

    private fun isSlotTaken(
        connection: Connection,
        date: LocalDate,
        time: LocalTime,
        excludedAppointmentId: Int? = null,
        odontologoId: Int? = null
    ): Boolean {
        val sql = """
            SELECT COUNT(*) AS total
            FROM citas
            WHERE fecha = ? AND hora = ? AND estatus <> 'CANCELADA'
              AND (? IS NULL OR id <> ?)
              AND (? IS NULL OR odontologo_id = ?)
        """.trimIndent()

        connection.prepareStatement(sql).use { statement ->
            statement.setDate(1, Date.valueOf(date))
            statement.setTime(2, Time.valueOf(time))
            if (excludedAppointmentId == null) {
                statement.setNull(3, java.sql.Types.INTEGER)
                statement.setNull(4, java.sql.Types.INTEGER)
            } else {
                statement.setInt(3, excludedAppointmentId)
                statement.setInt(4, excludedAppointmentId)
            }
            if (odontologoId == null) {
                statement.setNull(5, java.sql.Types.INTEGER)
                statement.setNull(6, java.sql.Types.INTEGER)
            } else {
                statement.setInt(5, odontologoId)
                statement.setInt(6, odontologoId)
            }

            statement.executeQuery().use { result ->
                result.next()
                return result.getInt("total") > 0
            }
        }
    }

    private fun findBusyTimes(date: LocalDate, excludedAppointmentId: Int?, odontologistId: Int?): Set<LocalTime> {
        val sql = """
            SELECT hora
            FROM citas
            WHERE fecha = ? AND estatus <> 'CANCELADA'
              AND (? IS NULL OR id <> ?)
              AND (? IS NULL OR odontologo_id = ?)
        """.trimIndent()
        val times = mutableSetOf<LocalTime>()

        Database.connection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setDate(1, Date.valueOf(date))
                if (excludedAppointmentId == null) {
                    statement.setNull(2, java.sql.Types.INTEGER)
                    statement.setNull(3, java.sql.Types.INTEGER)
                } else {
                    statement.setInt(2, excludedAppointmentId)
                    statement.setInt(3, excludedAppointmentId)
                }
                if (odontologistId == null) {
                    statement.setNull(4, java.sql.Types.INTEGER)
                    statement.setNull(5, java.sql.Types.INTEGER)
                } else {
                    statement.setInt(4, odontologistId)
                    statement.setInt(5, odontologistId)
                }

                statement.executeQuery().use { result ->
                    while (result.next()) {
                        result.getTime("hora")?.toLocalTime()?.let(times::add)
                    }
                }
            }
        }

        return times
    }

    private fun insertAppointment(
        connection: Connection,
        pacienteId: Int,
        odontologoId: Int,
        date: LocalDate,
        time: LocalTime,
        treatment: String
    ): Int {
        val sql = """
            INSERT INTO citas (paciente_id, odontologo_id, fecha, hora, tratamiento, estatus)
            VALUES (?, ?, ?, ?, ?, 'CONFIRMADA')
        """.trimIndent()

        connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { statement ->
            statement.setInt(1, pacienteId)
            statement.setInt(2, odontologoId)
            statement.setDate(3, Date.valueOf(date))
            statement.setTime(4, Time.valueOf(time))
            statement.setString(5, treatment.trim())
            statement.executeUpdate()

            statement.generatedKeys.use { keys ->
                if (keys.next()) return keys.getInt(1)
            }
        }

        error("No fue posible obtener el id de la cita registrada.")
    }

    private fun findActiveAppointmentDateTime(connection: Connection, appointmentId: Int, patientId: Int): LocalDateTime? {
        val sql = """
            SELECT fecha, hora
            FROM citas
            WHERE id = ? AND paciente_id = ? AND estatus <> 'CANCELADA'
            LIMIT 1
        """.trimIndent()

        connection.prepareStatement(sql).use { statement ->
            statement.setInt(1, appointmentId)
            statement.setInt(2, patientId)

            statement.executeQuery().use { result ->
                if (!result.next()) return null

                val date = result.getDate("fecha")?.toLocalDate() ?: return null
                val time = result.getTime("hora")?.toLocalTime() ?: return null
                return LocalDateTime.of(date, time)
            }
        }
    }
}

// Historial consultable del paciente para recepcion, odontologos y administradores.
object PatientHistoryRepository {
    fun get(patientId: Int): Map<String, Any?>? {
        Database.connection().use { connection ->
            val patient = findPatient(connection, patientId) ?: return null

            return mapOf(
                "paciente" to patient,
                "citas" to findAppointments(connection, patientId),
                "notasEvolucion" to findNotes(connection, patientId)
            )
        }
    }

    private fun findPatient(connection: Connection, patientId: Int): Map<String, Any?>? {
        val sql = """
            SELECT id, nombre, telefono, email, fecha_registro
            FROM pacientes
            WHERE id = ?
            LIMIT 1
        """.trimIndent()

        connection.prepareStatement(sql).use { statement ->
            statement.setInt(1, patientId)

            statement.executeQuery().use { result ->
                if (!result.next()) return null

                return mapOf(
                    "id" to result.getInt("id"),
                    "nombre" to result.getString("nombre"),
                    "telefono" to result.getString("telefono"),
                    "email" to result.getString("email"),
                    "fechaRegistro" to result.getTimestamp("fecha_registro")?.toLocalDateTime()?.toString()
                )
            }
        }
    }

    private fun findAppointments(connection: Connection, patientId: Int): List<Map<String, Any?>> {
        val sql = """
            SELECT c.id, c.fecha, c.hora, c.tratamiento, c.estatus,
                   c.odontologo_id, o.nombre AS odontologo_nombre
            FROM citas c
            LEFT JOIN odontologos o ON o.id = c.odontologo_id
            WHERE c.paciente_id = ?
            ORDER BY c.fecha DESC, c.hora DESC
        """.trimIndent()
        val appointments = mutableListOf<Map<String, Any?>>()

        connection.prepareStatement(sql).use { statement ->
            statement.setInt(1, patientId)

            statement.executeQuery().use { result ->
                while (result.next()) {
                    appointments.add(
                        mapOf(
                            "id" to result.getInt("id"),
                            "fecha" to result.getDate("fecha")?.toLocalDate()?.toString(),
                            "hora" to result.getTime("hora")?.toLocalTime()?.toString(),
                            "tratamiento" to result.getString("tratamiento"),
                            "estatus" to result.getString("estatus"),
                            "odontologoId" to result.getObject("odontologo_id"),
                            "odontologoNombre" to (result.getString("odontologo_nombre") ?: "Por asignar")
                        )
                    )
                }
            }
        }

        return appointments
    }

    private fun findNotes(connection: Connection, patientId: Int): List<Map<String, Any?>> {
        val sql = """
            SELECT id, texto_nota, fecha_hora
            FROM notas_evolucion
            WHERE paciente_id = ?
            ORDER BY fecha_hora DESC
        """.trimIndent()
        val notes = mutableListOf<Map<String, Any?>>()

        connection.prepareStatement(sql).use { statement ->
            statement.setInt(1, patientId)

            statement.executeQuery().use { result ->
                while (result.next()) {
                    notes.add(
                        mapOf(
                            "id" to result.getInt("id"),
                            "textoNota" to result.getString("texto_nota"),
                            "fechaHora" to result.getTimestamp("fecha_hora")?.toLocalDateTime()?.toString()
                        )
                    )
                }
            }
        }

        return notes
    }
}

object AuditRepository {
    fun create(
        principal: AuthPrincipal?,
        action: String,
        target: String,
        ip: String,
        method: String,
        path: String,
        details: String
    ) {
        val sql = """
            INSERT INTO auditoria (
              actor_tipo, actor_id, actor_rol, accion, recurso, ip, metodo, ruta, detalles, fecha_hora
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
        """.trimIndent()

        Database.connection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, principal?.tipo ?: "ANONIMO")
                if (principal == null) statement.setNull(2, java.sql.Types.INTEGER) else statement.setInt(2, principal.id)
                if (principal == null) statement.setNull(3, java.sql.Types.VARCHAR) else statement.setString(3, principal.rol)
                statement.setString(4, action)
                statement.setString(5, target)
                statement.setString(6, ip)
                statement.setString(7, method)
                statement.setString(8, path)
                statement.setString(9, details)
                statement.executeUpdate()
            }
        }
    }

    fun list(limit: Int): List<Map<String, Any?>> {
        val sql = """
            SELECT id, fecha_hora, actor_tipo, actor_id, actor_rol, accion, recurso, ip, metodo, ruta, detalles
            FROM auditoria
            ORDER BY fecha_hora DESC, id DESC
            LIMIT ?
        """.trimIndent()
        val events = mutableListOf<Map<String, Any?>>()

        Database.connection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setInt(1, limit)

                statement.executeQuery().use { result ->
                    while (result.next()) {
                        events.add(
                            mapOf(
                                "id" to result.getLong("id"),
                                "fechaHora" to result.getTimestamp("fecha_hora")?.toLocalDateTime()?.toString(),
                                "actorTipo" to result.getString("actor_tipo"),
                                "actorId" to result.getObject("actor_id"),
                                "actorRol" to result.getString("actor_rol"),
                                "accion" to result.getString("accion"),
                                "recurso" to result.getString("recurso"),
                                "ip" to result.getString("ip"),
                                "metodo" to result.getString("metodo"),
                                "ruta" to result.getString("ruta"),
                                "detalles" to result.getString("detalles")
                            )
                        )
                    }
                }
            }
        }

        return events
    }
}

// Solicitudes enviadas por pacientes; recepcion decide si se convierten en cita.
object AppointmentRequestRepository {
    fun create(request: CrearSolicitudCitaRequest): Int {
        val dateTime = parseDateTime(request.fechaHora)
        val sql = """
            INSERT INTO solicitudes_cita (paciente_id, odontologo_id, fecha, hora, motivo, estatus, fecha_solicitud)
            VALUES (?, ?, ?, ?, ?, 'PENDIENTE', NOW())
        """.trimIndent()

        Database.connection().use { connection ->
            if (!PatientRepository.existsActive(connection, request.pacienteId)) {
                throw PatientNotFoundException()
            }

            if (request.odontologoId > 0 && !DentistRepository.existsActive(connection, request.odontologoId)) {
                throw DentistNotFoundException()
            }

            connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { statement ->
                statement.setInt(1, request.pacienteId)
                if (request.odontologoId > 0) statement.setInt(2, request.odontologoId) else statement.setNull(2, java.sql.Types.INTEGER)
                statement.setDate(3, Date.valueOf(dateTime.toLocalDate()))
                statement.setTime(4, Time.valueOf(dateTime.toLocalTime()))
                statement.setString(5, request.motivo.trim())
                statement.executeUpdate()

                statement.generatedKeys.use { keys ->
                    if (keys.next()) return keys.getInt(1)
                }
            }
        }

        error("No fue posible obtener el id de la solicitud registrada.")
    }

    fun list(status: String = "PENDIENTE"): List<Map<String, Any?>> {
        val sql = """
            SELECT s.id, s.paciente_id, p.nombre AS paciente_nombre, s.fecha, s.hora, s.motivo,
                   s.estatus, s.fecha_solicitud, s.cita_id, s.odontologo_id, o.nombre AS odontologo_nombre
            FROM solicitudes_cita s
            INNER JOIN pacientes p ON p.id = s.paciente_id
            LEFT JOIN odontologos o ON o.id = s.odontologo_id
            WHERE s.estatus = ?
            ORDER BY s.fecha ASC, s.hora ASC
        """.trimIndent()
        val requests = mutableListOf<Map<String, Any?>>()

        Database.connection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, status.trim().uppercase(Locale.US))

                statement.executeQuery().use { result ->
                    while (result.next()) {
                        requests.add(
                            mapOf(
                                "id" to result.getInt("id"),
                                "pacienteId" to result.getInt("paciente_id"),
                                "pacienteNombre" to result.getString("paciente_nombre"),
                                "fecha" to result.getDate("fecha")?.toLocalDate()?.toString(),
                                "hora" to result.getTime("hora")?.toLocalTime()?.toString(),
                                "motivo" to result.getString("motivo"),
                                "odontologoId" to result.getObject("odontologo_id"),
                                "odontologoNombre" to result.getString("odontologo_nombre"),
                                "estatus" to result.getString("estatus"),
                                "fechaSolicitud" to result.getTimestamp("fecha_solicitud")?.toLocalDateTime()?.toString(),
                                "citaId" to result.getObject("cita_id")
                            )
                        )
                    }
                }
            }
        }

        return requests
    }

    fun accept(id: Int, assignedOdontologistId: Int? = null): Int {
        val request = findPending(id) ?: throw AppointmentRequestNotFoundException()
        val date = request["fecha"] as LocalDate
        val time = request["hora"] as LocalTime
        val patientId = request["pacienteId"] as Int
        val odontologistId = assignedOdontologistId?.takeIf { it > 0 }
            ?: request["odontologoId"] as? Int
            ?: AppConfig.getInt("DEFAULT_ODONTOLOGO_ID", 1)
        val reason = request["motivo"] as String

        val appointmentId = AppointmentRepository.createWithDentist(
            CrearCitaRequest(
                pacienteId = patientId,
                fechaHora = "${date}T${time}",
                motivo = reason
            ),
            odontologistId
        )
        mark(id, "ACEPTADA", appointmentId, odontologistId)
        return appointmentId
    }

    fun reject(id: Int) {
        val updated = mark(id, "RECHAZADA", null, null)
        if (!updated) throw AppointmentRequestNotFoundException()
    }

    private fun findPending(id: Int): Map<String, Any?>? {
        val sql = """
            SELECT id, paciente_id, odontologo_id, fecha, hora, motivo
            FROM solicitudes_cita
            WHERE id = ? AND estatus = 'PENDIENTE'
            LIMIT 1
        """.trimIndent()

        Database.connection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setInt(1, id)

                statement.executeQuery().use { result ->
                    if (!result.next()) return null

                    return mapOf(
                        "id" to result.getInt("id"),
                        "pacienteId" to result.getInt("paciente_id"),
                        "odontologoId" to result.getObject("odontologo_id"),
                        "fecha" to result.getDate("fecha").toLocalDate(),
                        "hora" to result.getTime("hora").toLocalTime(),
                        "motivo" to result.getString("motivo")
                    )
                }
            }
        }
    }

    private fun mark(id: Int, status: String, appointmentId: Int?, odontologistId: Int?): Boolean {
        val sql = """
            UPDATE solicitudes_cita
            SET estatus = ?, cita_id = ?, odontologo_id = COALESCE(?, odontologo_id), fecha_resolucion = NOW()
            WHERE id = ? AND estatus = 'PENDIENTE'
        """.trimIndent()

        Database.connection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, status)
                if (appointmentId == null) statement.setNull(2, java.sql.Types.INTEGER) else statement.setInt(2, appointmentId)
                if (odontologistId == null) statement.setNull(3, java.sql.Types.INTEGER) else statement.setInt(3, odontologistId)
                statement.setInt(4, id)
                return statement.executeUpdate() > 0
            }
        }
    }
}

// Operaciones del expediente clinico, odontograma y notas de evolucion.
object ClinicalRepository {
    // Denticion adulta usada para generar el odontograma inicial.
    private val adultTeeth = listOf(
        18, 17, 16, 15, 14, 13, 12, 11,
        21, 22, 23, 24, 25, 26, 27, 28,
        38, 37, 36, 35, 34, 33, 32, 31,
        41, 42, 43, 44, 45, 46, 47, 48
    )
    // Cada pieza se guarda con 5 superficies clinicas.
    private val toothSurfaces = listOf("VESTIBULAR", "DISTAL", "OCLUSAL", "MESIAL", "LINGUAL")

    // Retorna un expediente completo: paciente, datos clinicos, notas y odontograma.
    fun getPatientRecord(patientId: Int): Map<String, Any?>? {
        Database.connection().use { connection ->
            val patient = findPatient(connection, patientId) ?: return null
            val expediente = findClinicalFile(connection, patientId)

            ensureOdontogram(connection, patientId)

            return mapOf(
                "paciente" to patient,
                "expediente" to expediente,
                "notasEvolucion" to findNotes(connection, patientId),
                "odontograma" to findOdontogram(connection, patientId),
                "imagenesClinicas" to findClinicalImages(connection, patientId)
            )
        }
    }

    // Crea o actualiza datos clinicos basicos del expediente.
    fun updateClinicalFile(patientId: Int, request: ActualizarExpedienteRequest): Map<String, Any?> {
        Database.connection().use { connection ->
            if (findPatient(connection, patientId) == null) {
                throw PatientNotFoundException()
            }

            val currentFile = findClinicalFile(connection, patientId)
            val sql = """
                INSERT INTO expedientes (paciente_id, edad, grupo_sanguineo, alergias, antecedentes)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  edad = VALUES(edad),
                  grupo_sanguineo = VALUES(grupo_sanguineo),
                  alergias = VALUES(alergias),
                  antecedentes = VALUES(antecedentes)
            """.trimIndent()

            connection.prepareStatement(sql).use { statement ->
                val age = request.edad ?: (currentFile["edad"] as? Int)
                val bloodType = if (request.grupoSanguineo != null) request.grupoSanguineo.trim().ifBlank { null } else currentFile["grupoSanguineo"] as? String
                val allergies = if (request.alergias != null) request.alergias.trim().ifBlank { null } else currentFile["alergias"] as? String
                val background = if (request.antecedentes != null) request.antecedentes.trim().ifBlank { null } else currentFile["antecedentes"] as? String

                statement.setInt(1, patientId)
                if (age == null) statement.setNull(2, java.sql.Types.INTEGER) else statement.setInt(2, age)
                if (bloodType == null) statement.setNull(3, java.sql.Types.VARCHAR) else statement.setString(3, bloodType)
                if (allergies == null) statement.setNull(4, java.sql.Types.VARCHAR) else statement.setString(4, allergies)
                if (background == null) statement.setNull(5, java.sql.Types.LONGVARCHAR) else statement.setString(5, background)
                statement.executeUpdate()
            }

            return findClinicalFile(connection, patientId)
        }
    }

    // Actualiza exclusivamente una superficie de una pieza dental.
    fun updateToothPiece(odontogramId: Int, toothNumber: Int, request: ActualizarPiezaRequest) {
        val sql = """
            UPDATE odontograma_piezas
            SET estado = ?, fecha_actualizacion = NOW()
            WHERE id = ? AND numero_pieza = ? AND superficie = ?
        """.trimIndent()
        val surface = normalizeSurface(request.superficie)

        Database.connection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, normalizeClinicalStatus(request.estado))
                statement.setInt(2, odontogramId)
                statement.setInt(3, toothNumber)
                statement.setString(4, surface)

                if (statement.executeUpdate() == 0) {
                    throw OdontogramPieceNotFoundException()
                }
            }
        }
    }

    // Inserta notas con fecha del servidor para mantenerlas inmutables desde frontend.
    fun addNote(patientId: Int, text: String): Int {
        val sql = """
            INSERT INTO notas_evolucion (paciente_id, texto_nota, fecha_hora)
            VALUES (?, ?, NOW())
        """.trimIndent()

        Database.connection().use { connection ->
            if (findPatient(connection, patientId) == null) {
                throw PatientNotFoundException()
            }

            connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { statement ->
                statement.setInt(1, patientId)
                statement.setString(2, text.trim())
                statement.executeUpdate()

                statement.generatedKeys.use { keys ->
                    if (keys.next()) return keys.getInt(1)
                }
            }
        }

        error("No fue posible obtener el id de la nota registrada.")
    }

    fun addClinicalImage(
        patientId: Int,
        type: String,
        description: String?,
        originalName: String,
        storedName: String,
        contentType: String,
        filePath: String,
        publicUrl: String
    ): Map<String, Any?> {
        val sql = """
            INSERT INTO imagenes_clinicas (
              paciente_id, tipo, descripcion, nombre_original, nombre_archivo,
              content_type, ruta_archivo, url_publica, fecha_subida
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())
        """.trimIndent()

        Database.connection().use { connection ->
            if (findPatient(connection, patientId) == null) {
                throw PatientNotFoundException()
            }

            connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { statement ->
                statement.setInt(1, patientId)
                statement.setString(2, type.trim().uppercase().ifBlank { "IMAGEN" })
                if (description.isNullOrBlank()) statement.setNull(3, java.sql.Types.VARCHAR) else statement.setString(3, description.trim())
                statement.setString(4, originalName)
                statement.setString(5, storedName)
                statement.setString(6, contentType)
                statement.setString(7, filePath)
                statement.setString(8, publicUrl)
                statement.executeUpdate()

                statement.generatedKeys.use { keys ->
                    if (keys.next()) {
                        return findClinicalImageById(connection, keys.getInt(1))
                    }
                }
            }
        }

        error("No fue posible obtener el id de la imagen registrada.")
    }

    fun findClinicalImageFile(patientId: Int, imageId: Int): Map<String, Any?>? {
        val sql = """
            SELECT id, paciente_id, tipo, descripcion, nombre_original, nombre_archivo,
                   content_type, ruta_archivo, fecha_subida
            FROM imagenes_clinicas
            WHERE id = ? AND paciente_id = ?
            LIMIT 1
        """.trimIndent()

        Database.connection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setInt(1, imageId)
                statement.setInt(2, patientId)

                statement.executeQuery().use { result ->
                    if (!result.next()) return null

                    return mapOf(
                        "id" to result.getInt("id"),
                        "pacienteId" to result.getInt("paciente_id"),
                        "tipo" to result.getString("tipo"),
                        "descripcion" to result.getString("descripcion"),
                        "nombreOriginal" to result.getString("nombre_original"),
                        "nombreArchivo" to result.getString("nombre_archivo"),
                        "contentType" to result.getString("content_type"),
                        "rutaArchivo" to result.getString("ruta_archivo"),
                        "fechaSubida" to result.getTimestamp("fecha_subida")?.toLocalDateTime()?.toString()
                    )
                }
            }
        }
    }

    private fun findPatient(connection: Connection, patientId: Int): Map<String, Any?>? {
        val sql = """
            SELECT id, nombre, telefono, email, fecha_registro
            FROM pacientes
            WHERE id = ?
        """.trimIndent()

        connection.prepareStatement(sql).use { statement ->
            statement.setInt(1, patientId)

            statement.executeQuery().use { result ->
                if (!result.next()) return null

                return mapOf(
                    "id" to result.getInt("id"),
                    "nombre" to result.getString("nombre"),
                    "telefono" to result.getString("telefono"),
                    "email" to result.getString("email"),
                    "fechaRegistro" to result.getTimestamp("fecha_registro")?.toLocalDateTime()?.toString()
                )
            }
        }
    }

    // Crea filas faltantes del odontograma sin duplicar las existentes.
    private fun ensureOdontogram(connection: Connection, patientId: Int) {
        val sql = """
            INSERT IGNORE INTO odontograma_piezas (paciente_id, numero_pieza, superficie, estado)
            VALUES (?, ?, ?, 'SANO')
        """.trimIndent()

        connection.prepareStatement(sql).use { statement ->
            adultTeeth.forEach { tooth ->
                toothSurfaces.forEach { surface ->
                    statement.setInt(1, patientId)
                    statement.setInt(2, tooth)
                    statement.setString(3, surface)
                    statement.addBatch()
                }
            }

            statement.executeBatch()
        }
    }

    private fun findClinicalFile(connection: Connection, patientId: Int): Map<String, Any?> {
        val sql = """
            SELECT id, edad, grupo_sanguineo, alergias, antecedentes, fecha_creacion
            FROM expedientes
            WHERE paciente_id = ?
            LIMIT 1
        """.trimIndent()

        connection.prepareStatement(sql).use { statement ->
            statement.setInt(1, patientId)

            statement.executeQuery().use { result ->
                if (!result.next()) {
                    return mapOf(
                        "id" to null,
                        "edad" to null,
                        "grupoSanguineo" to null,
                        "alergias" to null,
                        "antecedentes" to null,
                        "fechaCreacion" to null
                    )
                }

                return mapOf(
                    "id" to result.getInt("id"),
                    "edad" to result.getObject("edad"),
                    "grupoSanguineo" to result.getString("grupo_sanguineo"),
                    "alergias" to result.getString("alergias"),
                    "antecedentes" to result.getString("antecedentes"),
                    "fechaCreacion" to result.getTimestamp("fecha_creacion")?.toLocalDateTime()?.toString()
                )
            }
        }
    }

    private fun findNotes(connection: Connection, patientId: Int): List<Map<String, Any?>> {
        val sql = """
            SELECT id, texto_nota, fecha_hora
            FROM notas_evolucion
            WHERE paciente_id = ?
            ORDER BY fecha_hora DESC
        """.trimIndent()
        val notes = mutableListOf<Map<String, Any?>>()

        connection.prepareStatement(sql).use { statement ->
            statement.setInt(1, patientId)

            statement.executeQuery().use { result ->
                while (result.next()) {
                    notes.add(
                        mapOf(
                            "id" to result.getInt("id"),
                            "textoNota" to result.getString("texto_nota"),
                            "fechaHora" to result.getTimestamp("fecha_hora")?.toLocalDateTime()?.toString()
                        )
                    )
                }
            }
        }

        return notes
    }

    private fun findClinicalImages(connection: Connection, patientId: Int): List<Map<String, Any?>> {
        val sql = """
            SELECT id, paciente_id, tipo, descripcion, nombre_original, content_type, url_publica, fecha_subida
            FROM imagenes_clinicas
            WHERE paciente_id = ?
            ORDER BY fecha_subida DESC
        """.trimIndent()
        val images = mutableListOf<Map<String, Any?>>()

        connection.prepareStatement(sql).use { statement ->
            statement.setInt(1, patientId)

            statement.executeQuery().use { result ->
                while (result.next()) {
                    images.add(mapClinicalImage(result))
                }
            }
        }

        return images
    }

    private fun findClinicalImageById(connection: Connection, imageId: Int): Map<String, Any?> {
        val sql = """
            SELECT id, paciente_id, tipo, descripcion, nombre_original, content_type, url_publica, fecha_subida
            FROM imagenes_clinicas
            WHERE id = ?
            LIMIT 1
        """.trimIndent()

        connection.prepareStatement(sql).use { statement ->
            statement.setInt(1, imageId)

            statement.executeQuery().use { result ->
                if (result.next()) return mapClinicalImage(result)
            }
        }

        error("No fue posible recuperar la imagen registrada.")
    }

    private fun mapClinicalImage(result: java.sql.ResultSet): Map<String, Any?> {
        return mapOf(
            "id" to result.getInt("id"),
            "pacienteId" to result.getInt("paciente_id"),
            "tipo" to result.getString("tipo"),
            "descripcion" to result.getString("descripcion"),
            "nombreOriginal" to result.getString("nombre_original"),
            "contentType" to result.getString("content_type"),
            "url" to "/api/pacientes/${result.getInt("paciente_id")}/imagenes/${result.getInt("id")}/archivo",
            "fechaSubida" to result.getTimestamp("fecha_subida")?.toLocalDateTime()?.toString()
        )
    }

    private fun findOdontogram(connection: Connection, patientId: Int): List<Map<String, Any?>> {
        val sql = """
            SELECT id, numero_pieza, superficie, estado, fecha_actualizacion
            FROM odontograma_piezas
            WHERE paciente_id = ?
            ORDER BY numero_pieza ASC, superficie ASC
        """.trimIndent()
        val pieces = mutableListOf<Map<String, Any?>>()

        connection.prepareStatement(sql).use { statement ->
            statement.setInt(1, patientId)

            statement.executeQuery().use { result ->
                while (result.next()) {
                    pieces.add(
                        mapOf(
                            "odontogramaId" to result.getInt("id"),
                            "numeroPieza" to result.getInt("numero_pieza"),
                            "superficie" to result.getString("superficie"),
                            "estado" to result.getString("estado"),
                            "fechaActualizacion" to result.getTimestamp("fecha_actualizacion")?.toLocalDateTime()?.toString()
                        )
                    )
                }
            }
        }

        return pieces
    }
}

// Registro operativo de pagos capturados por recepcion.
object PaymentRepository {
    fun create(request: CrearPagoRequest): Int {
        val sql = """
            INSERT INTO pagos (paciente_nombre, concepto, monto, metodo, fecha_registro)
            VALUES (?, ?, ?, ?, NOW())
        """.trimIndent()

        Database.connection().use { connection ->
            connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { statement ->
                statement.setString(1, request.pacienteNombre.trim())
                statement.setString(2, request.concepto.trim())
                statement.setDouble(3, request.monto)
                statement.setString(4, request.metodo.trim())
                statement.executeUpdate()

                statement.generatedKeys.use { keys ->
                    if (keys.next()) return keys.getInt(1)
                }
            }
        }

        error("No fue posible obtener el id del pago registrado.")
    }

    fun list(): List<Map<String, Any?>> {
        val sql = """
            SELECT id, paciente_nombre, concepto, monto, metodo, fecha_registro
            FROM pagos
            ORDER BY fecha_registro DESC
        """.trimIndent()
        val payments = mutableListOf<Map<String, Any?>>()

        Database.connection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.executeQuery().use { result ->
                    while (result.next()) {
                        payments.add(
                            mapOf(
                                "id" to result.getInt("id"),
                                "pacienteNombre" to result.getString("paciente_nombre"),
                                "concepto" to result.getString("concepto"),
                                "monto" to result.getDouble("monto"),
                                "metodo" to result.getString("metodo"),
                                "fechaRegistro" to result.getTimestamp("fecha_registro")?.toLocalDateTime()?.toString()
                            )
                        )
                    }
                }
            }
        }

        return payments
    }
}

// Resumen operativo usado por el dashboard de recepcion.
object ReceptionReportRepository {
    fun summary(): Map<String, Any> {
        Database.connection().use { connection ->
            return mapOf(
                "citasHoy" to count(connection, "SELECT COUNT(*) FROM citas WHERE fecha = CURDATE()"),
                "citasConfirmadasHoy" to count(
                    connection,
                    "SELECT COUNT(*) FROM citas WHERE fecha = CURDATE() AND estatus = 'CONFIRMADA'"
                ),
                "citasAsistioHoy" to count(
                    connection,
                    "SELECT COUNT(*) FROM citas WHERE fecha = CURDATE() AND estatus = 'ASISTIO'"
                ),
                "pacientesActivos" to count(connection, "SELECT COUNT(*) FROM pacientes WHERE activo = TRUE"),
                "pagosRegistrados" to count(connection, "SELECT COUNT(*) FROM pagos"),
                "pagosHoy" to count(connection, "SELECT COUNT(*) FROM pagos WHERE DATE(fecha_registro) = CURDATE()")
            )
        }
    }

    private fun count(connection: Connection, sql: String): Int {
        connection.prepareStatement(sql).use { statement ->
            statement.executeQuery().use { result ->
                result.next()
                return result.getInt(1)
            }
        }
    }
}

// Excepciones propias para convertir reglas de negocio en respuestas HTTP claras.
class AppointmentConflictException : RuntimeException()
class AppointmentNotFoundException : RuntimeException()
class AppointmentPolicyException : RuntimeException()
class OdontogramPieceNotFoundException : RuntimeException()
class PatientNotFoundException : RuntimeException()
class AdminUserNotFoundException : RuntimeException()
class HolidayNotFoundException : RuntimeException()
class AppointmentRequestNotFoundException : RuntimeException()
class InvalidCurrentPasswordException : RuntimeException()
class DentistNotFoundException : RuntimeException()

enum class AdminRole(val databaseValue: String) {
    ADMIN("ADMIN"),
    RECEPTION("RECEPCION"),
    DENTIST("ODONTOLOGO");

    companion object {
        fun from(value: String): AdminRole? {
            return when (normalizeText(value)) {
                "ADMIN", "ADMINISTRADOR" -> ADMIN
                "RECEPCION", "RECEPCIONISTA" -> RECEPTION
                "ODONTOLOGO", "DENTISTA" -> DENTIST
                else -> null
            }
        }
    }
}

// Estatus validos de cita y su valor final guardado en la base.
enum class AppointmentStatus(val databaseValue: String) {
    CONFIRMED("CONFIRMADA"),
    ATTENDED("ASISTIO"),
    MISSED("NO_ASISTIO"),
    CANCELLED("CANCELADA");

    companion object {
        fun from(value: String): AppointmentStatus? {
            val normalized = normalizeText(value)
                .replace(" ", "_")
                .replace("-", "_")

            return when (normalized) {
                "CONFIRMADA", "CONFIRMED" -> CONFIRMED
                "ASISTIO", "ATTENDED" -> ATTENDED
                "NO_ASISTIO", "NOASISTIO", "MISSED" -> MISSED
                "CANCELADA", "CANCELLED" -> CANCELLED
                else -> null
            }
        }
    }
}

fun currentAuth(ctx: Context): AuthPrincipal? {
    val header = ctx.header("Authorization").orEmpty()
    val token = header.removePrefix("Bearer ").trim()

    if (token.isBlank() || token == header) return null

    return AuthService.parseToken(token)
}

fun auditLog(ctx: Context, action: String, target: String, details: String = "") {
    val principal = currentAuth(ctx)
    val actor = if (principal == null) {
        "ANONIMO"
    } else {
        "${principal.tipo}#${principal.id} rol=${principal.rol}"
    }
    val forwardedIp = ctx.header("X-Forwarded-For")
        ?.split(",")
        ?.firstOrNull()
        ?.trim()
        ?.ifBlank { null }
    val ip = forwardedIp ?: ctx.req().remoteAddr
    val detailsText = details.ifBlank { "sin_detalles" }
    val safeAction = auditSafe(action)
    val safeTarget = auditSafe(target)
    val safeIp = auditSafe(ip)
    val safeMethod = auditSafe(ctx.req().method)
    val safePath = auditSafe(ctx.path())
    val safeDetails = auditSafe(detailsText)

    println(
        "[AUDIT] fecha=${LocalDateTime.now()} actor=${auditSafe(actor)} accion=$safeAction recurso=$safeTarget ip=$safeIp ruta=$safeMethod $safePath detalles=$safeDetails"
    )

    try {
        AuditRepository.create(
            principal = principal,
            action = safeAction,
            target = safeTarget,
            ip = safeIp,
            method = safeMethod,
            path = safePath,
            details = safeDetails
        )
    } catch (error: SQLException) {
        println("[AUDIT_DB_ERROR] ${auditSafe(error.message ?: "No fue posible guardar auditoria.")}")
    }
}

fun auditSafe(value: String): String = value
    .replace(Regex("[\\r\\n\\t]+"), " ")
    .take(500)

fun normalizeRequiredText(value: String, field: String, minLength: Int, maxLength: Int): String {
    val normalized = value.trim().replace(Regex("\\s+"), " ")

    if (normalized.length < minLength) {
        throw IllegalArgumentException("$field debe tener al menos $minLength caracteres.")
    }

    if (normalized.length > maxLength) {
        throw IllegalArgumentException("$field no debe exceder $maxLength caracteres.")
    }

    if (normalized.any { Character.isISOControl(it) }) {
        throw IllegalArgumentException("$field contiene caracteres no permitidos.")
    }

    return normalized
}

fun normalizeRequiredLongText(value: String, field: String, minLength: Int, maxLength: Int): String {
    val normalized = value.trim()

    if (normalized.length < minLength) {
        throw IllegalArgumentException("$field debe tener al menos $minLength caracteres.")
    }

    if (normalized.length > maxLength) {
        throw IllegalArgumentException("$field no debe exceder $maxLength caracteres.")
    }

    if (normalized.any { it != '\n' && it != '\r' && it != '\t' && Character.isISOControl(it) }) {
        throw IllegalArgumentException("$field contiene caracteres no permitidos.")
    }

    return normalized
}

fun normalizeEmail(value: String): String {
    val normalized = value.trim().lowercase()
    val emailPattern = Regex("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", RegexOption.IGNORE_CASE)

    if (normalized.length > 160 || !emailPattern.matches(normalized)) {
        throw IllegalArgumentException("El correo electronico no tiene un formato valido.")
    }

    return normalized
}

fun normalizePhone(value: String): String {
    val normalized = value.trim()
    val phonePattern = Regex("^[0-9+()\\s-]{7,20}$")

    if (!phonePattern.matches(normalized)) {
        throw IllegalArgumentException("El telefono debe tener de 7 a 20 caracteres y solo usar numeros, espacios, +, guiones o parentesis.")
    }

    return normalized
}

fun validatePasswordStrength(value: String) {
    if (value.length !in 8..128) {
        throw IllegalArgumentException("La contrasena debe tener entre 8 y 128 caracteres.")
    }

    if (!value.any { it.isLetter() } || !value.any { it.isDigit() }) {
        throw IllegalArgumentException("La contrasena debe incluir al menos una letra y un numero.")
    }
}

fun validateFutureDateTime(value: LocalDateTime, field: String) {
    if (value.isBefore(LocalDateTime.now())) {
        throw IllegalArgumentException("$field debe ser una fecha y hora futura.")
    }
}

fun requireRoles(ctx: Context, vararg roles: String): Boolean {
    val principal = currentAuth(ctx)

    if (principal == null) {
        ctx.status(HttpStatus.UNAUTHORIZED).json(ApiError("Sesion requerida."))
        return false
    }

    val allowedRoles = roles.map { it.uppercase() }.toSet()
    if (principal.rol.uppercase() !in allowedRoles) {
        ctx.status(HttpStatus.FORBIDDEN).json(ApiError("No tienes permisos para esta accion."))
        return false
    }

    return true
}

fun requirePatientOwnerOrRoles(ctx: Context, patientId: Int, vararg roles: String): Boolean {
    val principal = currentAuth(ctx)

    if (principal == null) {
        ctx.status(HttpStatus.UNAUTHORIZED).json(ApiError("Sesion requerida."))
        return false
    }

    val isPatientOwner = principal.tipo == "PACIENTE" && principal.id == patientId
    val allowedRoles = roles.map { it.uppercase() }.toSet()
    val hasStaffRole = principal.rol.uppercase() in allowedRoles

    if (!isPatientOwner && !hasStaffRole) {
        ctx.status(HttpStatus.FORBIDDEN).json(ApiError("No tienes permisos para este recurso."))
        return false
    }

    return true
}

@Suppress("UNUSED_PARAMETER")
fun main(args: Array<String>) {
    AppConfig.validateForStartup()

    val port = AppConfig.getInt("SERVER_PORT", 8080)
    val publicDirectory = AppConfig.get("STATIC_FILES_DIR", "public")
    val uploadsDirectory = AppConfig.get("UPLOADS_DIR", "uploads")
    val clinicalImagesDirectory = AppConfig.get("CLINICAL_IMAGES_DIR", "uploads/clinical-images")

    File(uploadsDirectory).mkdirs()
    File(clinicalImagesDirectory).mkdirs()

    val app = Javalin.create { config ->
        // Sirve los archivos HTML/CSS/JS desde la carpeta public.
        config.staticFiles.add { staticFiles ->
            staticFiles.hostedPath = "/"
            staticFiles.directory = publicDirectory
            staticFiles.location = Location.EXTERNAL
        }
    }

    // Rutas del portal público y recepción.
    app.post("/api/pacientes/registro") { ctx -> registerPatient(ctx) }
    app.post("/api/pacientes") { ctx -> registerPatient(ctx) }
    app.get("/api/pacientes") { ctx -> if (requireRoles(ctx, "ADMIN", "RECEPCION", "ODONTOLOGO")) listPatients(ctx) }
    app.get("/api/pacientes/buscar") { ctx -> if (requireRoles(ctx, "ADMIN", "RECEPCION", "ODONTOLOGO")) searchPatients(ctx) }
    app.put("/api/pacientes/{id}") { ctx -> if (requireRoles(ctx, "ADMIN", "RECEPCION")) updatePatient(ctx) }
    app.put("/api/pacientes/{id}/password") { ctx -> if (requireRoles(ctx, "ADMIN", "RECEPCION")) resetPatientPassword(ctx) }
    app.put("/api/pacientes/password") { ctx -> changeOwnPatientPassword(ctx) }
    app.delete("/api/pacientes/{id}") { ctx -> if (requireRoles(ctx, "ADMIN", "RECEPCION")) deletePatient(ctx) }
    app.get("/api/pacientes/{id}/citas") { ctx ->
        val patientId = ctx.pathParam("id").toIntOrNull()
        if (patientId == null) {
            badRequest(ctx, "El id del paciente debe ser numerico.")
        } else if (requirePatientOwnerOrRoles(ctx, patientId, "ADMIN", "RECEPCION")) {
            listPatientAppointments(ctx)
        }
    }
    app.put("/api/pacientes/{id}/citas/{citaId}/cancelar") { ctx ->
        val patientId = ctx.pathParam("id").toIntOrNull()
        if (patientId == null) {
            badRequest(ctx, "El id del paciente debe ser numerico.")
        } else if (requirePatientOwnerOrRoles(ctx, patientId, "ADMIN", "RECEPCION")) {
            cancelPatientAppointment(ctx)
        }
    }
    app.post("/api/login") { ctx -> loginPatient(ctx) }
    app.post("/api/admin/login") { ctx -> loginAdmin(ctx) }
    app.get("/api/admin/usuarios") { ctx -> if (requireRoles(ctx, "ADMIN")) listAdminUsers(ctx) }
    app.post("/api/admin/usuarios") { ctx -> if (requireRoles(ctx, "ADMIN")) createAdminUser(ctx) }
    app.put("/api/admin/usuarios/{id}/password") { ctx -> if (requireRoles(ctx, "ADMIN")) resetAdminUserPassword(ctx) }
    app.put("/api/admin/password") { ctx -> changeOwnAdminPassword(ctx) }
    app.delete("/api/admin/usuarios/{id}") { ctx -> if (requireRoles(ctx, "ADMIN")) deleteAdminUser(ctx) }
    app.get("/api/admin/auditoria") { ctx -> if (requireRoles(ctx, "ADMIN")) listAuditEvents(ctx) }
    app.post("/api/citas") { ctx -> if (requireRoles(ctx, "ADMIN", "RECEPCION")) createAppointment(ctx) }
    app.get("/api/citas") { ctx -> if (requireRoles(ctx, "ADMIN", "RECEPCION")) listAppointments(ctx) }
    app.get("/api/citas/disponibilidad") { ctx -> getAppointmentAvailability(ctx) }
    app.get("/api/odontologos") { ctx -> listDentists(ctx) }
    app.get("/api/citas/hoy") { ctx -> if (requireRoles(ctx, "ADMIN", "RECEPCION")) listTodayAppointments(ctx) }
    app.post("/api/citas/agendar") { ctx -> if (requireRoles(ctx, "ADMIN", "RECEPCION")) scheduleAppointment(ctx) }
    app.put("/api/citas/{id}/estatus") { ctx -> if (requireRoles(ctx, "ADMIN", "RECEPCION")) updateAppointmentStatus(ctx) }
    app.put("/api/citas/{id}/reprogramar") { ctx -> rescheduleAppointment(ctx) }
    app.post("/api/solicitudes-cita") { ctx -> createAppointmentRequest(ctx) }
    app.get("/api/solicitudes-cita") { ctx -> if (requireRoles(ctx, "ADMIN", "RECEPCION")) listAppointmentRequests(ctx) }
    app.put("/api/solicitudes-cita/{id}/aceptar") { ctx -> if (requireRoles(ctx, "ADMIN", "RECEPCION")) acceptAppointmentRequest(ctx) }
    app.put("/api/solicitudes-cita/{id}/rechazar") { ctx -> if (requireRoles(ctx, "ADMIN", "RECEPCION")) rejectAppointmentRequest(ctx) }
    app.get("/api/pagos") { ctx -> if (requireRoles(ctx, "ADMIN", "RECEPCION")) listPayments(ctx) }
    app.post("/api/pagos") { ctx -> if (requireRoles(ctx, "ADMIN", "RECEPCION")) createPayment(ctx) }
    app.get("/api/reportes/recepcion") { ctx -> if (requireRoles(ctx, "ADMIN", "RECEPCION")) getReceptionReport(ctx) }
    app.get("/api/horarios-atencion") { ctx -> listBusinessHours(ctx) }
    app.put("/api/horarios-atencion") { ctx -> if (requireRoles(ctx, "ADMIN", "RECEPCION")) updateBusinessHours(ctx) }
    app.get("/api/odontologos/{id}/horarios") { ctx -> if (requireRoles(ctx, "ADMIN", "RECEPCION", "ODONTOLOGO")) listDentistHours(ctx) }
    app.put("/api/odontologos/{id}/horarios") { ctx -> if (requireRoles(ctx, "ADMIN", "RECEPCION", "ODONTOLOGO")) updateDentistHours(ctx) }
    app.get("/api/dias-feriados") { ctx -> if (requireRoles(ctx, "ADMIN", "RECEPCION")) listHolidays(ctx) }
    app.post("/api/dias-feriados") { ctx -> if (requireRoles(ctx, "ADMIN", "RECEPCION")) createHoliday(ctx) }
    app.delete("/api/dias-feriados/{id}") { ctx -> if (requireRoles(ctx, "ADMIN", "RECEPCION")) deleteHoliday(ctx) }

    // Rutas del expediente clinico y odontograma.
    app.get("/api/pacientes/{id}/expediente") { ctx ->
        val patientId = ctx.pathParam("id").toIntOrNull()
        if (patientId == null) {
            badRequest(ctx, "El id del paciente debe ser numerico.")
        } else if (requirePatientOwnerOrRoles(ctx, patientId, "ADMIN", "RECEPCION", "ODONTOLOGO")) {
            getClinicalFile(ctx)
        }
    }
    app.get("/api/pacientes/{id}/historial") { ctx -> if (requireRoles(ctx, "ADMIN", "RECEPCION", "ODONTOLOGO")) getPatientHistory(ctx) }
    app.put("/api/pacientes/{id}/expediente") { ctx -> if (requireRoles(ctx, "ADMIN", "ODONTOLOGO")) updateClinicalFile(ctx) }
    app.put("/api/odontograma/{odontogramaId}/pieza/{numeroPieza}") { ctx -> if (requireRoles(ctx, "ADMIN", "ODONTOLOGO")) updateOdontogramPiece(ctx) }
    app.post("/api/pacientes/{id}/notas") { ctx -> if (requireRoles(ctx, "ADMIN", "ODONTOLOGO")) addClinicalNote(ctx) }
    app.post("/api/pacientes/{id}/imagenes") { ctx -> if (requireRoles(ctx, "ADMIN", "ODONTOLOGO")) uploadClinicalImage(ctx) }
    app.get("/api/pacientes/{id}/imagenes/{imagenId}/archivo") { ctx ->
        val patientId = ctx.pathParam("id").toIntOrNull()
        if (patientId == null) {
            badRequest(ctx, "El id del paciente debe ser numerico.")
        } else if (requirePatientOwnerOrRoles(ctx, patientId, "ADMIN", "RECEPCION", "ODONTOLOGO")) {
            serveClinicalImage(ctx)
        }
    }

    // Rutas de apoyo para pruebas locales.
    app.get("/api/health/db") { ctx -> checkDatabaseHealth(ctx) }
    app.post("/api/admin/init-db") { ctx -> initializeDatabaseSchema(ctx) }

    // No expone detalles tecnicos al usuario; los conserva solo en consola.
    app.exception(SQLException::class.java) { error, ctx ->
        logServerError(ctx, error)
        internalServerError(ctx)
    }

    app.exception(Exception::class.java) { error, ctx ->
        logServerError(ctx, error)
        internalServerError(ctx)
    }

    app.start(port)
    println("Servidor iniciado en http://localhost:$port")
}

// POST /api/pacientes: registra un paciente/usuario.
fun registerPatient(ctx: Context) {
    val request = ctx.bodyAsClass(RegistroPacienteRequest::class.java)

    try {
        normalizeRequiredText(request.nombre, "Nombre", 2, 120)
        normalizePhone(request.telefono)
        normalizeEmail(request.email)
        validatePasswordStrength(request.password)
    } catch (error: IllegalArgumentException) {
        return badRequest(ctx, error.message ?: "Datos de paciente invalidos.")
    }

    try {
        val patientId = PatientRepository.create(request)
        auditLog(ctx, "PACIENTE_CREADO", "paciente:$patientId", "email=${request.email.trim().lowercase()}")
        ctx.status(HttpStatus.CREATED).json(
            mapOf(
                "id" to patientId,
                "mensaje" to "Paciente registrado correctamente."
            )
        )
    } catch (_: SQLIntegrityConstraintViolationException) {
        ctx.status(HttpStatus.CONFLICT).json(ApiError("El email ya esta registrado. Usa otro correo para la prueba."))
    }
}

// GET /api/pacientes: lista pacientes guardados en MariaDB.
fun listPatients(ctx: Context) {
    ctx.json(PatientRepository.list())
}

// GET /api/pacientes/buscar?q=texto: busca pacientes activos por nombre, correo, telefono o id.
fun searchPatients(ctx: Context) {
    val query = ctx.queryParam("q")?.trim().orEmpty()

    if (query.length < 2 && query.toIntOrNull() == null) {
        ctx.json(emptyList<Map<String, Any?>>())
        return
    }

    ctx.json(PatientRepository.search(query))
}

// PUT /api/pacientes/{id}: edita datos basicos del paciente.
fun updatePatient(ctx: Context) {
    val patientId = ctx.pathParam("id").toIntOrNull()
    val request = ctx.bodyAsClass(ActualizarPacienteRequest::class.java)

    if (patientId == null || patientId <= 0) {
        return badRequest(ctx, "El id del paciente debe ser numerico.")
    }

    try {
        normalizeRequiredText(request.nombre, "Nombre", 2, 120)
        normalizePhone(request.telefono)
        normalizeEmail(request.email)
    } catch (error: IllegalArgumentException) {
        return badRequest(ctx, error.message ?: "Datos de paciente invalidos.")
    }

    try {
        PatientRepository.update(patientId, request)
        auditLog(ctx, "PACIENTE_ACTUALIZADO", "paciente:$patientId", "email=${request.email.trim().lowercase()}")
        ctx.json(
            mapOf(
                "id" to patientId,
                "nombre" to request.nombre.trim(),
                "telefono" to request.telefono.trim(),
                "email" to request.email.trim().lowercase(),
                "mensaje" to "Paciente actualizado correctamente."
            )
        )
    } catch (_: SQLIntegrityConstraintViolationException) {
        ctx.status(HttpStatus.CONFLICT).json(ApiError("El email ya esta registrado en otro paciente."))
    } catch (_: PatientNotFoundException) {
        ctx.status(HttpStatus.NOT_FOUND).json(ApiError("No se encontro el paciente solicitado."))
    }
}

// PUT /api/pacientes/{id}/password: cambia contrasena de paciente.
fun resetPatientPassword(ctx: Context) {
    val patientId = ctx.pathParam("id").toIntOrNull()
    val request = ctx.bodyAsClass(CambiarPasswordPacienteRequest::class.java)

    if (patientId == null || patientId <= 0) {
        return badRequest(ctx, "El id del paciente debe ser numerico.")
    }

    try {
        validatePasswordStrength(request.password)
    } catch (error: IllegalArgumentException) {
        return badRequest(ctx, error.message ?: "Contrasena invalida.")
    }

    try {
        PatientRepository.resetPassword(patientId, request.password)
        auditLog(ctx, "PASSWORD_PACIENTE_REINICIADA", "paciente:$patientId")
        ctx.json(
            mapOf(
                "id" to patientId,
                "mensaje" to "Contrasena actualizada correctamente."
            )
        )
    } catch (_: PatientNotFoundException) {
        ctx.status(HttpStatus.NOT_FOUND).json(ApiError("No se encontro el paciente solicitado."))
    }
}

// PUT /api/pacientes/password: permite al paciente cambiar su propia contraseña.
fun changeOwnPatientPassword(ctx: Context) {
    val principal = currentAuth(ctx)
    val request = ctx.bodyAsClass(CambiarPasswordPropiaRequest::class.java)

    if (principal == null || principal.tipo != "PACIENTE") {
        ctx.status(HttpStatus.UNAUTHORIZED).json(ApiError("Sesion de paciente requerida."))
        return
    }

    if (request.passwordActual.isBlank()) {
        return badRequest(ctx, "La contrasena actual es obligatoria.")
    }

    try {
        validatePasswordStrength(request.passwordNueva)
    } catch (error: IllegalArgumentException) {
        return badRequest(ctx, error.message ?: "Contrasena invalida.")
    }

    try {
        PatientRepository.changeOwnPassword(principal.id, request.passwordActual, request.passwordNueva)
        auditLog(ctx, "PASSWORD_PROPIA_PACIENTE_CAMBIADA", "paciente:${principal.id}")
        ctx.json(mapOf("mensaje" to "Contrasena actualizada correctamente."))
    } catch (_: InvalidCurrentPasswordException) {
        ctx.status(HttpStatus.CONFLICT).json(ApiError("La contrasena actual no es correcta."))
    } catch (_: PatientNotFoundException) {
        ctx.status(HttpStatus.NOT_FOUND).json(ApiError("No se encontro el paciente solicitado."))
    }
}

// DELETE /api/pacientes/{id}: borra perfil, citas e historial clinico del paciente.
fun deletePatient(ctx: Context) {
    val patientId = ctx.pathParam("id").toIntOrNull()

    if (patientId == null || patientId <= 0) {
        return badRequest(ctx, "El id del paciente debe ser numerico.")
    }

    try {
        PatientRepository.deleteProfileAndClinicalHistory(patientId)
        auditLog(ctx, "PACIENTE_E_HISTORIAL_ELIMINADOS", "paciente:$patientId")
        ctx.json(
            mapOf(
                "id" to patientId,
                "mensaje" to "Paciente e historial clinico eliminados correctamente."
            )
        )
    } catch (_: PatientNotFoundException) {
        ctx.status(HttpStatus.NOT_FOUND).json(ApiError("No se encontro el paciente solicitado."))
    }
}

// GET /api/pacientes/{id}/citas: proximas citas activas del paciente.
fun listPatientAppointments(ctx: Context) {
    val patientId = ctx.pathParam("id").toIntOrNull()

    if (patientId == null || patientId <= 0) {
        return badRequest(ctx, "El id del paciente debe ser numerico.")
    }

    ctx.json(AppointmentRepository.listByPatient(patientId))
}

// PUT /api/pacientes/{id}/citas/{citaId}/cancelar: cancela respetando la regla de 24 horas.
fun cancelPatientAppointment(ctx: Context) {
    val patientId = ctx.pathParam("id").toIntOrNull()
    val appointmentId = ctx.pathParam("citaId").toIntOrNull()

    if (patientId == null || patientId <= 0 || appointmentId == null || appointmentId <= 0) {
        return badRequest(ctx, "El id del paciente y de la cita deben ser numericos.")
    }

    try {
        AppointmentRepository.cancelByPatient(appointmentId, patientId)
        auditLog(ctx, "CITA_CANCELADA_POR_PACIENTE", "cita:$appointmentId", "pacienteId=$patientId")
        ctx.json(
            mapOf(
                "id" to appointmentId,
                "pacienteId" to patientId,
                "estatus" to AppointmentStatus.CANCELLED.databaseValue,
                "mensaje" to "Cita cancelada correctamente."
            )
        )
    } catch (_: AppointmentPolicyException) {
        ctx.status(HttpStatus.CONFLICT).json(
            ApiError("La cita solo puede cancelarse con al menos 24 horas de anticipacion.")
        )
    } catch (_: AppointmentNotFoundException) {
        ctx.status(HttpStatus.NOT_FOUND).json(ApiError("No se encontro la cita solicitada."))
    }
}

// POST /api/login: valida email y password del paciente.
fun loginPatient(ctx: Context) {
    val request = ctx.bodyAsClass(LoginRequest::class.java)

    if (request.email.isBlank() || request.password.isBlank()) {
        return badRequest(ctx, "Email y password son obligatorios.")
    }

    val patient = PatientRepository.findByCredentials(request.email, request.password)

    if (patient == null) {
        ctx.status(HttpStatus.UNAUTHORIZED).json(ApiError("Credenciales invalidas."))
        return
    }

    ctx.json(
        mapOf(
            "mensaje" to "Login correcto.",
            "paciente" to patient,
            "token" to AuthService.createToken(patient["id"] as Int, "PACIENTE", "PACIENTE")
        )
    )
}

// POST /api/admin/login: valida acceso de recepcion, odontologo o administrador.
fun loginAdmin(ctx: Context) {
    val request = ctx.bodyAsClass(AdminLoginRequest::class.java)

    if (request.email.isBlank() || request.password.isBlank()) {
        return badRequest(ctx, "Email y password son obligatorios.")
    }

    val user = AdminRepository.findByCredentials(request.email, request.password)

    if (user == null) {
        ctx.status(HttpStatus.UNAUTHORIZED).json(ApiError("Credenciales administrativas invalidas."))
        return
    }

    ctx.json(
        mapOf(
            "mensaje" to "Login administrativo correcto.",
            "usuario" to user,
            "token" to AuthService.createToken(user["id"] as Int, "ADMIN", user["rol"] as String)
        )
    )
}

// GET /api/admin/usuarios: lista usuarios internos del sistema.
fun listAdminUsers(ctx: Context) {
    ctx.json(AdminRepository.list())
}

// POST /api/admin/usuarios: crea administradores, recepcionistas u odontologos.
fun createAdminUser(ctx: Context) {
    val request = ctx.bodyAsClass(CrearAdminUsuarioRequest::class.java)
    val role = AdminRole.from(request.rol)

    if (role == null) {
        return badRequest(ctx, "El rol debe ser ADMIN, RECEPCION u ODONTOLOGO.")
    }

    try {
        normalizeRequiredText(request.nombre, "Nombre", 2, 120)
        normalizeEmail(request.email)
        validatePasswordStrength(request.password)
    } catch (error: IllegalArgumentException) {
        return badRequest(ctx, error.message ?: "Datos de usuario invalidos.")
    }

    try {
        val userId = AdminRepository.create(request, role)
        auditLog(ctx, "USUARIO_INTERNO_CREADO", "usuario_admin:$userId", "rol=${role.databaseValue} email=${request.email.trim().lowercase()}")
        ctx.status(HttpStatus.CREATED).json(
            mapOf(
                "id" to userId,
                "nombre" to request.nombre.trim(),
                "email" to request.email.trim().lowercase(),
                "rol" to role.databaseValue,
                "activo" to true,
                "mensaje" to "Usuario administrativo creado correctamente."
            )
        )
    } catch (_: SQLIntegrityConstraintViolationException) {
        ctx.status(HttpStatus.CONFLICT).json(ApiError("El email administrativo ya esta registrado."))
    }
}

// PUT /api/admin/usuarios/{id}/password: reinicia la contrasena de un usuario interno.
fun resetAdminUserPassword(ctx: Context) {
    val userId = ctx.pathParam("id").toIntOrNull()
    val request = ctx.bodyAsClass(CambiarPasswordAdminRequest::class.java)

    if (userId == null || userId <= 0) {
        return badRequest(ctx, "El id del usuario debe ser numerico.")
    }

    try {
        validatePasswordStrength(request.password)
    } catch (error: IllegalArgumentException) {
        return badRequest(ctx, error.message ?: "Contrasena invalida.")
    }

    try {
        AdminRepository.resetPassword(userId, request.password)
        auditLog(ctx, "PASSWORD_USUARIO_INTERNO_REINICIADA", "usuario_admin:$userId")
        ctx.json(
            mapOf(
                "id" to userId,
                "mensaje" to "Contrasena actualizada correctamente."
            )
        )
    } catch (_: AdminUserNotFoundException) {
        ctx.status(HttpStatus.NOT_FOUND).json(ApiError("No se encontro el usuario administrativo solicitado."))
    }
}

// PUT /api/admin/password: permite a recepcion, odontologo o admin cambiar su propia contrasena.
fun changeOwnAdminPassword(ctx: Context) {
    val principal = currentAuth(ctx)
    val request = ctx.bodyAsClass(CambiarPasswordPropiaRequest::class.java)

    if (principal == null || principal.tipo != "ADMIN") {
        ctx.status(HttpStatus.UNAUTHORIZED).json(ApiError("Sesion de personal requerida."))
        return
    }

    if (request.passwordActual.isBlank()) {
        return badRequest(ctx, "La contrasena actual es obligatoria.")
    }

    try {
        validatePasswordStrength(request.passwordNueva)
    } catch (error: IllegalArgumentException) {
        return badRequest(ctx, error.message ?: "Contrasena invalida.")
    }

    try {
        AdminRepository.changeOwnPassword(principal.id, request.passwordActual, request.passwordNueva)
        auditLog(ctx, "PASSWORD_PROPIA_PERSONAL_CAMBIADA", "usuario_admin:${principal.id}")
        ctx.json(mapOf("mensaje" to "Contrasena actualizada correctamente."))
    } catch (_: InvalidCurrentPasswordException) {
        ctx.status(HttpStatus.CONFLICT).json(ApiError("La contrasena actual no es correcta."))
    } catch (_: AdminUserNotFoundException) {
        ctx.status(HttpStatus.NOT_FOUND).json(ApiError("No se encontro el usuario administrativo solicitado."))
    }
}

// DELETE /api/admin/usuarios/{id}: elimina un usuario interno.
fun deleteAdminUser(ctx: Context) {
    val userId = ctx.pathParam("id").toIntOrNull()

    if (userId == null || userId <= 0) {
        return badRequest(ctx, "El id del usuario debe ser numerico.")
    }

    try {
        AdminRepository.deactivate(userId)
        auditLog(ctx, "USUARIO_INTERNO_ELIMINADO", "usuario_admin:$userId")
        ctx.json(
            mapOf(
                "id" to userId,
                "mensaje" to "Usuario eliminado correctamente."
            )
        )
    } catch (_: AdminUserNotFoundException) {
        ctx.status(HttpStatus.NOT_FOUND).json(ApiError("No se encontro el usuario administrativo solicitado."))
    }
}

// GET /api/admin/auditoria: consulta los eventos sensibles recientes.
fun listAuditEvents(ctx: Context) {
    val requestedLimit = ctx.queryParam("limit")?.toIntOrNull() ?: 100
    val limit = requestedLimit.coerceIn(1, 500)

    ctx.json(AuditRepository.list(limit))
}

// GET /api/citas/hoy: agenda consolidada del dia actual.
fun listTodayAppointments(ctx: Context) {
    ctx.json(AppointmentRepository.listToday())
}

// GET /api/citas?fecha=YYYY-MM-DD: agenda consolidada para una fecha especifica.
fun listAppointments(ctx: Context) {
    val requestedDate = ctx.queryParam("fecha")
    val appointmentDate = if (requestedDate.isNullOrBlank()) {
        LocalDate.now()
    } else {
        try {
            parseDate(requestedDate)
        } catch (_: IllegalArgumentException) {
            return badRequest(ctx, "La fecha debe tener formato YYYY-MM-DD.")
        }
    }

    ctx.json(AppointmentRepository.listByDate(appointmentDate))
}

// GET /api/citas/disponibilidad?fecha=YYYY-MM-DD&excluirCitaId=1: horarios libres/ocupados reales.
fun getAppointmentAvailability(ctx: Context) {
    val requestedDate = ctx.queryParam("fecha")?.trim()
    val excludedAppointmentId = ctx.queryParam("excluirCitaId")?.toIntOrNull()
    val odontologistId = ctx.queryParam("odontologoId")?.toIntOrNull()?.takeIf { it > 0 }

    if (requestedDate.isNullOrBlank()) {
        return badRequest(ctx, "La fecha es obligatoria.")
    }

    val appointmentDate = try {
        parseDate(requestedDate)
    } catch (_: IllegalArgumentException) {
        return badRequest(ctx, "La fecha debe tener formato YYYY-MM-DD.")
    }

    ctx.json(
        mapOf(
            "fecha" to appointmentDate.toString(),
            "odontologoId" to odontologistId,
            "horarios" to AppointmentRepository.availability(appointmentDate, excludedAppointmentId, odontologistId)
        )
    )
}

fun listDentists(ctx: Context) {
    ctx.json(DentistRepository.listActive())
}

// POST /api/citas/agendar: endpoint inicial usado por el portal publico.
fun scheduleAppointment(ctx: Context) {
    val request = ctx.bodyAsClass(AgendarCitaRequest::class.java)

    if (request.pacienteId <= 0 || request.odontologoId <= 0 || request.fecha.isBlank() || request.hora.isBlank()) {
        return badRequest(ctx, "Paciente, odontologo, fecha y hora son obligatorios.")
    }

    try {
        normalizeRequiredText(request.tratamiento, "Tratamiento", 3, 160)
        validateFutureDateTime(LocalDateTime.of(parseDate(request.fecha), parseTime(request.hora)), "La cita")
        val appointmentId = AppointmentRepository.schedule(request)
        ctx.status(HttpStatus.CREATED).json(
            mapOf(
                "id" to appointmentId,
                "mensaje" to "Cita agendada correctamente."
            )
        )
    } catch (_: AppointmentConflictException) {
        ctx.status(HttpStatus.CONFLICT).json(ApiError("La fecha y hora seleccionadas ya estan ocupadas."))
    } catch (_: PatientNotFoundException) {
        ctx.status(HttpStatus.NOT_FOUND).json(ApiError("No se encontro el paciente solicitado."))
    } catch (_: DentistNotFoundException) {
        ctx.status(HttpStatus.NOT_FOUND).json(ApiError("No se encontro el odontologo solicitado."))
    } catch (error: IllegalArgumentException) {
        badRequest(ctx, error.message ?: "Datos de cita invalidos.")
    }
}

// GET /api/pagos: devuelve la bitacora de pagos registrada.
fun listPayments(ctx: Context) {
    ctx.json(PaymentRepository.list())
}

// POST /api/pagos: guarda un pago operativo simple.
fun createPayment(ctx: Context) {
    val request = ctx.bodyAsClass(CrearPagoRequest::class.java)

    try {
        normalizeRequiredText(request.pacienteNombre, "Paciente", 2, 120)
        normalizeRequiredText(request.concepto, "Concepto", 3, 160)
        normalizeRequiredText(request.metodo, "Metodo de pago", 2, 40)
    } catch (error: IllegalArgumentException) {
        return badRequest(ctx, error.message ?: "Datos de pago invalidos.")
    }

    if (request.monto <= 0.0 || request.monto > 999999.99) {
        return badRequest(ctx, "El monto debe ser mayor a 0 y menor a 1,000,000.")
    }

    val paymentId = PaymentRepository.create(request)
    auditLog(ctx, "PAGO_REGISTRADO", "pago:$paymentId", "paciente=${request.pacienteNombre.trim()} monto=${request.monto}")
    ctx.status(HttpStatus.CREATED).json(
        mapOf(
            "id" to paymentId,
            "mensaje" to "Pago registrado correctamente."
        )
    )
}

// GET /api/reportes/recepcion: metricas rapidas calculadas desde MariaDB.
fun getReceptionReport(ctx: Context) {
    ctx.json(ReceptionReportRepository.summary())
}

// GET /api/horarios-atencion: configuracion semanal editable por recepcion.
fun listBusinessHours(ctx: Context) {
    ctx.json(BusinessHoursRepository.list())
}

// PUT /api/horarios-atencion: guarda dias activos y horario en bloques de 30 minutos.
fun updateBusinessHours(ctx: Context) {
    val request = ctx.bodyAsClass(ActualizarHorariosAtencionRequest::class.java)

    try {
        BusinessHoursRepository.update(request.horarios)
        auditLog(ctx, "HORARIOS_GENERALES_ACTUALIZADOS", "horarios_atencion", "dias=${request.horarios.size}")
        ctx.json(
            mapOf(
                "mensaje" to "Horarios de atencion actualizados correctamente.",
                "horarios" to BusinessHoursRepository.list()
            )
        )
    } catch (error: IllegalArgumentException) {
        badRequest(ctx, error.message ?: "Configuracion de horarios invalida.")
    }
}

fun listDentistHours(ctx: Context) {
    val odontologistId = ctx.pathParam("id").toIntOrNull()

    if (odontologistId == null || odontologistId <= 0) {
        return badRequest(ctx, "El id del odontologo debe ser numerico.")
    }

    ctx.json(
        mapOf(
            "odontologoId" to odontologistId,
            "horarios" to DentistHoursRepository.list(odontologistId)
        )
    )
}

fun updateDentistHours(ctx: Context) {
    val odontologistId = ctx.pathParam("id").toIntOrNull()
    val request = ctx.bodyAsClass(ActualizarHorariosAtencionRequest::class.java)

    if (odontologistId == null || odontologistId <= 0) {
        return badRequest(ctx, "El id del odontologo debe ser numerico.")
    }

    try {
        DentistHoursRepository.update(odontologistId, request.horarios)
        auditLog(ctx, "HORARIOS_ODONTOLOGO_ACTUALIZADOS", "odontologo:$odontologistId", "dias=${request.horarios.size}")
        ctx.json(
            mapOf(
                "odontologoId" to odontologistId,
                "mensaje" to "Horarios del odontologo actualizados correctamente.",
                "horarios" to DentistHoursRepository.list(odontologistId)
            )
        )
    } catch (error: IllegalArgumentException) {
        badRequest(ctx, error.message ?: "Configuracion de horarios invalida.")
    }
}

// GET /api/dias-feriados: lista cierres especiales configurados.
fun listHolidays(ctx: Context) {
    ctx.json(HolidayRepository.list())
}

// POST /api/dias-feriados: registra o actualiza un cierre especial por fecha.
fun createHoliday(ctx: Context) {
    val request = ctx.bodyAsClass(DiaFeriadoRequest::class.java)

    if (request.fecha.isBlank()) {
        return badRequest(ctx, "La fecha es obligatoria.")
    }

    try {
        val holidayId = HolidayRepository.create(request)
        auditLog(ctx, "CIERRE_ESPECIAL_REGISTRADO", "dia_feriado:$holidayId", "fecha=${request.fecha}")
        ctx.status(HttpStatus.CREATED).json(
            mapOf(
                "id" to holidayId,
                "mensaje" to "Dia feriado registrado correctamente.",
                "feriados" to HolidayRepository.list()
            )
        )
    } catch (error: IllegalArgumentException) {
        badRequest(ctx, error.message ?: "Fecha invalida.")
    }
}

// DELETE /api/dias-feriados/{id}: elimina un cierre especial.
fun deleteHoliday(ctx: Context) {
    val holidayId = ctx.pathParam("id").toIntOrNull()

    if (holidayId == null || holidayId <= 0) {
        return badRequest(ctx, "El id del dia feriado debe ser numerico.")
    }

    try {
        HolidayRepository.delete(holidayId)
        auditLog(ctx, "CIERRE_ESPECIAL_ELIMINADO", "dia_feriado:$holidayId")
        ctx.json(
            mapOf(
                "id" to holidayId,
                "mensaje" to "Dia feriado eliminado correctamente."
            )
        )
    } catch (_: HolidayNotFoundException) {
        ctx.status(HttpStatus.NOT_FOUND).json(ApiError("No se encontro el dia feriado solicitado."))
    }
}

// POST /api/citas: endpoint principal para crear citas desde frontend.
fun createAppointment(ctx: Context) {
    val request = ctx.bodyAsClass(CrearCitaRequest::class.java)

    if (request.pacienteId <= 0 || request.odontologoId <= 0 || request.fechaHora.isBlank() || request.motivo.isBlank()) {
        return badRequest(ctx, "Paciente, odontologo, fechaHora y motivo son obligatorios.")
    }

    try {
        normalizeRequiredText(request.motivo, "Motivo", 3, 160)
        validateFutureDateTime(parseDateTime(request.fechaHora), "La cita")
        val appointmentId = AppointmentRepository.create(request)
        auditLog(ctx, "CITA_CREADA", "cita:$appointmentId", "pacienteId=${request.pacienteId} odontologoId=${request.odontologoId} fechaHora=${request.fechaHora}")
        ctx.status(HttpStatus.CREATED).json(
            mapOf(
                "id" to appointmentId,
                "mensaje" to "Cita registrada correctamente."
            )
        )
    } catch (_: AppointmentConflictException) {
        ctx.status(HttpStatus.CONFLICT).json(ApiError("La fecha y hora seleccionadas ya estan ocupadas."))
    } catch (_: PatientNotFoundException) {
        ctx.status(HttpStatus.NOT_FOUND).json(ApiError("No se encontro el paciente solicitado."))
    } catch (_: DentistNotFoundException) {
        ctx.status(HttpStatus.NOT_FOUND).json(ApiError("No se encontro el odontologo solicitado."))
    } catch (error: IllegalArgumentException) {
        badRequest(ctx, error.message ?: "Datos de cita invalidos.")
    }
}

// PUT /api/citas/{id}/reprogramar: mueve una cita existente si cumple reglas de negocio.
fun rescheduleAppointment(ctx: Context) {
    val appointmentId = ctx.pathParam("id").toIntOrNull()
    val request = ctx.bodyAsClass(ReprogramarCitaRequest::class.java)

    if (appointmentId == null || appointmentId <= 0) {
        return badRequest(ctx, "El id de la cita debe ser numerico.")
    }

    if (request.pacienteId <= 0 || request.fechaHora.isBlank()) {
        return badRequest(ctx, "pacienteId y fechaHora son obligatorios.")
    }

    if (!requirePatientOwnerOrRoles(ctx, request.pacienteId, "ADMIN", "RECEPCION")) return

    try {
        validateFutureDateTime(parseDateTime(request.fechaHora), "La nueva fecha de la cita")
        AppointmentRepository.rescheduleByPatient(appointmentId, request)
        auditLog(ctx, "CITA_REPROGRAMADA", "cita:$appointmentId", "pacienteId=${request.pacienteId} fechaHora=${request.fechaHora}")
        ctx.json(
            mapOf(
                "id" to appointmentId,
                "pacienteId" to request.pacienteId,
                "fechaHora" to request.fechaHora,
                "mensaje" to "Cita reprogramada correctamente."
            )
        )
    } catch (_: AppointmentConflictException) {
        ctx.status(HttpStatus.CONFLICT).json(ApiError("La fecha y hora seleccionadas ya estan ocupadas."))
    } catch (_: AppointmentPolicyException) {
        ctx.status(HttpStatus.CONFLICT).json(
            ApiError("La cita solo puede reprogramarse con al menos 24 horas de anticipacion.")
        )
    } catch (_: AppointmentNotFoundException) {
        ctx.status(HttpStatus.NOT_FOUND).json(ApiError("No se encontro la cita solicitada para este paciente."))
    } catch (error: IllegalArgumentException) {
        badRequest(ctx, error.message ?: "Datos de cita invalidos.")
    }
}

// POST /api/solicitudes-cita: el paciente solicita una cita; recepcion la confirma o rechaza.
fun createAppointmentRequest(ctx: Context) {
    val request = ctx.bodyAsClass(CrearSolicitudCitaRequest::class.java)

    if (request.pacienteId <= 0 || request.fechaHora.isBlank() || request.motivo.isBlank()) {
        return badRequest(ctx, "pacienteId, fechaHora y motivo son obligatorios.")
    }

    if (!requirePatientOwnerOrRoles(ctx, request.pacienteId, "ADMIN", "RECEPCION")) return

    try {
        normalizeRequiredText(request.motivo, "Motivo", 3, 180)
        validateFutureDateTime(parseDateTime(request.fechaHora), "La solicitud de cita")
        val requestId = AppointmentRequestRepository.create(request)
        auditLog(ctx, "SOLICITUD_CITA_CREADA", "solicitud_cita:$requestId", "pacienteId=${request.pacienteId} fechaHora=${request.fechaHora}")
        ctx.status(HttpStatus.CREATED).json(
            mapOf(
                "id" to requestId,
                "mensaje" to "Solicitud enviada correctamente. Recepcion confirmara la cita."
            )
        )
    } catch (_: PatientNotFoundException) {
        ctx.status(HttpStatus.NOT_FOUND).json(ApiError("No se encontro el paciente solicitado."))
    } catch (_: DentistNotFoundException) {
        ctx.status(HttpStatus.NOT_FOUND).json(ApiError("No se encontro el odontologo solicitado."))
    } catch (error: IllegalArgumentException) {
        badRequest(ctx, error.message ?: "Datos de solicitud invalidos.")
    }
}

// GET /api/solicitudes-cita?estatus=PENDIENTE: bandeja de solicitudes para recepcion.
fun listAppointmentRequests(ctx: Context) {
    val status = ctx.queryParam("estatus") ?: "PENDIENTE"
    ctx.json(AppointmentRequestRepository.list(status))
}

// PUT /api/solicitudes-cita/{id}/aceptar: convierte la solicitud en cita confirmada.
fun acceptAppointmentRequest(ctx: Context) {
    val requestId = ctx.pathParam("id").toIntOrNull()
    val request = if (ctx.body().isBlank()) {
        AceptarSolicitudCitaRequest()
    } else {
        ctx.bodyAsClass(AceptarSolicitudCitaRequest::class.java)
    }

    if (requestId == null || requestId <= 0) {
        return badRequest(ctx, "El id de la solicitud debe ser numerico.")
    }

    if (request.odontologoId <= 0) {
        return badRequest(ctx, "Selecciona un odontologo para aceptar la solicitud.")
    }

    try {
        val appointmentId = AppointmentRequestRepository.accept(requestId, request.odontologoId)
        auditLog(ctx, "SOLICITUD_CITA_ACEPTADA", "solicitud_cita:$requestId", "citaId=$appointmentId odontologoId=${request.odontologoId}")
        ctx.json(
            mapOf(
                "id" to requestId,
                "citaId" to appointmentId,
                "mensaje" to "Solicitud aceptada y cita creada correctamente."
            )
        )
    } catch (_: AppointmentConflictException) {
        ctx.status(HttpStatus.CONFLICT).json(ApiError("Ese horario ya fue ocupado por otra cita."))
    } catch (_: PatientNotFoundException) {
        ctx.status(HttpStatus.NOT_FOUND).json(ApiError("No se encontro el paciente solicitado."))
    } catch (_: DentistNotFoundException) {
        ctx.status(HttpStatus.NOT_FOUND).json(ApiError("No se encontro el odontologo solicitado."))
    } catch (_: AppointmentRequestNotFoundException) {
        ctx.status(HttpStatus.NOT_FOUND).json(ApiError("No se encontro una solicitud pendiente con ese id."))
    }
}

// PUT /api/solicitudes-cita/{id}/rechazar: rechaza la solicitud del paciente.
fun rejectAppointmentRequest(ctx: Context) {
    val requestId = ctx.pathParam("id").toIntOrNull()

    if (requestId == null || requestId <= 0) {
        return badRequest(ctx, "El id de la solicitud debe ser numerico.")
    }

    try {
        AppointmentRequestRepository.reject(requestId)
        auditLog(ctx, "SOLICITUD_CITA_RECHAZADA", "solicitud_cita:$requestId")
        ctx.json(
            mapOf(
                "id" to requestId,
                "mensaje" to "Solicitud rechazada correctamente."
            )
        )
    } catch (_: AppointmentRequestNotFoundException) {
        ctx.status(HttpStatus.NOT_FOUND).json(ApiError("No se encontro una solicitud pendiente con ese id."))
    }
}

// PUT /api/citas/{id}/estatus: actualiza el estado de una cita.
fun updateAppointmentStatus(ctx: Context) {
    val appointmentId = ctx.pathParam("id").toIntOrNull()
    val request = ctx.bodyAsClass(ActualizarEstatusCitaRequest::class.java)
    val status = AppointmentStatus.from(request.estatus)

    if (appointmentId == null || appointmentId <= 0) {
        return badRequest(ctx, "El id de la cita debe ser numerico.")
    }

    if (status == null) {
        return badRequest(ctx, "Estatus invalido. Usa Confirmada, Asistio, No Asistio o Cancelada.")
    }

    try {
        AppointmentRepository.updateStatus(appointmentId, status)
        auditLog(ctx, "ESTATUS_CITA_ACTUALIZADO", "cita:$appointmentId", "estatus=${status.databaseValue}")
        ctx.json(
            mapOf(
                "id" to appointmentId,
                "estatus" to status.databaseValue,
                "mensaje" to "Estatus actualizado correctamente."
            )
        )
    } catch (_: AppointmentNotFoundException) {
        ctx.status(HttpStatus.NOT_FOUND).json(ApiError("No se encontro la cita solicitada."))
    }
}

// GET /api/pacientes/{id}/expediente: devuelve expediente completo.
fun getClinicalFile(ctx: Context) {
    val patientId = ctx.pathParam("id").toIntOrNull()

    if (patientId == null || patientId <= 0) {
        return badRequest(ctx, "El id del paciente debe ser numerico.")
    }

    val record = ClinicalRepository.getPatientRecord(patientId)

    if (record == null) {
        ctx.status(HttpStatus.NOT_FOUND).json(ApiError("No se encontro el paciente solicitado."))
        return
    }

    ctx.json(record)
}

// GET /api/pacientes/{id}/historial: citas historicas y notas de evolucion.
fun getPatientHistory(ctx: Context) {
    val patientId = ctx.pathParam("id").toIntOrNull()

    if (patientId == null || patientId <= 0) {
        return badRequest(ctx, "El id del paciente debe ser numerico.")
    }

    val history = PatientHistoryRepository.get(patientId)

    if (history == null) {
        ctx.status(HttpStatus.NOT_FOUND).json(ApiError("No se encontro el paciente solicitado."))
        return
    }

    ctx.json(history)
}

// PUT /api/pacientes/{id}/expediente: actualiza datos clinicos editables.
fun updateClinicalFile(ctx: Context) {
    val patientId = ctx.pathParam("id").toIntOrNull()
    val request = ctx.bodyAsClass(ActualizarExpedienteRequest::class.java)

    if (patientId == null || patientId <= 0) {
        return badRequest(ctx, "El id del paciente debe ser numerico.")
    }

    if (request.edad != null && request.edad !in 0..120) {
        return badRequest(ctx, "La edad debe estar entre 0 y 120.")
    }

    try {
        val clinicalFile = ClinicalRepository.updateClinicalFile(patientId, request)
        auditLog(ctx, "EXPEDIENTE_CLINICO_ACTUALIZADO", "paciente:$patientId")
        ctx.json(
            mapOf(
                "pacienteId" to patientId,
                "expediente" to clinicalFile,
                "mensaje" to "Expediente actualizado correctamente."
            )
        )
    } catch (_: PatientNotFoundException) {
        ctx.status(HttpStatus.NOT_FOUND).json(ApiError("No se encontro el paciente solicitado."))
    }
}

// PUT /api/odontograma/{odontogramaId}/pieza/{numeroPieza}: cambia estado dental.
fun updateOdontogramPiece(ctx: Context) {
    val odontogramId = ctx.pathParam("odontogramaId").toIntOrNull()
    val toothNumber = ctx.pathParam("numeroPieza").toIntOrNull()
    val request = ctx.bodyAsClass(ActualizarPiezaRequest::class.java)

    if (odontogramId == null || odontogramId <= 0 || toothNumber == null || toothNumber <= 0) {
        return badRequest(ctx, "odontogramaId y numeroPieza deben ser numericos.")
    }

    if (request.estado.isBlank()) {
        return badRequest(ctx, "El estado de la pieza es obligatorio.")
    }

    try {
        ClinicalRepository.updateToothPiece(odontogramId, toothNumber, request)
        auditLog(ctx, "ODONTOGRAMA_PIEZA_ACTUALIZADA", "odontograma:$odontogramId", "pieza=$toothNumber superficie=${normalizeSurface(request.superficie)} estado=${normalizeClinicalStatus(request.estado)}")
        ctx.json(
            mapOf(
                "odontogramaId" to odontogramId,
                "numeroPieza" to toothNumber,
                "superficie" to normalizeSurface(request.superficie),
                "estado" to normalizeClinicalStatus(request.estado),
                "mensaje" to "Pieza dental actualizada correctamente."
            )
        )
    } catch (_: OdontogramPieceNotFoundException) {
        ctx.status(HttpStatus.NOT_FOUND).json(ApiError("No se encontro la pieza del odontograma solicitada."))
    }
}

// POST /api/pacientes/{id}/notas: agrega nota de evolucion.
fun addClinicalNote(ctx: Context) {
    val patientId = ctx.pathParam("id").toIntOrNull()
    val request = ctx.bodyAsClass(CrearNotaRequest::class.java)

    if (patientId == null || patientId <= 0) {
        return badRequest(ctx, "El id del paciente debe ser numerico.")
    }

    try {
        normalizeRequiredLongText(request.textoNota, "Nota de evolucion", 3, 4000)
    } catch (error: IllegalArgumentException) {
        return badRequest(ctx, error.message ?: "Nota de evolucion invalida.")
    }

    try {
        val noteId = ClinicalRepository.addNote(patientId, request.textoNota)
        auditLog(ctx, "NOTA_EVOLUCION_AGREGADA", "nota:$noteId", "pacienteId=$patientId")
        ctx.status(HttpStatus.CREATED).json(
            mapOf(
                "id" to noteId,
                "pacienteId" to patientId,
                "mensaje" to "Nota registrada correctamente."
            )
        )
    } catch (_: PatientNotFoundException) {
        ctx.status(HttpStatus.NOT_FOUND).json(ApiError("No se encontro el paciente solicitado."))
    }
}

// POST /api/pacientes/{id}/imagenes: sube radiografias o imagenes clinicas al expediente.
fun uploadClinicalImage(ctx: Context) {
    val patientId = ctx.pathParam("id").toIntOrNull()
    val uploadedFile = ctx.uploadedFile("imagen")

    if (patientId == null || patientId <= 0) {
        return badRequest(ctx, "El id del paciente debe ser numerico.")
    }

    if (uploadedFile == null) {
        return badRequest(ctx, "Selecciona una imagen o archivo PDF.")
    }

    val contentType = uploadedFile.contentType() ?: "application/octet-stream"
    val maxBytes = AppConfig.getInt("MAX_CLINICAL_IMAGE_MB", 15) * 1024L * 1024L

    if (!isAllowedClinicalFile(contentType, uploadedFile.filename())) {
        return badRequest(ctx, "Formato no permitido. Usa JPG, PNG, WebP o PDF.")
    }

    if (uploadedFile.size() > maxBytes) {
        return badRequest(ctx, "El archivo supera el limite configurado.")
    }

    val uploadsRoot = AppConfig.get("CLINICAL_IMAGES_DIR", "uploads/clinical-images")
    val patientDirectory = File(uploadsRoot, "paciente-$patientId")
    patientDirectory.mkdirs()

    val extension = safeFileExtension(uploadedFile.filename(), contentType)
    val storedName = "imagen-${System.currentTimeMillis()}-${UUID.randomUUID()}$extension"
    val targetFile = File(patientDirectory, storedName)

    uploadedFile.content().use { input ->
        targetFile.outputStream().use { output -> input.copyTo(output) }
    }

    try {
        val image = ClinicalRepository.addClinicalImage(
            patientId = patientId,
            type = ctx.formParam("tipo") ?: "IMAGEN",
            description = ctx.formParam("descripcion"),
            originalName = uploadedFile.filename(),
            storedName = storedName,
            contentType = contentType,
            filePath = targetFile.path,
            publicUrl = ""
        )

        auditLog(ctx, "IMAGEN_CLINICA_SUBIDA", "paciente:$patientId", "archivo=${uploadedFile.filename()} tipo=$contentType")
        ctx.status(HttpStatus.CREATED).json(
            mapOf(
                "pacienteId" to patientId,
                "imagen" to image,
                "mensaje" to "Imagen clinica registrada correctamente."
            )
        )
    } catch (_: PatientNotFoundException) {
        targetFile.delete()
        ctx.status(HttpStatus.NOT_FOUND).json(ApiError("No se encontro el paciente solicitado."))
    }
}

// GET /api/pacientes/{id}/imagenes/{imagenId}/archivo: entrega archivo clinico protegido por sesion.
fun serveClinicalImage(ctx: Context) {
    val patientId = ctx.pathParam("id").toIntOrNull()
    val imageId = ctx.pathParam("imagenId").toIntOrNull()

    if (patientId == null || patientId <= 0 || imageId == null || imageId <= 0) {
        return badRequest(ctx, "El id del paciente y de la imagen deben ser numericos.")
    }

    val image = ClinicalRepository.findClinicalImageFile(patientId, imageId)

    if (image == null) {
        ctx.status(HttpStatus.NOT_FOUND).json(ApiError("No se encontro la imagen clinica solicitada."))
        return
    }

    val uploadsRoot = File(AppConfig.get("CLINICAL_IMAGES_DIR", "uploads/clinical-images")).canonicalFile
    val imageFile = File(image["rutaArchivo"] as String).canonicalFile

    if (!imageFile.path.startsWith(uploadsRoot.path) || !imageFile.exists() || !imageFile.isFile) {
        ctx.status(HttpStatus.NOT_FOUND).json(ApiError("No se encontro el archivo clinico solicitado."))
        return
    }

    val contentType = image["contentType"] as? String ?: "application/octet-stream"
    val originalName = (image["nombreOriginal"] as? String)
        ?.replace(Regex("[\\r\\n\"]+"), "_")
        ?.ifBlank { "archivo-clinico" }
        ?: "archivo-clinico"

    ctx.contentType(contentType)
    ctx.header("Cache-Control", "private, no-store")
    ctx.header("Content-Disposition", "inline; filename=\"$originalName\"")
    ctx.result(imageFile.inputStream())
}

// Respuesta comun para validaciones de entrada.
fun badRequest(ctx: Context, message: String) {
    ctx.status(HttpStatus.BAD_REQUEST).json(ApiError(message))
}

fun internalServerError(ctx: Context) {
    ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(
        ApiError("No fue posible procesar la solicitud. Intenta de nuevo o contacta a administracion.")
    )
}

fun logServerError(ctx: Context, error: Exception) {
    val principal = currentAuth(ctx)
    val actor = if (principal == null) "ANONIMO" else "${principal.tipo}#${principal.id} rol=${principal.rol}"
    val message = error.message ?: error::class.simpleName.orEmpty()

    println("[ERROR] fecha=${LocalDateTime.now()} actor=${auditSafe(actor)} ruta=${auditSafe(ctx.req().method)} ${auditSafe(ctx.path())} tipo=${error::class.simpleName} mensaje=${auditSafe(message)}")
    error.printStackTrace()
}

// Endpoint rapido para comprobar conexion con MariaDB.
fun checkDatabaseHealth(ctx: Context) {
    Database.connection().use { connection ->
        connection.prepareStatement("SELECT 1").use { statement ->
            statement.executeQuery().use { result ->
                result.next()
                ctx.json(
                    mapOf(
                        "database" to "ok",
                        "result" to result.getInt(1)
                    )
                )
            }
        }
    }
}

// Inicializador local para crear tablas cuando no se tiene cliente mysql instalado.
@Suppress("SqlSourceToSinkFlow")
fun initializeDatabaseSchema(ctx: Context) {
    val enabled = AppConfig.get("INIT_DB_ENABLED", "false").equals("true", ignoreCase = true)
    val expectedToken = AppConfig.get("INIT_DB_TOKEN", "")
    val receivedToken = ctx.header("X-Init-Token").orEmpty()

    if (!enabled || expectedToken.isBlank() || receivedToken != expectedToken) {
        ctx.status(HttpStatus.FORBIDDEN).json(ApiError("Inicializacion de base de datos no permitida."))
        return
    }

    val schemaFile = File("database/schema.sql")

    if (!schemaFile.exists()) {
        ctx.status(HttpStatus.NOT_FOUND).json(ApiError("No se encontro database/schema.sql."))
        return
    }

    val executedStatements = mutableListOf<String>()
    val statements = splitSqlStatements(schemaFile.readText())
    val databaseName = Database.configuredDatabaseName()

    if (databaseName.isBlank()) {
        return badRequest(ctx, "DB_URL debe incluir el nombre de la base de datos.")
    }

    val safeDatabaseName = safeSqlIdentifier(databaseName)

    Database.serverConnection().use { connection ->
        connection.createStatement().use { sqlStatement ->
            sqlStatement.execute(
                """
                CREATE DATABASE IF NOT EXISTS `$safeDatabaseName`
                  CHARACTER SET utf8mb4
                  COLLATE utf8mb4_unicode_ci
                """.trimIndent()
            )
            executedStatements.add("CREATE DATABASE IF NOT EXISTS $safeDatabaseName")
        }
    }

    Database.connection().use { connection ->
        statements.forEach { statement ->
            // La conexion ya apunta a la base configurada; estas sentencias no son necesarias aqui.
            if (statement.startsWith("CREATE DATABASE", ignoreCase = true) || statement.startsWith("USE ", ignoreCase = true)) {
                return@forEach
            }

            connection.createStatement().use { sqlStatement ->
                sqlStatement.execute(statement)
                executedStatements.add(statement.lineSequence().first().take(80))
            }
        }
    }

    ctx.json(
        mapOf(
            "database" to "initialized",
            "databaseName" to databaseName,
            "statementsExecuted" to executedStatements.size
        )
    )
    auditLog(ctx, "BASE_DATOS_INICIALIZADA", "database:$safeDatabaseName", "sentencias=${executedStatements.size}")
}

fun safeSqlIdentifier(value: String): String {
    val normalized = value.trim()

    require(Regex("[A-Za-z0-9_]+").matches(normalized)) {
        "El nombre de la base de datos solo puede usar letras, numeros y guion bajo."
    }

    return normalized
}

// Divide el schema.sql en sentencias independientes.
fun splitSqlStatements(sql: String): List<String> {
    return sql.split(";")
        .map { it.trim() }
        .filter { it.isNotBlank() }
}

fun isAllowedClinicalFile(contentType: String, filename: String): Boolean {
    val normalizedType = contentType.lowercase()
    val extension = filename.substringAfterLast('.', "").lowercase()
    val allowedTypes = setOf("image/jpeg", "image/png", "image/webp", "application/pdf")
    val allowedExtensions = setOf("jpg", "jpeg", "png", "webp", "pdf")

    return normalizedType in allowedTypes || extension in allowedExtensions
}

fun safeFileExtension(filename: String, contentType: String): String {
    val extension = filename.substringAfterLast('.', "").lowercase()

    return when {
        extension in setOf("jpg", "jpeg", "png", "webp", "pdf") -> ".$extension"
        contentType.equals("image/jpeg", ignoreCase = true) -> ".jpg"
        contentType.equals("image/png", ignoreCase = true) -> ".png"
        contentType.equals("image/webp", ignoreCase = true) -> ".webp"
        contentType.equals("application/pdf", ignoreCase = true) -> ".pdf"
        else -> ".bin"
    }
}

// Helpers de formato y normalizacion usados por controladores y repositorios.
fun parseDate(value: String): LocalDate {
    return try {
        LocalDate.parse(value)
    } catch (_: Exception) {
        throw IllegalArgumentException("La fecha debe tener formato yyyy-MM-dd.")
    }
}

fun parseTime(value: String): LocalTime {
    val normalized = value.trim().uppercase(Locale.US)
    val acceptedFormats = listOf(
        DateTimeFormatter.ofPattern("HH:mm"),
        DateTimeFormatter.ofPattern("hh:mm a", Locale.US)
    )

    acceptedFormats.forEach { formatter ->
        try {
            return LocalTime.parse(normalized, formatter)
        } catch (_: Exception) {
            // Intenta con el siguiente formato aceptado.
        }
    }

    throw IllegalArgumentException("La hora debe tener formato HH:mm o hh:mm AM/PM.")
}

fun parseDateTime(value: String): LocalDateTime {
    val normalized = value.trim()
    val acceptedFormats = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    )

    acceptedFormats.forEach { formatter ->
        try {
            return LocalDateTime.parse(normalized, formatter)
        } catch (_: Exception) {
            // Intenta con el siguiente formato aceptado.
        }
    }

    throw IllegalArgumentException("fechaHora debe tener formato yyyy-MM-dd'T'HH:mm o yyyy-MM-dd HH:mm.")
}

fun formatDisplayTime(time: LocalTime): String {
    return time.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.US))
}

fun normalizeText(value: String): String {
    return Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
        .uppercase(Locale.US)
}

fun normalizeSurface(value: String?): String {
    if (value.isNullOrBlank()) return "GENERAL"
    return normalizeText(value).replace(" ", "_").replace("-", "_")
}

fun normalizeClinicalStatus(value: String): String {
    return normalizeText(value).replace(" ", "_").replace("-", "_")
}

object PasswordService {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 16
    private const val PREFIX = "pbkdf2"
    private const val SEPARATOR = '$'
    private const val HASH_PREFIX = "$PREFIX$SEPARATOR"
    private val random = SecureRandom()

    fun hash(value: String): String {
        val salt = ByteArray(SALT_LENGTH)
        random.nextBytes(salt)
        val hash = pbkdf2(value, salt, ITERATIONS)

        return listOf(
            PREFIX,
            ITERATIONS.toString(),
            Base64.getUrlEncoder().withoutPadding().encodeToString(salt),
            Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
        ).joinToString(SEPARATOR.toString())
    }

    fun verify(value: String, storedHash: String?): Boolean {
        if (storedHash.isNullOrBlank()) return false

        if (!storedHash.startsWith(HASH_PREFIX)) {
            return legacySha256(value) == storedHash
        }

        val parts = storedHash.split(SEPARATOR)
        if (parts.size != 4) return false

        return try {
            val iterations = parts[1].toInt()
            val salt = Base64.getUrlDecoder().decode(parts[2])
            val expected = Base64.getUrlDecoder().decode(parts[3])
            val actual = pbkdf2(value, salt, iterations)

            MessageDigest.isEqual(expected, actual)
        } catch (_: Exception) {
            false
        }
    }

    fun needsUpgrade(storedHash: String?): Boolean {
        return storedHash.isNullOrBlank() || !storedHash.startsWith(HASH_PREFIX)
    }

    private fun pbkdf2(value: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(value.toCharArray(), salt, iterations, KEY_LENGTH)
        return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
    }

    private fun legacySha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

fun hashPassword(value: String): String = PasswordService.hash(value)
