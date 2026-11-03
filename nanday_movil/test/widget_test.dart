import 'package:flutter_test/flutter_test.dart';

import 'package:nanday_movil/api.dart';
import 'package:nanday_movil/config.dart';
import 'package:nanday_movil/vistas/comunes.dart';

void main() {
  test('guaranies() agrega separador de miles', () {
    expect(guaranies(0), '0');
    expect(guaranies(950), '950');
    expect(guaranies(1500), '1.500');
    expect(guaranies(1500000), '1.500.000');
    expect(guaranies('25000'), '25.000');
    expect(guaranies(-150), '-150');
    expect(guaranies(-1500), '-1.500');
    expect(guaranies(null), '0');
  });

  test('la URL apunta a la aplicación web', () {
    expect(Config.urlBase.endsWith(Config.aplicacion), isTrue);
  });

  test('textoPlano() limpia los avisos HTML de la web', () {
    expect(Api.textoPlano("<div class='alert alert-success'>Datos actualizados con &eacute;xito</div>"), contains('Datos actualizados'));
    expect(Api.textoPlano('<b>A</b><br>B'), 'A\nB');
    expect(Api.textoPlano('Datos actualizados con &eacute;xito'), 'Datos actualizados con éxito');
    expect(Api.textoPlano('A&ntilde;o &#225; &#xE9; &amp;lt;'), 'Año á é &lt;');
  });

  test('legible() y fechaCorta()', () {
    expect(legible('EN_PROCESO'), 'En proceso');
    expect(fechaCorta('2026-10-09'), '09/10/2026');
  });
}
