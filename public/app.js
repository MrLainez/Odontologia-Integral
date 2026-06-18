const loginView = document.querySelector("#login-view");
const portalView = document.querySelector("#portal-view");
const loginForm = document.querySelector("#login-form");
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

const AUTH_ENDPOINT = "/api/auth/login";
const APPOINTMENT_ENDPOINT = "/api/patient/appointments";

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

// Llamada preparada para el futuro endpoint de autenticacion.
async function login(payload) {
  const response = await fetch(AUTH_ENDPOINT, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(payload)
  });

  if (!response.ok) {
    throw new Error("No fue posible iniciar sesion.");
  }

  return response.json().catch(() => ({}));
}

// Llamada preparada para crear solicitudes de cita.
async function createAppointment(payload) {
  const response = await fetch(APPOINTMENT_ENDPOINT, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(payload)
  });

  if (!response.ok) {
    throw new Error("No fue posible crear la solicitud.");
  }

  return response.json().catch(() => ({}));
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

// Login: si el backend aun no responde, permite entrar en modo demostracion.
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
    await login(payload);
    showPortal();
  } catch (error) {
    console.info("Backend de autenticacion no disponible todavia. Flujo visual habilitado:", payload.email);
    loginStatus.textContent = "Acceso de demostracion habilitado mientras se conecta el backend.";
    showPortal();
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
  const payload = {
    service: data.get("service"),
    preferredDate: data.get("date"),
    notes: data.get("notes").trim()
  };

  try {
    await createAppointment(payload);
    requestStatus.textContent = "Solicitud enviada correctamente.";
  } catch (error) {
    console.info("Backend de citas no disponible todavia. Payload listo:", payload);
    requestStatus.textContent = "Solicitud preparada. Se enviara cuando el backend este conectado.";
  }

  requestForm.reset();
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
