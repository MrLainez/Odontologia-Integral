const adminLoginForm = document.querySelector("#admin-login-form");
const adminLoginStatus = document.querySelector("#admin-login-status");

window.location.replace("index.html");

const ADMIN_LOGIN_ENDPOINT = `${window.location.origin}/api/admin/login`;

function showFieldError(field, message) {
  const error = document.querySelector(`[data-error-for="${field.id}"]`);
  const row = field.closest(".form-row");

  if (row) row.classList.toggle("has-error", Boolean(message));
  if (error) error.textContent = message || "";
}

function validateField(field) {
  const value = field.value.trim();
  let message = "";

  if (!value) {
    message = "Campo requerido.";
  }

  if (field.name === "email" && value && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
    message = "Escribe un correo valido.";
  }

  if (field.name === "password" && value && value.length < 6) {
    message = "La contrasena debe tener al menos 6 caracteres.";
  }

  showFieldError(field, message);
  return message === "";
}

function getRedirectByRole(role) {
  if (role === "ODONTOLOGO") return "odontologo.html";
  return "recepcion.html";
}

async function loginAdmin(payload) {
  const response = await fetch(ADMIN_LOGIN_ENDPOINT, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(payload)
  });
  const data = await response.json().catch(() => ({}));

  if (!response.ok) {
    throw new Error(data.error || data.mensaje || "No fue posible iniciar sesion.");
  }

  return data;
}

adminLoginForm.addEventListener("input", (event) => {
  if (event.target.matches("input")) {
    validateField(event.target);
  }
});

adminLoginForm.addEventListener("submit", async (event) => {
  event.preventDefault();

  const fields = [...adminLoginForm.elements].filter((field) => field.name);
  const isValid = fields.map(validateField).every(Boolean);

  if (!isValid) {
    adminLoginStatus.textContent = "Revisa los campos marcados.";
    adminLoginStatus.classList.add("is-error");
    return;
  }

  const data = new FormData(adminLoginForm);
  const submitButton = adminLoginForm.querySelector("button[type='submit']");
  const payload = {
    email: data.get("email").trim(),
    password: data.get("password")
  };

  submitButton.disabled = true;
  adminLoginStatus.classList.remove("is-error");
  adminLoginStatus.textContent = "Validando acceso...";

  try {
    const result = await loginAdmin(payload);
    const user = result.usuario;

    localStorage.setItem("adminSession", JSON.stringify({
      id: user.id,
      nombre: user.nombre,
      email: user.email,
      rol: user.rol
    }));

    window.location.href = getRedirectByRole(user.rol);
  } catch (error) {
    adminLoginStatus.textContent = error.message;
    adminLoginStatus.classList.add("is-error");
  } finally {
    submitButton.disabled = false;
  }
});
