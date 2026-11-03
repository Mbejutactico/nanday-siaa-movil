import 'package:flutter/material.dart';

/// Colores del sistema web Ñanday SIAA (dist/css/nanday-bold.css y el login de index.jsp).
/// Si cambia la paleta de la web, se cambia aquí y toda la app queda igual.
class Nd {
  Nd._();

  // --- nanday-bold.css (:root) ---
  static const Color oscuro = Color(0xFF0A2E1A); // --nd-dark   (menú lateral)
  static const Color primario = Color(0xFF2E7D32); // --nd-primary
  static const Color profundo = Color(0xFF1B5E20); // --nd-deep
  static const Color acento = Color(0xFFF57C00); // --nd-accent (naranja)
  static const Color acentoProfundo = Color(0xFFE65100); // --nd-accent-deep
  static const Color info = Color(0xFF1E88E5); // --nd-info
  static const Color peligro = Color(0xFFC62828); // --nd-danger
  static const Color pizarra = Color(0xFF374151); // --nd-slate (texto)
  static const Color apagado = Color(0xFF6B7280); // --nd-muted
  static const Color fondo = Color(0xFFF3F4F6); // --nd-bg
  static const Color borde = Color(0xFFE5E7EB); // --nd-border
  static const Color titulo = Color(0xFF111827); // títulos h1 / card-title
  static const Color teal = Color(0xFF00897B);

  // --- login (index.jsp) ---
  static const Color loginDesde = Color(0xFF1E2420);
  static const Color loginHasta = Color(0xFF1B4D2E);
  static const Color loginNaranja = Color(0xFFC2410C);
  static const Color loginNaranjaClaro = Color(0xFFFB923C);
  static const Color verdeSuave = Color(0xFFE8F2EA); // input-group-text, alert-info del tablero
  static const Color verdeBorde = Color(0xFFD5DED7);
  static const Color verdeTexto = Color(0xFF1B4D2E);

  // --- menú lateral ---
  static const Color menuTexto = Color(0xFFD1D5DB);

  /// Degradado del fondo del login (y del splash).
  static const LinearGradient degradadoLogin = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: <Color>[loginDesde, loginHasta],
  );

  /// Sombra suave de las tarjetas (box-shadow: 0 4px 14px rgba(0,0,0,.05)).
  static const List<BoxShadow> sombra = <BoxShadow>[
    BoxShadow(color: Color(0x0D000000), blurRadius: 14, offset: Offset(0, 4)),
  ];
}

/// Tema de la app con la tipografía Inter y los colores de la web.
ThemeData temaNanday() {
  final ColorScheme esquema = ColorScheme.fromSeed(
    seedColor: Nd.primario,
    primary: Nd.primario,
    secondary: Nd.acento,
    error: Nd.peligro,
    surface: Colors.white,
  );
  final OutlineInputBorder bordeCampo = OutlineInputBorder(
    borderRadius: BorderRadius.circular(10),
    borderSide: const BorderSide(color: Color(0xFFCED4DA)),
  );
  final RoundedRectangleBorder botonRedondeado = RoundedRectangleBorder(borderRadius: BorderRadius.circular(10));

  final ThemeData base = ThemeData(
    useMaterial3: true,
    colorScheme: esquema,
    fontFamily: 'Inter',
    scaffoldBackgroundColor: Nd.fondo,
    // Barra superior blanca con línea inferior, como el topbar de la web
    appBarTheme: const AppBarTheme(
      backgroundColor: Colors.white,
      foregroundColor: Nd.pizarra,
      surfaceTintColor: Colors.white,
      elevation: 0,
      scrolledUnderElevation: 0,
      centerTitle: false,
      shape: Border(bottom: BorderSide(color: Nd.borde)),
      titleTextStyle: TextStyle(fontFamily: 'Inter', fontSize: 17, fontWeight: FontWeight.w600, color: Nd.pizarra),
    ),
    cardTheme: CardThemeData(
      color: Colors.white,
      surfaceTintColor: Colors.white,
      elevation: 0,
      margin: EdgeInsets.zero,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(14),
        side: const BorderSide(color: Nd.borde),
      ),
    ),
    // .form-control: radio 10, foco naranja
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: Colors.white,
      isDense: true,
      contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 13),
      hintStyle: const TextStyle(color: Color(0xFF9CA3AF)),
      border: bordeCampo,
      enabledBorder: bordeCampo,
      focusedBorder: bordeCampo.copyWith(borderSide: const BorderSide(color: Nd.acento, width: 1.6)),
      errorBorder: bordeCampo.copyWith(borderSide: const BorderSide(color: Nd.peligro)),
      focusedErrorBorder: bordeCampo.copyWith(borderSide: const BorderSide(color: Nd.peligro, width: 1.6)),
      prefixIconColor: Nd.apagado,
    ),
    // .btn-primary
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        backgroundColor: Nd.primario,
        foregroundColor: Colors.white,
        disabledBackgroundColor: Nd.primario.withValues(alpha: 0.55),
        disabledForegroundColor: Colors.white,
        shape: botonRedondeado,
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        textStyle: const TextStyle(fontFamily: 'Inter', fontWeight: FontWeight.w600, fontSize: 15),
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        foregroundColor: Nd.pizarra,
        side: const BorderSide(color: Color(0xFFCED4DA)),
        shape: botonRedondeado,
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        textStyle: const TextStyle(fontFamily: 'Inter', fontWeight: FontWeight.w600, fontSize: 15),
      ),
    ),
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(foregroundColor: Nd.primario, shape: botonRedondeado),
    ),
    dialogTheme: DialogThemeData(
      backgroundColor: Colors.white,
      surfaceTintColor: Colors.white,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
    ),
    snackBarTheme: SnackBarThemeData(
      behavior: SnackBarBehavior.floating,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
    ),
    progressIndicatorTheme: const ProgressIndicatorThemeData(color: Nd.primario),
    dividerTheme: const DividerThemeData(color: Nd.borde, thickness: 1, space: 1),
  );
  // Texto en gris pizarra y títulos casi negros, como en la web
  return base.copyWith(
    textTheme: base.textTheme.apply(bodyColor: Nd.pizarra, displayColor: Nd.titulo),
  );
}
