# FisioAgenda — Especificación técnica (contrato entre capas)

Aplicación de escritorio para que una clínica de fisioterapia pequeña (1-5 profesionales) gestione
pacientes, agenda de citas, catálogo de tratamientos e historial clínico. Proyecto Intermodular DAM,
Línea 2. Prototipo funcional, pero pulido: debe compilar, pasar los tests y verse profesional.

Todo el código, comentarios, mensajes de UI y nombres de dominio en **español** (identificadores sin tildes).
Javadoc breve en clases y métodos públicos. Nada de `System.out` salvo en `Main` para errores fatales.

## Tecnologías (ya en `pom.xml`, no cambiar versiones sin motivo)
- Java 17 (`maven.compiler.release=17`; el equipo del alumno puede tener JDK 17+). No usar APIs > 17.
- Swing + FlatLaf 3.7.2 (`com.formdev.flatlaf.FlatLightLaf`). **No** añadir flatlaf-extras ni MigLayout.
- SQLite vía `org.xerial:sqlite-jdbc` 3.53.4.0 (JDBC puro, sin ORM).
- JUnit 5.11.4 para pruebas. Empaquetado: `mvn package` genera `target/FisioAgenda.jar` ejecutable (shade).

## Estructura de paquetes (`src/main/java/com/fisioagenda`)
```
Main.java                      -> punto de entrada
model/                         -> YA ESCRITO: Rol, EstadoCita, Usuario, Paciente, Tratamiento, Cita, NotaClinica
dao/                           -> YA ESCRITO: interfaces UsuarioDAO, PacienteDAO, TratamientoDAO, CitaDAO, NotaClinicaDAO, DataAccessException
dao/sqlite/                    -> SqliteUsuarioDAO, SqlitePacienteDAO, SqliteTratamientoDAO, SqliteCitaDAO, SqliteNotaClinicaDAO
db/                            -> Database (conexión), EsquemaBD (DDL + datos de demostración)
security/                      -> PasswordHasher, Sesion
service/                       -> YA ESCRITO: ServiceException. Más: AuthService, PacienteService, CitaService,
                                  TratamientoService, UsuarioService, HistorialService, EstadisticasService, ServiceLocator
util/                          -> Validador, Formatos
ui/                            -> LoginFrame, MainFrame, Tema
ui/components/                 -> SidebarButton, AnimatedCardPanel, Toast, StatCard, Animador, FormBuilder (helpers opcionales)
ui/panels/                     -> DashboardPanel, PacientesPanel, AgendaPanel, TratamientosPanel, UsuariosPanel
ui/dialogs/                    -> PacienteDialog, CitaDialog, TratamientoDialog, UsuarioDialog, HistorialDialog, CambiarPasswordDialog
```
Regla de capas: `ui` solo habla con `service` (vía `ServiceLocator`) y `model`. `service` usa `dao` + `security` + `util`.
`dao.sqlite` usa `db`. Ninguna clase de `ui` importa `java.sql`.

## Base de datos (SQLite) — 5 tablas relacionadas
Fichero por defecto: `${user.home}/.fisioagenda/fisioagenda.db` (crear carpeta si no existe).
Se puede sobrescribir con la propiedad de sistema `fisioagenda.db` (ruta de fichero; los tests usan un fichero temporal).
Al abrir la conexión: `PRAGMA foreign_keys = ON;`. Fechas guardadas como TEXT ISO-8601
(`LocalDate.toString()`, `LocalDateTime` con formato `yyyy-MM-dd'T'HH:mm`). Booleanos como INTEGER 0/1.

```sql
CREATE TABLE IF NOT EXISTS usuario (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  username TEXT NOT NULL UNIQUE,
  nombre_completo TEXT NOT NULL,
  password_hash TEXT NOT NULL,
  salt TEXT NOT NULL,
  rol TEXT NOT NULL CHECK (rol IN ('ADMIN','FISIOTERAPEUTA','RECEPCION')),
  activo INTEGER NOT NULL DEFAULT 1,
  intentos_fallidos INTEGER NOT NULL DEFAULT 0,
  bloqueado_hasta TEXT
);
CREATE TABLE IF NOT EXISTS paciente (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  dni TEXT NOT NULL UNIQUE,
  nombre TEXT NOT NULL,
  apellidos TEXT NOT NULL,
  telefono TEXT NOT NULL,
  email TEXT,
  fecha_nacimiento TEXT,
  observaciones TEXT,
  fecha_alta TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS tratamiento (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  nombre TEXT NOT NULL UNIQUE,
  descripcion TEXT,
  duracion_min INTEGER NOT NULL CHECK (duracion_min BETWEEN 10 AND 240),
  precio REAL NOT NULL CHECK (precio >= 0),
  activo INTEGER NOT NULL DEFAULT 1
);
CREATE TABLE IF NOT EXISTS cita (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  paciente_id INTEGER NOT NULL REFERENCES paciente(id) ON DELETE CASCADE,
  fisioterapeuta_id INTEGER NOT NULL REFERENCES usuario(id),
  tratamiento_id INTEGER NOT NULL REFERENCES tratamiento(id),
  fecha_hora TEXT NOT NULL,
  estado TEXT NOT NULL DEFAULT 'PROGRAMADA' CHECK (estado IN ('PROGRAMADA','REALIZADA','CANCELADA','NO_ASISTIDA')),
  notas TEXT
);
CREATE INDEX IF NOT EXISTS idx_cita_fisio_fecha ON cita(fisioterapeuta_id, fecha_hora);
CREATE INDEX IF NOT EXISTS idx_cita_paciente ON cita(paciente_id);
CREATE INDEX IF NOT EXISTS idx_cita_fecha ON cita(fecha_hora);   -- consultas por día/intervalo sin filtro de fisio
CREATE TABLE IF NOT EXISTS nota_clinica (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  paciente_id INTEGER NOT NULL REFERENCES paciente(id) ON DELETE CASCADE,
  cita_id INTEGER REFERENCES cita(id) ON DELETE SET NULL,
  autor_id INTEGER NOT NULL REFERENCES usuario(id),
  fecha TEXT NOT NULL,
  contenido TEXT NOT NULL,
  escala_dolor INTEGER NOT NULL CHECK (escala_dolor BETWEEN 0 AND 10)
);
CREATE INDEX IF NOT EXISTS idx_nota_paciente ON nota_clinica(paciente_id, fecha);
```

### Datos de demostración (`EsquemaBD.cargarDatosDemo`, solo si la tabla usuario está vacía)
Usuarios (contraseñas hasheadas con `PasswordHasher`):
| username | contraseña | rol | nombre |
|---|---|---|---|
| admin | Admin1234! | ADMIN | Laura Martín Ruiz (directora) |
| fisio | Fisio1234! | FISIOTERAPEUTA | Carlos Pérez Gómez |
| fisio2 | Fisio1234! | FISIOTERAPEUTA | Marta Sánchez López |
| recepcion | Recep1234! | RECEPCION | Ana Torres Díaz |

Tratamientos: Fisioterapia general (45 min, 35 €), Masaje descontracturante (30 min, 30 €),
Punción seca (30 min, 40 €), Rehabilitación postquirúrgica (60 min, 45 €), Electroterapia (20 min, 20 €),
Drenaje linfático (50 min, 40 €), Fisioterapia deportiva (45 min, 38 €).
~12 pacientes ficticios con DNI **válidos** (letra correcta), teléfonos 6xxxxxxxx, emails `@example.com`.
Citas: ~25 citas relativas a la fecha actual (`LocalDate.now()`): varias hoy para ambos fisios, algunas
en los próximos 7 días, y varias en días pasados con estado REALIZADA / NO_ASISTIDA / CANCELADA, todas
en horario 9:00-20:00 y **sin solapes**. ~10 notas clínicas en pacientes con citas realizadas.

## Seguridad
- `PasswordHasher`: PBKDF2WithHmacSHA256, 600 000 iteraciones (OWASP), sal aleatoria de 16 bytes (`SecureRandom`),
  clave de 256 bits, Base64. Métodos: `static String generarSal()`, `static String hash(char[] password, String sal)`,
  `static boolean verificar(char[] password, String sal, String hashEsperado)` (comparación en tiempo constante
  con `MessageDigest.isEqual`). Limpiar el `char[]` con `Arrays.fill` donde se reciba de la UI.
- Política de contraseña (`Validador.esPasswordSegura`): ≥ 8 caracteres, mayúscula, minúscula, dígito y símbolo.
- Bloqueo: 5 intentos fallidos seguidos → `bloqueadoHasta = ahora + 5 minutos`, contador a 0. Login correcto → contador a 0.
  Usuario inactivo no puede entrar. Mensaje genérico "Usuario o contraseña incorrectos" (no revelar si existe el usuario):
  un nombre inexistente lleva un contador en memoria y recibe las mismas respuestas que uno real, también el aviso de bloqueo.
- SQL siempre con `PreparedStatement` (nunca concatenar entrada del usuario).
- Control de acceso por rol en **servicio** (no solo ocultando botones): lanzar `ServiceException("No tiene permisos para ...")`.
- `Sesion`: singleton con `iniciar(Usuario)`, `cerrar()`, `getUsuario()`, `estaIniciada()`, `tieneRol(Rol...)`.
  Cierre automático por inactividad: 15 minutos sin eventos de ratón/teclado → volver a Login (lo gestiona `MainFrame`
  con un `javax.swing.Timer` + `Toolkit.getDefaultToolkit().addAWTEventListener`).

### Permisos por rol
| Acción | ADMIN | FISIOTERAPEUTA | RECEPCION |
|---|---|---|---|
| Ver/crear/editar pacientes | ✔ | ver y editar | ✔ |
| Eliminar paciente | ✔ | ✘ | ✘ |
| Ver agenda | todas | todas (por defecto filtrada a la suya) | todas |
| Crear/editar/cancelar citas | ✔ | ✔ | ✔ |
| Marcar cita REALIZADA / NO_ASISTIDA | ✔ | ✔ | ✘ |
| Ver/añadir historial clínico (notas) | ✔ (ver) | ✔ (ver y añadir) | ✘ (datos de salud, RGPD) |
| Gestionar tratamientos | ✔ | ver | ver |
| Gestionar usuarios | ✔ | ✘ | ✘ |
| Cambiar su propia contraseña | ✔ | ✔ | ✔ |

## Servicios (firmas públicas EXACTAS — la UI se programa contra ellas)
`ServiceLocator` (singleton perezoso, construye DAOs SQLite y servicios una sola vez):
`static AuthService auth()`, `pacientes()`, `citas()`, `tratamientos()`, `usuarios()`, `historial()`, `estadisticas()`.

```java
// AuthService
Usuario login(String username, char[] password);            // ServiceException si falla/bloqueado/inactivo; inicia Sesion
void logout();
void cambiarPassword(char[] actual, char[] nueva, char[] repetida); // usuario de la sesión

// PacienteService
List<Paciente> listar();
List<Paciente> buscar(String texto);                         // texto vacío => listar()
Paciente guardar(Paciente p);   // valida (DNI/NIE, nombre, apellidos, teléfono 9 dígitos, email opcional, nacimiento no futuro); id 0 => insertar (fechaAlta=hoy), si no actualizar; DNI duplicado => ServiceException
void eliminar(int id);          // solo ADMIN
Optional<Paciente> buscarPorId(int id);

// CitaService
List<Cita> citasDelDia(LocalDate dia, Integer fisioterapeutaId);   // null = todos
List<Cita> citasDePaciente(int pacienteId);
Cita guardar(Cita c);           // valida: paciente, fisio (rol FISIOTERAPEUTA y activo), tratamiento activo, fecha no pasada (solo al crear),
                                // horario de apertura 08:00–21:00 (fin incluido), lunes-sábado, sin solape con otras citas PROGRAMADAS/REALIZADAS
                                // del mismo fisio NI del mismo paciente (excluir la propia cita al editar). Mensaje de solape indica hora ocupada.
                                // Solo se editan citas PROGRAMADAS.
void cambiarEstado(int citaId, EstadoCita nuevo);  // REALIZADA/NO_ASISTIDA solo ADMIN o FISIOTERAPEUTA y desde la hora de inicio; CANCELADA cualquiera
List<LocalTime> huecosLibres(LocalDate dia, int fisioterapeutaId, int duracionMin); // franjas cada 15 min dentro del horario sin solape

// TratamientoService
List<Tratamiento> listar(); List<Tratamiento> listarActivos();
Tratamiento guardar(Tratamiento t);   // solo ADMIN; nombre obligatorio, duración 10-240, precio >= 0;
                                      // la duración no cambia si hay citas PROGRAMADAS de hoy en adelante

// UsuarioService (solo ADMIN salvo listarFisioterapeutas)
List<Usuario> listar();
List<Usuario> listarFisioterapeutas();          // activos
Usuario crear(Usuario u, char[] password);      // valida username (3-20, [a-z0-9._]), único, password segura
void actualizar(Usuario u);                     // nombre, rol, activo (no puede desactivarse a sí mismo)
void restablecerPassword(int usuarioId, char[] nueva);
void desbloquear(int usuarioId);

// HistorialService
List<NotaClinica> notasDe(int pacienteId);      // ADMIN o FISIOTERAPEUTA
NotaClinica anadirNota(int pacienteId, Integer citaId, String contenido, int escalaDolor); // solo FISIOTERAPEUTA; autor = sesión; contenido 5-2000 chars

// EstadisticasService (para el panel de inicio)
int pacientesTotales();
int citasHoy(Integer fisioterapeutaId);
int citasPendientesSemana(Integer fisioterapeutaId);   // PROGRAMADAS desde ahora hasta +7 días
double tasaAsistenciaUltimos30Dias();                  // REALIZADAS / (REALIZADAS + NO_ASISTIDAS) en %, 0 si no hay datos
Map<DayOfWeek,Integer> citasPorDiaSemanaActual();      // lunes..sábado de la semana actual (para un mini gráfico de barras)
```
Constructores de servicios reciben sus DAOs por parámetro (inyección por constructor → testeable con BD temporal).
`AuthService` recibe además un `java.time.Clock` (para probar el bloqueo); `ServiceLocator` pasa `Clock.systemDefaultZone()`.

## Utilidades
- `Validador`: `esDniNieValido(String)` (8 dígitos+letra con algoritmo módulo 23; NIE X/Y/Z), `esEmailValido`, `esTelefonoValido` (9 dígitos, empieza por 6,7,8 o 9),
  `esPasswordSegura(char[])`, `esUsernameValido`, `noVacio`.
- `Formatos`: `FECHA` (dd/MM/yyyy), `HORA` (HH:mm), `FECHA_HORA`, `MONEDA` (es-ES, €), métodos `fecha(LocalDate)`, `hora(...)`, `moneda(double)`, `parseFecha(String)` (Optional).

## Interfaz (Swing + FlatLaf)
`Main`: `FlatLightLaf.setup()`, ajustes `UIManager` (arc de botones/campos 10, `Component.focusWidth` 1, fuente por defecto
Segoe UI/Dialog 13), color de acento **#0F766E** (verde azulado sanitario). `Database.getInstance()` + `EsquemaBD.inicializar()`;
si falla, `JOptionPane` de error y salir. Abre `LoginFrame` en el EDT.

`Tema`: constantes de color (PRIMARIO #0F766E, PRIMARIO_OSCURO #115E59, FONDO #F4F7F8, SIDEBAR #0B3B3A, TEXTO, EXITO, AVISO, ERROR,
colores por estado de cita) y fuentes (TITULO 22 bold, SUBTITULO 15 bold, NORMAL 13).

### LoginFrame
Ventana 900x560 centrada, no redimensionable. Mitad izquierda: panel con degradado PRIMARIO→PRIMARIO_OSCURO, logo dibujado con Java2D
(cruz/corazón simple + "FisioAgenda") y lema "Tu clínica, organizada". Mitad derecha: título "Iniciar sesión", campos usuario y contraseña
(JPasswordField con botón mostrar/ocultar), botón "Entrar" (default button, Enter envía), etiqueta de error en rojo con animación de
"sacudida" horizontal del formulario si falla, texto pie "Demo: admin / Admin1234!". Login en `SwingWorker` (PBKDF2 tarda) con el botón
deshabilitado y texto "Comprobando...". Animación de entrada: fundido de opacidad de ventana 0→1 en ~300 ms si `GraphicsDevice` lo soporta
(si no, mostrar directamente). Al entrar: abrir `MainFrame` y cerrar login.

### MainFrame
1200x760, mínimo 960x600, redimensionable, centrado, título "FisioAgenda — <nombre> (<rol>)".
- **Sidebar** izquierda (fondo SIDEBAR, 220 px) con logo, botones `SidebarButton` (icono dibujado o carácter + texto) para:
  Inicio, Pacientes, Agenda, Tratamientos, Usuarios (solo ADMIN); abajo "Cambiar contraseña" y "Cerrar sesión".
  Hover y selección con transición animada de color (Timer ~150 ms). **Responsive**: si el ancho de la ventana < 1100 px el sidebar
  se colapsa a 64 px mostrando solo iconos (con tooltip); vuelve a expandirse al agrandar.
- **Cabecera** superior: título de la sección + fecha de hoy + nombre/rol del usuario.
- **Contenido**: `AnimatedCardPanel` (CardLayout con transición de deslizamiento+fundido ~220 ms al cambiar de sección).
- Atajos: `Ctrl+1..5` secciones, `Ctrl+N` nuevo elemento en la sección actual, `Ctrl+F` foco en buscador, `F5` refrescar, `Ctrl+Q` cerrar sesión.
  Cada panel implementa una interfaz `ui.panels.Seccion { String getTitulo(); void refrescar(); default void nuevo(){}; default void enfocarBusqueda(){} }`.
- Timer de inactividad (15 min) → cerrar sesión con aviso.
- `Toast` (notificación no modal abajo a la derecha, aparece/desaparece con fundido, ~2,5 s) para confirmaciones ("Paciente guardado").

### DashboardPanel (Inicio)
Saludo "Buenos días/tardes, <nombre>", 4 `StatCard` (Pacientes, Citas hoy, Pendientes 7 días, % asistencia 30 días) en `GridLayout`
que pasa de 4 a 2 columnas si el ancho < 1000. Debajo: tabla "Próximas citas de hoy" (hora, paciente, tratamiento, fisio, estado con
color) y mini gráfico de barras (Java2D) "Citas esta semana" por día. Para FISIOTERAPEUTA las cifras de citas son las suyas.

### PacientesPanel
Barra superior: buscador (filtra en vivo con `DocumentListener` + debounce 250 ms, `TableRowSorter`), botones Nuevo, Editar, Eliminar
(solo ADMIN), Historial (solo ADMIN/FISIO), Ver citas. `JTable` (DNI, Nombre, Apellidos, Teléfono, Email, Alta), ordenable, doble clic = editar,
filas alternas, carga con `SwingWorker`. Menú contextual (clic derecho) con las mismas acciones. Contador "N pacientes".
`PacienteDialog`: formulario modal con validación al guardar (borde rojo + mensaje bajo el campo inválido), Esc cancela, Enter guarda.

### AgendaPanel
Navegación de día: botones ◀ Hoy ▶, selector de fecha (JSpinner con `SpinnerDateModel` formato dd/MM/yyyy), combo de fisioterapeuta
("Todos" + fisios; FISIO por defecto él mismo). Vista: tabla de citas del día (Hora inicio–fin, Paciente, Tratamiento, Fisioterapeuta,
Estado con chip de color via renderer, Notas). Botones: Nueva cita, Editar, Marcar realizada, No asistió, Cancelar, Añadir nota (FISIO).
Pie: resumen "X citas · Y realizadas · Z pendientes".
`CitaDialog`: combo paciente (con búsqueda por texto: `JComboBox` editable filtrando o lista con campo de búsqueda), combo fisio, combo
tratamiento (muestra duración y precio), fecha (spinner), combo de **huecos libres** recalculado al cambiar fisio/fecha/tratamiento
(`CitaService.huecosLibres`), notas. Muestra hora de fin calculada.

### TratamientosPanel
Tabla (Nombre, Duración, Precio, Activo). ADMIN: Nuevo/Editar/Activar-Desactivar; resto solo lectura. `TratamientoDialog`.

### UsuariosPanel (solo ADMIN)
Tabla (Usuario, Nombre, Rol, Activo, Bloqueado). Botones: Nuevo, Editar, Restablecer contraseña, Desbloquear. `UsuarioDialog`
(contraseña + repetir con indicador de fortaleza en barra de color al crear).

### HistorialDialog
Cabecera con datos del paciente (nombre, edad calculada, teléfono, observaciones). Lista cronológica de notas (fecha, autor, EVA dolor
con barra de color 0-10, contenido) en un panel con scroll; para FISIO, formulario abajo: área de texto + `JSlider` 0-10 de dolor +
botón "Añadir nota". Gráfico de línea pequeño (Java2D) con la evolución del dolor (EVA) a lo largo de las notas.

### CambiarPasswordDialog
Actual, nueva, repetir, indicador de fortaleza; usa `AuthService.cambiarPassword`.

### Rendimiento / buenas prácticas
- Operaciones de BD fuera del EDT con `SwingWorker` en cargas de tablas (y en login). Diálogos pequeños pueden ir en EDT.
- Una sola conexión JDBC reutilizada (`Database` singleton, `synchronized` en acceso) — evita abrir/cerrar en cada consulta.
- Índices en columnas de búsqueda frecuente (ver DDL). JOIN único para cargar citas con sus relaciones (evitar N+1).
- Liberar recursos con try-with-resources. Timers de animación se detienen al terminar.
- Ningún tamaño fijo absurdo: layouts (`BorderLayout`, `GridBagLayout`, `BoxLayout`, `FlowLayout`), textos legibles, contraste AA.

## Pruebas (JUnit 5, `src/test/java`) — mínimo ~30 tests, todos en verde
- `security/PasswordHasherTest`: hash distinto con sales distintas, verificar correcto/incorrecto, sal de 16 bytes.
- `util/ValidadorTest`: DNI/NIE válidos e inválidos (parametrizados), email, teléfono, password segura.
- `dao/SqliteDaoTest`: BD en fichero temporal (`@TempDir`, propiedad `fisioagenda.db`), CRUD de paciente, JOIN de citas, cascada al
  borrar paciente, restricción UNIQUE de DNI.
- `service/AuthServiceTest`: login ok, contraseña incorrecta, bloqueo tras 5 fallos con `Clock` fijo, desbloqueo al pasar 5 min, inactivo.
- `service/CitaServiceTest`: solape mismo fisio, solape mismo paciente, fuera de horario, domingo, fecha pasada, edición sin auto-solape, huecos libres.
- `service/PacienteServiceTest`: validaciones y DNI duplicado; permisos (recepción no elimina).
- `service/HistorialServiceTest`: recepción no puede ver notas; fisio añade nota.
`Database` debe permitir reinicializarse para tests: `static void configurar(String rutaFichero)` + `getInstance()`, `cerrar()`.
