import 'package:flutter/foundation.dart' show defaultTargetPlatform, kIsWeb, TargetPlatform;
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../api.dart';
import '../sesion.dart';
import '../tema.dart';
import 'comunes.dart';

/// MI PERFIL: datos del usuario, foto (cámara en el celular o archivo) y cambio de contraseña.
/// Usa APInanday/PerfilServlet, con las mismas reglas que la web. La foto se guarda en una carpeta
/// propia de la app: solo se ve en la app móvil (la foto de la web no cambia).
class PerfilPantalla extends StatefulWidget {
  const PerfilPantalla({super.key});

  @override
  State<PerfilPantalla> createState() => _PerfilPantallaState();
}

class _PerfilPantallaState extends State<PerfilPantalla> {
  final GlobalKey<FormState> _fDatos = GlobalKey<FormState>();
  final GlobalKey<FormState> _fClave = GlobalKey<FormState>();
  late final TextEditingController _nombre = TextEditingController(text: Sesion.nombre);
  late final TextEditingController _apellido = TextEditingController(text: Sesion.apellido);
  final TextEditingController _actual = TextEditingController();
  final TextEditingController _nueva = TextEditingController();
  final TextEditingController _confirmar = TextEditingController();
  bool _ocupado = false;
  int _versionFoto = 0;

  bool get _esCelular => !kIsWeb && (defaultTargetPlatform == TargetPlatform.android || defaultTargetPlatform == TargetPlatform.iOS);

  @override
  void dispose() {
    for (final TextEditingController c in <TextEditingController>[_nombre, _apellido, _actual, _nueva, _confirmar]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _ejecutar(Future<void> Function() accion) async {
    if (_ocupado) return;
    setState(() => _ocupado = true);
    try {
      await accion();
    } catch (e) {
      if (mounted) manejarError(context, e);
    } finally {
      if (mounted) setState(() => _ocupado = false);
    }
  }

  Future<void> _cambiarFoto(ImageSource origen) => _ejecutar(() async {
        final XFile? archivo = await ImagePicker().pickImage(source: origen, maxWidth: 1024, maxHeight: 1024, imageQuality: 85);
        if (archivo == null) return;
        final List<int> bytes = await archivo.readAsBytes();
        // Tipo real según el contenido (la web valida que sea JPG o PNG de verdad)
        final bool esPng = bytes.length > 4 && bytes[0] == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47;
        final bool esJpg = bytes.length > 3 && bytes[0] == 0xFF && bytes[1] == 0xD8 && bytes[2] == 0xFF;
        if (!esPng && !esJpg) {
          throw ApiError('La imagen debe ser JPG o PNG');
        }
        if (bytes.length > 2 * 1024 * 1024) {
          throw ApiError('La imagen supera el tamaño máximo permitido (2 MB)');
        }
        final String ruta = await Api.subirFoto(bytes, esPng ? 'foto.png' : 'foto.jpg', esPng ? 'image/png' : 'image/jpeg');
        Sesion.foto = ruta;
        if (!mounted) return;
        setState(() => _versionFoto++);
        avisar(context, 'Foto de perfil actualizada');
      });

  Future<void> _guardarDatos() async {
    if (!_fDatos.currentState!.validate()) return;
    await _ejecutar(() async {
      final String msg = await Api.html('perfilcontrolador.jsp', 'ActualizarDatos',
          <String, String>{'nombre': _nombre.text.trim(), 'apellido': _apellido.text.trim()});
      Sesion.nombre = _nombre.text.trim();
      Sesion.apellido = _apellido.text.trim();
      if (mounted) avisar(context, msg);
    });
  }

  Future<void> _cambiarClave() async {
    if (!_fClave.currentState!.validate()) return;
    await _ejecutar(() async {
      final String msg = await Api.html('perfilcontrolador.jsp', 'CambiarPassword', <String, String>{
        'password_actual': _actual.text,
        'password_nueva': _nueva.text,
        'password_confirmar': _confirmar.text,
      });
      _actual.clear();
      _nueva.clear();
      _confirmar.clear();
      if (mounted) avisar(context, msg);
    });
  }

  void _elegirOrigenFoto() {
    showModalBottomSheet<void>(
      context: context,
      builder: (BuildContext h) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            if (_esCelular)
              ListTile(
                leading: const Icon(Icons.photo_camera, color: Nd.primario),
                title: const Text('Sacar una foto'),
                onTap: () {
                  Navigator.of(h).pop();
                  _cambiarFoto(ImageSource.camera);
                },
              ),
            ListTile(
              leading: const Icon(Icons.photo_library, color: Nd.primario),
              title: Text(_esCelular ? 'Elegir de la galería' : 'Elegir un archivo de imagen'),
              onTap: () {
                Navigator.of(h).pop();
                _cambiarFoto(ImageSource.gallery);
              },
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final String inicial = Sesion.nombre.trim().isEmpty ? '?' : Sesion.nombre.trim()[0].toUpperCase();
    return PaginaApp(
      titulo: 'Mi Perfil',
      migas: const <String>['Inicio', 'Mi perfil'],
      hijos: <Widget>[
        Tarjeta(
          acento: Nd.primario,
          child: Padding(
            padding: const EdgeInsets.all(18),
            child: Row(
              children: <Widget>[
                GestureDetector(
                  onTap: _ocupado ? null : _elegirOrigenFoto,
                  child: Stack(
                    children: <Widget>[
                      Container(
                        padding: const EdgeInsets.all(3),
                        decoration: const BoxDecoration(color: Nd.primario, shape: BoxShape.circle),
                        child: CircleAvatar(
                          radius: 42,
                          backgroundColor: Colors.white,
                          child: Sesion.foto.isEmpty
                              ? Text(inicial, style: const TextStyle(fontSize: 34, color: Nd.primario, fontWeight: FontWeight.w700))
                              : ClipOval(
                                  child: Image.network(
                                    '${Api.urlFoto(Sesion.foto)}&v=$_versionFoto',
                                    key: ValueKey<int>(_versionFoto),
                                    headers: Api.cabecerasImagen,
                                    width: 84,
                                    height: 84,
                                    fit: BoxFit.cover,
                                    errorBuilder: (BuildContext c, Object e, StackTrace? s) =>
                                        Text(inicial, style: const TextStyle(fontSize: 34, color: Nd.primario, fontWeight: FontWeight.w700)),
                                  ),
                                ),
                        ),
                      ),
                      Positioned(
                        right: 0,
                        bottom: 0,
                        child: Container(
                          padding: const EdgeInsets.all(6),
                          decoration: const BoxDecoration(color: Nd.acento, shape: BoxShape.circle),
                          child: Icon(_esCelular ? Icons.photo_camera : Icons.edit, color: Colors.white, size: 16),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: <Widget>[
                      Text(Sesion.nombreCompleto, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700, color: Nd.titulo)),
                      Text(Sesion.email, style: const TextStyle(color: Nd.apagado)),
                      const SizedBox(height: 6),
                      Insignia(texto: Sesion.rolNombre.isEmpty ? Sesion.rol : Sesion.rolNombre, color: Nd.profundo),
                      if (Sesion.centro.isNotEmpty) ...<Widget>[
                        const SizedBox(height: 4),
                        Text('Centro: ${Sesion.centro}', style: const TextStyle(color: Nd.pizarra, fontSize: 13)),
                      ],
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
        espacio,
        Tarjeta(
          child: Form(
            key: _fDatos,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: <Widget>[
                const CabeceraTarjeta(titulo: 'Mis datos', icono: Icons.person),
                CuerpoTarjeta(<Widget>[
                  CampoFormulario(
                    etiqueta: 'Nombre',
                    obligatorio: true,
                    child: TextFormField(
                      controller: _nombre,
                      maxLength: 100,
                      decoration: const InputDecoration(counterText: ''),
                      validator: (String? v) => (v ?? '').trim().isEmpty ? 'Obligatorio' : null,
                    ),
                  ),
                  CampoFormulario(
                    etiqueta: 'Apellido',
                    obligatorio: true,
                    child: TextFormField(
                      controller: _apellido,
                      maxLength: 100,
                      decoration: const InputDecoration(counterText: ''),
                      validator: (String? v) => (v ?? '').trim().isEmpty ? 'Obligatorio' : null,
                    ),
                  ),
                  BotonPrincipal(texto: 'Guardar datos', icono: Icons.save, ocupado: _ocupado, alTocar: _guardarDatos),
                ]),
              ],
            ),
          ),
        ),
        espacio,
        Tarjeta(
          child: Form(
            key: _fClave,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: <Widget>[
                const CabeceraTarjeta(titulo: 'Cambiar contraseña', icono: Icons.lock),
                CuerpoTarjeta(<Widget>[
                  CampoFormulario(
                    etiqueta: 'Contraseña actual',
                    obligatorio: true,
                    child: TextFormField(
                      controller: _actual,
                      obscureText: true,
                      validator: (String? v) => (v ?? '').isEmpty ? 'Obligatorio' : null,
                    ),
                  ),
                  CampoFormulario(
                    etiqueta: 'Nueva contraseña',
                    obligatorio: true,
                    child: TextFormField(
                      controller: _nueva,
                      obscureText: true,
                      decoration: const InputDecoration(hintText: 'Al menos 6 caracteres'),
                      validator: (String? v) => (v ?? '').length < 6 ? 'Debe tener al menos 6 caracteres' : null,
                    ),
                  ),
                  CampoFormulario(
                    etiqueta: 'Confirmar nueva contraseña',
                    obligatorio: true,
                    child: TextFormField(
                      controller: _confirmar,
                      obscureText: true,
                      validator: (String? v) => v != _nueva.text ? 'No coincide con la nueva contraseña' : null,
                    ),
                  ),
                  BotonPrincipal(texto: 'Cambiar contraseña', icono: Icons.key, ocupado: _ocupado, alTocar: _cambiarClave),
                ]),
              ],
            ),
          ),
        ),
      ],
    );
  }
}
