import 'package:flutter/material.dart';

import 'config.dart';
import 'tema.dart';
import 'vistas/splash.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Config.cargar(); // servidor guardado en el teléfono (si el usuario eligió uno)
  runApp(const NandayApp());
}

class NandayApp extends StatelessWidget {
  const NandayApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'Ñanday SIAA',
      theme: temaNanday(),
      home: const SplashScreen(),
    );
  }
}
