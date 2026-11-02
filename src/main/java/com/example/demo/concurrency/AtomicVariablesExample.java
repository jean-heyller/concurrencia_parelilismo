package com.example.demo.concurrency;

import java.util.concurrent.atomic.*;

/**
 * ============================================================
 * EJEMPLO 9: VARIABLES ATOMICAS
 * ============================================================
 *
 * QUE SON LAS OPERACIONES ATOMICAS?
 * -----------------------------------
 * Una operacion atomica es INDIVISIBLE: se ejecuta completamente
 * o no se ejecuta. No puede ser interrumpida a la mitad.
 *
 * En Java, las variables atomicas del paquete java.util.concurrent.atomic
 * usan instrucciones especiales del CPU: CAS (Compare-And-Swap).
 *
 * CAS (Compare-And-Swap):
 * -----------------------
 *   atomic.compareAndSet(esperado, nuevo)
 *   -> Si el valor actual == esperado: actualiza a 'nuevo', retorna true
 *   -> Si el valor actual != esperado: NO cambia nada, retorna false
 *
 *   Esto es una operacion ATOMICA del CPU (una sola instruccion).
 *   No necesita locks!
 *
 * VENTAJA sobre synchronized:
 *   synchronized bloquea hilos -> cambio de contexto (costoso)
 *   Atomic usa CAS en CPU     -> sin locks, sin bloqueo (mas rapido)
 *
 * CLASES DISPONIBLES:
 * -------------------
 *   AtomicBoolean, AtomicInteger, AtomicLong, AtomicReference
 *   AtomicIntegerArray, AtomicLongArray, AtomicReferenceArray
 *   AtomicIntegerFieldUpdater, AtomicLongFieldUpdater
 *   LongAdder, DoubleAdder          (Java 8+, alta contension)
 *   LongAccumulator, DoubleAccumulator (Java 8+, operacion custom)
 */
public class AtomicVariablesExample {

    public static void main(String[] args) throws InterruptedException { run(); }

    public static void run() throws InterruptedException {
        ConcurrencyRunner.header("9. VARIABLES ATOMICAS");

        ejemplo_AtomicInteger();
        ejemplo_AtomicLong();
        ejemplo_AtomicBoolean();
        ejemplo_AtomicReference();
        ejemplo_AtomicArray();
        ejemplo_LongAdder();
        ejemplo_LongAccumulator();
        ejemplo_CAS_Manual();
    }

    // ----------------------------------------------------------------
    // 1. AtomicInteger
    // ----------------------------------------------------------------
    static void ejemplo_AtomicInteger() throws InterruptedException {
        ConcurrencyRunner.section("1. AtomicInteger");

        AtomicInteger contador = new AtomicInteger(0);

        // Operaciones atomicas disponibles:
        System.out.println("  get()           : " + contador.get());          // 0
        System.out.println("  incrementAndGet : " + contador.incrementAndGet()); // 1 (++i)
        System.out.println("  getAndIncrement : " + contador.getAndIncrement()); // 1 (i++)
        System.out.println("  get() ahora     : " + contador.get());            // 2
        System.out.println("  decrementAndGet : " + contador.decrementAndGet()); // 1
        System.out.println("  addAndGet(10)   : " + contador.addAndGet(10));     // 11
        System.out.println("  getAndAdd(5)    : " + contador.getAndAdd(5));      // 11 (retorna viejo)
        System.out.println("  get() ahora     : " + contador.get());             // 16
        System.out.println("  getAndSet(0)    : " + contador.getAndSet(0));      // 16 (retorna viejo, setea 0)
        System.out.println("  get() ahora     : " + contador.get());             // 0

        // updateAndGet/getAndUpdate con funcion (Java 8+)
        contador.set(5);
        System.out.println("  updateAndGet(*3): " + contador.updateAndGet(v -> v * 3)); // 15
        System.out.println("  accumulateAndGet(+5): " + contador.accumulateAndGet(5, Integer::sum)); // 20

        // Uso con multiples hilos
        AtomicInteger shared = new AtomicInteger(0);
        Thread[] hilos = new Thread[10];
        for (int i = 0; i < 10; i++) {
            hilos[i] = new Thread(() -> {
                for (int j = 0; j < 1000; j++) shared.incrementAndGet();
            });
        }
        for (Thread h : hilos) h.start();
        for (Thread h : hilos) h.join();

        System.out.println("  Contador con 10 hilos x 1000: " + shared.get() + " (esperado: 10000)");
    }

    // ----------------------------------------------------------------
    // 2. AtomicLong
    // ----------------------------------------------------------------
    static void ejemplo_AtomicLong() throws InterruptedException {
        ConcurrencyRunner.section("2. AtomicLong");

        AtomicLong idGenerador = new AtomicLong(1000);

        // Generar IDs unicos desde multiples hilos
        Thread[] hilos = new Thread[5];
        long[] ids = new long[5];
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            hilos[i] = new Thread(() ->
                ids[idx] = idGenerador.getAndIncrement()
            );
        }
        for (Thread h : hilos) h.start();
        for (Thread h : hilos) h.join();

        System.out.print("  IDs generados: ");
        for (long id : ids) System.out.print(id + " ");
        System.out.println();
        System.out.println("  Siguiente ID disponible: " + idGenerador.get());
    }

    // ----------------------------------------------------------------
    // 3. AtomicBoolean
    // ----------------------------------------------------------------
    static void ejemplo_AtomicBoolean() throws InterruptedException {
        ConcurrencyRunner.section("3. AtomicBoolean");

        AtomicBoolean inicializado = new AtomicBoolean(false);

        // Patron: inicializacion unica (only-once) desde multiples hilos
        // compareAndSet es atomico: solo un hilo puede cambiar false->true
        Thread[] hilos = new Thread[5];
        for (int i = 0; i < 5; i++) {
            hilos[i] = new Thread(() -> {
                boolean fueElPrimero = inicializado.compareAndSet(false, true);
                if (fueElPrimero) {
                    System.out.printf("  [%s] Fui el primero! Inicializando...%n",
                        Thread.currentThread().getName());
                } else {
                    System.out.printf("  [%s] Ya estaba inicializado, me salteo%n",
                        Thread.currentThread().getName());
                }
            });
        }
        for (Thread h : hilos) h.start();
        for (Thread h : hilos) h.join();

        System.out.println("  Estado final: " + inicializado.get());
    }

    // ----------------------------------------------------------------
    // 4. AtomicReference
    // ----------------------------------------------------------------
    static void ejemplo_AtomicReference() throws InterruptedException {
        ConcurrencyRunner.section("4. AtomicReference");

        // AtomicReference<T> para cualquier objeto
        record Usuario(String nombre, int version) {}

        AtomicReference<Usuario> usuarioRef = new AtomicReference<>(new Usuario("Ana", 1));

        System.out.println("  Usuario inicial: " + usuarioRef.get());

        // CAS con objetos: actualizar solo si nadie mas cambio mientras tanto
        Usuario actual = usuarioRef.get();
        Usuario actualizado = new Usuario(actual.nombre(), actual.version() + 1);
        boolean actualizo = usuarioRef.compareAndSet(actual, actualizado);
        System.out.println("  compareAndSet: " + actualizo + " -> " + usuarioRef.get());

        // Patrón de actualización segura con retry (optimistic locking)
        Thread t1 = new Thread(() -> {
            while (true) {
                Usuario u = usuarioRef.get();
                Usuario nuevo = new Usuario(u.nombre() + "-T1", u.version() + 1);
                if (usuarioRef.compareAndSet(u, nuevo)) {
                    System.out.println("  [T1] Actualizado: " + nuevo);
                    break;
                }
                // Si falla (otro hilo cambio primero), reintentar
                System.out.println("  [T1] Reintentando...");
            }
        });

        Thread t2 = new Thread(() -> {
            while (true) {
                Usuario u = usuarioRef.get();
                Usuario nuevo = new Usuario(u.nombre() + "-T2", u.version() + 1);
                if (usuarioRef.compareAndSet(u, nuevo)) {
                    System.out.println("  [T2] Actualizado: " + nuevo);
                    break;
                }
                System.out.println("  [T2] Reintentando...");
            }
        });

        t1.start(); t2.start();
        t1.join(); t2.join();
        System.out.println("  Estado final: " + usuarioRef.get());
    }

    // ----------------------------------------------------------------
    // 5. AtomicArray
    // ----------------------------------------------------------------
    static void ejemplo_AtomicArray() throws InterruptedException {
        ConcurrencyRunner.section("5. AtomicIntegerArray");

        // Array donde CADA ELEMENTO es atomico independientemente
        AtomicIntegerArray array = new AtomicIntegerArray(5);

        Thread[] hilos = new Thread[5];
        for (int i = 0; i < 5; i++) {
            final int idx = i % array.length();
            hilos[i] = new Thread(() -> {
                for (int j = 0; j < 100; j++) array.incrementAndGet(idx);
            });
        }
        for (Thread h : hilos) h.start();
        for (Thread h : hilos) h.join();

        System.out.print("  Array: [");
        for (int i = 0; i < array.length(); i++)
            System.out.print(array.get(i) + (i < array.length()-1 ? ", " : ""));
        System.out.println("]");
    }

    // ----------------------------------------------------------------
    // 6. LongAdder - para alta concurrencia (Java 8+)
    // ----------------------------------------------------------------
    static void ejemplo_LongAdder() throws InterruptedException {
        ConcurrencyRunner.section("6. LongAdder (alta concurrencia)");

        // LongAdder es MUCHO MAS RAPIDO que AtomicLong cuando hay
        // MUCHOS hilos incrementando al mismo tiempo (alta contension).
        //
        // Como funciona: tiene un array de "celdas" (cells).
        // Cada hilo incrementa su propia celda (menos contention).
        // sum() suma todas las celdas al final.
        //
        // AtomicLong:  todos pelean por el mismo valor -> contention alta
        // LongAdder:   cada hilo tiene su celda -> contention baja -> mas rapido

        LongAdder adder = new LongAdder();

        Thread[] hilos = new Thread[20];
        for (int i = 0; i < 20; i++) {
            hilos[i] = new Thread(() -> {
                for (int j = 0; j < 1000; j++) adder.increment();
            });
        }
        for (Thread h : hilos) h.start();
        for (Thread h : hilos) h.join();

        System.out.println("  LongAdder.sum(): " + adder.sum() + " (esperado: 20000)");
        System.out.println("  Util para: contadores de alta frecuencia, metricas, estadisticas");

        // DoubleAdder: igual pero para doubles
        DoubleAdder totalVentas = new DoubleAdder();
        totalVentas.add(100.50);
        totalVentas.add(200.75);
        System.out.println("  DoubleAdder: " + totalVentas.sum());
    }

    // ----------------------------------------------------------------
    // 7. LongAccumulator
    // ----------------------------------------------------------------
    static void ejemplo_LongAccumulator() throws InterruptedException {
        ConcurrencyRunner.section("7. LongAccumulator (operacion custom)");

        // LongAccumulator: como LongAdder pero con operacion personalizada
        // LongAccumulator(funcion, identidad)
        // funcion: (acumulado, nuevo) -> resultado

        // Maximo concurrente
        LongAccumulator maximo = new LongAccumulator(Long::max, Long.MIN_VALUE);

        Thread[] hilos = new Thread[5];
        long[] valores = {42, 100, 7, 89, 55};
        for (int i = 0; i < 5; i++) {
            final long val = valores[i];
            hilos[i] = new Thread(() -> {
                maximo.accumulate(val);
                System.out.printf("  [%s] accumulate(%d)%n", Thread.currentThread().getName(), val);
            });
        }
        for (Thread h : hilos) h.start();
        for (Thread h : hilos) h.join();

        System.out.println("  Maximo: " + maximo.get() + " (esperado: 100)");

        // Suma de cuadrados concurrente
        LongAccumulator sumaCuadrados = new LongAccumulator(Long::sum, 0);
        long[] nums = {1, 2, 3, 4, 5};
        Thread[] t2 = new Thread[5];
        for (int i = 0; i < 5; i++) {
            final long n = nums[i];
            t2[i] = new Thread(() -> sumaCuadrados.accumulate(n * n));
        }
        for (Thread t : t2) t.start();
        for (Thread t : t2) t.join();
        System.out.println("  Suma cuadrados 1..5: " + sumaCuadrados.get() + " (esperado: 55)");
    }

    // ----------------------------------------------------------------
    // 8. CAS manual - entender como funciona
    // ----------------------------------------------------------------
    static void ejemplo_CAS_Manual() throws InterruptedException {
        ConcurrencyRunner.section("8. CAS (Compare-And-Swap) - como funciona");

        System.out.println("  CAS es la base de TODOS los atomicos:");
        System.out.println("  compareAndSet(esperado, nuevo):");
        System.out.println("    if (valor == esperado) { valor = nuevo; return true; }");
        System.out.println("    else { return false; }");
        System.out.println("  Todo esto en UNA instruccion del CPU (LOCK CMPXCHG en x86)");
        System.out.println();

        AtomicInteger valor = new AtomicInteger(10);

        // CAS exitoso
        boolean ok1 = valor.compareAndSet(10, 20);
        System.out.println("  compareAndSet(10, 20): " + ok1 + " -> valor=" + valor.get());

        // CAS fallido (el valor ya cambio)
        boolean ok2 = valor.compareAndSet(10, 30); // falla porque valor es 20 ahora
        System.out.println("  compareAndSet(10, 30): " + ok2 + " -> valor=" + valor.get() + " (no cambio)");

        // Implementar increment() manualmente con CAS (como lo hace Java internamente)
        System.out.println();
        System.out.println("  Implementacion manual de incrementAndGet() con CAS:");
        AtomicInteger manual = new AtomicInteger(5);
        int resultado;
        do {
            int actual = manual.get();          // 1. leer valor actual
            int nuevo = actual + 1;             // 2. calcular nuevo valor
            // 3. si nadie cambio 'actual' mientras calculabamos, actualizar
            //    si alguien cambio, reintentar (bucle)
            if (manual.compareAndSet(actual, nuevo)) {
                resultado = nuevo;
                break;
            }
        } while (true);
        System.out.println("  incrementAndGet manual: " + resultado + " (esperado: 6)");

        System.out.println();
        System.out.println("  TABLA COMPARATIVA:");
        System.out.println("  Tecnica         | Thread-safe | Performance | Uso");
        System.out.println("  int             |     NO      |   MAX       | Solo 1 hilo");
        System.out.println("  synchronized    |     SI      |   BAJO      | Contention baja");
        System.out.println("  AtomicInteger   |     SI      |   ALTO      | Contention media");
        System.out.println("  LongAdder       |     SI      |   MUY ALTO  | Contention alta");
    }
}
