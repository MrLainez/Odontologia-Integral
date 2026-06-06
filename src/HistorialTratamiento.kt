import java.time.LocalDate
data class HistorialTratamiento(
    val id: Int? = null,
    val pacienteId: Int,
    val fecha: LocalDate,
    val trabajoRealizado: String, // Aquí el doctor escribe "Profilaxis", "Endodoncia", etc.

    // Campos financieros
    // Anulables, pero se guardan para mantener registro
    val total: Double? = null,
    val aCuenta: Double? = null,
    val saldo: Double? = null
)