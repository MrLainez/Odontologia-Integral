param(
  [string]$BaseUrl = "http://localhost:8080",
  [string]$AdminEmail = "admin@odonto.local",
  [string]$AdminPassword = "admin123"
)

$ErrorActionPreference = "Stop"
$script:Passed = 0
$script:Failed = 0

function Write-Step {
  param([string]$Message)
  Write-Host ""
  Write-Host "== $Message ==" -ForegroundColor Cyan
}

function Write-Pass {
  param([string]$Message)
  $script:Passed += 1
  Write-Host "[OK] $Message" -ForegroundColor Green
}

function Write-Fail {
  param([string]$Message)
  $script:Failed += 1
  Write-Host "[FAIL] $Message" -ForegroundColor Red
}

function Convert-JsonBody {
  param($Body)
  if ($null -eq $Body) { return $null }
  return $Body | ConvertTo-Json -Depth 8
}

function Invoke-Api {
  param(
    [string]$Method,
    [string]$Path,
    $Body = $null,
    [string]$Token = "",
    [int]$ExpectedStatus = 200
  )

  $uri = "$BaseUrl$Path"
  $headers = @{}
  if ($Token) {
    $headers["Authorization"] = "Bearer $Token"
  }

  $jsonBody = Convert-JsonBody $Body

  try {
    if ($null -eq $jsonBody) {
      $response = Invoke-WebRequest -Method $Method -Uri $uri -Headers $headers -UseBasicParsing
    } else {
      $response = Invoke-WebRequest -Method $Method -Uri $uri -Headers $headers -ContentType "application/json" -Body $jsonBody -UseBasicParsing
    }

    $status = [int]$response.StatusCode
    $content = $response.Content
  } catch {
    $webResponse = $_.Exception.Response
    if ($null -eq $webResponse) {
      throw "No se pudo conectar con $uri. Verifica que el servidor este encendido."
    }

    $status = [int]$webResponse.StatusCode
    $reader = New-Object System.IO.StreamReader($webResponse.GetResponseStream())
    $content = $reader.ReadToEnd()
    $reader.Close()
  }

  $data = $null
  if ($content) {
    try {
      $data = $content | ConvertFrom-Json
    } catch {
      $data = $content
    }
  }

  if ($status -ne $ExpectedStatus) {
    throw "Esperaba HTTP $ExpectedStatus en $Method $Path, pero recibio HTTP $status. Respuesta: $content"
  }

  return [PSCustomObject]@{
    Status = $status
    Data = $data
    Raw = $content
  }
}

function Assert-True {
  param(
    [bool]$Condition,
    [string]$Message
  )

  if (-not $Condition) {
    throw $Message
  }
}

function Get-PropertyValue {
  param(
    $Object,
    [string]$Name
  )

  if ($null -eq $Object) { return $null }

  $property = $Object.PSObject.Properties[$Name]
  if ($null -eq $property) { return $null }

  return $property.Value
}

function As-Array {
  param($Value)

  if ($null -eq $Value) { return @() }
  if ($Value -is [System.Array]) { return $Value }
  return @($Value)
}

function Get-ItemCount {
  param($Value)

  if ($null -eq $Value) { return 0 }
  if ($Value -is [System.Array]) { return $Value.Count }
  return 1
}

try {
  Write-Step "Salud de base de datos"
  $health = Invoke-Api -Method "GET" -Path "/api/health/db"
  Assert-True ($health.Data.database -eq "ok") "La base de datos no respondio OK."
  Write-Pass "MariaDB responde desde la API"

  Write-Step "Registro y login de paciente"
  $stamp = Get-Date -Format "yyyyMMddHHmmss"
  $patientEmail = "smoke-$stamp@correo.com"
  $patientPassword = "Paciente123"
  $patientPayload = @{
    nombre = "Paciente Smoke $stamp"
    telefono = "5551234567"
    email = $patientEmail
    password = $patientPassword
  }
  $createdPatient = Invoke-Api -Method "POST" -Path "/api/pacientes" -Body $patientPayload -ExpectedStatus 201
  $patientId = [int]$createdPatient.Data.id
  Assert-True ($patientId -gt 0) "No se recibio id del paciente creado."
  Write-Pass "Paciente creado con id $patientId"

  $patientLogin = Invoke-Api -Method "POST" -Path "/api/login" -Body @{
    email = $patientEmail
    password = $patientPassword
  }
  $patientToken = [string]$patientLogin.Data.token
  Assert-True ($patientToken.Length -gt 20) "No se recibio token de paciente."
  Write-Pass "Login de paciente correcto"

  Write-Step "Login administrativo"
  $adminLogin = Invoke-Api -Method "POST" -Path "/api/admin/login" -Body @{
    email = $AdminEmail
    password = $AdminPassword
  }
  $adminToken = [string]$adminLogin.Data.token
  Assert-True ($adminToken.Length -gt 20) "No se recibio token administrativo."
  Write-Pass "Login administrativo correcto"

  Write-Step "Consulta protegida y odontologo activo"
  $patients = Invoke-Api -Method "GET" -Path "/api/pacientes" -Token $adminToken
  $patientsData = As-Array $patients.Data
  Assert-True ($patientsData.Count -ge 1) "La lista de pacientes vino vacia."
  Write-Pass "Listado protegido de pacientes responde"

  $dentists = Invoke-Api -Method "GET" -Path "/api/odontologos"
  $dentistsData = As-Array $dentists.Data

  if ($dentistsData.Count -lt 1) {
    Write-Host "No hay odontologos activos; creando odontologo de prueba..." -ForegroundColor DarkYellow
    Invoke-Api -Method "POST" -Path "/api/admin/usuarios" -Token $adminToken -ExpectedStatus 201 -Body @{
      nombre = "Odontologo Smoke $stamp"
      email = "odontologo-smoke-$stamp@odonto.local"
      password = "Odonto123"
      rol = "ODONTOLOGO"
    } | Out-Null

    $dentists = Invoke-Api -Method "GET" -Path "/api/odontologos"
    $dentistsData = As-Array $dentists.Data
  }

  Assert-True ($dentistsData.Count -ge 1) "No hay odontologos activos para la prueba."
  $dentistId = [int]$dentistsData[0].id
  Write-Pass "Odontologo activo disponible con id $dentistId"

  Write-Step "Citas y conflicto 409"
  $appointmentDateTime = $null
  foreach ($daysAhead in 45..75) {
    $candidateDate = (Get-Date).AddDays($daysAhead).ToString("yyyy-MM-dd")
    $availability = Invoke-Api -Method "GET" -Path "/api/citas/disponibilidad?fecha=$candidateDate&odontologoId=$dentistId"
    $availableSlot = As-Array $availability.Data.horarios | Where-Object { $_.disponible -eq $true } | Select-Object -First 1

    if ($null -ne $availableSlot) {
      $appointmentDateTime = "$candidateDate`T$($availableSlot.valor)"
      break
    }
  }

  Assert-True (-not [string]::IsNullOrWhiteSpace($appointmentDateTime)) "No se encontro un horario disponible para crear cita smoke."
  $appointmentPayload = @{
    pacienteId = $patientId
    odontologoId = $dentistId
    fechaHora = $appointmentDateTime
    motivo = "Revision inicial smoke"
  }
  $appointment = Invoke-Api -Method "POST" -Path "/api/citas" -Body $appointmentPayload -Token $adminToken -ExpectedStatus 201
  $appointmentId = [int]$appointment.Data.id
  Assert-True ($appointmentId -gt 0) "No se recibio id de la cita creada."
  Write-Pass "Cita creada con id $appointmentId"

  Invoke-Api -Method "POST" -Path "/api/citas" -Body $appointmentPayload -Token $adminToken -ExpectedStatus 409 | Out-Null
  Write-Pass "Conflicto de horario devuelve 409"

  Invoke-Api -Method "PUT" -Path "/api/citas/$appointmentId/estatus" -Body @{ estatus = "Asistio" } -Token $adminToken | Out-Null
  Write-Pass "Estatus de cita actualizado"

  Write-Step "Expediente, nota e historial"
  $record = Invoke-Api -Method "GET" -Path "/api/pacientes/$patientId/expediente" -Token $adminToken
  Assert-True ($record.Data.paciente.id -eq $patientId) "El expediente no corresponde al paciente creado."
  Write-Pass "Expediente consultado"

  $note = Invoke-Api -Method "POST" -Path "/api/pacientes/$patientId/notas" -Body @{
    texto_nota = "Nota smoke generada por prueba automatizada."
  } -Token $adminToken -ExpectedStatus 201
  Assert-True ([int]$note.Data.id -gt 0) "No se recibio id de nota."
  Write-Pass "Nota de evolucion creada"

  $history = Invoke-Api -Method "GET" -Path "/api/pacientes/$patientId/historial" -Token $adminToken
  $historyAppointments = As-Array (Get-PropertyValue $history.Data "citas")
  $historyNotes = As-Array (Get-PropertyValue $history.Data "notasEvolucion")

  if ((Get-ItemCount $historyAppointments) -lt 1) {
    $appointmentDate = $appointmentDateTime.Substring(0, 10)
    $agenda = Invoke-Api -Method "GET" -Path "/api/citas?fecha=$appointmentDate" -Token $adminToken
    $agendaAppointments = As-Array $agenda.Data
    $agendaMatch = $agendaAppointments | Where-Object { [int]$_.id -eq $appointmentId }

    Write-Host "Historial recibido:" -ForegroundColor DarkYellow
    Write-Host $history.Raw
    Write-Host "Agenda de la fecha ${appointmentDate}:" -ForegroundColor DarkYellow
    Write-Host $agenda.Raw

    Assert-True ($null -ne $agendaMatch) "La cita creada no aparece ni en historial ni en agenda."
  }

  Assert-True ((Get-ItemCount $historyAppointments) -ge 1) "El historial no incluye citas."
  Assert-True ((Get-ItemCount $historyNotes) -ge 1) "El historial no incluye notas."
  Write-Pass "Historial incluye citas y notas"

  Write-Step "Auditoria"
  $audit = Invoke-Api -Method "GET" -Path "/api/admin/auditoria?limit=20" -Token $adminToken
  Assert-True ((As-Array $audit.Data).Count -ge 1) "La auditoria no devolvio eventos."
  Write-Pass "Auditoria consultable por admin"

} catch {
  Write-Fail $_.Exception.Message
}

Write-Host ""
Write-Host "Resultado: $script:Passed pruebas OK, $script:Failed fallas." -ForegroundColor Yellow

if ($script:Failed -gt 0) {
  exit 1
}

exit 0
