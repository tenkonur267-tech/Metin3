/*
 * frida/il2cpp_dump.js
 *
 * IL2CPP oyunlarinda TUM sinif / alan / metod offsetlerini runtime'da cikarir.
 *
 * Neden bu yol: IL2CPP runtime'i `il2cpp_field_get_offset()` API'sini disa aktarir.
 * Yani alan offsetlerini Cheat Engine ile tahmin etmeye GEREK YOK - oyun motoru
 * sana birebir dogru degeri veriyor. Pointer-scan'den kat kat guvenilir ve
 * oyun guncellendiginde tek komutla yeniden uretilir.
 *
 * Kullanim:
 *   frida -U -f com.sirket.metin3 -l frida/il2cpp_dump.js
 *   # oyun ana menuye gelince:
 *   rpc.exports.dumpall()          -> tum sinilar (buyuk)
 *   rpc.exports.find("Player")     -> isimde gecen siniflar
 *   rpc.exports.klass("PlayerController")  -> tek sinifin alan offsetleri
 *
 * Ya da python tarafindan: tools/dump_offsets.py
 */

'use strict';

const LIB = 'libil2cpp.so';

/* ---------- il2cpp export baglama ---------- */

let _base = null;
function base() {
    if (_base === null) {
        const m = Process.findModuleByName(LIB);
        if (m === null) throw new Error(LIB + ' yuklu degil. Oyun tam acilmadi mi?');
        _base = m.base;
    }
    return _base;
}

const _cache = {};
function api(name, ret, args) {
    if (_cache[name]) return _cache[name];
    const p = Module.findExportByName(LIB, name);
    if (p === null) throw new Error('il2cpp export bulunamadi: ' + name +
        ' (sembol tablosu soyulmus olabilir -> frida/il2cpp_resolve.js kullan)');
    _cache[name] = new NativeFunction(p, ret, args);
    return _cache[name];
}

const il2cpp = {
    domain_get:            () => api('il2cpp_domain_get', 'pointer', [])(),
    domain_get_assemblies: (d, n) => api('il2cpp_domain_get_assemblies', 'pointer', ['pointer', 'pointer'])(d, n),
    assembly_get_image:    (a) => api('il2cpp_assembly_get_image', 'pointer', ['pointer'])(a),
    image_get_name:        (i) => api('il2cpp_image_get_name', 'pointer', ['pointer'])(i),
    image_get_class_count: (i) => api('il2cpp_image_get_class_count', 'uint32', ['pointer'])(i),
    image_get_class:       (i, x) => api('il2cpp_image_get_class', 'pointer', ['pointer', 'uint32'])(i, x),
    class_get_name:        (k) => api('il2cpp_class_get_name', 'pointer', ['pointer'])(k),
    class_get_namespace:   (k) => api('il2cpp_class_get_namespace', 'pointer', ['pointer'])(k),
    class_get_fields:      (k, it) => api('il2cpp_class_get_fields', 'pointer', ['pointer', 'pointer'])(k, it),
    class_get_methods:     (k, it) => api('il2cpp_class_get_methods', 'pointer', ['pointer', 'pointer'])(k, it),
    class_get_parent:      (k) => api('il2cpp_class_get_parent', 'pointer', ['pointer'])(k),
    class_instance_size:   (k) => api('il2cpp_class_instance_size', 'int32', ['pointer'])(k),
    field_get_name:        (f) => api('il2cpp_field_get_name', 'pointer', ['pointer'])(f),
    field_get_offset:      (f) => api('il2cpp_field_get_offset', 'uint32', ['pointer'])(f),
    field_get_flags:       (f) => api('il2cpp_field_get_flags', 'int32', ['pointer'])(f),
    field_get_type:        (f) => api('il2cpp_field_get_type', 'pointer', ['pointer'])(f),
    type_get_name:         (t) => api('il2cpp_type_get_name', 'pointer', ['pointer'])(t),
    method_get_name:       (m) => api('il2cpp_method_get_name', 'pointer', ['pointer'])(m),
    method_get_param_count:(m) => api('il2cpp_method_get_param_count', 'uint32', ['pointer'])(m),
    thread_attach:         (d) => api('il2cpp_thread_attach', 'pointer', ['pointer'])(d),
};

const FIELD_STATIC = 0x0010;   // FIELD_ATTRIBUTE_STATIC
const FIELD_LITERAL = 0x0040;  // const - bellekte yeri yok, atlanir

function cstr(p) {
    if (p === null || p.isNull()) return '';
    try { return p.readUtf8String(); } catch (e) { return ''; }
}

/* Frida thread'inden il2cpp API cagirmadan once attach sart, yoksa cokme olur */
let _attached = false;
function attach() {
    if (_attached) return;
    il2cpp.thread_attach(il2cpp.domain_get());
    _attached = true;
}

/* ---------- gezinme ---------- */

function eachImage(fn) {
    attach();
    const domain = il2cpp.domain_get();
    const countPtr = Memory.alloc(Process.pointerSize);
    const assemblies = il2cpp.domain_get_assemblies(domain, countPtr);
    const count = countPtr.readUInt();
    for (let i = 0; i < count; i++) {
        const asm = assemblies.add(i * Process.pointerSize).readPointer();
        if (asm.isNull()) continue;
        const img = il2cpp.assembly_get_image(asm);
        if (img.isNull()) continue;
        fn(img, cstr(il2cpp.image_get_name(img)));
    }
}

function eachClass(fn) {
    eachImage((img, imgName) => {
        const n = il2cpp.image_get_class_count(img);
        for (let i = 0; i < n; i++) {
            const k = il2cpp.image_get_class(img, i);
            if (k.isNull()) continue;
            fn(k, imgName);
        }
    });
}

function fieldsOf(klass) {
    const out = [];
    const iter = Memory.alloc(Process.pointerSize);
    iter.writePointer(NULL);
    let f;
    while (!(f = il2cpp.class_get_fields(klass, iter)).isNull()) {
        const flags = il2cpp.field_get_flags(f);
        if (flags & FIELD_LITERAL) continue;
        let typeName = '';
        try { typeName = cstr(il2cpp.type_get_name(il2cpp.field_get_type(f))); } catch (e) {}
        out.push({
            name: cstr(il2cpp.field_get_name(f)),
            offset: il2cpp.field_get_offset(f),
            type: typeName,
            static: !!(flags & FIELD_STATIC),
        });
    }
    return out;
}

function methodsOf(klass) {
    const out = [];
    const iter = Memory.alloc(Process.pointerSize);
    iter.writePointer(NULL);
    const b = base();
    let m;
    while (!(m = il2cpp.class_get_methods(klass, iter)).isNull()) {
        // MethodInfo'nun ilk alani Il2CppMethodPointer methodPointer
        let rva = null;
        try {
            const code = m.readPointer();
            if (!code.isNull()) rva = '0x' + code.sub(b).toString(16);
        } catch (e) {}
        out.push({
            name: cstr(il2cpp.method_get_name(m)),
            params: il2cpp.method_get_param_count(m),
            rva: rva,                       // libil2cpp.so baslangicina gore offset
            addr: '0x' + m.toString(16),    // bu calistirmadaki MethodInfo adresi
        });
    }
    return out;
}

function describe(klass, imgName) {
    const ns = cstr(il2cpp.class_get_namespace(klass));
    const name = cstr(il2cpp.class_get_name(klass));
    let parent = '';
    try {
        const p = il2cpp.class_get_parent(klass);
        if (!p.isNull()) parent = cstr(il2cpp.class_get_name(p));
    } catch (e) {}
    let size = -1;
    try { size = il2cpp.class_instance_size(klass); } catch (e) {}
    return {
        image: imgName,
        namespace: ns,
        name: name,
        full: ns ? ns + '.' + name : name,
        parent: parent,
        instanceSize: size,
        fields: fieldsOf(klass),
        methods: methodsOf(klass),
    };
}

/* ---------- disa acilan komutlar ---------- */

function dumpAll(opts) {
    opts = opts || {};
    const skipSystem = opts.skipSystem !== false;  // varsayilan: sistem dll'lerini atla
    const result = [];
    eachClass((k, img) => {
        if (skipSystem && /^(mscorlib|System|Unity|Mono|netstandard)/i.test(img)) return;
        try { result.push(describe(k, img)); } catch (e) {}
    });
    console.log('[il2cpp_dump] ' + result.length + ' sinif cikarildi');
    return result;
}

function find(needle) {
    const q = String(needle).toLowerCase();
    const hits = [];
    eachClass((k, img) => {
        const name = cstr(il2cpp.class_get_name(k));
        if (name.toLowerCase().indexOf(q) !== -1) {
            const ns = cstr(il2cpp.class_get_namespace(k));
            hits.push({ image: img, full: ns ? ns + '.' + name : name,
                        addr: '0x' + k.toString(16) });
        }
    });
    console.log('[il2cpp_dump] "' + needle + '" -> ' + hits.length + ' sinif');
    hits.forEach(h => console.log('   ' + h.full + '   (' + h.image + ')'));
    return hits;
}

function klass(name) {
    const q = String(name).toLowerCase();
    let found = null;
    eachClass((k, img) => {
        if (found) return;
        if (cstr(il2cpp.class_get_name(k)).toLowerCase() === q) found = describe(k, img);
    });
    if (!found) { console.log('[il2cpp_dump] bulunamadi: ' + name); return null; }

    console.log('\n=== ' + found.full + ' ===  (instance size: ' + found.instanceSize + ' byte)');
    console.log('--- alanlar (offset = nesne basina gore) ---');
    found.fields.forEach(f => {
        const tag = f.static ? '[static]' : '        ';
        console.log('  ' + tag + ' 0x' + f.offset.toString(16).padStart(4, '0') +
                    '  ' + f.name + ' : ' + f.type);
    });
    console.log('--- metodlar (rva = libil2cpp.so + offset) ---');
    found.methods.forEach(m => {
        console.log('  ' + (m.rva || '  ?  ') + '  ' + m.name + '(' + m.params + ')');
    });
    return found;
}

rpc.exports = {
    dumpall: dumpAll,
    find: find,
    klass: klass,
    ping: () => ({ ok: true, base: '0x' + base().toString(16), pid: Process.id }),
};

console.log('[il2cpp_dump] hazir. rpc.exports.find("Player") ile basla.');
