import 'package:flutter/material.dart';

import '../api.dart';
import '../config.dart';
import '../sesion.dart';
import '../tema.dart';
import 'comunes.dart';
import 'menu.dart';

/// Inicio de sesión con los mismos usuarios (email y contraseña) del sistema web.
/// El rol y los centros del usuario (los mismos de la web) deciden qué módulos ve.
/// Diseño copiado de index.jsp: degradado verde oscuro, logo + "Ñanday SIAA" con línea
/// naranja, tarjeta blanca con franja naranja arriba y el mensaje de error dentro de la tarjeta.
class Login extends StatefulWidget {
  const Login({super.key});

  @override
  State<Login> createState() => _LoginState();
}

class _LoginState extends State<Login> {
  final GlobalKey<FormState> _formulario = GlobalKey<FormState>();
  final TextEditingController txtEmail = TextEditingController();
  final TextEditingController txtPassword = TextEditingController();
  bool _ocultar = true;
  bool _ingresando = false;
  String? _error;

  Future<void> ingresar() async {
    if (_ingresando) return; // evita un doble envío (Enter + botón)
    if (!_formulario.currentState!.validate()) return;
    setState(() {
      _ingresando = true;
      _error = null;
    });
    try {
      // Inicio de sesión en la API (mismos usuarios que la web) y luego los datos y permisos del usuario
      await Api.login(txtEmail.text.trim(), txtPassword.text);
      await Sesion.cargar();
      if (!mounted) return;
      Navigator.of(context).pushReplacement(
        MaterialPageRoute<void>(builder: (BuildContext context) => const Menu()),
      );
    } catch (e) {
      // si el login funcionó pero falló la carga de los datos, no queda un token suelto
      Api.olvidarSesion();
      if (!mounted) return;
      setState(() => _error = e is ApiError ? e.mensaje : 'Error al iniciar sesión. Intente de nuevo.');
    } finally {
      if (mounted) {
        setState(() => _ingresando = false);
      }
    }
  }

  /// Cambia la dirección del servidor (IP y puerto de Tomcat) sin tener que compilar de nuevo.
  Future<void> _cambiarServidor() async {
    final String? nuevo = await showDialog<String>(
      context: context,
      builder: (BuildContext d) => const _DialogoServidor(),
    );
    if (nuevo == null) return;
    await Config.guardar(nuevo);
    if (!mounted) return;
    setState(() => _error = null);
    avisar(context, 'Servidor: ${Config.urlBase}');
  }

  /// Campo con el ícono a la derecha en un recuadro verde claro (.input-group-text de la web).
  InputDecoration _decoracion(String sugerencia, IconData icono, {Widget? extra}) {
    return InputDecoration(
      hintText: sugerencia,
      contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 14),
      // Alto fijo del campo: así el recuadro verde de la derecha encaja justo dentro del borde.
      suffixIconConstraints: const BoxConstraints(minHeight: 50, maxHeight: 50),
      suffixIcon: Row(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          ?extra,
          // El recuadro se separa 2 px del borde (arriba, derecha y abajo) para no taparlo,
          // incluso cuando el campo tiene foco y el borde se hace más grueso.
          Padding(
            padding: const EdgeInsets.fromLTRB(0, 2, 2, 2),
            child: Container(
              width: 44,
              height: 46,
              decoration: const BoxDecoration(
                color: Nd.verdeSuave,
                border: Border(left: BorderSide(color: Nd.verdeBorde)),
                borderRadius: BorderRadius.horizontal(right: Radius.circular(8)),
              ),
              child: Icon(icono, size: 18, color: Nd.verdeTexto),
            ),
          ),
        ],
      ),
      enabledBorder: const OutlineInputBorder(
        borderRadius: BorderRadius.all(Radius.circular(10)),
        borderSide: BorderSide(color: Nd.verdeBorde),
      ),
      focusedBorder: const OutlineInputBorder(
        borderRadius: BorderRadius.all(Radius.circular(10)),
        borderSide: BorderSide(color: Nd.primario, width: 1.6),
      ),
      border: const OutlineInputBorder(borderRadius: BorderRadius.all(Radius.circular(10))),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Container(
        decoration: const BoxDecoration(gradient: Nd.degradadoLogin),
        child: SafeArea(
          child: Center(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(24),
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 400),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: <Widget>[
                    // ---- .login-logo: icono + "Ñanday SIAA", línea naranja debajo ----
                    Container(
                      padding: const EdgeInsets.only(bottom: 15),
                      margin: const EdgeInsets.only(bottom: 20),
                      decoration: const BoxDecoration(
                        border: Border(bottom: BorderSide(color: Nd.loginNaranja, width: 2)),
                      ),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: <Widget>[
                          Image.asset('assets/imagenes/icono.png', height: 80, width: 80),
                          const SizedBox(width: 12),
                          const Flexible(
                            child: Text.rich(
                              TextSpan(
                                children: <InlineSpan>[
                                  TextSpan(
                                    text: 'Ñanday',
                                    style: TextStyle(
                                      color: Nd.loginNaranjaClaro,
                                      fontSize: 32,
                                      fontWeight: FontWeight.w700,
                                    ),
                                  ),
                                  TextSpan(text: ' SIAA'),
                                ],
                              ),
                              style: TextStyle(
                                color: Colors.white,
                                fontSize: 28,
                                fontWeight: FontWeight.w700,
                                letterSpacing: 1,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),

                    // ---- .card: blanca, franja naranja arriba, sombra fuerte ----
                    Container(
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(12),
                        boxShadow: const <BoxShadow>[
                          BoxShadow(color: Color(0x59000000), blurRadius: 25, offset: Offset(0, 10)),
                        ],
                      ),
                      clipBehavior: Clip.antiAlias,
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: <Widget>[
                          Container(height: 4, color: Nd.loginNaranja),
                          Padding(
                            padding: const EdgeInsets.all(24),
                            child: Form(
                              key: _formulario,
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.stretch,
                                children: <Widget>[
                                  // .login-box-msg
                                  Container(
                                    padding: const EdgeInsets.only(bottom: 10),
                                    margin: const EdgeInsets.only(bottom: 20),
                                    decoration: const BoxDecoration(
                                      border: Border(bottom: BorderSide(color: Nd.verdeSuave)),
                                    ),
                                    child: const Text(
                                      'Acceder al sistema',
                                      textAlign: TextAlign.center,
                                      style: TextStyle(
                                        color: Nd.verdeTexto,
                                        fontWeight: FontWeight.w700,
                                        fontSize: 16,
                                      ),
                                    ),
                                  ),
                                  TextFormField(
                                    controller: txtEmail,
                                    keyboardType: TextInputType.emailAddress,
                                    textInputAction: TextInputAction.next,
                                    autocorrect: false,
                                    decoration: _decoracion('Correo', Icons.email),
                                    validator: (String? v) {
                                      if (v == null || v.trim().isEmpty) return 'Ingrese su correo';
                                      if (!v.contains('@')) return 'El correo no es válido';
                                      return null;
                                    },
                                  ),
                                  const SizedBox(height: 16),
                                  TextFormField(
                                    controller: txtPassword,
                                    obscureText: _ocultar,
                                    textInputAction: TextInputAction.done,
                                    onFieldSubmitted: (String v) => ingresar(),
                                    decoration: _decoracion(
                                      'Contraseña',
                                      Icons.lock,
                                      extra: IconButton(
                                        tooltip: _ocultar ? 'Mostrar contraseña' : 'Ocultar contraseña',
                                        icon: Icon(
                                          _ocultar ? Icons.visibility : Icons.visibility_off,
                                          size: 20,
                                          color: Nd.apagado,
                                        ),
                                        onPressed: () => setState(() => _ocultar = !_ocultar),
                                      ),
                                    ),
                                    validator: (String? v) {
                                      if (v == null || v.isEmpty) return 'Ingrese su contraseña';
                                      return null;
                                    },
                                  ),
                                  const SizedBox(height: 22),
                                  SizedBox(
                                    height: 48,
                                    child: FilledButton(
                                      onPressed: _ingresando ? null : ingresar,
                                      child: _ingresando
                                          ? const Row(
                                              mainAxisAlignment: MainAxisAlignment.center,
                                              children: <Widget>[
                                                SizedBox(
                                                  width: 18,
                                                  height: 18,
                                                  child: CircularProgressIndicator(
                                                    strokeWidth: 2,
                                                    color: Colors.white,
                                                  ),
                                                ),
                                                SizedBox(width: 10),
                                                Text('Verificando...'),
                                              ],
                                            )
                                          : const Text('Ingresar'),
                                    ),
                                  ),
                                  if (_error != null) ...<Widget>[
                                    const SizedBox(height: 18),
                                    Alerta(texto: _error!, error: true),
                                  ],
                                ],
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 14),
                    TextButton.icon(
                      onPressed: _ingresando ? null : _cambiarServidor,
                      style: TextButton.styleFrom(foregroundColor: Colors.white70),
                      icon: const Icon(Icons.dns, size: 16),
                      label: Text(
                        'Servidor: ${Config.urlBase}',
                        textAlign: TextAlign.center,
                        style: const TextStyle(fontSize: 12),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  @override
  void dispose() {
    txtEmail.dispose();
    txtPassword.dispose();
    super.dispose();
  }
}

/// Diálogo para escribir la IP y el puerto del servidor (ej.: 192.168.1.50:8080).
class _DialogoServidor extends StatefulWidget {
  const _DialogoServidor();

  @override
  State<_DialogoServidor> createState() => _DialogoServidorState();
}

class _DialogoServidorState extends State<_DialogoServidor> {
  late final TextEditingController _txt = TextEditingController(text: Config.servidor);
  final GlobalKey<FormState> _formulario = GlobalKey<FormState>();

  @override
  void dispose() {
    _txt.dispose();
    super.dispose();
  }

  void _guardar() {
    if (_formulario.currentState!.validate()) Navigator.of(context).pop(_txt.text.trim());
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Servidor', style: TextStyle(fontWeight: FontWeight.w700, color: Nd.titulo, fontSize: 18)),
      content: Form(
        key: _formulario,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            const Text(
              'IP y puerto de la PC o del servidor donde corre Tomcat con APInanday. '
              'El celular tiene que estar en la misma red.',
              style: TextStyle(height: 1.4, color: Nd.pizarra),
            ),
            const SizedBox(height: 14),
            TextFormField(
              controller: _txt,
              autofocus: true,
              autocorrect: false,
              keyboardType: TextInputType.url,
              decoration: const InputDecoration(hintText: '192.168.1.50:8080'),
              validator: (String? v) => Config.validar(v ?? ''),
              onFieldSubmitted: (_) => _guardar(),
            ),
            const SizedBox(height: 8),
            Text(
              'Por defecto: ${Config.servidorPorDefecto}',
              style: const TextStyle(fontSize: 12, color: Nd.apagado),
            ),
          ],
        ),
      ),
      actions: <Widget>[
        TextButton(
          onPressed: () => Navigator.of(context).pop(''),
          child: const Text('Predeterminado'),
        ),
        OutlinedButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Cancelar')),
        FilledButton(onPressed: _guardar, child: const Text('Guardar')),
      ],
    );
  }
}
