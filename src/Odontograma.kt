import java.time.LocalDate
data class Odontograma(
    val id: Int? = null,
    val pacienteId: Int,
    val color: String?, // Campo COLOR____ que aparece en medio del odontograma, Tengo que preguntar el uso
    val fechaActualizacion: LocalDate
)

data class PiezaDental(
    val id: Int? = null,
    val odontogramaId: Int, // Llave foránea hacia Odontograma
    val cuadrante: Int, // Ej. 1 (Superior Derecho), 2, 3, 4
    val numeroPieza: Int, // Del 1 al 8
    val estado: String, // Ej. "Sano", "Caries", "Ausente"
    val carasAfectadas: String?
)
