const loginView = document.querySelector("#login-view");
const portalView = document.querySelector("#portal-view");
const loginForm = document.querySelector("#login-form");
const registerForm = document.querySelector("#register-form");
const requestForm = document.querySelector("#appointment-request-form");
const loginStatus = document.querySelector("#login-status");
const requestStatus = document.querySelector("#request-status");
const logoutButton = document.querySelector("#logout-button");
const passwordInput = document.querySelector("#password");
const togglePasswordButton = document.querySelector("#toggle-password");
const appointmentList = document.querySelector("#appointment-list");
const appointmentsEmpty = document.querySelector("#appointments-empty");
const cancelDialog = document.querySelector("#cancel-dialog");
const confirmCancelButton = document.querySelector("#confirm-cancel-button");

const API_BASE_URL = "http://localhost:8080";
const REGISTER_ENDPOINT = `${API_BASE_URL}/api/pacientes`;
const AUTH_ENDPOINT = `${API_BASE_URL}/api/login`;
const APPOINTMENT_ENDPOINT = `${API_BASE_URL}/api/citas`;

let selectedAppointmentCard = null;

// Fecha base para evitar solicitudes en dias pasados.
const today = new Date();
today.setHours(0, 0, 0, 0);
document.querySelector("#date").min = today.toISOString().split("T")[0];

// Validaciones simples del formulario de login y solicitud.
const validators = {
  email: (value) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value) || "Escribe un correo valido.",
  password: (value) => value.trim().length >= 6 || "La contrasena debe tener al menos 6 caracteres.",
  service: (value) => value !== "" || "Selecciona un servicio.",
  date: (value) => {
    if (!value) return "Selecciona una fecha.";
    const selectedDate = new Date(`${value}T00:00:00`);
    return selectedDate >= today || "Selecciona una fecha futura.";
  }
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
  loginStatus.textContent = "";
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

// Muestra el estado vacio cuando ya no quedan citas.
function updateAppointmentsEmptyState() {
  const activeCards = appointmentList.querySelectorAll(".appointment-card");
  appointmentsEmpty.classList.toggle("is-hidden", activeCards.length > 0);
}

async function postJson(url, payload) {
  const response = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(payload)
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

async function registerPatient(payload) {
  return postJson(REGISTER_ENDPOINT, payload);
}

async function login(payload) {
  return postJson(AUTH_ENDPOINT, payload);
}

async function createAppointment(payload) {
  return postJson(APPOINTMENT_ENDPOINT, payload);
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
      alert("Paciente registrado correctamente.");
      registerForm.reset();
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

requestForm.addEventListener("input", (event) => {
  if (event.target.matches("input, select, textarea")) {
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
    const response = await login(payload);
    saveActivePatient(response);
    showPortal();
  } catch (error) {
    loginStatus.textContent = error.message || "No fue posible iniciar sesion.";
    loginStatus.classList.add("is-error");
  } finally {
    submitButton.disabled = false;
    submitButton.textContent = "Entrar al portal";
  }
});

// Solicitud rapida de cita desde el dashboard.
requestForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  requestStatus.classList.remove("is-error");
  requestStatus.textContent = "";

  if (!validateForm(requestForm)) {
    requestStatus.textContent = "Completa los campos marcados.";
    requestStatus.classList.add("is-error");
    return;
  }

  const data = new FormData(requestForm);
  const patientId = Number(localStorage.getItem("pacienteId") || "1");
  const payload = {
    pacienteId: patientId,
    fechaHora: `${data.get("date")}T09:00`,
    motivo: `${data.get("service")}${data.get("notes").trim() ? ` - ${data.get("notes").trim()}` : ""}`
  };

  try {
    await createAppointment(payload);
    requestStatus.textContent = "Cita registrada correctamente.";
    requestForm.reset();
  } catch (error) {
    requestStatus.classList.add("is-error");
    requestStatus.textContent = error.status === 409
      ? "Ese horario ya fue ocupado. Selecciona otro horario."
      : error.message || "No fue posible registrar la cita.";
  }
});

logoutButton.addEventListener("click", showLogin);

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

// Al confirmar, se oculta la tarjeta de la lista.
confirmCancelButton.addEventListener("click", () => {
  if (!selectedAppointmentCard) return;

  const cardToRemove = selectedAppointmentCard;
  cardToRemove.classList.add("is-removing");

  window.setTimeout(() => {
    cardToRemove.remove();
    closeCancelDialog();
    updateAppointmentsEmptyState();
  }, 180);
});
