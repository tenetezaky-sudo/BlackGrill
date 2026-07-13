package servidor;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class ServidorWeb {

    public static void main(String[] args) throws Exception {
        try {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            System.out.println("[OK] Driver de SQL Server detectado.");
        } catch (ClassNotFoundException e) {
            System.out.println("[ALERTA] No se encuentra el Driver de SQL Server.");
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        System.out.println("======================================================");
        System.out.println("Servidor de Black Grill iniciado con éxito.");
        System.out.println("👉 Entra a la aplicación: http://localhost:8080/");
        System.out.println("👉 Ver usuarios registrados: http://localhost:8080/VerUsuarios");
        System.out.println("======================================================");

        server.createContext("/", new ManejadorEstatico());
        server.createContext("/Registro", new RegistroHandler());
        server.createContext("/Login", new LoginHandler());
        server.createContext("/ObtenerMesas", new ObtenerMesasHandler());
        server.createContext("/ReservarMesa", new ReservarMesaHandler());
        server.createContext("/VerUsuarios", new VerUsuariosHandler());

        server.setExecutor(null); 
        server.start();
    }

    private static void iniciarTemporizadorCaducidad(int mesaId) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    System.out.println("[TEMPORIZADOR] La mesa " + mesaId + " está reservada. Expirará en 20 segundos...");
                    Thread.sleep(20000); 

                    try (Connection con = Conexion.getConnection()) {
                        PreparedStatement ps = con.prepareStatement(
                            "UPDATE mesas SET estado = 0, usuario_dni = NULL, fecha_reserva = NULL, hora_reserva = NULL WHERE id = ?"
                        );
                        ps.setInt(1, mesaId);
                        ps.executeUpdate();
                        System.out.println("[TEMPORIZADOR] El tiempo expiró. La mesa " + mesaId + " vuelve a estar libre.");
                    } catch (Exception e) {
                        System.out.println("[ERROR CADUCIDAD] " + e.getMessage());
                    }
                } catch (InterruptedException e) {
                    System.out.println("[ERROR] Temporizador interrumpido.");
                }
            }
        }).start();
    }

    private static Map<String, String> parsearPostData(String body) {
        Map<String, String> mapa = new HashMap<>();
        if (body == null || body.isEmpty()) return mapa;
        String[] pares = body.split("&");
        for (String par : pares) {
            String[] kv = par.split("=");
            if (kv.length == 2) {
                mapa.put(kv[0], kv[1]);
            }
        }
        return mapa;
    }

    static class ManejadorEstatico implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String ruta = exchange.getRequestURI().getPath();
            if (ruta.equals("/")) ruta = "/index.html";

            String contenido = "";
            String tipoContenido = "text/plain";

            if (ruta.equals("/index.html")) {
                tipoContenido = "text/html;charset=UTF-8";
                contenido = "<!DOCTYPE html>\n" +
                        "<html lang=\"es\">\n" +
                        "<head>\n" +
                        "    <meta charset=\"UTF-8\">\n" +
                        "    <title>Black Grill - Reservas</title>\n" +
                        "    <link rel=\"stylesheet\" href=\"style.css\">\n" +
                        "</head>\n" +
                        "<body>\n" +
                        "    <div id=\"seccion-auth\" class=\"auth-container\">\n" +
                        "        <div class=\"box-auth\" id=\"box-login\">\n" +
                        "            <h2>Black Grill - Iniciar Sesión</h2>\n" +
                        "            <input type=\"text\" id=\"login-dni\" placeholder=\"Ingresa tu DNI\">\n" +
                        "            <input type=\"password\" id=\"login-pass\" placeholder=\"Tu Contraseña\">\n" +
                        "            <button onclick=\"login()\">Entrar al Sistema</button>\n" +
                        "            <p>¿No estás registrado? <a href=\"#\" onclick=\"alternarAuth(false)\">Regístrate aquí</a></p>\n" +
                        "        </div>\n" +
                        "        <div class=\"box-auth hidden\" id=\"box-registro\">\n" +
                        "            <h2>Registro de Usuario</h2>\n" +
                        "            <input type=\"text\" id=\"reg-dni\" placeholder=\"Número de DNI\">\n" +
                        "            <input type=\"text\" id=\"reg-nombre\" placeholder=\"Nombre Completo\">\n" +
                        "            <input type=\"text\" id=\"reg-celular\" placeholder=\"Número de Celular\">\n" +
                        "            <input type=\"password\" id=\"reg-pass\" placeholder=\"Crea tu Contraseña\">\n" +
                        "            <button onclick=\"registrar()\">Crear Cuenta</button>\n" +
                        "            <p>¿Ya tienes cuenta? <a href=\"#\" onclick=\"alternarAuth(true)\">Inicia Sesión</a></p>\n" +
                        "        </div>\n" +
                        "    </div>\n" +
                        "    <div id=\"seccion-restaurante\" class=\"restaurante-container hidden\">\n" +
                        "        <div class=\"mapa-sala\">\n" +
                        "            <div class=\"header-sala\">\n" +
                        "                <h2>Plano de Mesas</h2>\n" +
                        "                <span id=\"saludo-usuario\"></span>\n" +
                        "            </div>\n" +
                        "            <div id=\"contenedor-mesas\" class=\"grid-mesas\"></div>\n" +
                        "        </div>\n" +
                        "        <div class=\"panel-control\">\n" +
                        "            <h2>Detalles de Reserva</h2>\n" +
                        "            <div class=\"leyenda\">\n" +
                        "                <div class=\"item-leyenda\"><span class=\"cuadro disponible\"></span> Disponible</div>\n" +
                        "                <div class=\"item-leyenda\"><span class=\"cuadro ocupada\"></span> Ocupada</div>\n" +
                        "                <div class=\"item-leyenda\"><span class=\"cuadro seleccionada\"></span> Tu Selección</div>\n" +
                        "            </div>\n" +
                        "            <hr>\n" +
                        "            <div id=\"info-mesa\"><p>Selecciona una mesa disponible en el plano.</p></div>\n" +
                        "            <div id=\"inputs-reserva\" class=\"hidden\">\n" +
                        "                <label>Fecha de Reserva:</label>\n" +
                        "                <input type=\"date\" id=\"reserva-fecha\">\n" +
                        "                <label>Horario:</label>\n" +
                        "                <input type=\"time\" id=\"reserva-hora\">\n" +
                        "                \n" +
                        "                <div class=\"seccion-pago\">\n" +
                        "                    <p class=\"costo-comision\"><strong>Comisión de Reserva:</strong> S/ 60.00</p>\n" +
                        "                    <label>Método de Pago:</label>\n" +
                        "                    <select id=\"metodo-pago\" onchange=\"cambiarMetodoPago()\">\n" +
                        "                        <option value=\"\">-- Seleccione un método --</option>\n" +
                        "                        <option value=\"Yape\">Yape</option>\n" +
                        "                        <option value=\"Tarjeta Debito\">Tarjeta de Débito</option>\n" +
                        "                        <option value=\"Tarjeta Credito\">Tarjeta de Crédito</option>\n" +
                        "                    </select>\n" +
                        "                    <div id=\"detalle-pago-dinamico\" style=\"margin-top:10px;\"></div>\n" +
                        "                </div>\n" +
                        "            </div>\n" +
                        "            <button id=\"btn-reservar\" disabled>Pagar y Confirmar Reserva</button>\n" +
                        "            <button onclick=\"logout()\" style=\"background-color: #ef4444; margin-top: 15px;\">Cerrar Sesión</button>\n" +
                        "        </div>\n" +
                        "    </div>\n" +
                        "    <script src=\"script.js\"></script>\n" +
                        "</body>\n" +
                        "</html>";
            } else if (ruta.equals("/style.css")) {
                tipoContenido = "text/css";
                contenido = "body { font-family: 'Segoe UI', sans-serif; background-image: url('https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?q=80&w=1470'); background-size: cover; background-position: center; margin: 0; padding: 20px; display: flex; justify-content: center; align-items: center; min-height: 95vh; }\n" +
                        "body::before { content: ''; position: absolute; top:0; left:0; width:100%; height:100%; background: rgba(0,0,0,0.5); z-index: -1; }\n" +
                        ".hidden { display: none !important; }\n" +
                        ".auth-container { background: rgba(255,255,255,0.95); padding: 40px; border-radius: 16px; box-shadow: 0 10px 25px rgba(0,0,0,0.3); max-width: 400px; width: 100%; }\n" +
                        ".box-auth h2 { margin-top: 0; color: #0f172a; text-align: center; font-size: 24px; }\n" +
                        ".box-auth input { width: 100%; padding: 12px; margin: 10px 0; border: 1px solid #cbd5e1; border-radius: 8px; box-sizing: border-box; }\n" +
                        ".restaurante-container { display: flex; gap: 30px; max-width: 1100px; width: 100%; z-index: 1; }\n" +
                        ".mapa-sala, .panel-control { background: rgba(255,255,255,0.96); padding: 25px; border-radius: 16px; box-shadow: 0 8px 20px rgba(0,0,0,0.2); }\n" +
                        ".mapa-sala { flex: 2; background-image: url('https://images.unsplash.com/photo-1552566626-52f8b828add9?q=80&w=1470'); background-size: cover; border-radius: 12px; opacity: 0.98; }\n" +
                        ".panel-control { flex: 1; display: flex; flex-direction: column; }\n" +
                        ".panel-control input, .panel-control select { width: 100%; padding: 10px; margin: 5px 0 15px 0; border: 1px solid #cbd5e1; border-radius: 6px; box-sizing: border-box; }\n" +
                        ".costo-comision { background: #f1f5f9; padding: 10px; border-radius: 6px; border-left: 4px solid #22c55e; color: #1e293b; margin: 10px 0; }\n" +
                        ".header-sala { background: rgba(255,255,255,0.9); padding: 10px; border-radius: 8px; display: flex; justify-content: space-between; align-items: center; }\n" +
                        ".grid-mesas { display: grid; grid-template-columns: repeat(auto-fill, minmax(110px, 1fr)); gap: 25px; margin-top: 20px; background: rgba(255,255,255,0.85); padding: 20px; border-radius: 12px; }\n" +
                        ".mesa { aspect-ratio: 1; border-radius: 50%; display: flex; flex-direction: column; align-items: center; justify-content: center; font-weight: bold; cursor: pointer; border: 3px solid #64748b; background-color: #f8fafc; color: #1e293b; transition: all 0.2s; }\n" +
                        ".mesa.disponible:hover { transform: scale(1.1); background-color: #dcfce7; border-color: #22c55e; }\n" +
                        ".mesa.ocupada { background-color: #fee2e2; color: #991b1b; border-color: #ef4444; cursor: not-allowed; }\n" +
                        ".mesa.seleccionada { background-color: #dbeafe; color: #1e40af; border-color: #3b82f6; transform: scale(1.1); }\n" +
                        ".leyenda { display: flex; flex-direction: column; gap: 8px; margin: 15px 0; font-size: 14px; }\n" +
                        ".cuadro { width: 18px; height: 18px; border-radius: 4px; display: inline-block; vertical-align: middle; margin-right: 8px; }\n" +
                        ".cuadro.disponible { background: #f8fafc; border: 2px solid #64748b; }\n" +
                        ".cuadro.ocupada { background: #fee2e2; border: 2px solid #ef4444; }\n" +
                        ".cuadro.seleccionada { background: #dbeafe; border: 2px solid #3b82f6; }\n" +
                        "button { background-color: #22c55e; color: white; border: none; padding: 14px; border-radius: 8px; font-size: 16px; font-weight: bold; cursor: pointer; width: 100%; margin-top: auto; }\n" +
                        "button:disabled { background-color: #cbd5e1; cursor: not-allowed; }\n" +
                        "hr { border: 0; border-top: 1px solid #e2e8f0; margin: 15px 0; }";
            } else if (ruta.equals("/script.js")) {
                tipoContenido = "application/javascript";
                contenido = "let mesaSeleccionada = null;\n" +
                        "let usuarioLogueadoDni = '';\n" +
                        "function inicializarVista() {\n" +
                        "    document.getElementById('seccion-auth').classList.remove('hidden');\n" +
                        "    document.getElementById('seccion-restaurante').classList.add('hidden');\n" +
                        "}\n" +
                        "setTimeout(inicializarVista, 100);\n" +
                        "function alternarAuth(mostrarLogin) {\n" +
                        "    document.getElementById('box-login').classList.toggle('hidden', !mostrarLogin);\n" +
                        "    document.getElementById('box-registro').classList.toggle('hidden', mostrarLogin);\n" +
                        "}\n" +
                        "function registrar() {\n" +
                        "    const dni = document.getElementById('reg-dni').value;\n" +
                        "    const nombre = document.getElementById('reg-nombre').value;\n" +
                        "    const celular = document.getElementById('reg-celular').value;\n" +
                        "    const password = document.getElementById('reg-pass').value;\n" +
                        "    if(!dni || !nombre || !celular || !password) return alert('Rellena todos los campos');\n" +
                        "    fetch('/Registro', { method: 'POST', body: `dni=${dni}&nombre=${nombre}&celular=${celular}&password=${password}` })\n" +
                        "    .then(r => r.json()).then(data => { alert(data.message); if(data.success) alternarAuth(true); });\n" +
                        "}\n" +
                        "function login() {\n" +
                        "    const dni = document.getElementById('login-dni').value;\n" +
                        "    const password = document.getElementById('login-pass').value;\n" +
                        "    if(!dni || !password) return alert('Ingresa DNI y contraseña');\n" +
                        "    fetch('/Login', { method: 'POST', body: `dni=${dni}&password=${password}` }\n" +
                        "    ).then(r => r.json()).then(data => {\n" +
                        "        if(data.success) {\n" +
                        "            usuarioLogueadoDni = dni;\n" +
                        "            document.getElementById('seccion-auth').classList.add('hidden');\n" +
                        "            document.getElementById('seccion-restaurante').classList.remove('hidden');\n" +
                        "            document.getElementById('saludo-usuario').innerText = data.message;\n" +
                        "            cargarMesas();\n" +
                        "        } else { alert(data.message); }\n" +
                        "    });\n" +
                        "}\n" +
                        "function logout() {\n" +
                        "    usuarioLogueadoDni = ''; mesaSeleccionada = null;\n" +
                        "    document.getElementById('login-dni').value = ''; document.getElementById('login-pass').value = '';\n" +
                        "    actualizarPanel(null); inicializarVista();\n" +
                        "}\n" +
                        "function cargarMesas() {\n" +
                        "    fetch('/ObtenerMesas').then(r => r.json()).then(mesas => renderizarMesas(mesas));\n" +
                        "}\n" +
                        "function renderizarMesas(mesas) {\n" +
                        "    const contenedor = document.getElementById('contenedor-mesas');\n" +
                        "    contenedor.innerHTML = '';\n" +
                        "    mesas.forEach(mesa => {\n" +
                        "        const div = document.createElement('div');\n" +
                        "        div.classList.add('mesa');\n" +
                        "        div.innerHTML = `<span>Mesa ${mesa.numero}</span><span class=\"capacidad\">Cap: ${mesa.capacidad}p</span>`;\n" +
                        "        if (mesa.reservada) { div.classList.add('ocupada'); } else {\n" +
                        "            div.classList.add('disponible');\n" +
                        "            div.addEventListener('click', () => seleccionarMesa(mesa, div));\n" +
                        "        }\n" +
                        "        contenedor.appendChild(div);\n" +
                        "    });\n" +
                        "}\n" +
                        "function seleccionarMesa(mesa, elementoDOM) {\n" +
                        "    document.querySelectorAll('.mesa').forEach(m => m.classList.remove('seleccionada'));\n" +
                        "    if (mesaSeleccionada && mesaSeleccionada.id === mesa.id) {\n" +
                        "        mesaSeleccionada = null; actualizarPanel(null);\n" +
                        "    } else {\n" +
                        "        mesaSeleccionada = mesa; elementoDOM.classList.add('seleccionada'); actualizarPanel(mesa);\n" +
                        "    }\n" +
                        "}\n" +
                        "function actualizarPanel(mesa) {\n" +
                        "    const info = document.getElementById('info-mesa');\n" +
                        "    const btn = document.getElementById('btn-reservar');\n" +
                        "    const inputs = document.getElementById('inputs-reserva');\n" +
                        "    if (mesa) {\n" +
                        "        info.innerHTML = `<h3>Mesa seleccionada: ${mesa.numero}</h3>`;\n" +
                        "        inputs.classList.remove('hidden');\n" +
                        "        btn.disabled = false;\n" +
                        "    } else {\n" +
                        "        info.innerHTML = '<p>Selecciona una mesa disponible en el plano.</p>';\n" +
                        "        inputs.classList.add('hidden');\n" +
                        "        btn.disabled = true;\n" +
                        "    }\n" +
                        "}\n" +
                        "function cambiarMetodoPago() {\n" +
                        "    const metodo = document.getElementById('metodo-pago').value;\n" +
                        "    const divDetalle = document.getElementById('detalle-pago-dinamico');\n" +
                        "    if(metodo === 'Yape') {\n" +
                        "        divDetalle.innerHTML = '<input type=\"text\" id=\"pago-celular\" placeholder=\"Número de Yape (9xxxxxxxx)\" maxlength=\"9\">';\n" +
                        "    } else if(metodo.includes('Tarjeta')) {\n" +
                        "        divDetalle.innerHTML = '<input type=\"text\" id=\"pago-tarjeta\" placeholder=\"Número de Tarjeta (16 dígitos)\" maxlength=\"16\">' +\n" +
                        "                               '<input type=\"text\" id=\"pago-cvv\" placeholder=\"CVV (3 dígitos)\" maxlength=\"3\" style=\"width:48%; display:inline-block; margin-right:4%;\">' +\n" +
                        "                               '<input type=\"text\" id=\"pago-exp\" placeholder=\"MM/AA\" style=\"width:48%; display:inline-block;\">';\n" +
                        "    } else {\n" +
                        "        divDetalle.innerHTML = '';\n" +
                        "    }\n" +
                        "}\n" +
                        "document.addEventListener('DOMContentLoaded', () => {\n" +
                        "    document.getElementById('btn-reservar').addEventListener('click', () => {\n" +
                        "        const fecha = document.getElementById('reserva-fecha').value;\n" +
                        "        const hora = document.getElementById('reserva-hora').value;\n" +
                        "        const metodo = document.getElementById('metodo-pago').value;\n" +
                        "        if(!fecha || !hora) return alert('Por favor, selecciona fecha y hora para tu reserva.');\n" +
                        "        if(!metodo) return alert('Por favor, selecciona un método de pago para abonar los S/ 60.');\n" +
                        "        if (mesaSeleccionada) {\n" +
                        "            fetch('/ReservarMesa', { method: 'POST', body: `id=${mesaSeleccionada.id}&dni=${usuarioLogueadoDni}&fecha=${fecha}&hora=${hora}&metodoPago=${metodo}` }\n" +
                        "            ).then(r => r.json()).then(data => {\n" +
                        "                alert(data.message); if(data.success) { mesaSeleccionada = null; actualizarPanel(null); }\n" +
                        "                cargarMesas();\n" +
                        "            });\n" +
                        "        }\n" +
                        "    });\n" +
                        "});";
            }

            if (!contenido.isEmpty()) {
                byte[] bytes = contenido.getBytes("UTF-8");
                exchange.getResponseHeaders().set("Content-Type", tipoContenido);
                exchange.sendResponseHeaders(200, bytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(bytes);
                os.close();
            } else {
                exchange.sendResponseHeaders(404, 0);
                exchange.close();
            }
        }
    }

    static class RegistroHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
            InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
            BufferedReader br = new BufferedReader(isr);
            Map<String, String> datos = parsearPostData(br.readLine());
            String res = "{\"success\": false, \"message\": \"Faltan datos\"}";
            try (Connection con = Conexion.getConnection()) {
                PreparedStatement ps = con.prepareStatement("INSERT INTO usuarios (dni, nombre, celular, password) VALUES (?, ?, ?, ?)");
                ps.setString(1, datos.get("dni"));
                ps.setString(2, datos.get("nombre"));
                ps.setString(3, datos.get("celular"));
                ps.setString(4, datos.get("password"));
                ps.executeUpdate();
                res = "{\"success\": true, \"message\": \"¡Usuario registrado con éxito!\"}";
            } catch (Exception e) {
                res = "{\"success\": false, \"message\": \"El DNI ya existe o error en Base de Datos.\"}";
            }
            byte[] resp = res.getBytes("UTF-8");
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.getResponseBody().close();
        }
    }

    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
            InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
            BufferedReader br = new BufferedReader(isr);
            Map<String, String> datos = parsearPostData(br.readLine());
            String res = "{\"success\": false, \"message\": \"DNI o contraseña incorrectos\"}";
            try (Connection con = Conexion.getConnection()) {
                PreparedStatement ps = con.prepareStatement("SELECT nombre FROM usuarios WHERE dni = ? AND password = ?");
                ps.setString(1, datos.get("dni"));
                ps.setString(2, datos.get("password"));
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    res = "{\"success\": true, \"message\": \"Bienvenido a Black Grill, " + rs.getString("nombre") + "\"}";
                }
            } catch (Exception e) {
                res = "{\"success\": false, \"message\": \"Error: " + e.getMessage() + "\"}";
            }
            byte[] resp = res.getBytes("UTF-8");
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.getResponseBody().close();
        }
    }

    static class ObtenerMesasHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
            StringBuilder json = new StringBuilder();
            json.append("[");
            try (Connection con = Conexion.getConnection()) {
                PreparedStatement ps = con.prepareStatement("SELECT id, numero, capacidad, estado FROM mesas");
                ResultSet rs = ps.executeQuery();
                while(rs.next()) {
                    json.append("{")
                        .append("\"id\":").append(rs.getInt("id")).append(",")
                        .append("\"numero\":\"").append(rs.getString("numero")).append("\",")
                        .append("\"capacidad\":").append(rs.getInt("capacidad")).append(",")
                        .append("\"reservada\":").append(rs.getInt("estado") == 1 ? "true" : "false")
                        .append("},");
                }
                if (json.length() > 1) json.setLength(json.length() - 1);
            } catch (Exception e) {
                System.out.println("Error SQL: " + e.getMessage());
            }
            json.append("]");
            byte[] respuesta = json.toString().getBytes("UTF-8");
            exchange.sendResponseHeaders(200, respuesta.length);
            exchange.getResponseBody().write(respuesta);
            exchange.getResponseBody().close();
        }
    }

    static class ReservarMesaHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
            InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
            BufferedReader br = new BufferedReader(isr);
            Map<String, String> datos = parsearPostData(br.readLine());
            String respuestaJson = "{\"success\": false, \"message\": \"Error\"}";
            
            if (datos.containsKey("id") && datos.containsKey("dni")) {
                int mesaId = Integer.parseInt(datos.get("id"));
                String dni = datos.get("dni");
                String fecha = datos.get("fecha");
                String hora = datos.get("hora");
                String metodoPago = datos.get("metodoPago"); // Recibimos el método elegido

                try (Connection con = Conexion.getConnection()) {
                    PreparedStatement checkPs = con.prepareStatement("SELECT COUNT(*) FROM mesas WHERE usuario_dni = ? AND estado = 1");
                    checkPs.setString(1, dni);
                    ResultSet rs = checkPs.executeQuery();
                    if (rs.next() && rs.getInt(1) > 0) {
                        respuestaJson = "{\"success\": false, \"message\": \"Lo sentimos, solo puedes reservar una mesa por usuario en Black Grill.\"}";
                    } else {
                        PreparedStatement ps = con.prepareStatement(
                            "UPDATE mesas SET estado = 1, usuario_dni = ?, fecha_reserva = ?, hora_reserva = ? WHERE id = ? AND estado = 0"
                        );
                        ps.setString(1, dni);
                        ps.setString(2, fecha);
                        ps.setString(3, hora);
                        ps.setInt(4, mesaId);
                        
                        int filas = ps.executeUpdate();
                        if (filas > 0) {
                            respuestaJson = "{\"success\": true, \"message\": \"Transacción Exitosa de S/ 60.00 mediante " + metodoPago + ". ¡Mesa " + mesaId + " reservada para el " + fecha + " a las " + hora + "!\"}";
                            iniciarTemporizadorCaducidad(mesaId);
                        } else {
                            respuestaJson = "{\"success\": false, \"message\": \"La mesa ya ha sido ocupada.\"}";
                        }
                    }
                } catch (Exception e) {
                    respuestaJson = "{\"success\": false, \"message\": \"Error SQL: " + e.getMessage() + "\"}";
                }
            }
            byte[] respuesta = respuestaJson.getBytes("UTF-8");
            exchange.sendResponseHeaders(200, respuesta.length);
            exchange.getResponseBody().write(respuesta);
            exchange.getResponseBody().close();
        }
    }

    static class VerUsuariosHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "text/html;charset=UTF-8");
            StringBuilder html = new StringBuilder();
            
            html.append("<html><head><meta charset='UTF-8'><title>Black Grill - Usuarios</title>");
            html.append("<style>body{font-family:sans-serif; padding:30px; background:#f8fafc;} table{width:100%; border-collapse:collapse; background:white; margin-top:20px;} th,td{border:1px solid #cbd5e1; padding:12px; text-align:left;} th{background:#0f172a; color:white;}</style>");
            html.append("</head><body>");
            html.append("<h2>Lista de Usuarios Registrados en Black Grill</h2>");
            html.append("<table><tr><th>DNI</th><th>Nombre Completo</th><th>Celular</th><th>Contraseña (Fines Educativos)</th></tr>");

            try (Connection con = Conexion.getConnection()) {
                PreparedStatement ps = con.prepareStatement("SELECT dni, nombre, celular, password FROM usuarios");
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    html.append("<tr>")
                        .append("<td>").append(rs.getString("dni")).append("</td>")
                        .append("<td>").append(rs.getString("nombre")).append("</td>")
                        .append("<td>").append(rs.getString("celular")).append("</td>")
                        .append("<td>").append(rs.getString("password")).append("</td>")
                        .append("</tr>");
                }
            } catch (Exception e) {
                html.append("<tr><td colspan='4' style='color:red;'>Error al conectar a la BD: ").append(e.getMessage()).append("</td></tr>");
            }

            html.append("</table></body></html>");

            byte[] respuesta = html.toString().getBytes("UTF-8");
            exchange.sendResponseHeaders(200, respuesta.length);
            exchange.getResponseBody().write(respuesta);
            exchange.getResponseBody().close();
        }
    }
}