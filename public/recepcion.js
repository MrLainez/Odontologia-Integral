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
const adminLogoutButton = document.querySelector("#admin-logout");
const changePasswordButton = document.querySelector("#change-password-button");
const passwordDialog = document.querySelector("#password-dialog");
const passwordForm = document.querySelector("#password-form");
const passwordStatus = document.querySelector("#password-status");
const patientHistoryModal = document.querySelector("#patient-history-modal");
const patientHistoryTitle = document.querySelector("#patient-history-title");
const patientHistoryStatus = document.querySelector("#patient-history-status");
const patientHistoryContent = document.querySelector("#patient-history-content");
const receptionNav = document.querySelector(".reception-nav");
const receptionSections = document.querySelectorAll(".reception-section");
const patientFilter = document.querySelector("#patient-filter");
const patientsList = document.querySelector("#patients-list");
const paymentForm = document.querySelector("#payment-form");
const paymentStatus = document.querySelector("#payment-status");
const paymentsList = document.querySelector("#payments-list");
const adminUserForm = document.querySelector("#admin-user-form");
const adminUserStatus = document.querySelector("#admin-user-status");
const adminUsersList = document.querySelector("#admin-users-list");
const statTotal = document.querySelector("#stat-total");
const statConfirmed = document.querySelector("#stat-confirmed");
const statAttended = document.querySelector("#stat-attended");
const reportAppointments = document.querySelector("#report-appointments");
const reportPayments = document.querySelector("#report-payments");
const reportPatients = document.querySelector("#report-patients");
const agendaDateInput = document.querySelector("#agenda-date");
const businessHoursForm = document.querySelector("#business-hours-form");
const businessHoursList = document.querySelector("#business-hours-list");
const businessHoursStatus = document.querySelector("#business-hours-status");
const dentistHoursForm = document.querySelector("#dentist-hours-form");
const dentistHoursSelect = document.querySelector("#dentist-hours-dentist");
const dentistHoursList = document.querySelector("#dentist-hours-list");
const dentistHoursStatus = document.querySelector("#dentist-hours-status");
const holidayForm = document.querySelector("#holiday-form");
const holidayList = document.querySelector("#holiday-list");
const holidayStatus = document.querySelector("#holiday-status");
const appointmentRequestsList = document.querySelector("#appointment-requests-list");
const appointmentRequestsStatus = document.querySelector("#appointment-requests-status");
const manualDentistSelect = document.querySelector("#manual-dentist");
const manualAvailabilityStatus = document.querySelector("#manual-availability-status");

// Endpoints usados por el panel de recepcion.
const API_BASE_URL = window.location.origin;
const API_CITAS_URL = `${API_BASE_URL}/api/citas`;
const API_PACIENTES_URL = `${API_BASE_URL}/api/pacientes`;
const API_PAGOS_URL = `${API_BASE_URL}/api/pagos`;
const API_ADMIN_USERS_URL = `${API_BASE_URL}/api/admin/usuarios`;
const API_ADMIN_PASSWORD_URL = `${API_BASE_URL}/api/admin/password`;
const API_RECEPTION_REPORT_URL = `${API_BASE_URL}/api/reportes/recepcion`;
const API_BUSINESS_HOURS_URL = `${API_BASE_URL}/api/horarios-atencion`;
const API_HOLIDAYS_URL = `${API_BASE_URL}/api/dias-feriados`;
const API_APPOINTMENT_REQUESTS_URL = `${API_BASE_URL}/api/solicitudes-cita`;
const API_DENTISTS_URL = `${API_BASE_URL}/api/odontologos`;
const API_AVAILABILITY_URL = `${API_BASE_URL}/api/citas/disponibilidad`;
const API_DENTIST_HOURS_URL = (dentistId) => `${API_BASE_URL}/api/odontologos/${dentistId}/horarios`;
const API_PATIENT_HISTORY_URL = (patientId) => `${API_BASE_URL}/api/pacientes/${patientId}/historial`;

// Proteccion simple de pantalla: requiere sesion administrativa local.
const adminSession = JSON.parse(localStorage.getItem("adminSession") || "null");
const authToken = localStorage.getItem("authToken");
const allowedReceptionRoles = ["ADMIN", "RECEPCION"];

if (!adminSession || !authToken || !allowedReceptionRoles.includes(adminSession.rol)) {
  localStorage.removeItem("adminSession");
  localStorage.removeItem("authToken");
  window.location.href = "index.html";
}

if (adminSession?.rol !== "ADMIN") {
  document.querySelectorAll("[data-admin-only]").forEach((element) => {
    element.remove();
  });
}

// Evita registrar citas manuales en fechas pasadas.
const today = new Date();
const manualDateInput = document.querySelector("#inputFechaHora");
let dentists = [];

if (manualDateInput) {
  manualDateInput.min = today.toISOString().slice(0, 16);
}

if (agendaDateInput) {
  agendaDateInput.value = today.toISOString().slice(0, 10);
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

const weekDays = {
  1: "Lunes",
  2: "Martes",
  3: "Miercoles",
  4: "Jueves",
  5: "Viernes",
  6: "Sabado",
  7: "Domingo"
};

function buildHalfHourOptions(selectedValue = "09:00") {
  const options = [];

  for (let hour = 7; hour <= 21; hour += 1) {
    [0, 30].forEach((minute) => {
      const value = `${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}`;
      const selected = value === String(selectedValue).slice(0, 5) ? "selected" : "";
      options.push(`<option value="${value}" ${selected}>${value}</option>`);
    });
  }

  return options.join("");
}

function setActiveSection(sectionId) {
  receptionSections.forEach((section) => {
    section.classList.toggle("is-active", section.dataset.section === sectionId);
  });

  receptionNav.querySelectorAll("a").forEach((link) => {
    link.classList.toggle("is-active", link.getAttribute("href") === `#${sectionId}`);
  });
}

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
    dentist: appointment.odontologoNombre || appointment.odontologo || "Por asignar",
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
    <span role="cell">
      ${escapeHtml(normalized.treatment)}
      <small class="cell-meta">${escapeHtml(normalized.dentist)}</small>
    </span>
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
  updateDashboardStats();
}

function updateDashboardStats() {
  const rows = [...agendaTable.querySelectorAll(".appointment-row")]
    .filter((row) => !row.dataset.agendaMessage);
  const confirmed = rows.filter((row) => row.dataset.status === "confirmed").length;
  const attended = rows.filter((row) => row.dataset.status === "attended").length;

  statTotal.textContent = rows.length;
  statConfirmed.textContent = confirmed;
  statAttended.textContent = attended;
}

function clearPatients() {
  patientsList.innerHTML = "";
}

function clearPatientUsers() {
  usersList.innerHTML = "";
}

function clearPayments() {
  paymentsList.innerHTML = "";
}

function clearAdminUsers() {
  if (adminUsersList) adminUsersList.innerHTML = "";
}

// Helper comun para fetch con JSON y errores legibles.
async function fetchJson(url, options = {}) {
  const token = localStorage.getItem("authToken");
  const response = await fetch(url, {
    ...options,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(options.headers || {})
    }
  });
  const data = await response.json().catch(() => ({}));

  if (!response.ok) {
    const message = data.error || data.mensaje || "No fue posible completar la solicitud.";
    const error = new Error(message);
    error.status = response.status;
    throw error;
  }

  return data;
}

function createDentistOptions(selectedId = "", includeEmpty = true) {
  const selected = String(selectedId || "");
  const firstOption = includeEmpty ? '<option value="">Selecciona odontólogo</option>' : "";
  const options = dentists.map((dentist) => {
    const value = String(dentist.id);
    return `<option value="${escapeHtml(value)}" ${value === selected ? "selected" : ""}>${escapeHtml(dentist.nombre)}</option>`;
  });

  return `${firstOption}${options.join("")}`;
}

function renderManualDentists() {
  if (!manualDentistSelect) return;

  manualDentistSelect.innerHTML = createDentistOptions(manualDentistSelect.value);
}

function renderDentistHoursSelect() {
  if (!dentistHoursSelect) return;

  dentistHoursSelect.innerHTML = createDentistOptions(dentistHoursSelect.value);
}

async function loadDentists() {
  try {
    dentists = await fetchJson(API_DENTISTS_URL);
    renderManualDentists();
    renderDentistHoursSelect();
  } catch (error) {
    dentists = [];
    if (manualDentistSelect) {
      manualDentistSelect.innerHTML = '<option value="">No se pudieron cargar odontólogos</option>';
    }
    if (dentistHoursSelect) {
      dentistHoursSelect.innerHTML = '<option value="">No se pudieron cargar odontólogos</option>';
    }
  }
}

async function checkDentistAvailability(date, time, dentistId) {
  if (!date || !time || !dentistId) return null;

  const url = new URL(API_AVAILABILITY_URL);
  url.searchParams.set("fecha", date);
  url.searchParams.set("odontologoId", dentistId);

  const availability = await fetchJson(url.toString());
  const slot = (availability.horarios || []).find((item) => String(item.valor).slice(0, 5) === String(time).slice(0, 5));

  return slot?.disponible === true;
}

async function updateRequestAvailability(row) {
  if (!row) return;

  const select = row.querySelector("[data-request-dentist]");
  const status = row.querySelector("[data-request-availability]");
  if (!select || !status) return;

  status.classList.remove("is-error", "is-success");

  if (!select.value) {
    status.textContent = "Selecciona odontólogo";
    return;
  }

  status.textContent = "Revisando disponibilidad...";

  try {
    const isAvailable = await checkDentistAvailability(row.dataset.requestDate, row.dataset.requestTime, select.value);
    status.textContent = isAvailable ? "Horario libre para este odontólogo" : "Horario ocupado para este odontólogo";
    status.classList.toggle("is-success", isAvailable);
    status.classList.toggle("is-error", !isAvailable);
  } catch (error) {
    status.textContent = "No se pudo verificar disponibilidad";
    status.classList.add("is-error");
  }
}

async function updateManualAvailability() {
  if (!manualAvailabilityStatus || !manualDateInput || !manualDentistSelect) return;

  manualAvailabilityStatus.classList.remove("is-error", "is-success");

  if (!manualDateInput.value || !manualDentistSelect.value) {
    manualAvailabilityStatus.textContent = "Selecciona fecha y odontólogo.";
    return;
  }

  const [date, time] = manualDateInput.value.split("T");
  manualAvailabilityStatus.textContent = "Revisando disponibilidad...";

  try {
    const isAvailable = await checkDentistAvailability(date, time, manualDentistSelect.value);
    manualAvailabilityStatus.textContent = isAvailable ? "Horario libre para este odontólogo." : "Horario ocupado para este odontólogo.";
    manualAvailabilityStatus.classList.toggle("is-success", isAvailable);
    manualAvailabilityStatus.classList.toggle("is-error", !isAvailable);
  } catch (error) {
    manualAvailabilityStatus.textContent = "No se pudo verificar disponibilidad.";
    manualAvailabilityStatus.classList.add("is-error");
  }
}

// Carga las citas del dia desde el backend y las pinta en la tabla.
async function loadTodayAgenda() {
  clearAgendaRows();
  showAgendaMessage("Cargando agenda...");

  try {
    const selectedDate = agendaDateInput?.value || today.toISOString().slice(0, 10);
    const data = await fetchJson(`${API_CITAS_URL}?fecha=${encodeURIComponent(selectedDate)}`);
    const appointments = Array.isArray(data) ? data : data.citas || data.appointments || [];

    clearAgendaRows();

    if (appointments.length === 0) {
      showAgendaMessage("No hay citas registradas para hoy.");
      updateDashboardStats();
      return;
    }

    appointments.forEach((appointment) => {
      agendaTable.append(createAppointmentRow(appointment));
    });
    updateDashboardStats();
  } catch (error) {
    clearAgendaRows();
    showAgendaMessage("No se pudo cargar la agenda. Verifica que el servidor este encendido.", true);
    updateDashboardStats();
  }
}

async function loadAppointmentRequests() {
  if (!appointmentRequestsList) return;

  appointmentRequestsStatus.textContent = "Cargando solicitudes...";

  try {
    const requests = await fetchJson(`${API_APPOINTMENT_REQUESTS_URL}?estatus=PENDIENTE`);
    renderAppointmentRequests(requests);
    appointmentRequestsStatus.textContent = "";
  } catch (error) {
    appointmentRequestsList.innerHTML = `
      <article class="record-row">
        <div>
          <strong>No se pudieron cargar solicitudes</strong>
          <span>Verifica la conexion con el servidor.</span>
        </div>
      </article>
    `;
    appointmentRequestsStatus.textContent = error.message || "No fue posible cargar solicitudes.";
    appointmentRequestsStatus.classList.add("is-error");
  }
}

function renderAppointmentRequests(requests) {
  appointmentRequestsList.innerHTML = "";

  if (!requests.length) {
    appointmentRequestsList.innerHTML = `
      <article class="record-row">
        <div>
          <strong>Sin solicitudes pendientes</strong>
          <span>Las nuevas solicitudes de pacientes apareceran aqui.</span>
        </div>
      </article>
    `;
    return;
  }

  requests.forEach((request) => {
    const row = document.createElement("article");
    row.className = "record-row";
    row.dataset.requestId = request.id;
    row.dataset.requestDate = request.fecha;
    row.dataset.requestTime = String(request.hora).slice(0, 5);
    row.innerHTML = `
      <div>
        <strong>${escapeHtml(request.pacienteNombre)} - ${formatDateOnly(request.fecha)} ${escapeHtml(String(request.hora).slice(0, 5))}</strong>
        <span>${escapeHtml(request.motivo || "Solicitud de cita")}</span>
        <label class="inline-select-label">
          Asignar odontólogo
          <select data-request-dentist>
            ${createDentistOptions(request.odontologoId || "", true)}
          </select>
        </label>
        <span class="availability-status" data-request-availability>Selecciona odontólogo</span>
        <span>Odontólogo: ${escapeHtml(request.odontologoNombre || "Sin preferencia")}</span>
      </div>
      <div class="record-actions">
        <button type="button" class="button button--secondary" data-accept-request>Aceptar</button>
        <button type="button" class="button button--danger" data-reject-request>Rechazar</button>
      </div>
    `;
    [...row.querySelectorAll("span")]
      .find((span) => span.textContent.trim().startsWith("Odont"))
      ?.remove();
    appointmentRequestsList.append(row);
    updateRequestAvailability(row);
  });
}

async function acceptAppointmentRequest(requestId, odontologoId) {
  return fetchJson(`${API_APPOINTMENT_REQUESTS_URL}/${requestId}/aceptar`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      odontologoId: Number(odontologoId)
    })
  });
}

async function rejectAppointmentRequest(requestId) {
  return fetchJson(`${API_APPOINTMENT_REQUESTS_URL}/${requestId}/rechazar`, {
    method: "PUT"
  });
}

async function loadPatients() {
  try {
    const patients = await fetchJson(API_PACIENTES_URL);

    clearPatients();
    clearPatientUsers();
    patients.forEach((patient) => {
      addPatientRow(patient);
      addPortalUserRow(patient);
    });
    updateDashboardStats();
  } catch (error) {
    clearPatients();
    clearPatientUsers();
    patientsList.innerHTML = `
      <article class="record-row">
        <div>
          <strong>No se pudieron cargar pacientes</strong>
          <span>Verifica la conexion con el servidor.</span>
        </div>
      </article>
    `;
    usersList.innerHTML = `
      <article class="user-row">
        <div>
          <strong>No se pudieron cargar usuarios</strong>
          <span>Verifica la conexion con el servidor.</span>
        </div>
        <span class="muted-label">Sin datos</span>
      </article>
    `;
  }
}

async function loadPayments() {
  try {
    const payments = await fetchJson(API_PAGOS_URL);

    clearPayments();
    payments.forEach(addPaymentRow);
    updateDashboardStats();
  } catch (error) {
    clearPayments();
    paymentsList.innerHTML = `
      <article class="record-row">
        <div>
          <strong>No se pudieron cargar pagos</strong>
          <span>Verifica la conexion con el servidor.</span>
        </div>
      </article>
    `;
  }
}

async function loadAdminUsers() {
  if (!adminUsersList || adminSession?.rol !== "ADMIN") return;

  try {
    const users = await fetchJson(API_ADMIN_USERS_URL);

    clearAdminUsers();
    users.forEach(addAdminUserRow);
  } catch (error) {
    clearAdminUsers();
    adminUsersList.innerHTML = `
      <article class="record-row">
        <div>
          <strong>No se pudo cargar el personal</strong>
          <span>Verifica la conexion con el servidor.</span>
        </div>
      </article>
    `;
  }
}

async function loadReceptionReport() {
  try {
    const report = await fetchJson(API_RECEPTION_REPORT_URL);

    reportAppointments.textContent = report.citasHoy ?? "0";
    reportPayments.textContent = report.pagosRegistrados ?? "0";
    reportPatients.textContent = report.pacientesActivos ?? "0";
  } catch (error) {
    updateDashboardStats();
  }
}

async function loadBusinessHours() {
  if (!businessHoursList) return;

  businessHoursStatus.textContent = "Cargando horarios...";

  try {
    const schedules = await fetchJson(API_BUSINESS_HOURS_URL);
    renderBusinessHours(schedules);
    businessHoursStatus.textContent = "";
  } catch (error) {
    businessHoursList.innerHTML = `
      <article class="record-row">
        <div>
          <strong>No se pudieron cargar los horarios</strong>
          <span>Verifica la conexion con el servidor.</span>
        </div>
      </article>
    `;
    businessHoursStatus.textContent = error.message || "No fue posible cargar horarios.";
    businessHoursStatus.classList.add("is-error");
  }
}

function renderBusinessHours(schedules, container = businessHoursList) {
  container.innerHTML = "";

  schedules.forEach((schedule) => {
    const row = document.createElement("article");
    row.className = "business-hours-row";
    row.dataset.day = schedule.diaSemana;
    row.innerHTML = `
      <label class="business-hours-toggle">
        <input type="checkbox" name="activo-${schedule.diaSemana}" ${schedule.activo ? "checked" : ""}>
        <span>${escapeHtml(schedule.diaNombre || weekDays[schedule.diaSemana])}</span>
      </label>
      <div class="business-hours-times">
        <label>
          Inicio
          <select name="inicio-${schedule.diaSemana}">
            ${buildHalfHourOptions(schedule.horaInicio || "09:00")}
          </select>
        </label>
        <label>
          Fin
          <select name="fin-${schedule.diaSemana}">
            ${buildHalfHourOptions(schedule.horaFin || "18:00")}
          </select>
        </label>
      </div>
    `;
    container.append(row);
  });
}

function buildBusinessHoursPayload(container = businessHoursList) {
  const horarios = [...container.querySelectorAll(".business-hours-row")].map((row) => {
    const day = Number(row.dataset.day);
    return {
      diaSemana: day,
      activo: row.querySelector(`[name="activo-${day}"]`).checked,
      horaInicio: row.querySelector(`[name="inicio-${day}"]`).value,
      horaFin: row.querySelector(`[name="fin-${day}"]`).value
    };
  });

  return { horarios };
}

async function loadDentistHours() {
  if (!dentistHoursList || !dentistHoursSelect) return;

  dentistHoursStatus.classList.remove("is-error");

  if (!dentistHoursSelect.value) {
    dentistHoursList.innerHTML = "";
    dentistHoursStatus.textContent = "Selecciona un odontólogo para revisar su disponibilidad.";
    return;
  }

  dentistHoursStatus.textContent = "Cargando horarios del odontólogo...";

  try {
    const data = await fetchJson(API_DENTIST_HOURS_URL(dentistHoursSelect.value));
    renderBusinessHours(data.horarios || [], dentistHoursList);
    dentistHoursStatus.textContent = data.horarios?.some((schedule) => schedule.heredado)
      ? "Este odontólogo hereda el horario general hasta que guardes cambios propios."
      : "";
  } catch (error) {
    dentistHoursList.innerHTML = "";
    dentistHoursStatus.textContent = error.message || "No fue posible cargar horarios del odontólogo.";
    dentistHoursStatus.classList.add("is-error");
  }
}

async function saveDentistHours(event) {
  event.preventDefault();

  dentistHoursStatus.classList.remove("is-error");

  if (!dentistHoursSelect.value) {
    dentistHoursStatus.textContent = "Selecciona un odontólogo.";
    dentistHoursStatus.classList.add("is-error");
    return;
  }

  dentistHoursStatus.textContent = "Guardando horario del odontólogo...";

  try {
    const data = await fetchJson(API_DENTIST_HOURS_URL(dentistHoursSelect.value), {
      method: "PUT",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify(buildBusinessHoursPayload(dentistHoursList))
    });

    renderBusinessHours(data.horarios || [], dentistHoursList);
    dentistHoursStatus.textContent = "Horario del odontólogo actualizado correctamente.";
    await loadAppointmentRequests();
    await loadTodayAgenda();
  } catch (error) {
    dentistHoursStatus.textContent = error.message || "No fue posible guardar el horario del odontólogo.";
    dentistHoursStatus.classList.add("is-error");
  }
}

async function saveBusinessHours(event) {
  event.preventDefault();

  businessHoursStatus.classList.remove("is-error");
  businessHoursStatus.textContent = "Guardando horarios...";

  try {
    const data = await fetchJson(API_BUSINESS_HOURS_URL, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify(buildBusinessHoursPayload())
    });

    renderBusinessHours(data.horarios || []);
    businessHoursStatus.textContent = "Horarios actualizados correctamente.";
    await loadTodayAgenda();
  } catch (error) {
    businessHoursStatus.textContent = error.message || "No fue posible guardar horarios.";
    businessHoursStatus.classList.add("is-error");
  }
}

async function loadHolidays() {
  if (!holidayList) return;

  try {
    const holidays = await fetchJson(API_HOLIDAYS_URL);
    renderHolidays(holidays);
  } catch (error) {
    holidayList.innerHTML = `
      <article class="record-row">
        <div>
          <strong>No se pudieron cargar cierres</strong>
          <span>Verifica la conexion con el servidor.</span>
        </div>
      </article>
    `;
  }
}

function renderHolidays(holidays) {
  holidayList.innerHTML = "";

  if (!holidays.length) {
    holidayList.innerHTML = `
      <article class="record-row">
        <div>
          <strong>Sin cierres especiales</strong>
          <span>La disponibilidad depende solo del horario semanal.</span>
        </div>
      </article>
    `;
    return;
  }

  holidays.forEach((holiday) => {
    const row = document.createElement("article");
    row.className = "record-row";
    row.dataset.holidayId = holiday.id;
    row.innerHTML = `
      <div>
        <strong>${formatDateOnly(holiday.fecha)}</strong>
        <span>${escapeHtml(holiday.motivo || "Cierre especial")}</span>
      </div>
      <div class="record-actions">
        <button type="button" class="button button--danger" data-delete-holiday>Eliminar</button>
      </div>
    `;
    holidayList.append(row);
  });
}

function formatDateOnly(dateValue) {
  if (!dateValue) return "Fecha no registrada";

  return new Date(`${dateValue}T00:00:00`).toLocaleDateString("es-MX", {
    day: "2-digit",
    month: "short",
    year: "numeric"
  });
}

function formatHistoryTime(timeValue) {
  if (!timeValue) return "--:--";

  return String(timeValue).slice(0, 5);
}

function formatHistoryTimestamp(value) {
  if (!value) return "Fecha no registrada";

  return new Date(value).toLocaleString("es-MX", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit"
  });
}

function createHistoryAppointmentItem(appointment) {
  const item = document.createElement("article");
  item.className = "history-item";
  item.innerHTML = `
    <div>
      <strong>${formatDateOnly(appointment.fecha)} ${escapeHtml(formatHistoryTime(appointment.hora))}</strong>
      <span>${escapeHtml(appointment.odontologoNombre || "Por asignar")} - ${escapeHtml(appointment.estatus || "Sin estatus")}</span>
    </div>
    <p>${escapeHtml(appointment.tratamiento || "Sin tratamiento registrado")}</p>
  `;
  return item;
}

function createHistoryNoteItem(note) {
  const item = document.createElement("article");
  item.className = "history-item";
  item.innerHTML = `
    <div>
      <strong>${formatHistoryTimestamp(note.fechaHora || note.fecha_hora)}</strong>
      <span>Nota de evolucion</span>
    </div>
    <p>${escapeHtml(note.textoNota || note.texto_nota || "")}</p>
  `;
  return item;
}

function renderPatientHistory(history) {
  const appointments = history.citas || [];
  const notes = history.notasEvolucion || [];
  const appointmentsList = document.createElement("div");
  const notesList = document.createElement("div");

  patientHistoryContent.innerHTML = "";
  appointmentsList.className = "history-list";
  notesList.className = "history-list";

  appointments.forEach((appointment) => {
    appointmentsList.append(createHistoryAppointmentItem(appointment));
  });
  notes.forEach((note) => {
    notesList.append(createHistoryNoteItem(note));
  });

  patientHistoryContent.innerHTML = `
    <section>
      <h3>Citas del paciente</h3>
      ${appointments.length ? "" : "<p class=\"empty-state\">Sin citas registradas.</p>"}
    </section>
    <section>
      <h3>Notas de evolucion</h3>
      ${notes.length ? "" : "<p class=\"empty-state\">Sin notas registradas.</p>"}
    </section>
  `;
  patientHistoryContent.querySelector("section:nth-child(1)").append(appointmentsList);
  patientHistoryContent.querySelector("section:nth-child(2)").append(notesList);
}

async function openPatientHistory(patientId, patientName) {
  patientHistoryTitle.textContent = patientName ? `Historial de ${patientName}` : "Historial del paciente";
  patientHistoryStatus.textContent = "Cargando historial...";
  patientHistoryStatus.classList.remove("is-error");
  patientHistoryContent.innerHTML = "";
  patientHistoryModal.classList.remove("is-hidden");
  document.body.classList.add("has-dialog");

  try {
    const history = await fetchJson(API_PATIENT_HISTORY_URL(patientId));
    renderPatientHistory(history);
    patientHistoryStatus.textContent = "Historial actualizado.";
  } catch (error) {
    patientHistoryStatus.textContent = error.message || "No fue posible cargar el historial.";
    patientHistoryStatus.classList.add("is-error");
  }
}

function closePatientHistory() {
  patientHistoryModal.classList.add("is-hidden");
  document.body.classList.remove("has-dialog");
  patientHistoryStatus.textContent = "";
  patientHistoryContent.innerHTML = "";
}

async function createHoliday(event) {
  event.preventDefault();

  const data = new FormData(holidayForm);
  const payload = {
    fecha: data.get("fecha"),
    motivo: data.get("motivo").trim() || "Cierre especial"
  };

  if (!payload.fecha) {
    holidayStatus.textContent = "Selecciona la fecha del cierre.";
    holidayStatus.classList.add("is-error");
    return;
  }

  holidayStatus.classList.remove("is-error");
  holidayStatus.textContent = "Guardando cierre...";

  try {
    const response = await fetchJson(API_HOLIDAYS_URL, {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify(payload)
    });

    renderHolidays(response.feriados || []);
    holidayForm.reset();
    holidayStatus.textContent = "Cierre registrado correctamente.";
  } catch (error) {
    holidayStatus.textContent = error.message || "No fue posible registrar el cierre.";
    holidayStatus.classList.add("is-error");
  }
}

async function deleteHoliday(holidayId) {
  await fetchJson(`${API_HOLIDAYS_URL}/${holidayId}`, {
    method: "DELETE"
  });
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
  renderManualDentists();
  document.querySelector("#manual-name")?.focus();
}

function closeManualModal() {
  manualModal.classList.add("is-hidden");
  document.body.classList.remove("has-dialog");
  manualForm.reset();
  manualStatus.textContent = "";
  if (manualAvailabilityStatus) {
    manualAvailabilityStatus.classList.remove("is-error", "is-success");
    manualAvailabilityStatus.textContent = "Selecciona fecha y odontólogo.";
  }
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

function openPasswordDialog() {
  passwordDialog.classList.remove("is-hidden");
  document.body.classList.add("has-dialog");
  passwordForm.reset();
  passwordStatus.textContent = "";
  passwordStatus.classList.remove("is-error");
  passwordForm.elements.passwordActual.focus();
}

function closePasswordDialog() {
  passwordDialog.classList.add("is-hidden");
  document.body.classList.remove("has-dialog");
  passwordForm.reset();
  passwordStatus.textContent = "";
  passwordStatus.classList.remove("is-error");
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

  if (field.name === "password" && value && (value.length < 8 || !/[A-Za-z]/.test(value) || !/\d/.test(value))) {
    message = "Usa al menos 8 caracteres, una letra y un numero.";
  }

  showFieldError(field, message);
  return message === "";
}

// JSON que espera POST /api/citas.
function buildManualAppointmentPayload(form) {
  const data = new FormData(form);

  return {
    pacienteId: Number(data.get("patientId")),
    odontologoId: Number(data.get("odontologoId")),
    fechaHora: data.get("dateTime"),
    motivo: data.get("reason").trim()
  };
}

// Registra una cita manual y la agrega visualmente si la API responde OK.
async function guardarCita() {
  const cita = buildManualAppointmentPayload(manualForm);

  try {
    await fetchJson(API_CITAS_URL, {
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
    await loadTodayAgenda();
    await loadReceptionReport();
    window.setTimeout(closeManualModal, 650);
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
  addPortalUserRow(user);
  addPatientRow(user);
  updateDashboardStats();
}

function addPortalUserRow(user) {
  const row = document.createElement("article");
  row.className = "user-row";
  row.innerHTML = `
    <div>
      <strong>${escapeHtml(user.nombre)}</strong>
      <span>${escapeHtml(user.email)}</span>
    </div>
    <span class="status-pill">Activo</span>
  `;

  usersList.append(row);
}

function addPatientRow(patient, prepend = false) {
  const row = document.createElement("article");
  const searchableText = `${patient.nombre} ${patient.email} ${patient.telefono}`.toLowerCase();
  const isActive = patient.activo !== false;

  row.className = "record-row";
  row.dataset.patientId = patient.id || "";
  row.dataset.patientName = patient.nombre || "";
  row.dataset.patientEmail = patient.email || "";
  row.dataset.patientPhone = patient.telefono || "";
  row.dataset.patientText = searchableText;
  row.innerHTML = `
    <div>
      <strong>${escapeHtml(patient.nombre)}</strong>
      <span>${escapeHtml(patient.email)} · ${escapeHtml(patient.telefono)}</span>
    </div>
    <div class="record-actions">
      <span class="${isActive ? "status-pill" : "muted-label"}">${isActive ? "Activo" : "Inactivo"}</span>
      <button type="button" class="button button--secondary" data-view-patient-history>Historial</button>
      ${isActive ? `
        <button type="button" class="button button--secondary" data-edit-patient>Editar</button>
        <button type="button" class="button button--secondary" data-reset-patient-password>Contraseña</button>
        <button type="button" class="button button--danger" data-delete-patient>Eliminar</button>
      ` : ""}
    </div>
  `;

  if (prepend) {
    patientsList.prepend(row);
  } else {
    patientsList.append(row);
  }
}

async function updatePatient(patientId, payload) {
  return fetchJson(`${API_PACIENTES_URL}/${patientId}`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(payload)
  });
}

async function resetPatientPassword(patientId, password) {
  return fetchJson(`${API_PACIENTES_URL}/${patientId}/password`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ password })
  });
}

async function deletePatient(patientId) {
  return fetchJson(`${API_PACIENTES_URL}/${patientId}`, {
    method: "DELETE"
  });
}

function addPaymentRow(payment, prepend = false) {
  const row = document.createElement("article");
  const amount = payment.monto || payment.amount;
  const concept = payment.concepto || payment.concept;
  const patient = payment.pacienteNombre || payment.patient;
  const method = payment.metodo || payment.method;
  const date = payment.fechaRegistro
    ? new Date(payment.fechaRegistro).toLocaleDateString("es-MX")
    : new Date().toLocaleDateString("es-MX");

  row.className = "record-row";
  row.innerHTML = `
    <div>
      <strong>${formatCurrency(amount)} - ${escapeHtml(concept)}</strong>
      <span>${escapeHtml(patient)} - ${escapeHtml(method)} - ${date}</span>
    </div>
    <span class="muted-label">Registrado</span>
  `;

  if (prepend) {
    paymentsList.prepend(row);
  } else {
    paymentsList.append(row);
  }
}

function roleLabel(role) {
  const labels = {
    ADMIN: "Administrador",
    RECEPCION: "Recepcionista",
    ODONTOLOGO: "Odontologo"
  };

  return labels[role] || role;
}

function addAdminUserRow(user) {
  const row = document.createElement("article");
  const isActive = user.activo !== false;

  row.className = "record-row";
  row.dataset.adminUserId = user.id;
  row.innerHTML = `
    <div>
      <strong>${escapeHtml(user.nombre)}</strong>
      <span>${escapeHtml(user.email)} - ${roleLabel(user.rol)}</span>
    </div>
    <div class="record-actions">
      <span class="${isActive ? "status-pill" : "muted-label"}">${isActive ? "Activo" : "Inactivo"}</span>
      ${isActive ? `
        <button type="button" class="button button--secondary" data-reset-admin-password>Contraseña</button>
        <button type="button" class="button button--danger" data-delete-admin-user>Eliminar</button>
      ` : ""}
    </div>
  `;

  adminUsersList.append(row);
}

function buildAdminUserPayload(form) {
  const data = new FormData(form);

  return {
    nombre: data.get("nombre").trim(),
    email: data.get("email").trim(),
    password: data.get("password"),
    rol: data.get("rol")
  };
}

async function createAdminUser(payload) {
  return fetchJson(API_ADMIN_USERS_URL, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(payload)
  });
}

async function resetAdminPassword(userId, password) {
  return fetchJson(`${API_ADMIN_USERS_URL}/${userId}/password`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ password })
  });
}

async function changeOwnAdminPassword(payload) {
  return fetchJson(API_ADMIN_PASSWORD_URL, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(payload)
  });
}

async function deleteAdminUser(userId) {
  return fetchJson(`${API_ADMIN_USERS_URL}/${userId}`, {
    method: "DELETE"
  });
}

function filterPatients(query) {
  const normalizedQuery = query.trim().toLowerCase();

  patientsList.querySelectorAll(".record-row").forEach((row) => {
    row.classList.toggle("is-hidden", Boolean(normalizedQuery) && !row.dataset.patientText.includes(normalizedQuery));
  });
}

function formatCurrency(value) {
  return Number(value).toLocaleString("es-MX", {
    style: "currency",
    currency: "MXN"
  });
}

async function registerPayment(form) {
  const data = new FormData(form);
  const patient = data.get("patient").trim();
  const concept = data.get("concept").trim();
  const amount = Number(data.get("amount"));
  const method = data.get("method");

  if (!patient || !concept || !amount || !method) {
    paymentStatus.textContent = "Completa todos los datos del pago.";
    paymentStatus.classList.add("is-error");
    return;
  }

  const payload = {
    pacienteNombre: patient,
    concepto: concept,
    monto: amount,
    metodo: method
  };

  paymentStatus.classList.remove("is-error");
  paymentStatus.textContent = "Guardando pago...";

  try {
    await fetchJson(API_PAGOS_URL, {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify(payload)
    });

    await loadPayments();
    await loadReceptionReport();
    paymentForm.reset();
    paymentStatus.textContent = "Pago registrado en bitacora.";
  } catch (error) {
    paymentStatus.textContent = error.message || "No fue posible guardar el pago.";
    paymentStatus.classList.add("is-error");
  }
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
    loadReceptionReport();
  } catch (error) {
    alert(error.message || "No se pudo actualizar el estatus. Verifica que el servidor este encendido.");
  } finally {
    statusButton.disabled = false;
  }
});

openManualButton.addEventListener("click", openManualModal);
openUserButton.addEventListener("click", openUserModal);
changePasswordButton.addEventListener("click", openPasswordDialog);

adminLogoutButton.addEventListener("click", () => {
  localStorage.removeItem("adminSession");
  localStorage.removeItem("authToken");
  window.location.href = "index.html";
});

receptionNav.addEventListener("click", (event) => {
  const link = event.target.closest("a[href^='#']");
  if (!link) return;

  event.preventDefault();
  setActiveSection(link.getAttribute("href").slice(1));
});

patientFilter.addEventListener("input", () => {
  filterPatients(patientFilter.value);
});

if (agendaDateInput) {
  agendaDateInput.addEventListener("change", loadTodayAgenda);
}

if (manualDateInput) {
  manualDateInput.addEventListener("change", updateManualAvailability);
}

if (manualDentistSelect) {
  manualDentistSelect.addEventListener("change", updateManualAvailability);
}

if (businessHoursForm) {
  businessHoursForm.addEventListener("submit", saveBusinessHours);
}

if (dentistHoursSelect) {
  dentistHoursSelect.addEventListener("change", loadDentistHours);
}

if (dentistHoursForm) {
  dentistHoursForm.addEventListener("submit", saveDentistHours);
}

if (holidayForm) {
  holidayForm.addEventListener("submit", createHoliday);
}

if (holidayList) {
  holidayList.addEventListener("click", async (event) => {
    const deleteButton = event.target.closest("[data-delete-holiday]");
    if (!deleteButton) return;

    const row = deleteButton.closest("[data-holiday-id]");
    const confirmed = window.confirm("Este dia dejara de estar marcado como cierre especial. Deseas continuar?");

    if (!confirmed) return;

    try {
      await deleteHoliday(row.dataset.holidayId);
      await loadHolidays();
      await loadTodayAgenda();
      holidayStatus.textContent = "Cierre eliminado correctamente.";
      holidayStatus.classList.remove("is-error");
    } catch (error) {
      holidayStatus.textContent = error.message || "No fue posible eliminar el cierre.";
      holidayStatus.classList.add("is-error");
    }
  });
}

if (appointmentRequestsList) {
  appointmentRequestsList.addEventListener("change", (event) => {
    if (!event.target.matches("[data-request-dentist]")) return;

    const row = event.target.closest("[data-request-id]");
    updateRequestAvailability(row);
  });

  appointmentRequestsList.addEventListener("click", async (event) => {
    const acceptButton = event.target.closest("[data-accept-request]");
    const rejectButton = event.target.closest("[data-reject-request]");
    if (!acceptButton && !rejectButton) return;

    const row = event.target.closest("[data-request-id]");
    if (!row) return;

    const requestId = row.dataset.requestId;

    try {
      if (acceptButton) {
        const dentistSelect = row.querySelector("[data-request-dentist]");

        if (!dentistSelect?.value) {
          appointmentRequestsStatus.textContent = "Selecciona un odontólogo antes de aceptar la solicitud.";
          appointmentRequestsStatus.classList.add("is-error");
          return;
        }

        await acceptAppointmentRequest(requestId, dentistSelect.value);
        appointmentRequestsStatus.textContent = "Solicitud aceptada y cita creada.";
      }

      if (rejectButton) {
        const confirmed = window.confirm("Deseas rechazar esta solicitud de cita?");
        if (!confirmed) return;

        await rejectAppointmentRequest(requestId);
        appointmentRequestsStatus.textContent = "Solicitud rechazada correctamente.";
      }

      appointmentRequestsStatus.classList.remove("is-error");
      await loadAppointmentRequests();
      await loadTodayAgenda();
      await loadReceptionReport();
    } catch (error) {
      appointmentRequestsStatus.textContent = error.status === 409
        ? "Ese horario ya fue ocupado. Rechaza la solicitud o coordina otro horario con el paciente."
        : error.message || "No fue posible procesar la solicitud.";
      appointmentRequestsStatus.classList.add("is-error");
    }
  });
}

patientsList.addEventListener("click", async (event) => {
  const row = event.target.closest("[data-patient-id]");
  if (!row) return;

  const patientId = row.dataset.patientId;

  if (event.target.closest("[data-view-patient-history]")) {
    await openPatientHistory(patientId, row.dataset.patientName || "");
    return;
  }

  if (event.target.closest("[data-edit-patient]")) {
    const currentName = row.dataset.patientName || "";
    const currentEmail = row.dataset.patientEmail || "";
    const currentPhone = row.dataset.patientPhone || "";
    const nombre = window.prompt("Nombre del paciente:", currentName);

    if (nombre === null) return;

    const telefono = window.prompt("Telefono del paciente:", currentPhone);

    if (telefono === null) return;

    const email = window.prompt("Correo del paciente:", currentEmail);

    if (email === null) return;

    if (!nombre.trim() || !telefono.trim() || !email.trim()) {
      alert("Nombre, telefono y correo son obligatorios.");
      return;
    }

    try {
      await updatePatient(patientId, {
        nombre,
        telefono,
        email
      });
      await loadPatients();
      await loadReceptionReport();
      alert("Paciente actualizado correctamente.");
    } catch (error) {
      alert(error.message || "No fue posible actualizar el paciente.");
    }
  }

  if (event.target.closest("[data-reset-patient-password]")) {
    const newPassword = window.prompt("Nueva contraseña temporal para este paciente:");

    if (!newPassword) return;
    if (newPassword.length < 8 || !/[A-Za-z]/.test(newPassword) || !/\d/.test(newPassword)) {
      alert("La contraseña debe tener al menos 8 caracteres, una letra y un numero.");
      return;
    }

    try {
      await resetPatientPassword(patientId, newPassword);
      alert("Contraseña del paciente actualizada correctamente.");
    } catch (error) {
      alert(error.message || "No fue posible actualizar la contraseña.");
    }
  }

  if (event.target.closest("[data-delete-patient]")) {
    const confirmed = window.confirm("Se borrara el perfil del paciente junto con citas, expediente, notas y odontograma. Esta accion no se puede deshacer. Deseas continuar?");

    if (!confirmed) return;

    try {
      await deletePatient(patientId);
      await loadPatients();
      await loadTodayAgenda();
      await loadReceptionReport();
      alert("Paciente e historial clinico eliminados correctamente.");
    } catch (error) {
      alert(error.message || "No fue posible eliminar el paciente.");
    }
  }
});

paymentForm.addEventListener("submit", (event) => {
  event.preventDefault();
  registerPayment(paymentForm);
});

if (adminUserForm) {
  adminUserForm.addEventListener("submit", async (event) => {
    event.preventDefault();

    const payload = buildAdminUserPayload(adminUserForm);
    const submitButton = adminUserForm.querySelector("button[type='submit']");

    if (!payload.nombre || !payload.email || payload.password.length < 8 || !/[A-Za-z]/.test(payload.password) || !/\d/.test(payload.password) || !payload.rol) {
      adminUserStatus.textContent = "Completa nombre, correo, rol y una contraseña de al menos 8 caracteres, una letra y un numero.";
      adminUserStatus.classList.add("is-error");
      return;
    }

    submitButton.disabled = true;
    adminUserStatus.classList.remove("is-error");
    adminUserStatus.textContent = "Creando usuario interno...";

    try {
      await createAdminUser(payload);
      clearAdminUsers();
      await loadAdminUsers();
      if (payload.rol === "ODONTOLOGO") {
        await loadDentists();
        await loadAppointmentRequests();
      }
      adminUserForm.reset();
      adminUserStatus.textContent = "Usuario interno creado correctamente.";
    } catch (error) {
      adminUserStatus.textContent = error.message || "No fue posible crear el usuario interno.";
      adminUserStatus.classList.add("is-error");
    } finally {
      submitButton.disabled = false;
    }
  });

  adminUsersList.addEventListener("click", async (event) => {
    const row = event.target.closest("[data-admin-user-id]");
    if (!row) return;

    const userId = row.dataset.adminUserId;

    if (event.target.closest("[data-reset-admin-password]")) {
      const newPassword = window.prompt("Nueva contraseña temporal para este usuario:");

      if (!newPassword) return;
      if (newPassword.length < 8 || !/[A-Za-z]/.test(newPassword) || !/\d/.test(newPassword)) {
        alert("La contraseña debe tener al menos 8 caracteres, una letra y un numero.");
        return;
      }

      try {
        await resetAdminPassword(userId, newPassword);
        alert("Contraseña actualizada correctamente.");
      } catch (error) {
        alert(error.message || "No fue posible actualizar la contraseña.");
      }
    }

    if (event.target.closest("[data-delete-admin-user]")) {
      const confirmed = window.confirm("Este usuario interno se eliminara y ya no podra iniciar sesion. Deseas continuar?");

      if (!confirmed) return;

      try {
        await deleteAdminUser(userId);
        row.remove();
        alert("Usuario eliminado correctamente.");
      } catch (error) {
        alert(error.message || "No fue posible eliminar el usuario.");
      }
    }
  });
}

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

passwordDialog.addEventListener("click", (event) => {
  if (event.target.matches("[data-close-password-dialog]")) {
    closePasswordDialog();
  }
});

patientHistoryModal.addEventListener("click", (event) => {
  if (event.target.matches("[data-close-patient-history]")) {
    closePatientHistory();
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

  if (event.key === "Escape" && !passwordDialog.classList.contains("is-hidden")) {
    closePasswordDialog();
  }

  if (event.key === "Escape" && !patientHistoryModal.classList.contains("is-hidden")) {
    closePatientHistory();
  }
});

// Validacion en vivo de formularios.
manualForm.addEventListener("input", (event) => {
  if (event.target.matches("input, select")) {
    validateManualField(event.target);
  }
});

manualForm.addEventListener("change", (event) => {
  if (event.target.matches("select")) {
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
    await loadPatients();
    await loadReceptionReport();
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

passwordForm.addEventListener("submit", async (event) => {
  event.preventDefault();

  const data = new FormData(passwordForm);
  const payload = {
    passwordActual: data.get("passwordActual"),
    passwordNueva: data.get("passwordNueva")
  };

  if (!payload.passwordActual || payload.passwordNueva.length < 8 || !/[A-Za-z]/.test(payload.passwordNueva) || !/\d/.test(payload.passwordNueva)) {
    passwordStatus.textContent = "Escribe tu contraseña actual y una nueva de al menos 8 caracteres, una letra y un numero.";
    passwordStatus.classList.add("is-error");
    return;
  }

  passwordStatus.classList.remove("is-error");
  passwordStatus.textContent = "Guardando contraseña...";

  try {
    await changeOwnAdminPassword(payload);
    passwordStatus.textContent = "Contraseña actualizada correctamente.";
    window.setTimeout(closePasswordDialog, 700);
  } catch (error) {
    passwordStatus.textContent = error.message || "No fue posible actualizar la contraseña.";
    passwordStatus.classList.add("is-error");
  }
});

// Al entrar al panel se carga la agenda desde la API.
document.addEventListener("DOMContentLoaded", loadTodayAgenda);
document.addEventListener("DOMContentLoaded", async () => {
  await loadDentists();
  await loadAppointmentRequests();
});
document.addEventListener("DOMContentLoaded", loadPatients);
document.addEventListener("DOMContentLoaded", loadPayments);
document.addEventListener("DOMContentLoaded", loadAdminUsers);
document.addEventListener("DOMContentLoaded", loadReceptionReport);
document.addEventListener("DOMContentLoaded", loadBusinessHours);
document.addEventListener("DOMContentLoaded", loadHolidays);
document.addEventListener("DOMContentLoaded", () => {
  const initialSection = window.location.hash.replace("#", "") || "agenda";
  setActiveSection(initialSection);
});

window.guardarCita = guardarCita;
