const odontogramGrid = document.querySelector("#odontogram-grid");
const toothMenu = document.querySelector("#tooth-menu");
const toothMenuTitle = document.querySelector("#tooth-menu-title");
const noteInput = document.querySelector("#evolution-note");
const addNoteButton = document.querySelector("#add-note");
const noteList = document.querySelector("#note-list");
const appointmentHistoryList = document.querySelector("#appointment-history-list");
const noteStatus = document.querySelector("#note-status");
const patientSearch = document.querySelector("#patient-search");
const patientName = document.querySelector("#patient-name");
const patientAge = document.querySelector("#patient-age");
const patientAllergies = document.querySelector("#patient-allergies");
const patientLastVisit = document.querySelector("#patient-last-visit");
const adminLogoutButton = document.querySelector("#admin-logout");
const changePasswordButton = document.querySelector("#change-password-button");
const passwordDialog = document.querySelector("#password-dialog");
const passwordForm = document.querySelector("#password-form");
const passwordStatus = document.querySelector("#password-status");
const patientResults = document.querySelector("#patient-results");
const clinicalFileForm = document.querySelector("#clinical-file-form");
const clinicalAgeInput = document.querySelector("#clinical-age");
const clinicalBloodTypeInput = document.querySelector("#clinical-blood-type");
const clinicalAllergiesInput = document.querySelector("#clinical-allergies");
const clinicalBackgroundInput = document.querySelector("#clinical-background");
const clinicalImageForm = document.querySelector("#clinical-image-form");
const clinicalImageFileInput = document.querySelector("#clinical-image-file");
const imageStatus = document.querySelector("#image-status");
const clinicalImageList = document.querySelector("#clinical-image-list");

// Ruta base de la API local en Kotlin/Javalin.
const API_BASE_URL = window.location.origin;
const API_ADMIN_PASSWORD_URL = `${API_BASE_URL}/api/admin/password`;
const API_PATIENT_HISTORY_URL = (patientId) => `${API_BASE_URL}/api/pacientes/${patientId}/historial`;

// Proteccion simple de pantalla: requiere sesion de odontologo o admin.
const adminSession = JSON.parse(localStorage.getItem("adminSession") || "null");
const authToken = localStorage.getItem("authToken");
const allowedDentistRoles = ["ADMIN", "ODONTOLOGO"];

if (!adminSession || !authToken || !allowedDentistRoles.includes(adminSession.rol)) {
  localStorage.removeItem("adminSession");
  localStorage.removeItem("authToken");
  window.location.replace("index.html?acceso=odontologo");
  throw new Error("Acceso restringido al modulo odontologico.");
}

// Estado activo de la pantalla: paciente cargado y superficie dental selecciónada.
let activePatientId = Number(localStorage.getItem("odontologoPacienteId") || "0");
let selectedSurface = null;
let odontogramRecords = new Map();
let searchTimeout = null;

// Piezas dentales adultas organizadas por cuadrante.
const teethByQuadrant = {
  1: [18, 17, 16, 15, 14, 13, 12, 11],
  2: [21, 22, 23, 24, 25, 26, 27, 28],
  3: [38, 37, 36, 35, 34, 33, 32, 31],
  4: [41, 42, 43, 44, 45, 46, 47, 48]
};

// Cada pieza dental se divide en 5 superficies clínicas.
const toothSurfaces = [
  { id: "vestibular", apiValue: "VESTIBULAR", label: "Vestibular", className: "surface--top" },
  { id: "distal", apiValue: "DISTAL", label: "Distal", className: "surface--right" },
  { id: "oclusal", apiValue: "OCLUSAL", label: "Oclusal/Incisal", className: "surface--center" },
  { id: "mesial", apiValue: "MESIAL", label: "Mesial", className: "surface--left" },
  { id: "lingual", apiValue: "LINGUAL", label: "Lingual/Palatina", className: "surface--bottom" }
];

// Relaciona el estado clinico con el texto de API y la clase CSS visual.
const toothStatuses = {
  healthy: { label: "Sano", apiValue: "SANO", className: "tooth--healthy" },
  caries: { label: "Caries", apiValue: "CARIES", className: "tooth--caries" },
  filling: { label: "Obturación", apiValue: "OBTURACION", className: "tooth--filling" },
  extraction: { label: "Extracción", apiValue: "EXTRACCION", className: "tooth--extraction" }
};

// Clave interna para ubicar rapidamente una superficie dentro del odontograma.
function recordKey(toothNumber, surface) {
  return `${toothNumber}:${String(surface).toUpperCase()}`;
}

// Normaliza textos recibidos de la base para compararlos con los data-attributes.
function normalizeSurface(surface) {
  return String(surface || "")
    .trim()
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "");
}

// Convierte el estado de la base al nombre usado por las clases CSS.
function normalizeStatus(status) {
  const normalized = String(status || "SANO")
    .trim()
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replaceAll("_", " ");

  if (normalized.includes("caries")) return "caries";
  if (normalized.includes("obtur") || normalized.includes("filling")) return "filling";
  if (normalized.includes("extrac")) return "extraction";
  return "healthy";
}

function getSurfaceLabel(surfaceId) {
  return toothSurfaces.find((surface) => surface.id === surfaceId)?.label || surfaceId;
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

// Helper comun para peticiones JSON y manejo simple de errores HTTP.
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
    const error = new Error(data.error || data.mensaje || "No fue posible completar la solicitud.");
    error.status = response.status;
    throw error;
  }

  return data;
}

async function fetchFormData(url, formData) {
  const token = localStorage.getItem("authToken");
  const response = await fetch(url, {
    method: "POST",
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: formData
  });
  const data = await response.json().catch(() => ({}));

  if (!response.ok) {
    const error = new Error(data.error || data.mensaje || "No fue posible subir el archivo.");
    error.status = response.status;
    throw error;
  }

  return data;
}

async function fetchProtectedBlob(url) {
  const token = localStorage.getItem("authToken");
  const response = await fetch(url, {
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    }
  });

  if (!response.ok) {
    throw new Error("No fue posible cargar el archivo clínico.");
  }

  return response.blob();
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

async function changeOwnAdminPassword(payload) {
  return fetchJson(API_ADMIN_PASSWORD_URL, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(payload)
  });
}

// Formato visual para notas e historial clinico.
function formatTimestamp(value) {
  const date = value ? new Date(value) : new Date();

  return new Intl.DateTimeFormat("es-MX", {
    dateStyle: "short",
    timeStyle: "short"
  }).format(date);
}

function formatDateOnly(value) {
  if (!value) return "Fecha no registrada";

  return new Date(`${value}T00:00:00`).toLocaleDateString("es-MX", {
    day: "2-digit",
    month: "short",
    year: "numeric"
  });
}

function formatTimeOnly(value) {
  return value ? String(value).slice(0, 5) : "--:--";
}

function clearStatusClasses(surfaceButton) {
  surfaceButton.classList.remove("tooth--healthy", "tooth--caries", "tooth--filling", "tooth--extraction");
}

// Aplica el color/estado visual a una superficie del diente.
function paintSurface(surfaceButton, status) {
  const config = toothStatuses[status] || toothStatuses.healthy;
  const toothNumber = surfaceButton.dataset.tooth;

  clearStatusClasses(surfaceButton);
  surfaceButton.classList.add(config.className);
  surfaceButton.dataset.status = status;
  surfaceButton.setAttribute(
    "aria-label",
    `Pieza ${toothNumber}, superficie ${getSurfaceLabel(surfaceButton.dataset.surface)}, estado ${config.label}`
  );
}

// Crea en HTML las 32 piezas adultas con sus 5 superficies.
function renderOdontogram() {
  odontogramGrid.innerHTML = "";

  Object.entries(teethByQuadrant).forEach(([quadrant, teeth]) => {
    const quadrantGroup = document.createElement("section");
    quadrantGroup.className = "quadrant-group";
    quadrantGroup.setAttribute("aria-label", `Cuadrante ${quadrant}`);

    const title = document.createElement("h3");
    title.textContent = `Cuadrante ${quadrant}`;
    quadrantGroup.append(title);

    const teethGrid = document.createElement("div");
    teethGrid.className = "teeth-grid";

    teeth.forEach((toothNumber) => {
      const tooth = document.createElement("div");
      tooth.className = "tooth";
      tooth.dataset.tooth = String(toothNumber);
      tooth.setAttribute("aria-label", `Pieza ${toothNumber}`);

      toothSurfaces.forEach((surface) => {
        const surfaceButton = document.createElement("button");
        surfaceButton.type = "button";
        surfaceButton.className = `tooth-surface ${surface.className} tooth--healthy`;
        surfaceButton.dataset.tooth = String(toothNumber);
        surfaceButton.dataset.surface = surface.id;
        surfaceButton.dataset.surfaceApi = surface.apiValue;
        surfaceButton.dataset.status = "healthy";
        surfaceButton.setAttribute("aria-label", `Pieza ${toothNumber}, superficie ${surface.label}, estado sano`);
        surfaceButton.textContent = surface.id === "oclusal" ? toothNumber : "";
        tooth.append(surfaceButton);
      });

      teethGrid.append(tooth);
    });

    quadrantGroup.append(teethGrid);
    odontogramGrid.append(quadrantGroup);
  });
}

// Pinta el odontograma con los registros que vienen de MariaDB.
function paintOdontogram(odontogram) {
  odontogramRecords = new Map();

  document.querySelectorAll(".tooth-surface").forEach((surfaceButton) => {
    paintSurface(surfaceButton, "healthy");
  });

  odontogram.forEach((record) => {
    const toothNumber = record.númeroPieza || record.número_pieza;
    const surface = normalizeSurface(record.superficie);
    const status = normalizeStatus(record.estado);
    const surfaceButton = document.querySelector(`.tooth-surface[data-tooth="${toothNumber}"][data-surface="${surface}"]`);

    odontogramRecords.set(recordKey(toothNumber, record.superficie), {
      id: record.odontogramaId || record.id,
      toothNumber,
      surface: String(record.superficie || "").toUpperCase(),
      status
    });

    if (surfaceButton) {
      surfaceButton.dataset.odontogramId = record.odontogramaId || record.id;
      paintSurface(surfaceButton, status);
    }
  });
}

// Renderiza las notas historicas del expediente.
function renderAppointmentHistory(appointments) {
  appointmentHistoryList.innerHTML = "";

  if (!appointments.length) {
    const emptyItem = document.createElement("article");
    emptyItem.className = "note-item";
    emptyItem.innerHTML = "<p>Sin citas registradas.</p>";
    appointmentHistoryList.append(emptyItem);
    return;
  }

  appointments.forEach((appointment) => {
    const item = document.createElement("article");
    item.className = "note-item";
    item.innerHTML = `
      <time>${formatDateOnly(appointment.fecha)} ${escapeHtml(formatTimeOnly(appointment.hora))}</time>
      <p><strong>${escapeHtml(appointment.odontologoNombre || "Por asignar")}</strong></p>
      <p>${escapeHtml(appointment.tratamiento || "Sin tratamiento registrado")} - ${escapeHtml(appointment.estatus || "Sin estatus")}</p>
    `;
    appointmentHistoryList.append(item);
  });
}

async function loadPatientHistory(patientId) {
  try {
    const history = await fetchJson(API_PATIENT_HISTORY_URL(patientId));
    renderAppointmentHistory(history.citas || []);
  } catch (error) {
    appointmentHistoryList.innerHTML = `
      <article class="note-item">
        <p>No fue posible cargar el historial de citas.</p>
      </article>
    `;
  }
}

function renderNotes(notes) {
  noteList.innerHTML = "";

  if (!notes.length) {
    const emptyNote = document.createElement("article");
    emptyNote.className = "note-item";
    emptyNote.innerHTML = "<p>Sin notas de evolucion registradas.</p>";
    noteList.append(emptyNote);
    return;
  }

  notes.forEach((note) => {
    addNoteItem(note.textoNota || note.texto_nota || note.nota || "", note.fechaHora || note.fecha_hora, false);
  });
}

function renderClinicalImages(images) {
  clinicalImageList.innerHTML = "";

  if (!images.length) {
    const emptyItem = document.createElement("article");
    emptyItem.className = "clinical-image-item";
    emptyItem.innerHTML = "<p>Sin radiografías o imágenes registradas.</p>";
    clinicalImageList.append(emptyItem);
    return;
  }

  images.forEach((image) => {
    clinicalImageList.append(createClinicalImageItem(image));
  });
}

function createClinicalImageItem(image) {
  const item = document.createElement("article");
  const isImage = String(image.contentType || "").startsWith("image/");
  const previewLabel = isImage ? "Cargando imagen..." : "Cargando PDF...";

  item.className = "clinical-image-item";
  item.innerHTML = `
    <a class="clinical-image-preview" href="#" target="_blank" rel="noopener" data-protected-file>
      ${isImage
        ? `<span>${previewLabel}</span>`
        : `<span>${previewLabel}</span>`}
    </a>
    <div>
      <strong>${escapeHtml(image.tipo || "IMAGEN")}</strong>
      <p>${escapeHtml(image.descripcion || image.nombreOriginal || "Archivo clinico")}</p>
      <small>${formatTimestamp(image.fechaSubida)} · ${escapeHtml(image.nombreOriginal || "")}</small>
    </div>
  `;

  loadProtectedClinicalImagePreview(item, image, isImage);

  return item;
}

async function loadProtectedClinicalImagePreview(item, image, isImage) {
  const link = item.querySelector("[data-protected-file]");

  try {
    const blob = await fetchProtectedBlob(`${API_BASE_URL}${image.url}`);
    const objectUrl = URL.createObjectURL(blob);
    link.href = objectUrl;

    if (isImage) {
      link.innerHTML = `<img src="${objectUrl}" alt="${escapeHtml(image.descripcion || image.nombreOriginal || "Imagen clínica")}">`;
    } else {
      link.innerHTML = "<span>PDF</span>";
    }
  } catch (error) {
    link.removeAttribute("href");
    link.innerHTML = "<span>No disponible</span>";
    link.addEventListener("click", (event) => event.preventDefault());
  }
}

// Llena la cabecera clínica del paciente activo.
function fillPatientHeader(expediente) {
  const paciente = expediente.paciente || {};
  const clinicalFile = expediente.expediente || {};
  const notes = expediente.notasEvolución || [];

  patientName.textContent = paciente.nombre || "Paciente sin nombre";
  patientAge.textContent = clinicalFile.edad ? `${clinicalFile.edad} años` : "No registrado";
  patientAllergies.textContent = clinicalFile.alergias || "Sin alergias registradas";
  patientLastVisit.textContent = notes[0]?.fechaHora ? formatTimestamp(notes[0].fechaHora) : "Sin consultas";
  clinicalAgeInput.value = clinicalFile.edad || "";
  clinicalBloodTypeInput.value = clinicalFile.grupoSanguineo || "";
  clinicalAllergiesInput.value = clinicalFile.alergias || "";
  clinicalBackgroundInput.value = clinicalFile.antecedentes || "";
}

// Busca el expediente completo del paciente y refresca toda la vista clínica.
async function loadPatientRecord(patientId) {
  noteStatus.classList.remove("is-error");
  noteStatus.textContent = "Cargando expediente...";

  try {
    const expediente = await fetchJson(`${API_BASE_URL}/api/pacientes/${patientId}/expediente`);

    activePatientId = patientId;
    localStorage.setItem("odontologoPacienteId", String(patientId));
    fillPatientHeader(expediente);
    paintOdontogram(expediente.odontograma || []);
    renderNotes(expediente.notasEvolución || []);
    renderClinicalImages(expediente.imágenesClinicas || []);
    await loadPatientHistory(patientId);
    noteStatus.textContent = "Expediente cargado correctamente.";
    imageStatus.textContent = "";
  } catch (error) {
    noteStatus.textContent = error.message || "No fue posible cargar el expediente.";
    noteStatus.classList.add("is-error");
  }
}

async function uploadClinicalImage(event) {
  event.preventDefault();

  if (!activePatientId) {
    imageStatus.textContent = "Selecciona un paciente antes de subir imágenes.";
    imageStatus.classList.add("is-error");
    return;
  }

  if (!clinicalImageFileInput.files.length) {
    imageStatus.textContent = "Selecciona un archivo JPG, PNG, WebP o PDF.";
    imageStatus.classList.add("is-error");
    return;
  }

  imageStatus.classList.remove("is-error");
  imageStatus.textContent = "Subiendo archivo...";

  try {
    const formData = new FormData(clinicalImageForm);
    const response = await fetchFormData(`${API_BASE_URL}/api/pacientes/${activePatientId}/imágenes`, formData);
    const emptyItem = clinicalImageList.querySelector(".clinical-image-item p");

    if (emptyItem?.textContent.includes("Sin radiografías") || emptyItem?.textContent.includes("Selecciona")) {
      clinicalImageList.innerHTML = "";
    }

    clinicalImageList.prepend(createClinicalImageItem(response.imagen));
    clinicalImageForm.reset();
    imageStatus.textContent = "Imagen registrada correctamente.";
  } catch (error) {
    imageStatus.textContent = error.message || "No fue posible subir la imagen.";
    imageStatus.classList.add("is-error");
  }
}

async function searchPatients(query) {
  const normalizedQuery = query.trim();

  if (normalizedQuery.length < 2 && !Number(normalizedQuery)) {
    patientResults.classList.add("is-hidden");
    patientResults.innerHTML = "";
    return;
  }

  try {
    const patients = await fetchJson(`${API_BASE_URL}/api/pacientes/buscar?q=${encodeURIComponent(normalizedQuery)}`);
    renderPatientResults(patients);
  } catch (error) {
    patientResults.innerHTML = `
      <div class="patient-result">
        <strong>No se pudo buscar</strong>
        <span>Verifica que el servidor este activo.</span>
      </div>
    `;
    patientResults.classList.remove("is-hidden");
  }
}

function loadPatientFromSearchInput() {
  const directPatientId = Number(patientSearch.value.trim());

  if (Number.isInteger(directPatientId) && directPatientId > 0) {
    patientResults.classList.add("is-hidden");
    loadPatientRecord(directPatientId);
    return;
  }

  const firstResult = patientResults.querySelector("[data-patient-id]");
  if (firstResult) firstResult.click();
}

function renderPatientResults(patients) {
  patientResults.innerHTML = "";

  if (!patients.length) {
    patientResults.innerHTML = `
      <div class="patient-result">
        <strong>Sin resultados</strong>
        <span>Intenta con otro nombre, correo o ID.</span>
      </div>
    `;
    patientResults.classList.remove("is-hidden");
    return;
  }

  patients.forEach((patient) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "patient-result";
    button.dataset.patientId = patient.id;
    button.innerHTML = `
      <strong>${escapeHtml(patient.nombre)}</strong>
      <span>#${escapeHtml(patient.id)} - ${escapeHtml(patient.email)} - ${escapeHtml(patient.telefono)}</span>
    `;
    patientResults.append(button);
  });

  patientResults.classList.remove("is-hidden");
}

async function saveClinicalFile(event) {
  event.preventDefault();

  if (!activePatientId) {
    noteStatus.textContent = "Selecciona un paciente antes de guardar el expediente.";
    noteStatus.classList.add("is-error");
    return;
  }

  const payload = {
    edad: clinicalAgeInput.value ? Number(clinicalAgeInput.value) : null,
    grupoSanguineo: clinicalBloodTypeInput.value,
    alergias: clinicalAllergiesInput.value.trim(),
    antecedentes: clinicalBackgroundInput.value.trim()
  };

  if (payload.edad !== null && (payload.edad < 0 || payload.edad > 120)) {
    noteStatus.textContent = "La edad debe estar entre 0 y 120.";
    noteStatus.classList.add("is-error");
    return;
  }

  noteStatus.classList.remove("is-error");
  noteStatus.textContent = "Guardando expediente...";

  try {
    await fetchJson(`${API_BASE_URL}/api/pacientes/${activePatientId}/expediente`, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify(payload)
    });

    await loadPatientRecord(activePatientId);
    noteStatus.textContent = "Expediente actualizado correctamente.";
  } catch (error) {
    noteStatus.textContent = error.message || "No fue posible guardar el expediente.";
    noteStatus.classList.add("is-error");
  }
}

// Abre el menu contextual junto a la superficie selecciónada.
function openToothMenu(surface) {
  const rect = surface.getBoundingClientRect();
  selectedSurface = surface;
  toothMenuTitle.textContent = `Pieza ${surface.dataset.tooth} - ${getSurfaceLabel(surface.dataset.surface)}`;
  toothMenu.classList.remove("is-hidden");
  toothMenu.style.left = `${Math.min(rect.left, window.innerWidth - 190)}px`;
  toothMenu.style.top = `${rect.bottom + 8 + window.scrollY}px`;
}

function closeToothMenu() {
  selectedSurface = null;
  toothMenu.classList.add("is-hidden");
}

// Envia a la API el cambio de estado de una superficie especifica.
async function updateSurfaceRecord(surfaceButton, status) {
  const toothNumber = Number(surfaceButton.dataset.tooth);
  const odontogramId = surfaceButton.dataset.odontogramId;
  const config = toothStatuses[status];

  if (!odontogramId) {
    throw new Error("No se encontro el registro del odontograma para esta superficie.");
  }

  await fetchJson(`${API_BASE_URL}/api/odontograma/${odontogramId}/pieza/${toothNumber}`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      estado: config.apiValue,
      superficie: surfaceButton.dataset.surfaceApi
    })
  });

  odontogramRecords.set(recordKey(toothNumber, surfaceButton.dataset.surfaceApi), {
    id: Number(odontogramId),
    toothNumber,
    surface: surfaceButton.dataset.surfaceApi,
    status
  });
}

function getToothSurfaceButtons(toothNumber) {
  return [...document.querySelectorAll(`.tooth-surface[data-tooth="${toothNumber}"]`)];
}

function shouldUpdateFullTooth(toothNumber, status) {
  const surfaces = getToothSurfaceButtons(toothNumber);
  const hasExtraction = surfaces.some((surfaceButton) => surfaceButton.dataset.status === "extraction");

  return status === "extraction" || (status === "healthy" && hasExtraction);
}

// Cambia visualmente el diente y confirma el cambio en base de datos.
async function updateToothStatus(status) {
  if (!selectedSurface) return;

  const toothNumber = selectedSurface.dataset.tooth;
  const targetSurfaces = shouldUpdateFullTooth(toothNumber, status)
    ? getToothSurfaceButtons(toothNumber)
    : [selectedSurface];

  const previousStates = targetSurfaces.map((surfaceButton) => ({
    surfaceButton,
    status: surfaceButton.dataset.status
  }));

  targetSurfaces.forEach((surfaceButton) => paintSurface(surfaceButton, status));
  closeToothMenu();

  try {
    await Promise.all(targetSurfaces.map((surfaceButton) => updateSurfaceRecord(surfaceButton, status)));
    noteStatus.classList.remove("is-error");
    noteStatus.textContent = `Pieza ${toothNumber} actualizada correctamente.`;
  } catch (error) {
    previousStates.forEach(({ surfaceButton, status: previousStatus }) => {
      paintSurface(surfaceButton, previousStatus);
    });
    noteStatus.textContent = error.message || "No fue posible actualizar la pieza dental.";
    noteStatus.classList.add("is-error");
  }
}

// Inserta una nota en el historial visual.
function addNoteItem(text, timestamp, prepend = true) {
  const note = document.createElement("article");
  const noteTime = document.createElement("time");
  const noteText = document.createElement("p");

  note.className = "note-item";
  noteTime.textContent = formatTimestamp(timestamp);
  noteText.textContent = text;
  note.append(noteTime, noteText);

  if (prepend) {
    noteList.prepend(note);
  } else {
    noteList.append(note);
  }
}

// Guarda una nota clínica en la API y la agrega a la lista sin recargar.
async function addEvolutionNote() {
  const text = noteInput.value.trim();

  if (!activePatientId) {
    noteStatus.textContent = "Selecciona un paciente antes de agregar notas.";
    noteStatus.classList.add("is-error");
    return;
  }

  if (!text) {
    noteStatus.textContent = "Escribe una nota antes de agregarla.";
    noteStatus.classList.add("is-error");
    return;
  }

  addNoteButton.disabled = true;
  noteStatus.classList.remove("is-error");
  noteStatus.textContent = "Guardando nota...";

  try {
    await fetchJson(`${API_BASE_URL}/api/pacientes/${activePatientId}/notas`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        texto_nota: text
      })
    });

    noteList.querySelector(".note-item p")?.textContent === "Sin notas de evolucion registradas." && (noteList.innerHTML = "");
    addNoteItem(text, new Date().toISOString(), true);
    noteInput.value = "";
    noteStatus.textContent = "Nota agregada al historial.";
  } catch (error) {
    noteStatus.textContent = error.message || "No fue posible guardar la nota.";
    noteStatus.classList.add("is-error");
  } finally {
    addNoteButton.disabled = false;
  }
}

patientSearch.addEventListener("input", () => {
  window.clearTimeout(searchTimeout);
  searchTimeout = window.setTimeout(() => searchPatients(patientSearch.value), 260);
});

patientSearch.addEventListener("keydown", (event) => {
  if (event.key !== "Enter") return;

  event.preventDefault();
  loadPatientFromSearchInput();
});

patientSearch.addEventListener("search", () => {
  searchPatients(patientSearch.value);
});

patientResults.addEventListener("click", (event) => {
  const result = event.target.closest("[data-patient-id]");
  if (!result) return;

  patientResults.classList.add("is-hidden");
  patientSearch.value = result.querySelector("strong")?.textContent || "";
  loadPatientRecord(Number(result.dataset.patientId));
});

// Click en una superficie abre el menu de estados.
odontogramGrid.addEventListener("click", (event) => {
  const surface = event.target.closest(".tooth-surface");
  if (!surface) return;

  openToothMenu(surface);
});

// Seleccion de estado desde el menu dental.
toothMenu.addEventListener("click", (event) => {
  const option = event.target.closest("[data-tooth-status]");
  if (!option) return;

  updateToothStatus(option.dataset.toothStatus);
});

// Cierra el menu si el usuario hace click fuera.
document.addEventListener("click", (event) => {
  if (toothMenu.classList.contains("is-hidden")) return;
  if (event.target.closest(".tooth-surface") || event.target.closest("#tooth-menu")) return;

  closeToothMenu();
});

document.addEventListener("keydown", (event) => {
  if (event.key === "Escape") {
    closeToothMenu();
    if (!passwordDialog.classList.contains("is-hidden")) {
      closePasswordDialog();
    }
  }
});

addNoteButton.addEventListener("click", addEvolutionNote);
clinicalFileForm.addEventListener("submit", saveClinicalFile);
clinicalImageForm.addEventListener("submit", uploadClinicalImage);
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

  if (!payload.passwordActual || payload.passwordNueva.length < 8 || !/[A-Za-z]/.test(payload.passwordNueva) || !/\d/.test(payload.passwordNueva)) {
    passwordStatus.textContent = "Escribe tu contraseña actual y una nueva de al menos 8 caracteres, una letra y un número.";
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

adminLogoutButton.addEventListener("click", () => {
  localStorage.removeItem("adminSession");
  localStorage.removeItem("authToken");
  window.location.href = "index.html";
});

// Estado inicial: dibuja el odontograma y carga el paciente de prueba.
renderOdontogram();
if (activePatientId) {
  loadPatientRecord(activePatientId);
} else {
  noteStatus.textContent = "Busca y seleccióna un paciente para comenzar.";
  renderAppointmentHistory([]);
  renderNotes([]);
  renderClinicalImages([]);
}
