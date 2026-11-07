import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import {
  doc, getDoc, getDocs, setDoc, addDoc, updateDoc, deleteDoc, collection, query, where, writeBatch, serverTimestamp,
} from 'firebase/firestore';
import { readFileSync } from 'node:fs';

const env = await initializeTestEnvironment({
  projectId: 'demo-huella',
  firestore: { host: '127.0.0.1', port: 8089, rules: readFileSync(new URL('../../firestore.rules', import.meta.url), 'utf8') },
});

const ANA = 'ana@x.com', BOB = 'bob@x.com', VET = 'vet@x.com', FUNC = 'func@x.com';

async function seed() {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'roles_profesionales', VET), { rol: 'veterinario' });
    await setDoc(doc(db, 'roles_profesionales', FUNC), { rol: 'funcionario' });
    await setDoc(doc(db, 'usuarios', ANA), { nombre: 'Ana' });
    await setDoc(doc(db, 'usuarios', BOB), { nombre: 'Bob' });
    await setDoc(doc(db, 'veterinarios', VET), { estado: 'pendiente' });
    await setDoc(doc(db, 'veterinarios', 'vet2@x.com'), { estado: 'aceptado' });
    await setDoc(doc(db, 'veterinarios', 'vet3@x.com'), { estado: 'pendiente' });
    await setDoc(doc(db, 'mascotas', 'm1'), { nombre: 'Toby', duenoCorreo: ANA, estado: 'pendiente', comportamiento: [], creadoPorVet: VET });
    await setDoc(doc(db, 'mascotas', 'm2'), { nombre: 'Rex', duenoCorreo: BOB, estado: 'aceptada', comportamiento: [], creadoPorVet: VET });
    await setDoc(doc(db, 'mascotas/m1/historial', 'h1'), { descripcion: 'Vacuna', estado: 'aceptado', creadoPorVet: VET });
    await setDoc(doc(db, 'mascotas/m1/historial', 'h2'), { descripcion: 'Control', estado: 'pendiente', creadoPorVet: VET });
    await setDoc(doc(db, 'mascotas/m2/historial', 'h3'), { descripcion: 'Otro', estado: 'aceptado', creadoPorVet: VET });
    await setDoc(doc(db, 'reportes', 'r1'), { tipo: 'Herido', reportanteCorreo: ANA, estado: 'pendiente' });
    await setDoc(doc(db, 'reportes', 'r2'), { tipo: 'Herido', reportanteCorreo: BOB, estado: 'pendiente' });
    await setDoc(doc(db, 'reportes', 'r3'), { tipo: 'Aviso', reportanteCorreo: BOB, perdidoId: 'p1', perdidoDuenoCorreo: ANA, estado: 'pendiente' });
    await setDoc(doc(db, 'perdidos', 'p1'), { nombre: 'Michi', reportanteCorreo: ANA, estado: 'publicado' });
    await setDoc(doc(db, 'perdidos', 'p2'), { nombre: 'Luna', reportanteCorreo: BOB, estado: 'pendiente' });
    await setDoc(doc(db, 'perdidos', 'p3'), { nombre: 'Fido', reportanteCorreo: BOB, estado: 'publicado' });
    await setDoc(doc(db, 'comunitarios', 'c1'), { nombre: 'Canelo', especie: 'Perro', activo: true, creadoPor: FUNC });
    await setDoc(doc(db, 'comunitarios', 'c2'), { nombre: 'Fantasma', especie: 'Gato', activo: false, creadoPor: FUNC });
    await setDoc(doc(db, 'chats', ANA), { ultimoMensaje: 'hola' });
    await setDoc(doc(db, 'chats/ana@x.com/mensajes', 'x1'), { texto: 'hola', remitente: 'ciudadano' });
    await setDoc(doc(db, 'chats', BOB), { ultimoMensaje: 'hey' });
  });
}

let pass = 0, fail = 0; const bad = [];
async function t(name, expectOk, fn) {
  try {
    if (expectOk) await assertSucceeds(fn()); else await assertFails(fn());
    pass++; console.log(`  ok    ${name}`);
  } catch (e) {
    fail++; bad.push(name); console.log(`  FALLA ${name}  (esperaba ${expectOk ? 'PERMITIDO' : 'BLOQUEADO'})`);
  }
}
const as = (email) => env.authenticatedContext('uid-' + email, { email }).firestore();
const anon = () => env.unauthenticatedContext().firestore();
const s = (title) => console.log(`\n== ${title}`);

// ---------------- Sin sesion ----------------
await seed(); s('Sin sesión');
{ const db = anon();
  await t('no lee mascotas', false, () => getDocs(collection(db, 'mascotas')));
  await t('no lee perdidos publicados', false, () => getDocs(query(collection(db, 'perdidos'), where('estado', '==', 'publicado'))));
  await t('no lee chats', false, () => getDoc(doc(db, 'chats', ANA)));
  await t('no crea reportes', false, () => addDoc(collection(db, 'reportes'), { reportanteCorreo: ANA, estado: 'pendiente' }));
}

// ---------------- Ciudadana (Ana) ----------------
await seed(); s('Ciudadana (Ana)');
{ const db = as(ANA);
  await t('perfil propio', true, () => getDoc(doc(db, 'usuarios', ANA)));
  await t('perfil ajeno bloqueado', false, () => getDoc(doc(db, 'usuarios', BOB)));
  await t('su rol (propio doc)', true, () => getDoc(doc(db, 'roles_profesionales', ANA)));
  await t('rol de otro bloqueado', false, () => getDoc(doc(db, 'roles_profesionales', FUNC)));
  await t('no puede darse un rol', false, () => setDoc(doc(db, 'roles_profesionales', ANA), { rol: 'funcionario' }));
  await t('crear su perfil', true, () => setDoc(doc(db, 'usuarios', ANA), { nombre: 'Ana P' }));

  await t('mis mascotas (consulta por dueño)', true, () => getDocs(query(collection(db, 'mascotas'), where('duenoCorreo', '==', ANA))));
  await t('todas las mascotas bloqueado', false, () => getDocs(collection(db, 'mascotas')));
  await t('mascota ajena bloqueada', false, () => getDoc(doc(db, 'mascotas', 'm2')));
  await t('crear mascota bloqueado', false, () => addDoc(collection(db, 'mascotas'), { nombre: 'X', duenoCorreo: ANA, creadoPorVet: ANA, estado: 'pendiente' }));
  await t('aceptar su mascota', true, () => updateDoc(doc(db, 'mascotas', 'm1'), { estado: 'aceptada' }));
  await t('agregar comportamiento', true, () => updateDoc(doc(db, 'mascotas', 'm1'), { comportamiento: ['tranquila'] }));
  await t('cambiar nombre de mascota bloqueado', false, () => updateDoc(doc(db, 'mascotas', 'm1'), { nombre: 'Otro' }));
  await t('cambiar dueño bloqueado', false, () => updateDoc(doc(db, 'mascotas', 'm1'), { duenoCorreo: BOB }));
  await t('mascota ajena: modificar bloqueado', false, () => updateDoc(doc(db, 'mascotas', 'm2'), { estado: 'pendiente' }));

  await t('historial de su mascota', true, () => getDocs(collection(db, 'mascotas/m1/historial')));
  await t('historial ajeno bloqueado', false, () => getDocs(collection(db, 'mascotas/m2/historial')));
  await t('aceptar registro pendiente', true, () => updateDoc(doc(db, 'mascotas/m1/historial', 'h2'), { estado: 'aceptado' }));
  await t('editar registro aceptado bloqueado', false, () => updateDoc(doc(db, 'mascotas/m1/historial', 'h1'), { descripcion: 'x' }));
  await t('crear registro bloqueado', false, () => addDoc(collection(db, 'mascotas/m1/historial'), { descripcion: 'x', estado: 'aceptado', creadoPorVet: ANA }));
  await t('borrar registro aceptado bloqueado', false, () => deleteDoc(doc(db, 'mascotas/m1/historial', 'h1')));

  await t('mis reportes (consulta)', true, () => getDocs(query(collection(db, 'reportes'), where('reportanteCorreo', '==', ANA))));
  await t('todos los reportes bloqueado', false, () => getDocs(collection(db, 'reportes')));
  await t('reporte ajeno bloqueado', false, () => getDoc(doc(db, 'reportes', 'r2')));
  await t('crear reporte propio', true, () => addDoc(collection(db, 'reportes'), { tipo: 'Herido', reportanteCorreo: ANA, estado: 'pendiente', fechaCreacion: serverTimestamp() }));
  await t('crear reporte que ya venga "atendido" bloqueado', false, () => addDoc(collection(db, 'reportes'), { tipo: 'Herido', reportanteCorreo: ANA, estado: 'pendiente', atendidoPor: FUNC }));
  await t('aviso "lo encontré" legítimo', true, () => addDoc(collection(db, 'reportes'), { tipo: 'Aviso', reportanteCorreo: ANA, estado: 'pendiente', perdidoId: 'p3', perdidoDuenoCorreo: BOB }));
  await t('aviso con un dueño falso bloqueado', false, () => addDoc(collection(db, 'reportes'), { tipo: 'Aviso', reportanteCorreo: ANA, estado: 'pendiente', perdidoId: 'p3', perdidoDuenoCorreo: ANA }));
  await t('aviso de un animal que no existe bloqueado', false, () => addDoc(collection(db, 'reportes'), { tipo: 'Aviso', reportanteCorreo: ANA, estado: 'pendiente', perdidoId: 'nope', perdidoDuenoCorreo: ANA }));
  await t('reporte propio con "dueño" propio (para poder borrarlo) bloqueado', false, () => addDoc(collection(db, 'reportes'), { tipo: 'Herido', reportanteCorreo: ANA, estado: 'pendiente', perdidoDuenoCorreo: ANA }));
  await t('crear reporte a nombre de otro bloqueado', false, () => addDoc(collection(db, 'reportes'), { tipo: 'Herido', reportanteCorreo: BOB, estado: 'pendiente' }));
  await t('crear reporte ya "resuelto" bloqueado', false, () => addDoc(collection(db, 'reportes'), { tipo: 'Herido', reportanteCorreo: ANA, estado: 'resuelto' }));
  await t('tomar/resolver caso bloqueado', false, () => updateDoc(doc(db, 'reportes', 'r1'), { estado: 'resuelto' }));
  await t('¿ya avisé? (perdidoId + mi correo)', true, () => getDocs(query(collection(db, 'reportes'), where('perdidoId', '==', 'p1'), where('reportanteCorreo', '==', ANA))));
  await t('avisos sobre mi animal (dueño)', true, () => getDocs(query(collection(db, 'reportes'), where('perdidoId', '==', 'p1'), where('perdidoDuenoCorreo', '==', ANA))));
  await t('avisos de un animal ajeno sin filtro bloqueado', false, () => getDocs(query(collection(db, 'reportes'), where('perdidoId', '==', 'p1'))));

  await t('catálogo de perdidos publicados', true, () => getDocs(query(collection(db, 'perdidos'), where('estado', '==', 'publicado'))));
  await t('mis perdidos (consulta)', true, () => getDocs(query(collection(db, 'perdidos'), where('reportanteCorreo', '==', ANA))));
  await t('todos los perdidos bloqueado', false, () => getDocs(collection(db, 'perdidos')));
  await t('perdido ajeno sin publicar bloqueado', false, () => getDoc(doc(db, 'perdidos', 'p2')));
  await t('perdido ajeno publicado', true, () => getDoc(doc(db, 'perdidos', 'p3')));
  await t('publicar perdido (crear pendiente)', true, () => addDoc(collection(db, 'perdidos'), { nombre: 'Z', reportanteCorreo: ANA, estado: 'pendiente' }));
  await t('crear perdido que ya venga "revisado" bloqueado', false, () => addDoc(collection(db, 'perdidos'), { nombre: 'Z', reportanteCorreo: ANA, estado: 'pendiente', revisadoPor: FUNC }));
  await t('crear perdido ya publicado bloqueado', false, () => addDoc(collection(db, 'perdidos'), { nombre: 'Z', reportanteCorreo: ANA, estado: 'publicado' }));
  await t('aprobarse su propio perdido bloqueado', false, () => updateDoc(doc(db, 'perdidos', 'p1'), { estado: 'publicado' }));
  await t('"ya lo encontré": borra avisos + perdido (batch)', true, async () => {
    const b = writeBatch(db); b.delete(doc(db, 'reportes', 'r3')); b.delete(doc(db, 'perdidos', 'p1')); return b.commit();
  });
  await t('borrar perdido ajeno bloqueado', false, () => deleteDoc(doc(db, 'perdidos', 'p3')));

  await t('su chat', true, () => getDoc(doc(db, 'chats', ANA)));
  await t('chat ajeno bloqueado', false, () => getDoc(doc(db, 'chats', BOB)));
  await t('listar chats bloqueado', false, () => getDocs(collection(db, 'chats')));
  await t('escribir mensaje propio', true, async () => {
    const b = writeBatch(db);
    b.set(doc(db, 'chats', ANA), { ultimoMensaje: 'x', ultimoRemitente: 'ciudadano' }, { merge: true });
    b.set(doc(collection(db, 'chats/ana@x.com/mensajes')), { texto: 'x', remitente: 'ciudadano', remitenteCorreo: ANA });
    return b.commit();
  });
  await t('mensaje con el correo en mayúsculas', true, () => addDoc(collection(db, 'chats/ana@x.com/mensajes'), { texto: 'x', remitente: 'ciudadano', remitenteCorreo: 'Ana@X.com' }));
  await t('mensaje firmado con el correo de otra persona bloqueado', false, () => addDoc(collection(db, 'chats/ana@x.com/mensajes'), { texto: 'x', remitente: 'ciudadano', remitenteCorreo: BOB }));
  await t('mensaje sin correo de remitente bloqueado', false, () => addDoc(collection(db, 'chats/ana@x.com/mensajes'), { texto: 'x', remitente: 'ciudadano' }));
  await t('mensaje haciéndose pasar por funcionario bloqueado', false, () => addDoc(collection(db, 'chats/ana@x.com/mensajes'), { texto: 'x', remitente: 'funcionario', remitenteCorreo: ANA }));
  await t('mensaje en chat ajeno bloqueado', false, () => addDoc(collection(db, 'chats/bob@x.com/mensajes'), { texto: 'x', remitente: 'ciudadano', remitenteCorreo: ANA }));
  await t('borrar su chat bloqueado', false, () => deleteDoc(doc(db, 'chats', ANA)));
  await t('editar mensaje bloqueado', false, () => updateDoc(doc(db, 'chats/ana@x.com/mensajes', 'x1'), { texto: 'editado' }));

  await t('solicitudes de veterinarios (consulta)', true, () => getDocs(query(collection(db, 'veterinarios'), where('estado', '==', 'pendiente'))));
  await t('aceptar veterinario', true, () => updateDoc(doc(db, 'veterinarios', VET), { estado: 'aceptado' }));
  await t('revertir a un veterinario aceptado a pendiente bloqueado', false, () => updateDoc(doc(db, 'veterinarios', 'vet2@x.com'), { estado: 'pendiente' }));
  await t('poner un estado inventado bloqueado', false, () => updateDoc(doc(db, 'veterinarios', 'vet3@x.com'), { estado: 'suspendido' }));
  await t('borrar a un veterinario aceptado bloqueado', false, () => deleteDoc(doc(db, 'veterinarios', 'vet2@x.com')));
  await t('rechazar (borrar) una solicitud pendiente', true, () => deleteDoc(doc(db, 'veterinarios', 'vet3@x.com')));
  await t('cambiar otro campo de veterinario bloqueado', false, () => updateDoc(doc(db, 'veterinarios', VET), { nota: 'x' }));
  await t('crear veterinario bloqueado', false, () => setDoc(doc(db, 'veterinarios', ANA), { estado: 'pendiente' }));
}

// ---------------- Veterinario ----------------
await seed(); s('Veterinario');
{ const db = as(VET);
  await t('lista todas las mascotas', true, () => getDocs(query(collection(db, 'mascotas'))));
  await t('crea mascota (pendiente, a su nombre)', true, () => addDoc(collection(db, 'mascotas'), { nombre: 'N', duenoCorreo: ANA, creadoPorVet: VET, estado: 'pendiente', comportamiento: [] }));
  await t('crea mascota a nombre de otro vet bloqueado', false, () => addDoc(collection(db, 'mascotas'), { nombre: 'N', duenoCorreo: ANA, creadoPorVet: 'otro@x.com', estado: 'pendiente' }));
  await t('crea mascota ya aceptada bloqueado', false, () => addDoc(collection(db, 'mascotas'), { nombre: 'N', duenoCorreo: ANA, creadoPorVet: VET, estado: 'aceptada' }));
  await t('cambia datos de mascota bloqueado', false, () => updateDoc(doc(db, 'mascotas', 'm1'), { nombre: 'Otro' }));
  await t('lee historial', true, () => getDocs(collection(db, 'mascotas/m1/historial')));
  await t('crea registro pendiente', true, () => addDoc(collection(db, 'mascotas/m1/historial'), { descripcion: 'x', estado: 'pendiente', creadoPorVet: VET }));
  await t('crea registro ya aceptado bloqueado', false, () => addDoc(collection(db, 'mascotas/m1/historial'), { descripcion: 'x', estado: 'aceptado', creadoPorVet: VET }));
  await t('edita registro aceptado', true, () => updateDoc(doc(db, 'mascotas/m1/historial', 'h1'), { descripcion: 'corregido', editadoPorVet: VET }));
  await t('anula registro aceptado', true, () => updateDoc(doc(db, 'mascotas/m1/historial', 'h1'), { estado: 'anulado', motivoAnulacion: 'error' }));
  await t('aceptar un registro pendiente bloqueado', false, () => updateDoc(doc(db, 'mascotas/m1/historial', 'h2'), { estado: 'aceptado' }));
  await t('borrar registro bloqueado', false, () => deleteDoc(doc(db, 'mascotas/m1/historial', 'h1')));
  await t('lee su solicitud de veterinario', true, () => getDoc(doc(db, 'veterinarios', VET)));
  await t('aceptarse a sí mismo bloqueado', false, () => updateDoc(doc(db, 'veterinarios', VET), { estado: 'aceptado' }));
  await t('lista reportes bloqueado', false, () => getDocs(collection(db, 'reportes')));
  await t('lee chats bloqueado', false, () => getDoc(doc(db, 'chats', ANA)));
  await t('perdidos publicados', true, () => getDocs(query(collection(db, 'perdidos'), where('estado', '==', 'publicado'))));
  await t('moderar perdidos bloqueado', false, () => updateDoc(doc(db, 'perdidos', 'p2'), { estado: 'publicado' }));
}

// ---------------- Funcionario ----------------
await seed(); s('Funcionario');
{ const db = as(FUNC);
  await t('lista todos los reportes', true, () => getDocs(query(collection(db, 'reportes'))));
  await t('toma un caso', true, () => updateDoc(doc(db, 'reportes', 'r1'), { estado: 'en_atencion', atendidoPor: FUNC }));
  await t('resuelve un caso', true, () => updateDoc(doc(db, 'reportes', 'r1'), { estado: 'resuelto' }));
  await t('perdidos pendientes (bandeja)', true, () => getDocs(query(collection(db, 'perdidos'), where('estado', '==', 'pendiente'))));
  await t('publica un perdido', true, () => updateDoc(doc(db, 'perdidos', 'p2'), { estado: 'publicado', revisadoPor: FUNC }));
  await t('rechaza un perdido', true, () => updateDoc(doc(db, 'perdidos', 'p3'), { estado: 'rechazado', motivoRechazo: 'x' }));
  await t('lista todos los chats', true, () => getDocs(collection(db, 'chats')));
  await t('lee mensajes de un ciudadano', true, () => getDocs(collection(db, 'chats/ana@x.com/mensajes')));
  await t('responde en un chat', true, async () => {
    const b = writeBatch(db);
    b.set(doc(db, 'chats', ANA), { ultimoRemitente: 'funcionario', noLeidoCiudadano: true }, { merge: true });
    b.set(doc(collection(db, 'chats/ana@x.com/mensajes')), { texto: 'x', remitente: 'funcionario', remitenteCorreo: FUNC });
    return b.commit();
  });
  await t('mensaje como ciudadano bloqueado', false, () => addDoc(collection(db, 'chats/ana@x.com/mensajes'), { texto: 'x', remitente: 'ciudadano', remitenteCorreo: FUNC }));
  await t('mensaje firmado por otra persona bloqueado', false, () => addDoc(collection(db, 'chats/ana@x.com/mensajes'), { texto: 'x', remitente: 'funcionario', remitenteCorreo: ANA }));
  await t('borrar un chat bloqueado', false, () => deleteDoc(doc(db, 'chats', ANA)));
  await t('mascotas bloqueado (no es veterinario)', false, () => getDocs(collection(db, 'mascotas')));
  await t('perfiles de ciudadanos bloqueado', false, () => getDoc(doc(db, 'usuarios', ANA)));
  await t('no puede asignar roles', false, () => setDoc(doc(db, 'roles_profesionales', 'nuevo@x.com'), { rol: 'funcionario' }));
}

// ---------------- Animales comunitarios ----------------
await seed(); s('Animales comunitarios');
{ const sinSesion = anon(), ciudadano = as(ANA), vet = as(VET), func = as(FUNC);
  const nuevo = (por) => ({ nombre: 'Luna', especie: 'Gato', activo: true, creadoPor: por, lat: 5.02, lon: -74.0 });
  await t('sin sesión no los lee', false, () => getDocs(query(collection(sinSesion, 'comunitarios'), where('activo', '==', true))));
  await t('ciudadano lee los activos (consulta)', true, () => getDocs(query(collection(ciudadano, 'comunitarios'), where('activo', '==', true))));
  await t('ciudadano lee uno activo', true, () => getDoc(doc(ciudadano, 'comunitarios', 'c1')));
  await t('ciudadano no lee uno que ya no está', false, () => getDoc(doc(ciudadano, 'comunitarios', 'c2')));
  await t('ciudadano no lista todos (incluye inactivos)', false, () => getDocs(collection(ciudadano, 'comunitarios')));
  await t('ciudadano no crea', false, () => addDoc(collection(ciudadano, 'comunitarios'), nuevo(ANA)));
  await t('ciudadano no edita', false, () => updateDoc(doc(ciudadano, 'comunitarios', 'c1'), { vacunado: true }));
  await t('ciudadano no borra', false, () => deleteDoc(doc(ciudadano, 'comunitarios', 'c1')));
  await t('veterinario crea', true, () => addDoc(collection(vet, 'comunitarios'), nuevo(VET)));
  await t('veterinario no crea a nombre de otro', false, () => addDoc(collection(vet, 'comunitarios'), nuevo(FUNC)));
  await t('veterinario no crea uno ya inactivo', false, () => addDoc(collection(vet, 'comunitarios'), { ...nuevo(VET), activo: false }));
  await t('veterinario marca vacunado', true, () => updateDoc(doc(vet, 'comunitarios', 'c1'), { vacunado: true }));
  await t('veterinario ve los inactivos', true, () => getDoc(doc(vet, 'comunitarios', 'c2')));
  await t('funcionario crea', true, () => addDoc(collection(func, 'comunitarios'), nuevo(FUNC)));
  await t('funcionario lista todos', true, () => getDocs(collection(func, 'comunitarios')));
  await t('funcionario marca «ya no está»', true, () => updateDoc(doc(func, 'comunitarios', 'c1'), { activo: false }));
  await t('funcionario borra', true, () => deleteDoc(doc(func, 'comunitarios', 'c2')));
}


console.log(`\nRESULTADO: ${pass} correctas, ${fail} con fallo`);
if (fail) console.log('Fallos:\n - ' + bad.join('\n - '));
await env.cleanup();
process.exit(fail ? 1 : 0);
