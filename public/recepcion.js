const agendaTable = document.querySelector(".appointments-table");
const manualModal = document.querySelector("#manual-appointment-modal");
const openManualButton = document.querySelector("#open-manual-appointment");
const manualForm = document.querySelector("#manual-appointment-form");
const manualStatus = document.querySelector("#manual-status");
const userModal = document.querySelector("#user-modal");
const openUserButton = document.querySelector("#open-user-modal");
const userForm = document.querySelector("#user-form");
const userStatus = document.querySelector("#user-status");
const usersList = document.querySelector("#users-list");

// Endpoints usados por el panel de recepcion.
const API_BASE_URL = "http://localhost:8080";
const API_CITAS_URL = `${API_BASE_URL}/api/citas`;
const API_CITAS_HOY_URL = `${API_BASE_URL}/api/citas/hoy`;
const API_PACIENTES_URL = `${API_BASE_URL}/api/pacientes`;

// Evita registrar citas manuales en fechas pasadas.
const today = new Date();
const manualDateInput = document.querySelector("#inputFechaHora");

if (manualDateInput) {
  manualDateInput.min = today.toISOString().slice(0, 16);
}

// Configuracion visual y valor enviado a la API para cada estatus.
const statusConfig = {
  confirmed: {
    label: "Confirmada",
    apiValue: "Confirmada",
    className: "status-confirmed"
  },
  attended: {
    label: "Asistio",
    apiValue: "Asistio",
    className: "status-attended"
  },
  missed: {
    label: "No Asistio",
    apiValue: "No Asistio",
    className: "status-missed"
  },
  cancelled: {
    label: "Cancelada",
    apiValue: "Cancelada",
    className: "status-cancelled"
  }
};

// Evita que texto de la base rompa el HTML al inyectarlo en la tabla.
function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function getAgendaRows() {
  return agendaTable.querySelectorAll(".appointment-row");
}

// Limpia las filas dinamicas antes de volver a pintar la agenda.
function clearAgendaRows() {
  getAgendaRows().forEach((row) => row.remove());
  agendaTable.querySelector("[data-agenda-message]")?.remove();
}

// Muestra mensajes dentro de la tabla, por ejemplo carga o error.
function showAgendaMessage(message, isError = false) {
  agendaTable.querySelector("[data-agenda-message]")?.remove();

  const messageRow = document.createElement("article");
  messageRow.className = `appointment-row ${isError ? "status-cancelled" : "status-confirmed"}`;
  messageRow.dataset.agendaMessage = "true";
  messageRow.innerHTML = `<span role="cell">${escapeHtml(message)}</span>`;
  agendaTable.append(messageRow);
}

// Convierte estatus de la API al nombre interno usado por CSS.
function normalizeStatus(status) {
  const normalized = String(status || "confirmed")
    .trim()
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replaceAll("_", " ")
    .replaceAll("-", " ");

  if (normalized.includes("asistio") && !normalized.includes("no")) return "attended";
  if (normalized.includes("no asistio") || normalized.includes("noasistio") || normalized.includes("missed")) return "missed";
  if (normalized.includes("cancel")) return "cancelled";
  return "confirmed";
}

// Obtiene hora desde distintos formatos posibles de respuesta.
function formatAppointmentTime(appointment) {
  const rawTime = appointment.hora || appointment.time || appointment.appointmentTime;
  const rawDateTime = appointment.fechaHora || appointment.fecha_hora || appointment.dateTime;

  if (rawTime) {
    return String(rawTime).slice(0, 5);
  }

  if (rawDateTime) {
    return new Date(rawDateTime).toLocaleTimeString("es-MX", {
      hour: "2-digit",
      minute: "2-digit"
    });
  }

  return "--:--";
}

// Deja una cita en una estructura uniforme para renderizarla.
function normalizeAppointment(appointment) {
  return {
    id: appointment.id,
    time: formatAppointmentTime(appointment),
    patient: appointment.pacienteNombre
      || appointment.nombrePaciente
      || appointment.paciente?.nombre
      || appointment.paciente
      || `Paciente #${appointment.pacienteId || appointment.paciente_id || "-"}`,
    treatment: appointment.tratamiento || appointment.motivo || "Consulta",
    status: normalizeStatus(appointment.estatus || appointment.status)
  };
}

function createStatusButtons() {
  return Object.entries(statusConfig).map(([status, config]) => (
    `<button type="button" data-status="${status}">${config.label}</button>`
  )).join("");
}

// Crea una fila visual de agenda a partir de una cita de la API.
function createAppointmentRow(appointment) {
  const normalized = normalizeAppointment(appointment);
  const config = statusConfig[normalized.status];
  const row = document.createElement("article");

  row.className = `appointment-row ${config.className}`;
  row.setAttribute("role", "row");
  row.dataset.status = normalized.status;
  row.dataset.appointmentId = normalized.id || "";

  row.innerHTML = `
    <span class="appointment-row__time" role="cell">${escapeHtml(normalized.time)}</span>
    <span role="cell">${escapeHtml(normalized.patient)}</span>
    <span role="cell">${escapeHtml(normalized.treatment)}</span>
    <span role="cell"><span class="reception-badge" data-status-badge>${config.label}</span></span>
    <div class="status-actions" role="cell">
      ${createStatusButtons()}
    </div>
  `;

  return row;
}

// Actualiza el color y badge de una fila despues de guardar el cambio.
function updateAppointmentStatus(row, status) {
  const badge = row.querySelector("[data-status-badge]");
  const config = statusConfig[status];

  row.classList.remove("status-confirmed", "status-attended", "status-missed", "status-cancelled");
  row.classList.add(config.className);
  row.dataset.status = status;
  badge.textContent = config.label;
}

// Helper comun para fetch con JSON y errores legibles.
async function fetchJson(url, options = {}) {
  const response = await fetch(url, options);
  const data = await response.json().catch(() => ({}));

  if (!response.ok) {
    const message = data.error || data.mensaje || "No fue posible completar la solicitud.";
    const error = new Error(message);
    error.status = response.status;
    throw error;
  }

  return data;
}

// Carga las citas del dia desde el backend y las pinta en la tabla.
async function loadTodayAgenda() {
  clearAgendaRows();
  showAgendaMessage("Cargando agenda...");

  try {
    const data = await fetchJson(API_CITAS_HOY_URL);
    const appointments = Array.isArray(data) ? data : data.citas || data.appointments || [];

    clearAgendaRows();

    if (appointments.length === 0) {
      showAgendaMessage("No hay citas registradas para hoy.");
      return;
    }

    appointments.forEach((appointment) => {
      agendaTable.append(createAppointmentRow(appointment));
    });
  } catch (error) {
    clearAgendaRows();
    showAgendaMessage("No se pudo cargar la agenda. Verifica que el servidor este encendido.", true);
  }
}

// Guarda el nuevo estatus de una cita en la API.
async function saveAppointmentStatus(appointmentId, status) {
  return fetchJson(`${API_CITAS_URL}/${appointmentId}/estatus`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      estatus: statusConfig[status].apiValue
    })
  });
}

// Abre/cierra modal de cita presencial.
function openManualModal() {
  manualModal.classList.remove("is-hidden");
  document.body.classList.add("has-dialog");
  document.querySelector("#manual-name")?.focus();
}

function closeManualModal() {
  manualModal.classList.add("is-hidden");
  document.body.classList.remove("has-dialog");
  manualForm.reset();
  manualStatus.textContent = "";
}

// Abre/cierra modal de creacion de usuarios pacientes.
function openUserModal() {
  userModal.classList.remove("is-hidden");
  document.body.classList.add("has-dialog");
  document.querySelector("#user-name")?.focus();
}

function closeUserModal() {
  userModal.classList.add("is-hidden");
  document.body.classList.remove("has-dialog");
  userForm.reset();
  userStatus.textContent = "";
  userStatus.classList.remove("is-error");
}

// Muestra errores debajo de cada campo del formulario.
function showFieldError(field, message) {
  const error = document.querySelector(`[data-error-for="${field.id}"]`);
  const row = field.closest(".form-row");

  if (row) row.classList.toggle("has-error", Boolean(message));
  if (error) error.textContent = message || "";
}

// Validacion sencilla para el formulario de cita presencial.
function validateManualField(field) {
  const value = field.value.trim();
  let message = "";

  if (!value) {
    message = "Campo requerido.";
  }

  if (field.name === "phone" && value && !/^\d{10}$/.test(value.replace(/\D/g, ""))) {
    message = "Escribe un telefono de 10 digitos.";
  }

  if (field.name === "patientId" && Number(value) <= 0) {
    message = "Escribe un ID de paciente valido.";
  }

  showFieldError(field, message);
  return message === "";
}

// Validacion sencilla para crear usuario de paciente.
function validateUserField(field) {
  const value = field.value.trim();
  let message = "";

  if (!value) {
    message = "Campo requerido.";
  }

  if (field.name === "telefono" && value && !/^\d{10}$/.test(value.replace(/\D/g, ""))) {
    message = "Escribe un telefono de 10 digitos.";
  }

  if (field.name === "email" && value && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
    message = "Escribe un correo valido.";
  }

  if (field.name === "password" && value && value.length < 6) {
    message = "Usa al menos 6 caracteres.";
  }

  showFieldError(field, message);
  return message === "";
}

// JSON que espera POST /api/citas.
function buildManualAppointmentPayload(form) {
  const data = new FormData(form);

  return {
    pacienteId: Number(data.get("patientId")),
    fechaHora: data.get("dateTime"),
    motivo: data.get("reason").trim()
  };
}

// Registra una cita manual y la agrega visualmente si la API responde OK.
async function guardarCita() {
  const cita = buildManualAppointmentPayload(manualForm);

  try {
    const data = await fetchJson(API_CITAS_URL, {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify(cita)
    });

    alert("Cita guardada correctamente.");
    manualForm.reset();
    manualStatus.classList.remove("is-error");
    manualStatus.textContent = "Cita guardada correctamente.";
    agendaTable.append(createAppointmentRow({ ...cita, id: data.id, estatus: "Confirmada" }));
    return true;
  } catch (error) {
    const message = error.status === 409
      ? "El horario seleccionado ya esta ocupado."
      : error.message || "No se pudo conectar con el servidor local.";

    alert(message);
    manualStatus.textContent = message;
    manualStatus.classList.add("is-error");
    return false;
  }
}

// JSON que espera POST /api/pacientes.
function buildUserPayload(form) {
  const data = new FormData(form);

  return {
    nombre: data.get("nombre").trim(),
    telefono: data.get("telefono").trim(),
    email: data.get("email").trim(),
    password: data.get("password")
  };
}

// Agrega el usuario creado a la lista del dashboard.
function addUserRow(user) {
  const row = document.createElement("article");
  row.className = "user-row";
  row.innerHTML = `
    <div>
      <strong>${escapeHtml(user.nombre)}</strong>
      <span>${escapeHtml(user.email)}</span>
    </div>
    <span class="status-pill">Activo</span>
  `;

  usersList.prepend(row);
}

// Crea el usuario/paciente desde recepcion.
async function crearUsuarioPaciente(payload) {
  return fetchJson(API_PACIENTES_URL, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(payload)
  });
}

// Delegacion de clicks: solo los botones de estatus disparan el PUT.
agendaTable.addEventListener("click", async (event) => {
  const statusButton = event.target.closest("button[data-status]");
  if (!statusButton) return;

  const row = statusButton.closest(".appointment-row");
  const appointmentId = row?.dataset.appointmentId;
  const newStatus = statusButton.dataset.status;

  if (!row || !appointmentId) {
    alert("No se encontro el id de la cita para actualizar.");
    return;
  }

  statusButton.disabled = true;

  try {
    await saveAppointmentStatus(appointmentId, newStatus);
    updateAppointmentStatus(row, newStatus);
  } catch (error) {
    alert(error.message || "No se pudo actualizar el estatus. Verifica que el servidor este encendido.");
  } finally {
    statusButton.disabled = false;
  }
});

openManualButton.addEventListener("click", openManualModal);
openUserButton.addEventListener("click", openUserModal);

// Cierre de modales desde backdrop o boton cerrar.
manualModal.addEventListener("click", (event) => {
  if (event.target.matches("[data-close-manual-modal]")) {
    closeManualModal();
  }
});

userModal.addEventListener("click", (event) => {
  if (event.target.matches("[data-close-user-modal]")) {
    closeUserModal();
  }
});

// Escape cierra cualquier modal abierto.
document.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && !manualModal.classList.contains("is-hidden")) {
    closeManualModal();
  }

  if (event.key === "Escape" && !userModal.classList.contains("is-hidden")) {
    closeUserModal();
  }
});

// Validacion en vivo de formularios.
manualForm.addEventListener("input", (event) => {
  if (event.target.matches("input")) {
    validateManualField(event.target);
  }
});

userForm.addEventListener("input", (event) => {
  if (event.target.matches("input")) {
    validateUserField(event.target);
  }
});

// Envio de cita presencial.
manualForm.addEventListener("submit", async (event) => {
  event.preventDefault();

  const fields = [...manualForm.elements].filter((field) => field.name);
  const isValid = fields.map(validateManualField).every(Boolean);

  if (!isValid) {
    manualStatus.textContent = "Revisa los campos marcados.";
    manualStatus.classList.add("is-error");
    return;
  }

  await guardarCita();
});

// Envio de creacion de usuario/paciente.
userForm.addEventListener("submit", async (event) => {
  event.preventDefault();

  const fields = [...userForm.elements].filter((field) => field.name);
  const isValid = fields.map(validateUserField).every(Boolean);

  if (!isValid) {
    userStatus.textContent = "Revisa los campos marcados.";
    userStatus.classList.add("is-error");
    return;
  }

  const payload = buildUserPayload(userForm);
  const submitButton = userForm.querySelector("button[type='submit']");

  submitButton.disabled = true;
  userStatus.classList.remove("is-error");
  userStatus.textContent = "Guardando usuario...";

  try {
    await crearUsuarioPaciente(payload);
    addUserRow(payload);
    userStatus.textContent = "Usuario creado correctamente.";
    alert("Usuario de paciente creado correctamente.");
    window.setTimeout(closeUserModal, 650);
  } catch (error) {
    userStatus.textContent = error.status === 409
      ? "Ese correo ya esta registrado."
      : error.message || "No fue posible crear el usuario.";
    userStatus.classList.add("is-error");
  } finally {
    submitButton.disabled = false;
  }
});

// Al entrar al panel se carga la agenda desde la API.
document.addEventListener("DOMContentLoaded", loadTodayAgenda);

window.guardarCita = guardarCita;
