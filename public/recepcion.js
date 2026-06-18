const agendaTable = document.querySelector(".appointments-table");
const manualModal = document.querySelector("#manual-appointment-modal");
const openManualButton = document.querySelector("#open-manual-appointment");
const manualForm = document.querySelector("#manual-appointment-form");
const manualStatus = document.querySelector("#manual-status");

const today = new Date();
const manualDateInput = document.querySelector("#manual-date");

if (manualDateInput) {
  manualDateInput.min = today.toISOString().split("T")[0];
}

const statusConfig = {
  confirmed: {
    label: "Confirmada",
    className: "status-confirmed"
  },
  attended: {
    label: "Asistio",
    className: "status-attended"
  },
  missed: {
    label: "No Asistio",
    className: "status-missed"
  },
  cancelled: {
    label: "Cancelada",
    className: "status-cancelled"
  }
};

// RF7: cambia el estatus visual de una cita desde los botones de accion.
function updateAppointmentStatus(row, status) {
  const badge = row.querySelector("[data-status-badge]");
  const config = statusConfig[status];

  row.classList.remove("status-confirmed", "status-attended", "status-missed", "status-cancelled");
  row.classList.add(config.className);
  row.dataset.status = status;
  badge.textContent = config.label;
}

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

function showFieldError(field, message) {
  const error = document.querySelector(`[data-error-for="${field.id}"]`);
  const row = field.closest(".form-row");

  if (row) row.classList.toggle("has-error", Boolean(message));
  if (error) error.textContent = message || "";
}

function validateManualField(field) {
  const value = field.value.trim();
  let message = "";

  if (!value) {
    message = "Campo requerido.";
  }

  if (field.name === "phone" && value && !/^\d{10}$/.test(value.replace(/\D/g, ""))) {
    message = "Escribe un telefono de 10 digitos.";
  }

  showFieldError(field, message);
  return message === "";
}

function buildManualAppointmentPayload(form) {
  const data = new FormData(form);

  return {
    patientName: data.get("name").trim(),
    phone: data.get("phone").replace(/\D/g, ""),
    appointmentDate: data.get("date"),
    appointmentTime: data.get("time"),
    status: "CONFIRMED",
    source: "RECEPTION_MANUAL"
  };
}

function createAppointmentRow(payload) {
  const row = document.createElement("article");
  row.className = "appointment-row status-confirmed";
  row.setAttribute("role", "row");
  row.dataset.status = "confirmed";
  row.innerHTML = `
    <span class="appointment-row__time" role="cell">${payload.appointmentTime}</span>
    <span role="cell">${payload.patientName}</span>
    <span role="cell">Consulta presencial</span>
    <span role="cell"><span class="reception-badge" data-status-badge>Confirmada</span></span>
    <div class="status-actions" role="cell">
      <button type="button" data-status="confirmed">Confirmada</button>
      <button type="button" data-status="attended">Asistio</button>
      <button type="button" data-status="missed">No Asistio</button>
      <button type="button" data-status="cancelled">Cancelada</button>
    </div>
  `;

  agendaTable.append(row);
}

agendaTable.addEventListener("click", (event) => {
  const statusButton = event.target.closest("button[data-status]");
  if (!statusButton) return;

  const row = statusButton.closest(".appointment-row");
  if (!row) return;

  updateAppointmentStatus(row, statusButton.dataset.status);
});

openManualButton.addEventListener("click", openManualModal);

manualModal.addEventListener("click", (event) => {
  if (event.target.matches("[data-close-manual-modal]")) {
    closeManualModal();
  }
});

document.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && !manualModal.classList.contains("is-hidden")) {
    closeManualModal();
  }
});

manualForm.addEventListener("input", (event) => {
  if (event.target.matches("input")) {
    validateManualField(event.target);
  }
});

// RF6: valida el formulario y deja listo el JSON para el backend Kotlin.
manualForm.addEventListener("submit", (event) => {
  event.preventDefault();

  const fields = [...manualForm.elements].filter((field) => field.name);
  const isValid = fields.map(validateManualField).every(Boolean);

  if (!isValid) {
    manualStatus.textContent = "Revisa los campos marcados.";
    manualStatus.classList.add("is-error");
    return;
  }

  const payload = buildManualAppointmentPayload(manualForm);
  console.log("JSON cita presencial para API Kotlin:", payload);
  createAppointmentRow(payload);

  manualStatus.classList.remove("is-error");
  manualStatus.textContent = "Cita presencial registrada en maqueta.";
});
