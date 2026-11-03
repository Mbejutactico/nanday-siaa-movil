import 'dart:async';

import 'package:flutter/material.dart';

import '../tema.dart';
import 'login.dart';

/// Pantalla de presentación con el logo de Ñanday S.A., sobre el mismo degradado del login web.
class SplashScreen extends StatefulWidget {
  const SplashScreen({super.key});

  @override
  State<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen> {
  Timer? _reloj;

  @override
  void initState() {
    super.initState();
    _reloj = Timer(const Duration(milliseconds: 1200), _irAlLogin);
  }

  void _irAlLogin() {
    if (!mounted) return;
    Navigator.of(context).pushReplacement(
      PageRouteBuilder<void>(
        transitionDuration: const Duration(milliseconds: 450),
        pageBuilder: (BuildContext context, Animation<double> a, Animation<double> b) => const Login(),
        transitionsBuilder: (BuildContext context, Animation<double> a, Animation<double> b, Widget hijo) =>
            FadeTransition(opacity: a, child: hijo),
      ),
    );
  }

  @override
  void dispose() {
    _reloj?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Container(
        decoration: const BoxDecoration(gradient: Nd.degradadoLogin),
        child: SafeArea(
          child: Center(
            child: Padding(
              padding: const EdgeInsets.all(28),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: <Widget>[
                  // Logo completo en una tarjeta blanca con la franja naranja del login
                  ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 380),
                    child: Container(
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(14),
                        boxShadow: const <BoxShadow>[
                          BoxShadow(color: Color(0x59000000), blurRadius: 25, offset: Offset(0, 10)),
                        ],
                      ),
                      clipBehavior: Clip.antiAlias,
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: <Widget>[
                          Container(height: 4, color: Nd.loginNaranja),
                          Padding(
                            padding: const EdgeInsets.fromLTRB(24, 22, 24, 22),
                            child: Image.asset('assets/imagenes/logo_nanday.png', fit: BoxFit.contain),
                          ),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 28),
                  const Text.rich(
                    TextSpan(
                      children: <InlineSpan>[
                        TextSpan(
                          text: 'Ñanday',
                          style: TextStyle(color: Nd.loginNaranjaClaro, fontSize: 30, fontWeight: FontWeight.w700),
                        ),
                        TextSpan(text: ' SIAA'),
                      ],
                    ),
                    style: TextStyle(color: Colors.white, fontSize: 26, fontWeight: FontWeight.w700, letterSpacing: 1),
                  ),
                  const SizedBox(height: 6),
                  const Text(
                    'Sistema de Gestión de Activos e Insumos',
                    textAlign: TextAlign.center,
                    style: TextStyle(color: Colors.white70, fontSize: 14),
                  ),
                  const SizedBox(height: 36),
                  const SizedBox(
                    width: 26,
                    height: 26,
                    child: CircularProgressIndicator(color: Nd.loginNaranjaClaro, strokeWidth: 3),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
