const calendarGrid = document.querySelector("#calendar-grid");
const currentMonthLabel = document.querySelector("#current-month");
const timeSlots = document.querySelector("#time-slots");
const summaryDate = document.querySelector("#summary-date");
const summaryTime = document.querySelector("#summary-time");
const summaryStatus = document.querySelector("#summary-status");
const confirmSlotButton = document.querySelector("#confirm-slot");
const scheduleStatus = document.querySelector("#schedule-status");
const confirmationToast = document.querySelector("#confirmation-toast");
const toastMessage = document.querySelector("#toast-message");
const scheduleTitle = document.querySelector("#schedule-title");
const previousMonthButton = document.querySelector("#previous-month");
const nextMonthButton = document.querySelector("#next-month");
const preferredDentistSelect = document.querySelector("#preferred-dentist");
const summaryDentist = document.querySelector("#summary-dentist");
const appointmentReasonInput = document.querySelector("#appointment-reason");
const API_BASE_URL = window.location.origin;
const APPOINTMENT_ENDPOINT = `${API_BASE_URL}/api/citas`;
const APPOINTMENT_REQUEST_ENDPOINT = `${API_BASE_URL}/api/solicitudes-cita`;
const AVAILABILITY_ENDPOINT = `${API_BASE_URL}/api/citas/disponibilidad`;
const BUSINESS_HOURS_ENDPOINT = `${API_BASE_URL}/api/horarios-atencion`;
const DENTISTS_ENDPOINT = `${API_BASE_URL}/api/odontologos`;
const monthNames = [
  "Enero",
  "Febrero",
  "Marzo",
  "Abril",
  "Mayo",
  "Junio",
  "Julio",
  "Agosto",
  "Septiembre",
  "Octubre",
  "Noviembre",
  "Diciembre"
];

const today = new Date();
today.setHours(0, 0, 0, 0);
const firstAvailableMonth = new Date(today.getFullYear(), today.getMonth(), 1);

let selectedDayKey = "";
let selectedTime = "";
let selectedTimeValue = "";
let selectedFormattedDate = "";
let toastTimer = 0;
let visibleMonthDate = new Date(today.getFullYear(), today.getMonth(), 1);
let activeWeekDays = new Set([1, 2, 3, 4, 5, 6]);
const params = new URLSearchParams(window.location.search);
const rescheduleAppointmentId = Number(params.get("reprogramar") || "0");
const isRescheduleMode = rescheduleAppointmentId > 0;

if (!getActivePatientId() || !localStorage.getItem("authToken")) {
  window.location.href = "index.html";
}

if (isRescheduleMode) {
  scheduleTitle.textContent = "Reprogramar Cita";
  confirmSlotButton.textContent = "Confirmar Reprogramacion";
  summaryStatus.textContent = "Reprogramacion";
}

function getActivePatientId() {
  return Number(localStorage.getItem("pacienteId") || "0");
}

// Convierte una fecha a formato YYYY-MM-DD para enviarla al backend.
function getDateKey(date) {
  return date.toISOString().split("T")[0];
}

// Define si un dia se puede seleccionar.
function isAvailableDay(date) {
  const dayOfWeek = date.getDay();
  const isoDayOfWeek = dayOfWeek === 0 ? 7 : dayOfWeek;
  const isPast = date < today;

  return !isPast && activeWeekDays.has(isoDayOfWeek);
}

// Formato visible para el paciente.
function formatDate(date) {
  return `${date.getDate()} de ${monthNames[date.getMonth()]} ${date.getFullYear()}`;
}

function resetSelection() {
  selectedDayKey = "";
  selectedTime = "";
  selectedTimeValue = "";
  selectedFormattedDate = "";
  summaryDate.textContent = "Sin seleccionar";
  summaryTime.textContent = "Sin seleccionar";
  summaryStatus.textContent = "Pendiente";
  scheduleStatus.textContent = "";
  timeSlots.innerHTML = "";
  confirmSlotButton.disabled = true;
  confirmSlotButton.classList.remove("is-visible");
}

function updateMonthNavigation() {
  const isCurrentMonth = visibleMonthDate.getFullYear() === firstAvailableMonth.getFullYear()
    && visibleMonthDate.getMonth() === firstAvailableMonth.getMonth();

  previousMonthButton.disabled = isCurrentMonth;
}

function changeVisibleMonth(offset) {
  const nextMonth = new Date(visibleMonthDate.getFullYear(), visibleMonthDate.getMonth() + offset, 1);

  if (nextMonth < firstAvailableMonth) return;

  visibleMonthDate = nextMonth;
  resetSelection();
  renderCalendar();
}

// RF2: genera dinamicamente el calendario del mes visible.
function renderCalendar() {
  const year = visibleMonthDate.getFullYear();
  const month = visibleMonthDate.getMonth();
  const firstDay = new Date(year, month, 1);
  const totalDays = new Date(year, month + 1, 0).getDate();
  const startOffset = (firstDay.getDay() + 6) % 7;

  currentMonthLabel.textContent = `${monthNames[month]} ${year}`;
  calendarGrid.innerHTML = "";
  updateMonthNavigation();

  for (let index = 0; index < startOffset; index += 1) {
    const emptyCell = document.createElement("span");
    emptyCell.className = "calendar-day calendar-day--empty";
    calendarGrid.append(emptyCell);
  }

  for (let day = 1; day <= totalDays; day += 1) {
    const date = new Date(year, month, day);
    const dateKey = getDateKey(date);
    const available = isAvailableDay(date);
    const dayButton = document.createElement("button");

    dayButton.type = "button";
    dayButton.className = "calendar-day";
    dayButton.textContent = day;
    dayButton.dataset.date = dateKey;
    dayButton.disabled = !available;
    dayButton.setAttribute("aria-label", `${formatDate(date)} ${available ? "disponible" : "no disponible"}`);

    if (dateKey === getDateKey(today)) {
      dayButton.classList.add("calendar-day--today");
    }

    if (!available) {
      dayButton.classList.add("calendar-day--disabled");
    }

    dayButton.addEventListener("click", () => selectDay(date, dayButton));
    calendarGrid.append(dayButton);
  }
}

// Selecciona un dia y muestra sus horarios.
function selectDay(date, dayButton) {
  selectedDayKey = getDateKey(date);
  selectedTime = "";
  selectedTimeValue = "";
  selectedFormattedDate = formatDate(date);

  document.querySelectorAll(".calendar-day.is-selected").forEach((button) => {
    button.classList.remove("is-selected");
  });

  dayButton.classList.add("is-selected");
  summaryDate.textContent = selectedFormattedDate;
  summaryTime.textContent = "Sin seleccionar";
  summaryStatus.textContent = "Elige horario";
  confirmSlotButton.disabled = true;
  confirmSlotButton.classList.remove("is-visible");
  scheduleStatus.textContent = "";

  renderTimeSlots(date);
}

// Crea los chips de horarios usando disponibilidad real desde MariaDB.
async function renderTimeSlots(date) {
  timeSlots.innerHTML = `
    <div class="time-slots__header">
      <strong>Horarios disponibles</strong>
      <span>${formatDate(date)}</span>
    </div>
    <p class="form-status">Consultando disponibilidad...</p>
  `;

  try {
    const availability = await loadAvailability(getDateKey(date));
    renderAvailabilityChips(availability);
  } catch (error) {
    timeSlots.innerHTML += `<p class="form-status is-error">No fue posible cargar disponibilidad real.</p>`;
  }
}

async function loadAvailability(dateKey) {
  const url = new URL(AVAILABILITY_ENDPOINT);
  url.searchParams.set("fecha", dateKey);
  if (preferredDentistSelect?.value) {
    url.searchParams.set("odontologoId", preferredDentistSelect.value);
  }
  if (isRescheduleMode) {
    url.searchParams.set("excluirCitaId", String(rescheduleAppointmentId));
  }

  const token = localStorage.getItem("authToken");
  const response = await fetch(url, {
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    }
  });
  const data = await response.json().catch(() => ({}));

  if (!response.ok) {
    const error = new Error(data.error || data.mensaje || "No fue posible consultar disponibilidad.");
    error.status = response.status;
    throw error;
  }

  return data;
}

async function loadBusinessHours() {
  const response = await fetch(BUSINESS_HOURS_ENDPOINT);
  const schedules = await response.json().catch(() => []);

  if (!response.ok) return;

  activeWeekDays = new Set(
    schedules
      .filter((schedule) => schedule.activo)
      .map((schedule) => Number(schedule.diaSemana))
  );
}

async function loadDentists() {
  const response = await fetch(DENTISTS_ENDPOINT);
  const dentists = await response.json().catch(() => []);

  if (!response.ok || !preferredDentistSelect) return;

  dentists.forEach((dentist) => {
    const option = document.createElement("option");
    option.value = dentist.id;
    option.textContent = dentist.nombre;
    preferredDentistSelect.append(option);
  });
}

function renderAvailabilityChips(availability) {
  timeSlots.querySelector(".form-status")?.remove();
  const slots = availability.horarios || [];
  const chipList = document.createElement("div");
  chipList.className = "chip-list";

  if (availability.cerrado) {
    const closedMessage = document.createElement("p");
    closedMessage.className = "form-status";
    closedMessage.textContent = availability.motivo
      ? `Consultorio cerrado: ${availability.motivo}.`
      : "Consultorio cerrado en esta fecha.";
    timeSlots.append(closedMessage);
    return;
  }

  if (!slots.length) {
    const emptyMessage = document.createElement("p");
    emptyMessage.className = "form-status";
    emptyMessage.textContent = "No hay horarios de atencion configurados para este dia.";
    timeSlots.append(emptyMessage);
    return;
  }

  slots.forEach((slot) => {
    const time = slot.hora;
    const timeValue = slot.valor || timeToTwentyFourHourValue(time);
    const isBusy = !slot.disponible;
    const chip = document.createElement("button");

    chip.type = "button";
    chip.className = "time-chip";
    chip.textContent = time;
    chip.disabled = isBusy;

    if (isBusy) {
      chip.classList.add("time-chip--disabled");
      chip.setAttribute("aria-label", `${time} ocupado`);
    } else {
      chip.setAttribute("aria-label", `${time} disponible`);
      chip.addEventListener("click", () => selectTime(chip, time, timeValue));
    }

    chipList.append(chip);
  });

  timeSlots.append(chipList);
}

// Guarda el horario seleccionado y habilita el boton flotante.
function selectTime(chip, time, timeValue) {
  selectedTime = time;
  selectedTimeValue = timeValue;

  document.querySelectorAll(".time-chip.is-selected").forEach((button) => {
    button.classList.remove("is-selected");
  });

  chip.classList.add("is-selected");
  summaryTime.textContent = time;
  summaryStatus.textContent = "Listo";
  confirmSlotButton.disabled = false;
  confirmSlotButton.classList.add("is-visible");
  scheduleStatus.textContent = "";
}

// Muestra una notificacion temporal de confirmacion.
function showConfirmationToast(message) {
  toastMessage.textContent = message;
  confirmationToast.classList.add("is-visible");
  window.clearTimeout(toastTimer);

  toastTimer = window.setTimeout(() => {
    confirmationToast.classList.remove("is-visible");
  }, 4200);
}

function timeToTwentyFourHourValue(time) {
  const [hourMinute, period] = time.split(" ");
  const [rawHour, minute] = hourMinute.split(":").map(Number);
  let hour = rawHour;

  if (period === "PM" && hour !== 12) hour += 12;
  if (period === "AM" && hour === 12) hour = 0;

  return `${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}`;
}

function buildAppointmentPayload() {
  const motivo = appointmentReasonInput?.value.trim() || "Solicitud de cita";

  return {
    pacienteId: getActivePatientId(),
    odontologoId: Number(preferredDentistSelect?.value || "0"),
    fechaHora: `${selectedDayKey}T${selectedTimeValue || timeToTwentyFourHourValue(selectedTime)}`,
    motivo
  };
}

async function saveAppointment(payload) {
  const url = isRescheduleMode
    ? `${APPOINTMENT_ENDPOINT}/${rescheduleAppointmentId}/reprogramar`
    : APPOINTMENT_REQUEST_ENDPOINT;
  const response = await fetch(url, {
    method: isRescheduleMode ? "PUT" : "POST",
    headers: {
      "Content-Type": "application/json",
      ...(localStorage.getItem("authToken") ? { Authorization: `Bearer ${localStorage.getItem("authToken")}` } : {})
    },
    body: JSON.stringify(payload)
  });
  const data = await response.json().catch(() => ({}));

  if (!response.ok) {
    const message = data.error || data.mensaje || "No fue posible confirmar la cita.";
    const error = new Error(message);
    error.status = response.status;
    throw error;
  }

  return data;
}

// RF3: confirma la cita contra la API y muestra el toast.
confirmSlotButton.addEventListener("click", async () => {
  if (!selectedDayKey || !selectedTime) return;
  if (!getActivePatientId()) {
    alert("Inicia sesion nuevamente para agendar.");
    window.location.href = "index.html";
    return;
  }

  const appointmentPayload = buildAppointmentPayload();
  const confirmationMessage = isRescheduleMode
    ? `Cita reprogramada para el ${selectedFormattedDate} a las ${selectedTime}.`
    : `Solicitud enviada para el ${selectedFormattedDate} a las ${selectedTime}. Recepcion confirmara la cita.`;

  confirmSlotButton.disabled = true;
  scheduleStatus.textContent = isRescheduleMode ? "Confirmando cambio..." : "Enviando solicitud...";

  try {
    await saveAppointment(appointmentPayload);
    scheduleStatus.textContent = confirmationMessage;
    showConfirmationToast(confirmationMessage);
    await renderTimeSlots(new Date(`${selectedDayKey}T00:00:00`));
  } catch (error) {
    scheduleStatus.textContent = error.status === 409
      ? error.message || "Ese horario ya fue ocupado. Selecciona otro horario."
      : error.message || "No fue posible confirmar la cita.";
    alert(scheduleStatus.textContent);
    confirmSlotButton.disabled = false;
  }
});

previousMonthButton.addEventListener("click", () => changeVisibleMonth(-1));
nextMonthButton.addEventListener("click", () => changeVisibleMonth(1));
preferredDentistSelect.addEventListener("change", () => {
  summaryDentist.textContent = preferredDentistSelect.selectedOptions[0]?.textContent || "Sin preferencia";
  if (selectedDayKey) {
    confirmSlotButton.disabled = true;
    confirmSlotButton.classList.remove("is-visible");
    summaryTime.textContent = "Sin seleccionar";
    selectedTime = "";
    selectedTimeValue = "";
    renderTimeSlots(new Date(`${selectedDayKey}T00:00:00`));
  }
});

Promise.all([loadBusinessHours(), loadDentists()])
  .catch(() => {})
  .finally(renderCalendar);
