const loginView = document.querySelector("#login-view");
const portalView = document.querySelector("#portal-view");
const loginForm = document.querySelector("#login-form");
const registerForm = document.querySelector("#register-form");
const loginStatus = document.querySelector("#login-status");
const rememberEmailInput = document.querySelector("#remember-email");
const logoutButton = document.querySelector("#logout-button");
const changePasswordButton = document.querySelector("#change-password-button");
const passwordDialog = document.querySelector("#password-dialog");
const passwordForm = document.querySelector("#password-form");
const passwordStatus = document.querySelector("#password-status");
const passwordInput = document.querySelector("#password");
const togglePasswordButton = document.querySelector("#toggle-password");
const appointmentList = document.querySelector("#appointment-list");
const appointmentsEmpty = document.querySelector("#appointments-empty");
const cancelDialog = document.querySelector("#cancel-dialog");
const confirmCancelButton = document.querySelector("#confirm-cancel-button");
const nextAppointmentStatus = document.querySelector("#next-appointment-status");
const nextAppointmentDay = document.querySelector("#next-appointment-day");
const nextAppointmentMonth = document.querySelector("#next-appointment-month");
const nextAppointmentTime = document.querySelector("#next-appointment-time");
const nextAppointmentTreatment = document.querySelector("#next-appointment-treatment");
const nextAppointmentDoctor = document.querySelector("#next-appointment-doctor");
const recordBloodType = document.querySelector("#record-blood-type");
const recordAllergies = document.querySelector("#record-allergies");
const recordLastVisit = document.querySelector("#record-last-visit");

const API_BASE_URL = window.location.origin;
const urlParams = new URLSearchParams(window.location.search);
const REGISTER_ENDPOINT = `${API_BASE_URL}/api/pacientes`;
const AUTH_ENDPOINT = `${API_BASE_URL}/api/login`;
const ADMIN_AUTH_ENDPOINT = `${API_BASE_URL}/api/admin/login`;
const APPOINTMENT_ENDPOINT = `${API_BASE_URL}/api/citas`;
const PATIENT_APPOINTMENTS_ENDPOINT = (patientId) => `${API_BASE_URL}/api/pacientes/${patientId}/citas`;
const PATIENT_RECORD_ENDPOINT = (patientId) => `${API_BASE_URL}/api/pacientes/${patientId}/expediente`;
const CANCEL_APPOINTMENT_ENDPOINT = (patientId, appointmentId) => (
  `${API_BASE_URL}/api/pacientes/${patientId}/citas/${appointmentId}/cancelar`
);
const CHANGE_PATIENT_PASSWORD_ENDPOINT = `${API_BASE_URL}/api/pacientes/password`;

let selectedAppointmentCard = null;

// Fecha base para evitar solicitudes en dias pasados.
const today = new Date();
today.setHours(0, 0, 0, 0);
const quickRequestDate = document.querySelector("#date");
if (quickRequestDate) {
  quickRequestDate.min = today.toISOString().split("T")[0];
}

// Validaciones simples del formulario de login y solicitud.
const validators = {
  email: (value) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value) || "Escribe un correo valido.",
  password: (value) => (
    value.trim().length >= 8 && /[A-Za-z]/.test(value) && /\d/.test(value)
  ) || "La contrasena debe tener al menos 8 caracteres, una letra y un numero."
};

function showFieldError(field, message) {
  const error = document.querySelector(`[data-error-for="${field.name}"]`);
  const row = field.closest(".form-row");

  if (row) row.classList.toggle("has-error", Boolean(message));
  if (error) error.textContent = message || "";
}

function validateField(field) {
  const validator = validators[field.name];
  if (!validator) return true;

  const result = validator(field.value, field);
  showFieldError(field, result === true ? "" : result);
  return result === true;
}

function validateForm(form) {
  return [...form.elements]
    .filter((field) => field.name)
    .map(validateField)
    .every(Boolean);
}

// Cambia de la pantalla de login al dashboard interno.
function showPortal() {
  loginView.classList.add("is-hidden");
  portalView.classList.remove("is-hidden");
  requestAnimationFrame(() => portalView.scrollIntoView({ block: "start" }));
}

// Regresa al login y limpia el formulario.
function showLogin() {
  portalView.classList.add("is-hidden");
  loginView.classList.remove("is-hidden");
  loginForm.reset();
  loadRememberedEmail();
  loginStatus.textContent = urlParams.get("acceso") === "odontologo"
    ? "Ingresa con una cuenta de odontologo o administrador."
    : "";
}

function loadRememberedEmail() {
  const rememberedEmail = localStorage.getItem("rememberedEmail");
  if (!rememberedEmail || !rememberEmailInput) return;

  loginForm.elements.email.value = rememberedEmail;
  rememberEmailInput.checked = true;
}

function syncRememberedEmail(email) {
  if (!rememberEmailInput) return;

  if (rememberEmailInput.checked) {
    localStorage.setItem("rememberedEmail", email);
  } else {
    localStorage.removeItem("rememberedEmail");
  }
}

// Abre el modal de cancelacion para la tarjeta seleccionada.
function openCancelDialog(card) {
  selectedAppointmentCard = card;
  cancelDialog.classList.remove("is-hidden");
  document.body.classList.add("has-dialog");
  confirmCancelButton.focus();
}

// Cierra el modal de cancelacion.
function closeCancelDialog() {
  selectedAppointmentCard = null;
  cancelDialog.classList.add("is-hidden");
  document.body.classList.remove("has-dialog");
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
}

// Muestra el estado vacio cuando ya no quedan citas.
function updateAppointmentsEmptyState() {
  const activeCards = appointmentList.querySelectorAll(".appointment-card");
  appointmentsEmpty.classList.toggle("is-hidden", activeCards.length > 0);
}

async function postJson(url, payload) {
  return requestJson(url, {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

async function putJson(url, payload = {}) {
  return requestJson(url, {
    method: "PUT",
    body: JSON.stringify(payload)
  });
}

async function getJson(url) {
  return requestJson(url);
}

async function requestJson(url, options = {}) {
  const token = localStorage.getItem("authToken");
  const response = await fetch(url, {
    ...options,
    headers: {
      "Content-Type": "application/json",
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

function getActivePatientId() {
  return Number(localStorage.getItem("pacienteId") || "0");
}

function resetPatientSession() {
  localStorage.removeItem("pacienteId");
  localStorage.removeItem("pacienteNombre");
  localStorage.removeItem("authToken");
  localStorage.removeItem("adminSession");
}

function handlePatientLoadError(error) {
  if (error?.status === 401 || error?.status === 403) {
    resetPatientSession();
    showLogin();
    loginStatus.textContent = "Tu sesion expiro o no corresponde a este paciente. Inicia sesion nuevamente.";
    loginStatus.classList.add("is-error");
    return true;
  }

  return false;
}

function formatDateParts(dateValue) {
  const date = new Date(`${dateValue}T00:00:00`);
  const monthYear = date.toLocaleDateString("es-MX", { month: "long", year: "numeric" });

  return {
    day: date.toLocaleDateString("es-MX", { day: "2-digit" }),
    monthYear: monthYear.charAt(0).toUpperCase() + monthYear.slice(1)
  };
}

function formatTime(timeValue) {
  const [hours = "00", minutes = "00"] = String(timeValue).split(":");
  const date = new Date();
  date.setHours(Number(hours), Number(minutes), 0, 0);

  return date.toLocaleTimeString("es-MX", {
    hour: "2-digit",
    minute: "2-digit"
  });
}

function formatShortDate(dateTimeValue) {
  if (!dateTimeValue) return "Sin notas clinicas";

  return new Date(dateTimeValue).toLocaleDateString("es-MX", {
    day: "2-digit",
    month: "short",
    year: "numeric"
  });
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

async function registerPatient(payload) {
  return postJson(REGISTER_ENDPOINT, payload);
}

async function login(payload) {
  return postJson(AUTH_ENDPOINT, payload);
}

async function loginAdmin(payload) {
  return postJson(ADMIN_AUTH_ENDPOINT, payload);
}

async function createAppointment(payload) {
  return postJson(APPOINTMENT_ENDPOINT, payload);
}

async function loadPatientAppointments() {
  const patientId = getActivePatientId();

  if (!patientId) {
    appointmentList.innerHTML = "";
    updateNextAppointment(null);
    updateAppointmentsEmptyState();
    return;
  }

  try {
    const appointments = await getJson(PATIENT_APPOINTMENTS_ENDPOINT(patientId));
    appointmentList.innerHTML = "";
    appointmentsEmpty.textContent = "No tienes citas proximas registradas.";
    appointments.forEach((appointment) => {
      appointmentList.appendChild(createAppointmentCard(appointment));
    });

    updateNextAppointment(appointments[0] || null);
    updateAppointmentsEmptyState();
  } catch (error) {
    if (handlePatientLoadError(error)) return;

    appointmentList.innerHTML = "";
    appointmentsEmpty.textContent = "No fue posible cargar tus citas. Revisa la conexion con el servidor.";
    updateNextAppointment(null);
    updateAppointmentsEmptyState();
  }
}

async function loadPatientRecord() {
  const patientId = getActivePatientId();
  if (!patientId) return;

  try {
    const record = await getJson(PATIENT_RECORD_ENDPOINT(patientId));
    const patientName = record.paciente?.nombre || localStorage.getItem("pacienteNombre") || "";
    const allergies = record.expediente?.alergias || "No registradas";
    const bloodType = record.expediente?.grupoSanguineo || "No registrado";
    const lastNote = record.notasEvolucion?.[0]?.fechaHora || null;

    if (patientName) {
      localStorage.setItem("pacienteNombre", patientName);
      document.querySelector("#portal-title").textContent = `Hola, ${patientName}`;
    }

    recordBloodType.textContent = bloodType;
    recordAllergies.textContent = allergies;
    recordLastVisit.textContent = formatShortDate(lastNote);
  } catch (error) {
    if (handlePatientLoadError(error)) return;

    recordBloodType.textContent = "No registrado";
    recordAllergies.textContent = "No registradas";
    recordLastVisit.textContent = "Sin consultas";
  }
}

async function loadPatientDashboard() {
  const patientName = localStorage.getItem("pacienteNombre");

  if (patientName) {
    document.querySelector("#portal-title").textContent = `Hola, ${patientName}`;
  }

  loadPatientAppointments();
  loadPatientRecord();
}

function createAppointmentCard(appointment) {
  const dateParts = formatDateParts(appointment.fecha);
  const card = document.createElement("article");
  card.className = "appointment-card";
  card.dataset.appointmentId = appointment.id;

  card.innerHTML = `
    <div class="appointment-card__date">
      <span>${dateParts.day}</span>
      <div>
        <strong>${dateParts.monthYear}</strong>
        <small>${formatTime(appointment.hora)}</small>
      </div>
    </div>
    <div class="appointment-card__body">
      <div>
        <h3>${escapeHtml(appointment.tratamiento || "Consulta dental")}</h3>
        <span class="status-pill">${escapeHtml(appointment.estatus || "CONFIRMADA")}</span>
      </div>
      <p>${escapeHtml(appointment.odontologoNombre || "Por asignar")}</p>
    </div>
    <div class="appointment-card__actions">
      <a class="button button--secondary" href="agendar.html?reprogramar=${encodeURIComponent(appointment.id)}">Reprogramar</a>
      <button class="button button--danger" type="button" data-cancel-appointment>Cancelar Cita</button>
    </div>
  `;

  return card;
}

function updateNextAppointment(appointment) {
  if (!appointment) {
    nextAppointmentStatus.textContent = "Sin cita";
    nextAppointmentDay.textContent = "--";
    nextAppointmentMonth.textContent = "Sin cita programada";
    nextAppointmentTime.textContent = "--:--";
    nextAppointmentTreatment.textContent = "Agenda una nueva cita para verla aqui.";
    nextAppointmentDoctor.textContent = "Por asignar";
    return;
  }

  const dateParts = formatDateParts(appointment.fecha);
  nextAppointmentStatus.textContent = appointment.estatus || "CONFIRMADA";
  nextAppointmentDay.textContent = dateParts.day;
  nextAppointmentMonth.textContent = dateParts.monthYear;
  nextAppointmentTime.textContent = formatTime(appointment.hora);
  nextAppointmentTreatment.textContent = appointment.tratamiento || "Consulta dental";
  nextAppointmentDoctor.textContent = appointment.odontologoNombre || "Por asignar";
}

function getAdminRedirect(role) {
  if (role === "ODONTOLOGO") return "odontologo.html";
  return "recepcion.html";
}

function saveAdminSession(response) {
  const user = response.usuario;

  localStorage.setItem("adminSession", JSON.stringify({
    id: user.id,
    nombre: user.nombre,
    email: user.email,
    rol: user.rol
  }));
  localStorage.setItem("authToken", response.token);
}

function saveActivePatient(response) {
  const patient = response.paciente || response.patient || null;
  const patientId = patient?.id || response.pacienteId || response.id;

  if (patientId) {
    localStorage.setItem("pacienteId", String(patientId));
  }

  if (patient?.nombre) {
    localStorage.setItem("pacienteNombre", patient.nombre);
  }

  if (response.token) {
    localStorage.setItem("authToken", response.token);
  }

  return patientId;
}

if (registerForm) {
  registerForm.addEventListener("submit", async (event) => {
    event.preventDefault();

    const data = new FormData(registerForm);
    const payload = {
      nombre: data.get("nombre")?.trim() || "",
      telefono: data.get("telefono")?.trim() || "",
      email: data.get("email")?.trim() || "",
      password: data.get("password") || ""
    };

    try {
      const response = await registerPatient(payload);
      saveActivePatient(response);
      localStorage.setItem("pacienteNombre", payload.nombre);
      alert("Paciente registrado correctamente.");
      registerForm.reset();
      showPortal();
      loadPatientDashboard();
    } catch (error) {
      alert(error.message || "No fue posible registrar el paciente.");
    }
  });
}

loginForm.addEventListener("input", (event) => {
  if (event.target.matches("input")) {
    validateField(event.target);
  }
});

togglePasswordButton.addEventListener("click", () => {
  const shouldShowPassword = passwordInput.type === "password";
  passwordInput.type = shouldShowPassword ? "text" : "password";
  togglePasswordButton.textContent = shouldShowPassword ? "Ocultar" : "Ver";
  togglePasswordButton.setAttribute("aria-label", shouldShowPassword ? "Ocultar contrasena" : "Mostrar contrasena");
});

loginForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  loginStatus.classList.remove("is-error");
  loginStatus.textContent = "";

  if (!validateForm(loginForm)) {
    loginStatus.textContent = "Revisa tus datos para continuar.";
    loginStatus.classList.add("is-error");
    return;
  }

  const submitButton = loginForm.querySelector("button[type='submit']");
  const data = new FormData(loginForm);
  const payload = {
    email: data.get("email").trim(),
    password: data.get("password")
  };

  submitButton.disabled = true;
  submitButton.textContent = "Validando...";

  try {
    const adminResponse = await loginAdmin(payload);
    saveAdminSession(adminResponse);
    syncRememberedEmail(payload.email);
    window.location.href = getAdminRedirect(adminResponse.usuario.rol);
  } catch (adminError) {
    if (adminError.status !== 401) {
      loginStatus.textContent = "No fue posible conectar con el servidor. Revisa la direccion/IP del sistema.";
      loginStatus.classList.add("is-error");
      submitButton.disabled = false;
      submitButton.textContent = "Entrar";
      return;
    }

    try {
      const patientResponse = await login(payload);
      localStorage.removeItem("adminSession");
      saveActivePatient(patientResponse);
      syncRememberedEmail(payload.email);
      showPortal();
      loadPatientDashboard();
    } catch (patientError) {
      loginStatus.textContent = patientError.status === 401
        ? "Credenciales invalidas."
        : "No fue posible conectar con el servidor. Revisa la direccion/IP del sistema.";
      loginStatus.classList.add("is-error");
    }
  } finally {
    submitButton.disabled = false;
    submitButton.textContent = "Entrar";
  }
});

logoutButton.addEventListener("click", () => {
  resetPatientSession();
  appointmentList.innerHTML = "";
  showLogin();
});

changePasswordButton.addEventListener("click", openPasswordDialog);

passwordDialog.addEventListener("click", (event) => {
  if (event.target.matches("[data-close-password-dialog]")) {
    closePasswordDialog();
  }
});

passwordForm.addEventListener("submit", async (event) => {
  event.preventDefault();

  const data = new FormData(passwordForm);
  const payload = {
    passwordActual: data.get("passwordActual"),
    passwordNueva: data.get("passwordNueva")
  };

  if (!payload.passwordActual || payload.passwordNueva.length < 6) {
    passwordStatus.textContent = "Escribe tu contraseña actual y una nueva de al menos 8 caracteres, una letra y un numero.";
    passwordStatus.classList.add("is-error");
    return;
  }

  passwordStatus.classList.remove("is-error");
  passwordStatus.textContent = "Guardando contraseña...";

  try {
    await putJson(CHANGE_PATIENT_PASSWORD_ENDPOINT, payload);
    passwordStatus.textContent = "Contraseña actualizada correctamente.";
    window.setTimeout(closePasswordDialog, 700);
  } catch (error) {
    passwordStatus.textContent = error.message || "No fue posible actualizar la contraseña.";
    passwordStatus.classList.add("is-error");
  }
});

// RF4: abre la alerta al presionar "Cancelar Cita".
appointmentList.addEventListener("click", (event) => {
  const cancelButton = event.target.closest("[data-cancel-appointment]");
  if (!cancelButton) return;

  const card = cancelButton.closest(".appointment-card");
  openCancelDialog(card);
});

cancelDialog.addEventListener("click", (event) => {
  if (event.target.matches("[data-close-dialog]")) {
    closeCancelDialog();
  }
});

document.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && !cancelDialog.classList.contains("is-hidden")) {
    closeCancelDialog();
  }
});

// Al confirmar, la cancelacion se guarda en MariaDB y despues se actualiza la lista.
confirmCancelButton.addEventListener("click", async () => {
  if (!selectedAppointmentCard) return;

  const cardToRemove = selectedAppointmentCard;
  const patientId = getActivePatientId();
  const appointmentId = Number(cardToRemove.dataset.appointmentId);

  confirmCancelButton.disabled = true;
  confirmCancelButton.textContent = "Cancelando...";

  try {
    await putJson(CANCEL_APPOINTMENT_ENDPOINT(patientId, appointmentId));
    cardToRemove.classList.add("is-removing");

    window.setTimeout(() => {
      cardToRemove.remove();
      closeCancelDialog();
      updateAppointmentsEmptyState();
      loadPatientAppointments();
    }, 180);
  } catch (error) {
    alert(error.status === 409
      ? "La cita solo puede cancelarse con al menos 24 horas de anticipacion."
      : error.message || "No fue posible cancelar la cita.");
  } finally {
    confirmCancelButton.disabled = false;
    confirmCancelButton.textContent = "Si, cancelar";
  }
});

if (getActivePatientId()) {
  showPortal();
  loadPatientDashboard();
} else if (urlParams.get("acceso") === "odontologo") {
  loginStatus.textContent = "Ingresa con una cuenta de odontologo o administrador.";
}
