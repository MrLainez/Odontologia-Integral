import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.HttpStatus
import io.javalin.http.staticfiles.Location
import java.io.File
import java.security.MessageDigest
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

// Modelos simples que representan los JSON recibidos por la API.
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

data class CrearCitaRequest(
    val pacienteId: Int = 0,
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
    val texto_nota: String = ""
)

data class ApiError(val error: String)

// Lee valores desde variables de entorno o desde config.properties.
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

// Punto unico para abrir conexiones JDBC hacia MariaDB.
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
        val appointmentDateTime = parseDateTime(request.fechaHora)

        return scheduleInternal(
            pacienteId = request.pacienteId,
            odontologoId = defaultOdontologistId,
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
                // Regla critica: no permitir dos citas activas en la misma fecha y hora.
                if (isSlotTaken(connection, date, time)) {
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
                "odontograma" to findOdontogram(connection, patientId)
            )
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
            SELECT id, alergias, antecedentes, fecha_creacion
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
                        "alergias" to null,
                        "antecedentes" to null,
                        "fechaCreacion" to null
                    )
                }

                return mapOf(
                    "id" to result.getInt("id"),
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

// Excepciones propias para convertir reglas de negocio en respuestas HTTP claras.
class AppointmentConflictException : RuntimeException()
class AppointmentNotFoundException : RuntimeException()
class OdontogramPieceNotFoundException : RuntimeException()
class PatientNotFoundException : RuntimeException()

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

@Suppress("UNUSED_PARAMETER")
fun main(args: Array<String>) {
    val port = AppConfig.getInt("SERVER_PORT", 8080)
    val publicDirectory = AppConfig.get("STATIC_FILES_DIR", "public")

    val app = Javalin.create { config ->
        // Sirve los archivos HTML/CSS/JS desde la carpeta public.
        config.staticFiles.add { staticFiles ->
            staticFiles.hostedPath = "/"
            staticFiles.directory = publicDirectory
            staticFiles.location = Location.EXTERNAL
        }
    }

    // Rutas del portal publico y recepcion.
    app.post("/api/pacientes/registro") { ctx -> registerPatient(ctx) }
    app.post("/api/pacientes") { ctx -> registerPatient(ctx) }
    app.post("/api/login") { ctx -> loginPatient(ctx) }
    app.post("/api/citas") { ctx -> createAppointment(ctx) }
    app.post("/api/citas/agendar") { ctx -> scheduleAppointment(ctx) }
    app.put("/api/citas/{id}/estatus") { ctx -> updateAppointmentStatus(ctx) }

    // Rutas del expediente clinico y odontograma.
    app.get("/api/pacientes/{id}/expediente") { ctx -> getClinicalFile(ctx) }
    app.put("/api/odontograma/{odontogramaId}/pieza/{numeroPieza}") { ctx -> updateOdontogramPiece(ctx) }
    app.post("/api/pacientes/{id}/notas") { ctx -> addClinicalNote(ctx) }

    // Rutas de apoyo para pruebas locales.
    app.get("/api/health/db") { ctx -> checkDatabaseHealth(ctx) }
    app.post("/api/admin/init-db") { ctx -> initializeDatabaseSchema(ctx) }

    // Devuelve errores de base de datos en JSON para facilitar depuracion.
    app.exception(SQLException::class.java) { error, ctx ->
        error.printStackTrace()
        ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(
            ApiError("Error de base de datos: ${error.message ?: "revisa la conexion y las tablas."}")
        )
    }

    app.start(port)
    println("Servidor iniciado en http://localhost:$port")
}

// POST /api/pacientes: registra un paciente/usuario.
fun registerPatient(ctx: Context) {
    val request = ctx.bodyAsClass(RegistroPacienteRequest::class.java)

    if (request.nombre.isBlank() || request.telefono.isBlank() || request.email.isBlank() || request.password.isBlank()) {
        return badRequest(ctx, "Nombre, telefono, email y password son obligatorios.")
    }

    try {
        val patientId = PatientRepository.create(request)
        ctx.status(HttpStatus.CREATED).json(
            mapOf(
                "id" to patientId,
                "mensaje" to "Paciente registrado correctamente."
            )
        )
    } catch (error: SQLIntegrityConstraintViolationException) {
        ctx.status(HttpStatus.CONFLICT).json(ApiError("El email ya esta registrado. Usa otro correo para la prueba."))
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
            "paciente" to patient
        )
    )
}

// POST /api/citas/agendar: endpoint inicial usado por el portal publico.
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

// POST /api/citas: endpoint principal para crear citas desde frontend.
fun createAppointment(ctx: Context) {
    val request = ctx.bodyAsClass(CrearCitaRequest::class.java)

    if (request.pacienteId <= 0 || request.fechaHora.isBlank() || request.motivo.isBlank()) {
        return badRequest(ctx, "Paciente, fechaHora y motivo son obligatorios.")
    }

    try {
        val appointmentId = AppointmentRepository.create(request)
        ctx.status(HttpStatus.CREATED).json(
            mapOf(
                "id" to appointmentId,
                "mensaje" to "Cita registrada correctamente."
            )
        )
    } catch (error: AppointmentConflictException) {
        ctx.status(HttpStatus.CONFLICT).json(ApiError("La fecha y hora seleccionadas ya estan ocupadas."))
    } catch (error: IllegalArgumentException) {
        badRequest(ctx, error.message ?: "Datos de cita invalidos.")
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
        ctx.json(
            mapOf(
                "id" to appointmentId,
                "estatus" to status.databaseValue,
                "mensaje" to "Estatus actualizado correctamente."
            )
        )
    } catch (error: AppointmentNotFoundException) {
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
        ctx.json(
            mapOf(
                "odontogramaId" to odontogramId,
                "numeroPieza" to toothNumber,
                "superficie" to normalizeSurface(request.superficie),
                "estado" to normalizeClinicalStatus(request.estado),
                "mensaje" to "Pieza dental actualizada correctamente."
            )
        )
    } catch (error: OdontogramPieceNotFoundException) {
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

    if (request.texto_nota.isBlank()) {
        return badRequest(ctx, "texto_nota es obligatorio.")
    }

    try {
        val noteId = ClinicalRepository.addNote(patientId, request.texto_nota)
        ctx.status(HttpStatus.CREATED).json(
            mapOf(
                "id" to noteId,
                "pacienteId" to patientId,
                "mensaje" to "Nota registrada correctamente."
            )
        )
    } catch (error: PatientNotFoundException) {
        ctx.status(HttpStatus.NOT_FOUND).json(ApiError("No se encontro el paciente solicitado."))
    }
}

// Respuesta comun para validaciones de entrada.
fun badRequest(ctx: Context, message: String) {
    ctx.status(HttpStatus.BAD_REQUEST).json(ApiError(message))
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
fun initializeDatabaseSchema(ctx: Context) {
    val schemaFile = File("database/schema.sql")

    if (!schemaFile.exists()) {
        ctx.status(HttpStatus.NOT_FOUND).json(ApiError("No se encontro database/schema.sql."))
        return
    }

    val executedStatements = mutableListOf<String>()
    val statements = splitSqlStatements(schemaFile.readText())

    Database.connection().use { connection ->
        statements.forEach { statement ->
            // La conexion ya apunta a odonto_gral; estas sentencias no son necesarias aqui.
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
            "statementsExecuted" to executedStatements.size
        )
    )
}

// Divide el schema.sql en sentencias independientes.
fun splitSqlStatements(sql: String): List<String> {
    return sql.split(";")
        .map { it.trim() }
        .filter { it.isNotBlank() }
}

// Helpers de formato y normalizacion usados por controladores y repositorios.
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

fun hashPassword(value: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}
