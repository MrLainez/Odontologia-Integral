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

const availableTimes = ["09:00 AM", "10:30 AM", "12:00 PM", "04:00 PM", "05:30 PM"];
const APPOINTMENT_ENDPOINT = "http://localhost:8080/api/citas";
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

let selectedDayKey = "";
let selectedTime = "";
let selectedFormattedDate = "";
let toastTimer = 0;

function getActivePatientId() {
  return Number(localStorage.getItem("pacienteId") || "1");
}

// Convierte una fecha a formato YYYY-MM-DD para enviarla al backend.
function getDateKey(date) {
  return date.toISOString().split("T")[0];
}

// Simula horarios ocupados segun el dia seleccionado.
function getBusySlots(day) {
  const occupiedByDay = {
    2: ["10:30 AM", "04:00 PM"],
    4: ["09:00 AM", "12:00 PM"],
    5: ["05:30 PM"]
  };

  return occupiedByDay[day % 6] || [];
}

// Define si un dia se puede seleccionar.
function isAvailableDay(date) {
  const dayOfWeek = date.getDay();
  const isPast = date < today;
  const isSunday = dayOfWeek === 0;
  const simulatedClosedDay = date.getDate() % 9 === 0;

  return !isPast && !isSunday && !simulatedClosedDay;
}

// Formato visible para el paciente.
function formatDate(date) {
  return `${date.getDate()} de ${monthNames[date.getMonth()]} ${date.getFullYear()}`;
}

// RF2: genera dinamicamente el calendario del mes actual.
function renderCalendar() {
  const year = today.getFullYear();
  const month = today.getMonth();
  const firstDay = new Date(year, month, 1);
  const totalDays = new Date(year, month + 1, 0).getDate();
  const startOffset = (firstDay.getDay() + 6) % 7;

  currentMonthLabel.textContent = `${monthNames[month]} ${year}`;
  calendarGrid.innerHTML = "";

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

// Crea los chips de horarios y deshabilita los ocupados.
function renderTimeSlots(date) {
  const busySlots = getBusySlots(date.getDate());
  timeSlots.innerHTML = `
    <div class="time-slots__header">
      <strong>Horarios disponibles</strong>
      <span>${formatDate(date)}</span>
    </div>
  `;

  const chipList = document.createElement("div");
  chipList.className = "chip-list";

  availableTimes.forEach((time) => {
    const isBusy = busySlots.includes(time);
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
      chip.addEventListener("click", () => selectTime(chip, time));
    }

    chipList.append(chip);
  });

  timeSlots.append(chipList);
}

// Guarda el horario seleccionado y habilita el boton flotante.
function selectTime(chip, time) {
  selectedTime = time;

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
  return {
    pacienteId: getActivePatientId(),
    fechaHora: `${selectedDayKey}T${timeToTwentyFourHourValue(selectedTime)}`,
    motivo: "Cita agendada desde Portal Publico"
  };
}

async function saveAppointment(payload) {
  const response = await fetch(APPOINTMENT_ENDPOINT, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
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

  const appointmentPayload = buildAppointmentPayload();
  const confirmationMessage = `Cita confirmada para el ${selectedFormattedDate} a las ${selectedTime}.`;

  confirmSlotButton.disabled = true;
  scheduleStatus.textContent = "Confirmando cita...";

  try {
    await saveAppointment(appointmentPayload);
    scheduleStatus.textContent = confirmationMessage;
    showConfirmationToast(confirmationMessage);
  } catch (error) {
    scheduleStatus.textContent = error.status === 409
      ? "Ese horario ya fue ocupado. Selecciona otro horario."
      : error.message || "No fue posible confirmar la cita.";
    alert(scheduleStatus.textContent);
    confirmSlotButton.disabled = false;
  }
});

renderCalendar();
