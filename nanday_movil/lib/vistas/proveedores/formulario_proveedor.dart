import 'package:flutter/material.dart';

import '../comunes.dart';
import '../listado.dart';

/// Formulario de proveedor. Lo usan "Nuevo proveedor" y "Modificar proveedor":
/// cada pantalla decide qué hacer con los datos en [alGuardar] (POST o PUT).
/// Mismos campos y etiquetas que el modal de proveedorvista.jsp.
class FormularioProveedor extends StatefulWidget {
  /// Datos actuales del proveedor (al modificar). null = formulario vacío (nuevo).
  final Map<String, dynamic>? inicial;
  final String textoBoton;
  final Future<void> Function(Map<String, String> datos) alGuardar;

  const FormularioProveedor({
    super.key,
    this.inicial,
    required this.textoBoton,
    required this.alGuardar,
  });

  @override
  State<FormularioProveedor> createState() => _FormularioProveedorState();
}

class _FormularioProveedorState extends State<FormularioProveedor> {
  final GlobalKey<FormState> _formulario = GlobalKey<FormState>();
  final TextEditingController txtRazonSocial = TextEditingController();
  final TextEditingController txtRuc = TextEditingController();
  final TextEditingController txtDireccion = TextEditingController();
  final TextEditingController txtTelefono = TextEditingController();
  final TextEditingController txtEmail = TextEditingController();
  bool _activo = true;
  bool _guardando = false;

  @override
  void initState() {
    super.initState();
    final Map<String, dynamic>? p = widget.inicial;
    if (p != null) {
      txtRazonSocial.text = (p['razon_social'] ?? '').toString();
      txtRuc.text = (p['ruc'] ?? '').toString();
      txtDireccion.text = (p['direccion'] ?? '').toString();
      txtTelefono.text = (p['telefono'] ?? '').toString();
      txtEmail.text = (p['email'] ?? '').toString();
      _activo = p['activo'] != false;
    }
  }

  Future<void> _guardar() async {
    if (_guardando) return;
    if (!_formulario.currentState!.validate()) return;
    setState(() => _guardando = true);
    try {
      await widget.alGuardar(<String, String>{
        'razon_social': txtRazonSocial.text.trim(),
        'ruc': txtRuc.text.trim(),
        'direccion': txtDireccion.text.trim(),
        'telefono': txtTelefono.text.trim(),
        'email': txtEmail.text.trim(),
        'activo': _activo ? 'true' : 'false',
      });
    } catch (e) {
      if (!mounted) return;
      manejarError(context, e);
    } finally {
      if (mounted) {
        setState(() => _guardando = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    // En pantallas anchas, RUC y Teléfono van uno al lado del otro (como en la web)
    final bool ancho = MediaQuery.sizeOf(context).width >= 560;
    final Widget ruc = CampoFormulario(
      etiqueta: 'RUC',
      obligatorio: true,
      child: TextFormField(
        controller: txtRuc,
        maxLength: 20,
        textInputAction: TextInputAction.next,
        decoration: const InputDecoration(hintText: 'Ej.: 80012345-6', counterText: ''),
        validator: (String? v) => (v == null || v.trim().isEmpty) ? 'El RUC es obligatorio' : null,
      ),
    );
    final Widget telefono = CampoFormulario(
      etiqueta: 'Teléfono',
      child: TextFormField(
        controller: txtTelefono,
        maxLength: 50,
        keyboardType: TextInputType.phone,
        textInputAction: TextInputAction.next,
        decoration: const InputDecoration(counterText: ''),
      ),
    );

    return Form(
      key: _formulario,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: <Widget>[
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 18, 16, 2),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: <Widget>[
                CampoFormulario(
                  etiqueta: 'Razón Social',
                  obligatorio: true,
                  child: TextFormField(
                    controller: txtRazonSocial,
                    maxLength: 200,
                    textCapitalization: TextCapitalization.words,
                    textInputAction: TextInputAction.next,
                    decoration: const InputDecoration(counterText: ''),
                    validator: (String? v) =>
                        (v == null || v.trim().isEmpty) ? 'La razón social es obligatoria' : null,
                  ),
                ),
                if (ancho)
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: <Widget>[
                      Expanded(child: ruc),
                      const SizedBox(width: 14),
                      Expanded(child: telefono),
                    ],
                  )
                else ...<Widget>[ruc, telefono],
                CampoFormulario(
                  etiqueta: 'Dirección',
                  child: TextFormField(
                    controller: txtDireccion,
                    maxLength: 300,
                    textInputAction: TextInputAction.next,
                    decoration: const InputDecoration(counterText: ''),
                  ),
                ),
                CampoFormulario(
                  etiqueta: 'Email',
                  child: TextFormField(
                    controller: txtEmail,
                    maxLength: 150,
                    keyboardType: TextInputType.emailAddress,
                    autocorrect: false,
                    decoration: const InputDecoration(counterText: ''),
                    validator: (String? v) {
                      final String t = (v ?? '').trim();
                      if (t.isNotEmpty && !RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$').hasMatch(t)) {
                        return 'El email no es válido';
                      }
                      return null;
                    },
                  ),
                ),
                CampoFormulario(
                  etiqueta: 'Activo',
                  child: SelectorActivo(valor: _activo, alCambiar: (bool v) => setState(() => _activo = v)),
                ),
              ],
            ),
          ),
          PieFormulario(guardando: _guardando, textoGuardar: widget.textoBoton, alGuardar: _guardar),
        ],
      ),
    );
  }

  @override
  void dispose() {
    txtRazonSocial.dispose();
    txtRuc.dispose();
    txtDireccion.dispose();
    txtTelefono.dispose();
    txtEmail.dispose();
    super.dispose();
  }
}
