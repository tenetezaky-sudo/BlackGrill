let mesaSeleccionada = null;

// Configurar los campos visuales al cargar la pantalla
function inicializarVista() {
    document.getElementById('seccion-auth').classList.remove('hidden');
    document.getElementById('seccion-restaurante').classList.add('hidden');
    document.getElementById('box-login').classList.remove('hidden');
    document.getElementById('box-registro').classList.add('hidden');
}

// Arrancar vista automáticamente
inicializarVista();

// Cambiar entre Login y Registro
function alternarAuth(mostrarLogin) {
    if(mostrarLogin) {
        document.getElementById('box-login').classList.remove('hidden');
        document.getElementById('box-registro').classList.add('hidden');
    } else {
        document.getElementById('box-login').classList.add('hidden');
        document.getElementById('box-registro').classList.remove('hidden');
    }
}

// REGISTRAR USUARIO (Envía DNI, nombre, celular, pass)
function registrar() {
    const dni = document.getElementById('reg-dni').value;
    const nombre = document.getElementById('reg-nombre').value;
    const celular = document.getElementById('reg-celular').value;
    const password = document.getElementById('reg-pass').value;

    if(!dni || !nombre || !celular || !password) return alert("Por favor rellena todos los campos");

    fetch('/Registro', {
        method: 'POST',
        body: `dni=${dni}&nombre=${nombre}&celular=${celular}&password=${password}`
    })
    .then(r => r.json())
    .then(data => {
        alert(data.message);
        if(data.success) alternarAuth(true); // Mandar al login si funcionó
    });
}

// INICIAR SESIÓN
function login() {
    const dni = document.getElementById('login-dni').value;
    const password = document.getElementById('login-pass').value;

    if(!dni || !password) return alert("Ingresa DNI y contraseña");

    fetch('/Login', {
        method: 'POST',
        body: `dni=${dni}&password=${password}`
    })
    .then(r => r.json())
    .then(data => {
        if(data.success) {
            // Cambiar de pantallas
            document.getElementById('seccion-auth').classList.add('hidden');
            document.getElementById('seccion-restaurante').classList.remove('hidden');
            document.getElementById('saludo-usuario').innerText = data.message;
            
            // Cargar el esquema de mesas de la BD
            cargarMesas(); 
        } else {
            alert(data.message);
        }
    });
}

// CERRAR SESIÓN
function logout() {
    document.getElementById('login-dni').value = "";
    document.getElementById('login-pass').value = "";
    mesaSeleccionada = null;
    actualizarPanel(null);
    inicializarVista();
}

// CARGAR MESAS DESDE JAVA + SQL SERVER
function cargarMesas() {
    fetch('/ObtenerMesas')
        .then(r => r.json())
        .then(mesas => renderizarMesas(mesas))
        .catch(err => console.error("Error al obtener mesas:", err));
}

// Dibujar las mesas redondas en la cuadricula
function renderizarMesas(mesas) {
    const contenedor = document.getElementById('contenedor-mesas');
    contenedor.innerHTML = '';

    mesas.forEach(mesa => {
        const div = document.createElement('div');
        div.classList.add('mesa');
        // Imprime el número y la capacidad asignada (2 o 4 personas)
        div.innerHTML = `<span>${mesa.numero}</span><span class="capacidad">Cap: ${mesa.capacidad}p</span>`;

        if (mesa.reservada) {
            div.classList.add('ocupada'); // Se bloquea y pinta de rojo automáticamente
        } else {
            div.classList.add('disponible'); // Queda en azul claro
            div.addEventListener('click', () => seleccionarMesa(mesa, div));
        }
        contenedor.appendChild(div);
    });
}

// Controlar la mesa seleccionada
function seleccionarMesa(mesa, elementoDOM) {
    document.querySelectorAll('.mesa').forEach(m => m.classList.remove('seleccionada'));
    if (mesaSeleccionada && mesaSeleccionada.id === mesa.id) {
        mesaSeleccionada = null;
        actualizarPanel(null);
    } else {
        mesaSeleccionada = mesa;
        elementoDOM.classList.add('seleccionada');
        actualizarPanel(mesa);
    }
}

// Cambiar la descripción lateral según la mesa elegida
function actualizarPanel(mesa) {
    const info = document.getElementById('info-mesa');
    const btn = document.getElementById('btn-reservar');
    if (mesa) {
        info.innerHTML = `<h3>${mesa.numero}</h3><p>Mesa ideal para <strong>${mesa.capacidad} personas</strong> seleccionada.</p>`;
        btn.disabled = false;
    } else {
        info.innerHTML = `<p>Selecciona una mesa disponible en el plano.</p>`;
        btn.disabled = true;
    }
}

// Enviar la confirmación de la reserva al servidor
document.getElementById('btn-reservar').addEventListener('click', () => {
    if (mesaSeleccionada) {
        fetch('/ReservarMesa', { 
            method: 'POST', 
            body: `id=${mesaSeleccionada.id}` 
        })
        .then(r => r.json())
        .then(data => {
            alert(data.message);
            mesaSeleccionada = null;
            actualizarPanel(null);
            cargarMesas(); // Recargar mapa para bloquear la mesa recién reservada
        });
    }
});