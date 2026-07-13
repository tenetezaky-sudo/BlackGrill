let mesaSeleccionada = null;

// Intercambiar formularios de Login y Registro
function alternarAuth(mostrarLogin) {
    if(mostrarLogin) {
        document.getElementById('box-login').classList.remove('hidden');
        document.getElementById('box-registro').classList.add('hidden');
    } else {
        document.getElementById('box-login').classList.add('hidden');
        document.getElementById('box-registro').classList.remove('hidden');
    }
}

// ACCIÓN: Registrar Usuario
function registrar() {
    const dni = document.getElementById('reg-dni').value;
    const nombre = document.getElementById('reg-nombre').value;
    const celular = document.getElementById('reg-celular').value;
    const password = document.getElementById('reg-pass').value;

    if(!dni || !nombre || !celular || !password) return alert("Completa todos los campos");

    fetch('/Registro', {
        method: 'POST',
        body: `dni=${dni}&nombre=${nombre}&celular=${celular}&password=${password}`
    })
    .then(r => r.json())
    .then(data => {
        alert(data.message);
        if(data.success) alternarAuth(true); // Mandar a login
    });
}

// ACCIÓN: Iniciar Sesión
function login() {
    const dni = document.getElementById('login-dni').value;
    const password = document.getElementById('login-pass').value;

    if(!dni || !password) return alert("Ingresa tus credenciales");

    fetch('/Login', {
        method: 'POST',
        body: `dni=${dni}&password=${password}`
    })
    .then(r => r.json())
    .then(data => {
        if(data.success) {
            document.getElementById('seccion-auth').classList.add('hidden');
            document.getElementById('seccion-restaurante').classList.remove('hidden');
            document.getElementById('saludo-usuario').innerText = data.message;
            cargarMesas(); // Cargar el mapa de mesas ahora que entró
        } else {
            alert(data.value || data.message);
        }
    });
}

function logout() {
    document.getElementById('seccion-auth').classList.remove('hidden');
    document.getElementById('seccion-restaurante').classList.add('hidden');
}

// CARGAR MESAS DESDE SQL SERVER
function cargarMesas() {
    fetch('/ObtenerMesas')
        .then(r => r.json())
        .then(mesas => renderizarMesas(mesas));
}

function renderizarMesas(mesas) {
    const contenedor = document.getElementById('contenedor-mesas');
    contenedor.innerHTML = '';

    mesas.forEach(mesa => {
        const div = document.createElement('div');
        div.classList.add('mesa');
        div.innerHTML = `<span>${mesa.numero}</span><span class="capacidad">${mesa.capacidad} pers</span>`;

        if (mesa.reservada) {
            div.classList.add('ocupada'); // Pinta rojo, bloqueado por CSS
        } else {
            div.classList.add('disponible'); // Pinta azul
            div.addEventListener('click', () => seleccionarMesa(mesa, div));
        }
        contenedor.appendChild(div);
    });
}

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

function actualizarPanel(mesa) {
    const info = document.getElementById('info-mesa');
    const btn = document.getElementById('btn-reservar');
    if (mesa) {
        info.innerHTML = `<h3>${mesa.numero}</h3><p>Mesa de <strong>${mesa.capacidad} personas</strong> disponible.</p>`;
        btn.disabled = false;
    } else {
        info.innerHTML = `<p>Selecciona una mesa disponible en el plano.</p>`;
        btn.disabled = true;
    }
}

document.getElementById('btn-reservar').addEventListener('click', () => {
    if (mesaSeleccionada) {
        fetch('/ReservarMesa', { method: 'POST', body: `id=${mesaSeleccionada.id}` })
        .then(r => r.json())
        .then(data => {
            alert(data.message);
            mesaSeleccionada = null;
            actualizarPanel(null);
            cargarMesas();
        });
    }
});