package com.example.demo.concurrency;

/**
 * ============================================================
 * EJEMPLO: VISIBILIDAD DE MEMORIA Y volatile
 * ============================================================
 *
 * CONCEPTO CLAVE: ¿Por qué un hilo no ve los cambios de otro?
 * -----------------------------------------------------------
 * Los procesadores modernos tienen una jerarquía de memorias:
 *
 *   CPU Core 1          CPU Core 2
 *   +---------+         +---------+
 *   | Registro|         | Registro|    <- más rápida, privada del core
 *   |  L1 Cache |       |  L1 Cache |
 *   |  L2 Cache |       |  L2 Cache |
 *   +---------+         +---------+
 *         \                  /
 *          \                /
 *        L3 Cache (compartida)
 *              |
 *         RAM Principal
 *
 * EL PROBLEMA:
 * ------------
 * El Hilo A (en Core 1) modifica una variable. El valor queda
 * en el Registro/Cache del Core 1, NO va directamente a RAM.
 * El Hilo B (en Core 2) lee la variable desde SU cache o RAM.
 * Si el Core 1 no ha "vaciado" su cache a RAM, Hilo B ve el
 * valor VIEJO. Esto se llama "visibilidad de memoria".
 *
 * EL JIT COMPILER también puede cachear valores en registros:
 * Cuando el JIT ve `while (!stop)`, puede optimizarlo como:
 *   if (!stop) { while(true) {} }   <- loop infinito nunca sale!
 *
 * LA SOLUCIÓN: volatile
 * ---------------------
 * La palabra clave `volatile` le dice a Java:
 *   1. Escribe SIEMPRE a RAM principal (no solo al cache del core)
 *   2. Lee SIEMPRE desde RAM principal (nunca desde cache)
 *   3. Deshabilita optimizaciones del JIT para esa variable
 *   4. Establece relación HAPPENS-BEFORE: escritura de A
 *      es visible para lecturas de B que vengan después.
 *
 * IMPORTANTE - Lo que volatile NO hace:
 * --------------------------------------
 *   volatile int counter = 0;
 *   counter++;  <-- SIGUE SIN SER ATOMICO
 *   (leer-incrementar-escribir son 3 operaciones, volatile
 *    solo garantiza visibilidad, no atomicidad)
 *   Para atomicidad: usar AtomicInteger o synchronized.
 *
 * CUÁNDO USAR volatile:
 * ---------------------
 *   SI: flags de estado (running, stop, initialized)
 *   SI: un hilo escribe, otros solo leen
 *   NO: cuando necesitas operaciones compuestas (i++, check-then-act)
 */
public class MemoryVisibilityExample {

    // ============================================================
    // PROBLEMA 1: Flag sin volatile -> hilo puede ejecutarse infinito
    // ============================================================

    // SIN volatile: el JIT puede cachear el valor en registro
    private static boolean sinVolatile = false;

    // CON volatile: siempre se lee/escribe en RAM
    private static volatile boolean conVolatile = false;

    // ============================================================
    // PROBLEMA 2: Publicación insegura de objetos
    // ============================================================

    // SIN volatile: otro hilo puede ver el objeto a medias construido
    private static HeavyObject objetoInseguro = null;

    // CON volatile: garantiza visibilidad completa del objeto
    private static volatile HeavyObject objetoSeguro = null;

    static class HeavyObject {
        final int a, b, c;

        HeavyObject() {
            this.a = 1;  // sin volatile, otro hilo podría ver a=1, b=0
            this.b = 2;  // antes de que b y c sean escritos en RAM
            this.c = 3;
        }
    }

    // ============================================================
    // PROBLEMA 3: Double-Checked Locking (patron Singleton)
    // ============================================================

    // SIN volatile: tiene bug sutil de visibilidad
    private static InstanciaServicio instanciaRota;

    // CON volatile: correcto y eficiente
    private static volatile InstanciaServicio instanciaCorrecta;

    static class InstanciaServicio {
        private final String config;
        InstanciaServicio(String config) { this.config = config; }
        String getConfig() { return config; }
    }

    // ============================================================

    public static void main(String[] args) throws InterruptedException {
        run();
    }

    public static void run() throws InterruptedException {
        ConcurrencyRunner.header("VISIBILIDAD DE MEMORIA Y volatile");

        demo1_ProblemaFlagSinVolatile();
        demo2_SolucionConVolatile();
        demo3_PublicacionInseguraDeObjetos();
        demo4_DoubleCheckedLocking();
        demo5_VolatileNoEsAtomico();
        demo6_HappensBefore();
    }

    // ----------------------------------------------------------------
    // DEMO 1: El problema - flag sin volatile puede quedar en cache
    // ----------------------------------------------------------------
    static void demo1_ProblemaFlagSinVolatile() throws InterruptedException {
        ConcurrencyRunner.section("PROBLEMA: Flag sin volatile");

        sinVolatile = false;

        Thread trabajador = new Thread(() -> {
            System.out.println("  [trabajador] Iniciando. Mirando el flag...");
            long iteraciones = 0;
            // El JIT puede optimizar este bucle y nunca leer sinVolatile
            // de RAM nuevamente (lo mantiene en un registro del CPU).
            // Resultado posible: LOOP INFINITO aunque main ponga true.
            while (!sinVolatile) {
                iteraciones++;
                if (iteraciones > 500_000_000L) {
                    // Límite de seguridad para que no cuelgue la demo
                    System.out.println("  [trabajador] *** PROBLEMA: llevo 500M iteraciones y");
                    System.out.println("               sinVolatile sigue en FALSE para mi cache!");
                    System.out.println("               (el JIT cacheo el valor en mi registro)");
                    return;
                }
            }
            System.out.println("  [trabajador] Vi el cambio despues de " + iteraciones + " iteraciones");
        }, "trabajador-sin-volatile");

        trabajador.start();
        Thread.sleep(100); // dar tiempo al JIT para optimizar el bucle

        System.out.println("  [main] Poniendo sinVolatile = true...");
        sinVolatile = true; // puede que el trabajador NUNCA vea esto!
        System.out.println("  [main] Cambio hecho. El trabajador deberia verlo...");

        trabajador.join(500); // esperar max 500ms
        if (trabajador.isAlive()) {
            System.out.println("  [main] El trabajador SIGUE corriendo 500ms despues del cambio!");
            System.out.println("  [main] -> Visibilidad de memoria FALLIDA sin volatile");
            trabajador.interrupt();
            trabajador.join(200);
        }
        System.out.println();
    }

    // ----------------------------------------------------------------
    // DEMO 2: La solución - volatile garantiza visibilidad
    // ----------------------------------------------------------------
    static void demo2_SolucionConVolatile() throws InterruptedException {
        ConcurrencyRunner.section("SOLUCION: Flag con volatile");

        conVolatile = false;

        Thread trabajador = new Thread(() -> {
            System.out.println("  [trabajador] Iniciando. Mirando el flag volatile...");
            long iteraciones = 0;
            // Con volatile, cada lectura de conVolatile va a RAM
            // El JIT NO puede cachear el valor en registro
            while (!conVolatile) {
                iteraciones++;
            }
            System.out.println("  [trabajador] Vi el cambio despues de " + iteraciones + " iteraciones");
            System.out.println("  [trabajador] volatile funciono correctamente!");
        }, "trabajador-con-volatile");

        trabajador.start();
        Thread.sleep(100);

        System.out.println("  [main] Poniendo conVolatile = true...");
        conVolatile = true; // EL TRABAJADOR LO VE INMEDIATAMENTE
        System.out.println("  [main] Cambio hecho.");

        trabajador.join(500);
        if (trabajador.isAlive()) {
            System.out.println("  [main] (En esta maquina el JIT no optimizo el bucle sin volatile)");
            trabajador.interrupt();
        } else {
            System.out.println("  [main] El trabajador termino correctamente!");
        }

        System.out.println();
        System.out.println("  DIFERENCIA VISUAL:");
        System.out.println();
        System.out.println("  SIN volatile:");
        System.out.println("    Core 1 cache: stop=false    Core 2 cache: stop=false");
        System.out.println("    main escribe: stop=true     (queda solo en Core 1 cache)");
        System.out.println("    Core 1 cache: stop=true     Core 2 cache: stop=false ← PROBLEMA");
        System.out.println();
        System.out.println("  CON volatile:");
        System.out.println("    main escribe stop=true  →  flush a RAM inmediatamente");
        System.out.println("    trabajador lee stop     ←  lee desde RAM (ve true)");
        System.out.println();
    }

    // ----------------------------------------------------------------
    // DEMO 3: Publicación insegura de objetos
    // ----------------------------------------------------------------
    static void demo3_PublicacionInseguraDeObjetos() throws InterruptedException {
        ConcurrencyRunner.section("Publicacion segura vs insegura de objetos");

        // Publicacion INSEGURA: sin volatile
        // El problema: la JVM puede reordenar instrucciones.
        // Al hacer: instanciaRota = new HeavyObject()
        // La JVM puede ejecutarlo como:
        //   1. Asignar memoria
        //   2. Escribir referencia (instanciaRota != null)  <- otro hilo puede ver esto
        //   3. Inicializar campos a, b, c                  <- pero aun no termino!
        // Resultado: otro hilo ve un objeto A MEDIAS construido

        Thread escritorInseguro = new Thread(() -> {
            objetoInseguro = new HeavyObject(); // sin volatile
        });

        Thread lectorInseguro = new Thread(() -> {
            HeavyObject obj = objetoInseguro; // puede ver null, o un objeto parcial!
            if (obj != null) {
                // En teoria, obj.b podria ser 0 si la escritura fue parcial
                System.out.println("  [lector-inseguro] Lee: a=" + obj.a + " b=" + obj.b + " c=" + obj.c);
                System.out.println("  [lector-inseguro] (puede ver valores parciales/incorrectos)");
            } else {
                System.out.println("  [lector-inseguro] Ve null (escritura no visible aun)");
            }
        });

        escritorInseguro.start();
        escritorInseguro.join();
        lectorInseguro.start();
        lectorInseguro.join();

        // Publicacion SEGURA: con volatile
        Thread escritorSeguro = new Thread(() -> {
            HeavyObject local = new HeavyObject();  // construir localmente primero
            objetoSeguro = local;                   // LUEGO asignar con volatile
            // Con volatile, la asignacion incluye una barrera de memoria:
            // garantiza que todos los campos esten escritos ANTES de que
            // otro hilo pueda ver la referencia != null
        });

        Thread lectorSeguro = new Thread(() -> {
            while (objetoSeguro == null) { /* esperar */ }
            HeavyObject obj = objetoSeguro; // garantizado: objeto completamente inicializado
            System.out.println("  [lector-seguro]   Lee: a=" + obj.a + " b=" + obj.b + " c=" + obj.c);
            System.out.println("  [lector-seguro]   Con volatile siempre ve el objeto completo");
        });

        escritorSeguro.start();
        escritorSeguro.join();
        lectorSeguro.start();
        lectorSeguro.join();
    }

    // ----------------------------------------------------------------
    // DEMO 4: Double-Checked Locking (Singleton thread-safe)
    // ----------------------------------------------------------------
    static void demo4_DoubleCheckedLocking() throws InterruptedException {
        ConcurrencyRunner.section("Double-Checked Locking (patron Singleton)");

        System.out.println("  Implementacion ROTA (sin volatile):");
        System.out.println("  --------------------------------");
        System.out.println("  static InstanciaServicio getInstanciaRota() {");
        System.out.println("      if (instanciaRota == null) {         // 1er check (sin lock)");
        System.out.println("          synchronized (lock) {");
        System.out.println("              if (instanciaRota == null) { // 2do check (con lock)");
        System.out.println("                  instanciaRota = new InstanciaServicio('cfg');");
        System.out.println("                  // BUG: otro hilo puede ver instanciaRota != null");
        System.out.println("                  // ANTES de que el constructor termine!");
        System.out.println("              }");
        System.out.println("          }");
        System.out.println("      }");
        System.out.println("      return instanciaRota;");
        System.out.println("  }");

        System.out.println();
        System.out.println("  Implementacion CORRECTA (con volatile):");
        System.out.println("  ----------------------------------------");
        System.out.println("  private static volatile InstanciaServicio instancia; // <- volatile");
        System.out.println();
        System.out.println("  static InstanciaServicio getInstancia() {");
        System.out.println("      if (instanciaCorrecta == null) {         // 1er check (sin lock)");
        System.out.println("          synchronized (MemoryVisibilityExample.class) {");
        System.out.println("              if (instanciaCorrecta == null) { // 2do check (con lock)");
        System.out.println("                  instanciaCorrecta = new InstanciaServicio('cfg');");
        System.out.println("                  // volatile garantiza que la referencia NO se");
        System.out.println("                  // hace visible hasta que el constructor termino.");
        System.out.println("              }");
        System.out.println("          }");
        System.out.println("      }");
        System.out.println("      return instanciaCorrecta;");
        System.out.println("  }");

        // Ejecutar la version correcta con multiples hilos
        Thread[] hilos = new Thread[10];
        for (int i = 0; i < 10; i++) {
            hilos[i] = new Thread(() -> {
                InstanciaServicio s = getSingleton();
                // Todos los hilos deben obtener LA MISMA instancia
            });
        }
        for (Thread h : hilos) h.start();
        for (Thread h : hilos) h.join();

        System.out.println();
        System.out.println("  Singleton creado: " + instanciaCorrecta.getConfig());
        System.out.println("  Todos los hilos obtuvieron la misma instancia: OK");
    }

    static InstanciaServicio getSingleton() {
        if (instanciaCorrecta == null) {                  // 1er check: rapido, sin lock
            synchronized (MemoryVisibilityExample.class) {
                if (instanciaCorrecta == null) {          // 2do check: dentro del lock
                    instanciaCorrecta = new InstanciaServicio("config-produccion");
                }
            }
        }
        return instanciaCorrecta;
    }

    // ----------------------------------------------------------------
    // DEMO 5: volatile NO garantiza atomicidad
    // ----------------------------------------------------------------
    static void demo5_VolatileNoEsAtomico() throws InterruptedException {
        ConcurrencyRunner.section("volatile NO es atomico (error comun)");

        // volatile garantiza VISIBILIDAD pero NO ATOMICIDAD
        // counter++ con volatile SIGUE siendo una race condition

        VolatileCounter contadorMal = new VolatileCounter();
        CorrectCounter  contadorBien = new CorrectCounter();

        int HILOS = 10, ITER = 1000;

        Thread[] hMal = new Thread[HILOS];
        Thread[] hBien = new Thread[HILOS];

        for (int i = 0; i < HILOS; i++) {
            hMal[i]  = new Thread(() -> { for (int j = 0; j < ITER; j++) contadorMal.increment(); });
            hBien[i] = new Thread(() -> { for (int j = 0; j < ITER; j++) contadorBien.increment(); });
        }

        for (int i = 0; i < HILOS; i++) { hMal[i].start(); hBien[i].start(); }
        for (int i = 0; i < HILOS; i++) { hMal[i].join(); hBien[i].join(); }

        System.out.println("  volatile counter++ (race condition): " + contadorMal.value
            + " (esperado: " + (HILOS * ITER) + ")");
        System.out.println("  AtomicInteger      (correcto)       : " + contadorBien.value.get()
            + " (esperado: " + (HILOS * ITER) + ")");
        System.out.println();
        System.out.println("  volatile int count = 0;");
        System.out.println("  count++;  <- SON 3 operaciones aunque count sea volatile:");
        System.out.println("    1. LEER  count desde RAM   (gracias a volatile)");
        System.out.println("    2. SUMAR count + 1");
        System.out.println("    3. ESCRIBIR resultado a RAM (gracias a volatile)");
        System.out.println("  Entre el paso 1 y 3 otro hilo puede hacer lo mismo -> race condition!");
        System.out.println("  USAR: AtomicInteger.incrementAndGet() para operaciones atomicas");
    }

    static class VolatileCounter {
        volatile int value = 0;
        void increment() { value++; } // NO ATOMICO aunque sea volatile
    }

    static class CorrectCounter {
        java.util.concurrent.atomic.AtomicInteger value = new java.util.concurrent.atomic.AtomicInteger(0);
        void increment() { value.incrementAndGet(); } // ATOMICO
    }

    // ----------------------------------------------------------------
    // DEMO 6: Happens-Before con volatile
    // ----------------------------------------------------------------
    static void demo6_HappensBefore() throws InterruptedException {
        ConcurrencyRunner.section("Happens-Before con volatile");

        System.out.println("  REGLA HAPPENS-BEFORE de volatile:");
        System.out.println("  Una escritura volatile HAPPENS-BEFORE cualquier lectura");
        System.out.println("  subsiguiente de la misma variable.");
        System.out.println();
        System.out.println("  Esto significa que TODO lo escrito antes de la escritura volatile");
        System.out.println("  es visible para el hilo que lee la variable volatile.");

        // Demostrar happens-before
        int[] datosCompartidos = new int[3];
        // volatile no puede ser local: usar campo estatico o AtomicBoolean
        java.util.concurrent.atomic.AtomicBoolean publicado = new java.util.concurrent.atomic.AtomicBoolean(false);

        Thread escritor = new Thread(() -> {
            datosCompartidos[0] = 100;  // escrito ANTES de la barrera
            datosCompartidos[1] = 200;
            datosCompartidos[2] = 300;
            publicado.set(true);        // ESCRITURA con barrera de memoria (AtomicBoolean usa volatile internamente)
            // Todo lo de arriba ya esta en RAM cuando esto se ejecuta
        });

        Thread lector = new Thread(() -> {
            while (!publicado.get()) { /* esperar */ }
            // LECTURA VOLATILE = barrera de memoria
            // Gracias al happens-before de volatile, los datos de arriba son visibles
            System.out.println("  [lector] Lee datos[0]=" + datosCompartidos[0]
                + " datos[1]=" + datosCompartidos[1]
                + " datos[2]=" + datosCompartidos[2]);
            System.out.println("  [lector] TODOS visibles gracias al happens-before de volatile");
        });

        escritor.start(); lector.start();
        escritor.join(); lector.join();

        System.out.println();
        System.out.println("  RESUMEN volatile:");
        System.out.println("  ┌─────────────────────────────────────────────────────────┐");
        System.out.println("  │ volatile GARANTIZA:                                     │");
        System.out.println("  │   ✓ Visibilidad: todos los hilos ven el valor actual    │");
        System.out.println("  │   ✓ Orden: no se reordenan instrucciones (barrera)      │");
        System.out.println("  │   ✓ Happens-before entre escrituras y lecturas          │");
        System.out.println("  │                                                         │");
        System.out.println("  │ volatile NO GARANTIZA:                                  │");
        System.out.println("  │   ✗ Atomicidad (i++ sigue siendo race condition)        │");
        System.out.println("  │   ✗ Exclusion mutua (varios hilos pueden entrar)        │");
        System.out.println("  └─────────────────────────────────────────────────────────┘");
    }
}
