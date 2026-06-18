import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.HttpStatus
import io.javalin.http.staticfiles.Location
import java.security.MessageDigest
import java.sql.Connection
import java.sql.Date
import java.sql.DriverManager
import java.sql.Time
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.Properties

data class RegistroPacienteRequest(
    val nombre: String = "",
    val telefono: String = "",
    val email: String = "",
    val password: String = ""
)

data class LoginRequest(
    val email: String = "",
    val password: String = ""
)

data class AgendarCitaRequest(
    val pacienteId: Int = 0,
    val odontologoId: Int = 0,
    val fecha: String = "",
    val hora: String = "",
    val tratamiento: String = ""
)

data class ApiError(val error: String)

object AppConfig {
    private const val CONFIG_FILE = "config.properties"

    private val properties = Properties().apply {
        val configFile = java.io.File(CONFIG_FILE)

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
}

object Database {
    private val jdbcUrl = AppConfig.get("DB_URL", "jdbc:mariadb://localhost:3306/odonto_gral")
    private val user = AppConfig.get("DB_USER", "root")
    private val password = AppConfig.get("DB_PASSWORD", "")
    private val driver = AppConfig.get("DB_DRIVER", "org.mariadb.jdbc.Driver")

    init {
        Class.forName(driver)
    }

    fun connection(): Connection = DriverManager.getConnection(jdbcUrl, user, password)
}

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
            SELECT id, nombre, email
            FROM pacientes
            WHERE email = ? AND password_hash = ?
            LIMIT 1
        """.trimIndent()

        Database.connection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, email.trim().lowercase())
                statement.setString(2, hashPassword(password))

                statement.executeQuery().use { result ->
                    if (!result.next()) return null

                    return mapOf(
                        "id" to result.getInt("id"),
                        "nombre" to result.getString("nombre"),
                        "email" to result.getString("email")
                    )
                }
            }
        }
    }
}

object AppointmentRepository {
    fun schedule(request: AgendarCitaRequest): Int {
        val appointmentDate = parseDate(request.fecha)
        val appointmentTime = parseTime(request.hora)

        Database.connection().use { connection ->
            connection.autoCommit = false

            try {
                if (isSlotTaken(connection, appointmentDate, appointmentTime)) {
                    connection.rollback()
                    throw AppointmentConflictException()
                }

                val id = insertAppointment(connection, request, appointmentDate, appointmentTime)
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

    private fun isSlotTaken(connection: Connection, date: LocalDate, time: LocalTime): Boolean {
        val sql = """
            SELECT COUNT(*) AS total
            FROM citas
            WHERE fecha = ? AND hora = ? AND estatus <> 'CANCELADA'
        """.trimIndent()

        connection.prepareStatement(sql).use { statement ->
            statement.setDate(1, Date.valueOf(date))
            statement.setTime(2, Time.valueOf(time))

            statement.executeQuery().use { result ->
                result.next()
                return result.getInt("total") > 0
            }
        }
    }

    private fun insertAppointment(
        connection: Connection,
        request: AgendarCitaRequest,
        date: LocalDate,
        time: LocalTime
    ): Int {
        val sql = """
            INSERT INTO citas (paciente_id, odontologo_id, fecha, hora, tratamiento, estatus)
            VALUES (?, ?, ?, ?, ?, 'CONFIRMADA')
        """.trimIndent()

        connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { statement ->
            statement.setInt(1, request.pacienteId)
            statement.setInt(2, request.odontologoId)
            statement.setDate(3, Date.valueOf(date))
            statement.setTime(4, Time.valueOf(time))
            statement.setString(5, request.tratamiento.trim())
            statement.executeUpdate()

            statement.generatedKeys.use { keys ->
                if (keys.next()) return keys.getInt(1)
            }
        }

        error("No fue posible obtener el id de la cita registrada.")
    }
}

class AppointmentConflictException : RuntimeException()

@Suppress("UNUSED_PARAMETER")
fun main(args: Array<String>) {
    val port = AppConfig.getInt("SERVER_PORT", 8080)
    val publicDirectory = AppConfig.get("STATIC_FILES_DIR", "public")

    val app = Javalin.create { config ->
        config.staticFiles.add { staticFiles ->
            staticFiles.hostedPath = "/"
            staticFiles.directory = publicDirectory
            staticFiles.location = Location.EXTERNAL
        }

        config.routes.post("/api/pacientes/registro") { ctx -> registerPatient(ctx) }
        config.routes.post("/api/login") { ctx -> loginPatient(ctx) }
        config.routes.post("/api/citas/agendar") { ctx -> scheduleAppointment(ctx) }
    }

    app.start(port)
    println("Servidor iniciado en http://localhost:$port")
}

fun registerPatient(ctx: Context) {
    val request = ctx.bodyAsClass(RegistroPacienteRequest::class.java)

    if (request.nombre.isBlank() || request.telefono.isBlank() || request.email.isBlank() || request.password.isBlank()) {
        return badRequest(ctx, "Nombre, telefono, email y password son obligatorios.")
    }

    val patientId = PatientRepository.create(request)
    ctx.status(HttpStatus.CREATED).json(
        mapOf(
            "id" to patientId,
            "mensaje" to "Paciente registrado correctamente."
        )
    )
}

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
            "paciente" to patient
        )
    )
}

fun scheduleAppointment(ctx: Context) {
    val request = ctx.bodyAsClass(AgendarCitaRequest::class.java)

    if (request.pacienteId <= 0 || request.odontologoId <= 0 || request.fecha.isBlank() || request.hora.isBlank()) {
        return badRequest(ctx, "Paciente, odontologo, fecha y hora son obligatorios.")
    }

    try {
        val appointmentId = AppointmentRepository.schedule(request)
        ctx.status(HttpStatus.CREATED).json(
            mapOf(
                "id" to appointmentId,
                "mensaje" to "Cita agendada correctamente."
            )
        )
    } catch (error: AppointmentConflictException) {
        ctx.status(HttpStatus.CONFLICT).json(ApiError("La fecha y hora seleccionadas ya estan ocupadas."))
    } catch (error: IllegalArgumentException) {
        badRequest(ctx, error.message ?: "Datos de cita invalidos.")
    }
}

fun badRequest(ctx: Context, message: String) {
    ctx.status(HttpStatus.BAD_REQUEST).json(ApiError(message))
}

fun parseDate(value: String): LocalDate {
    return try {
        LocalDate.parse(value)
    } catch (error: Exception) {
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

fun hashPassword(value: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}
