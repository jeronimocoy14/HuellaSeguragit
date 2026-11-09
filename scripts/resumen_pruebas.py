"""
Resumen de las pruebas automáticas de Huella Segura, leído de los resultados que dejó cada ejecución.

Antes de correrlo, ejecuta las pruebas (ver PRUEBAS.md):
    gradlew testDebugUnitTest                  -> pruebas unitarias
    gradlew connectedDebugAndroidTest          -> pruebas con la app abierta (necesita un emulador o celular)
    cd tests/rules && npm test                 -> pruebas de las reglas de seguridad (se pasa su salida con --reglas)

Uso:  python scripts/resumen_pruebas.py [--reglas "RESULTADO: 120 correctas, 0 con fallo"]
"""
import glob
import os
import re
import sys
import xml.etree.ElementTree as ET

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def leer(patron):
    """Devuelve {clase: (pruebas, fallos, omitidas)} de los XML JUnit que coincidan con el patrón."""
    clases = {}
    for ruta in glob.glob(os.path.join(RAIZ, patron), recursive=True):
        for caso in ET.parse(ruta).getroot().iter('testcase'):
            clase = caso.get('classname', '?').split('.')[-1]
            pruebas, fallos, omitidas = clases.get(clase, (0, 0, 0))
            malo = caso.find('failure') is not None or caso.find('error') is not None
            salto = caso.find('skipped') is not None
            clases[clase] = (pruebas + 1, fallos + (1 if malo else 0), omitidas + (1 if salto else 0))
    return clases


def mostrar(titulo, clases):
    print(f'\n{titulo}')
    if not clases:
        print('  (sin resultados: corre primero esa parte)')
        return 0, 0
    total = fallos = 0
    for clase, (p, f, o) in sorted(clases.items()):
        estado = 'OK ' if f == 0 else 'FALLA'
        print(f'  {estado} {clase:<28} {p:>3} pruebas' + (f'  ({f} con fallo)' if f else ''))
        total += p
        fallos += f
    print(f'  -> {total} pruebas, {fallos} con fallo')
    return total, fallos


def main():
    reglas = None
    if '--reglas' in sys.argv:
        m = re.search(r'(\d+) correctas, (\d+) con fallo', sys.argv[sys.argv.index('--reglas') + 1])
        if m:
            reglas = (int(m.group(1)) + int(m.group(2)), int(m.group(2)))

    unitarias = mostrar('Pruebas unitarias (lógica pura, en el computador)',
                        leer('app/build/test-results/testDebugUnitTest/TEST-*.xml'))
    app = mostrar('Pruebas con la app abierta (Espresso, en el emulador o celular)',
                  leer('app/build/outputs/androidTest-results/connected/**/*.xml'))
    print('\nPruebas de las reglas de seguridad de Firestore (emulador de base de datos)')
    if reglas:
        print(f'  -> {reglas[0]} pruebas, {reglas[1]} con fallo')
    else:
        print('  (corre «npm test» en tests/rules y pasa su resultado con --reglas)')

    total = unitarias[0] + app[0] + (reglas[0] if reglas else 0)
    fallos = unitarias[1] + app[1] + (reglas[1] if reglas else 0)
    print(f'\nTOTAL: {total} pruebas automáticas, {fallos} con fallo')
    sys.exit(1 if fallos else 0)


if __name__ == '__main__':
    main()
