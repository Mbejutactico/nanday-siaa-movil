import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../comunes.dart';
import '../listado.dart';

/// Formulario de insumo. Lo usan "Nuevo insumo" y "Modificar insumo":
/// cada pantalla decide qué hacer con los datos en [alGuardar] (POST o PUT).
/// Mismos campos y etiquetas que el modal de insumosvista.jsp.
class FormularioInsumo extends StatefulWidget {
  /// Datos actuales del insumo (al modificar). null = formulario vacío (nuevo).
  final Map<String, dynamic>? inicial;

  /// Unidades de medida para el desplegable: cada una con 'id' y 'nombre'.
  final List<Map<String, dynamic>> unidades;
  final String textoBoton;
  final Future<void> Function(Map<String, String> datos) alGuardar;

  const FormularioInsumo({
    super.key,
    this.inicial,
    required this.unidades,
    required this.textoBoton,
    required this.alGuardar,
  });

  @override
  State<FormularioInsumo> createState() => _FormularioInsumoState();
}

class _FormularioInsumoState extends State<FormularioInsumo> {
  final GlobalKey<FormState> _formulario = GlobalKey<FormState>();
  final TextEditingController txtCodigo = TextEditingController();
  final TextEditingController txtDescripcion = TextEditingController();
  final TextEditingController txtPrecio = TextEditingController();
  int _idUnidad = 0; // 0 = sin unidad
  bool _activo = true;
  bool _guardando = false;

  /// Opciones del desplegable: "Sin unidad" + las unidades activas
  /// (+ la unidad actual del insumo si fue desactivada, para no perderla).
  final List<DropdownMenuItem<int>> _opciones = <DropdownMenuItem<int>>[];

  @override
  void initState() {
    super.initState();
    final Map<String, dynamic>? i = widget.inicial;
    if (i != null) {
      txtCodigo.text = (i['codigo'] ?? '').toString();
      txtDescripcion.text = (i['descripcion'] ?? '').toString();
      // el servidor lo envía como texto ("1500.00"): 1500.00 -> "1500" ; 1500.50 -> "1500.5"
      final num? precio = num.tryParse('${i['precio_compra'] ?? ''}');
      if (precio != null) {
        txtPrecio.text = precio == precio.roundToDouble() ? precio.round().toString() : precio.toString();
      }
      _idUnidad = (i['id_unidad'] as num?)?.toInt() ?? 0;
      _activo = i['activo'] != false;
    }

    _opciones.add(const DropdownMenuItem<int>(value: 0, child: Text('Sin unidad')));
    bool estaLaActual = _idUnidad == 0;
    for (final Map<String, dynamic> u in widget.unidades) {
      final int id = (u['id'] as num?)?.toInt() ?? 0;
      if (id <= 0) continue;
      if (id == _idUnidad) estaLaActual = true;
      _opciones.add(DropdownMenuItem<int>(value: id, child: Text((u['nombre'] ?? '').toString())));
    }
    if (!estaLaActual) {
      final String nombre = (i?['unidad'] ?? 'Unidad actual').toString();
      _opciones.add(DropdownMenuItem<int>(value: _idUnidad, child: Text('$nombre (inactiva)')));
    }
  }

  Future<void> _guardar() async {
    if (_guardando) return;
    if (!_formulario.currentState!.validate()) return;
    setState(() => _guardando = true);
    try {
      await widget.alGuardar(<String, String>{
        'codigo': txtCodigo.text.trim(),
        'descripcion': txtDescripcion.text.trim(),
        'precio_compra': txtPrecio.text.trim().replaceAll(',', '.'),
        'id_unidad': _idUnidad > 0 ? '$_idUnidad' : '',
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

  /// Dos campos en la misma fila en pantallas anchas (como .col-md-6 en la web).
  Widget _par(bool ancho, Widget a, Widget b) {
    if (!ancho) return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: <Widget>[a, b]);
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[Expanded(child: a), const SizedBox(width: 14), Expanded(child: b)],
    );
  }

  @override
  Widget build(BuildContext context) {
    final bool ancho = MediaQuery.sizeOf(context).width >= 560;
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
                _par(
                  ancho,
                  CampoFormulario(
                    etiqueta: 'Código',
                    obligatorio: true,
                    child: TextFormField(
                      controller: txtCodigo,
                      maxLength: 50,
                      textCapitalization: TextCapitalization.characters,
                      textInputAction: TextInputAction.next,
                      decoration: const InputDecoration(hintText: 'Ej.: INS010', counterText: ''),
                      validator: (String? v) => (v == null || v.trim().isEmpty) ? 'El código es obligatorio' : null,
                    ),
                  ),
                  CampoFormulario(
                    etiqueta: 'Unidad Medida',
                    child: DropdownButtonFormField<int>(
                      initialValue: _idUnidad,
                      isExpanded: true,
                      items: _opciones,
                      onChanged: (int? v) => setState(() => _idUnidad = v ?? 0),
                    ),
                  ),
                ),
                CampoFormulario(
                  etiqueta: 'Descripción',
                  obligatorio: true,
                  child: TextFormField(
                    controller: txtDescripcion,
                    maxLength: 300,
                    minLines: 2,
                    maxLines: 4,
                    textCapitalization: TextCapitalization.sentences,
                    decoration: const InputDecoration(counterText: ''),
                    validator: (String? v) =>
                        (v == null || v.trim().isEmpty) ? 'La descripción es obligatoria' : null,
                  ),
                ),
                _par(
                  ancho,
                  CampoFormulario(
                    etiqueta: 'Precio de Compra (Gs.)',
                    child: TextFormField(
                      controller: txtPrecio,
                      keyboardType: const TextInputType.numberWithOptions(decimal: true),
                      inputFormatters: <TextInputFormatter>[
                        FilteringTextInputFormatter.allow(RegExp(r'[0-9.,]')),
                      ],
                      decoration: const InputDecoration(hintText: '0'),
                      validator: (String? v) {
                        final String t = (v ?? '').trim().replaceAll(',', '.');
                        if (t.isEmpty) return null; // vacío = 0
                        if (!RegExp(r'^[0-9]{1,10}(\.[0-9]{1,2})?$').hasMatch(t)) {
                          return 'Número mayor o igual a cero, sin puntos de miles';
                        }
                        return null;
                      },
                    ),
                  ),
                  CampoFormulario(
                    etiqueta: 'Activo',
                    child: SelectorActivo(valor: _activo, alCambiar: (bool v) => setState(() => _activo = v)),
                  ),
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
    txtCodigo.dispose();
    txtDescripcion.dispose();
    txtPrecio.dispose();
    super.dispose();
  }
}

/// Convierte la respuesta de GET /UnidadServlet en la lista que usa el formulario.
List<Map<String, dynamic>> unidadesDesde(dynamic datos) {
  final List<Map<String, dynamic>> lista = <Map<String, dynamic>>[];
  if (datos is List) {
    for (final dynamic u in datos) {
      if (u is Map) {
        lista.add(Map<String, dynamic>.from(u));
      }
    }
  }
  return lista;
}
