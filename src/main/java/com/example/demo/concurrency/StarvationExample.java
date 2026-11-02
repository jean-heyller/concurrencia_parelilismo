package com.example.demo.concurrency;

import java.util.concurrent.locks.ReentrantLock;

/**
 * ============================================================
 * EJEMPLO: INANICION (Starvation)
 * ============================================================
 *
 * QUE ES LA INANICION?
 * --------------------
 * La inanición ocurre cuando un hilo NUNCA obtiene los recursos
 * que necesita para ejecutarse, porque otros hilos (normalmente
 * con mayor prioridad o más veloces) siempre se lo ganan.
 *
 * El hilo NO está bloqueado (como en Deadlock).
 * Está vivo y listo para ejecutarse (estado RUNNABLE),
 * pero el planificador del SO nunca le da tiempo de CPU.
 *
 * DIFERENCIA CLAVE:
 * ─────────────────────────────────────────────────────
 *  Deadlock   : hilos BLOQUEADOS entre sí (ninguno avanza)
 *  Starvation : un hilo está listo pero NUNCA es elegido
 *  Livelock   : hilos ACTIVOS pero sin progreso útil
 * ─────────────────────────────────────────────────────
 *
 * CAUSAS COMUNES:
 * ---------------
 *  1. Locks no equitativos: hilos de alta prioridad siempre ganan
 *  2. synchronized no es fair: el SO elige quien entra, no hay cola
 *  3. Hilos de alta prioridad monopolizando CPU
 *  4. Locking de grano fino con muchos hilos compitiendo
 *
 * SOLUCIÓN:
 * ---------
 *  ReentrantLock(fair = true): usa cola FIFO, cada hilo espera su turno.
 *  Si llevas esperando más, eres el próximo.
 */
public class StarvationExample {

    public static void main(String[] args) throws InterruptedException {
        run();
    }

    public static void run() throws InterruptedException {
        ConcurrencyRunner.header("INANICION (Starvation)");

        demo1_StarvationConSynchronized();
        Thread.sleep(200);
        demo2_SolucionFairLock();
        demo3_ExplicacionVisual();
    }

    // ================================================================
    // DEMO 1: Starvation con synchronized (lock NO equitativo)
    // ================================================================
    static void demo1_StarvationConSynchronized() throws InterruptedException {
        ConcurrencyRunner.section("PROBLEMA: Starvation con synchronized (no fair)");

        // synchronized NO es justo: cuando el lock se libera,
        // el SO elige ARBITRARIAMENTE qué hilo entra a continuación.
        // Los hilos con mejor timing (o mayor prioridad del OS) ganan siempre.
        // Un hilo con mala suerte puede quedarse esperando indefinidamente.

        final Object lock = new Object();
        int[] conteos = new int[4]; // cuántas veces ejecutó cada hilo
        final int DURACION_MS = 500;
        final long fin = System.currentTimeMillis() + DURACION_MS;

        // 4 hilos compiten por el mismo lock
        // Los "codiciosos" hacen poco trabajo (liberan rápido y vuelven a pedir)
        // El hilo de "baja prioridad" tarda más dentro del lock

        Thread[] hilos = new Thread[4];
        String[] nombres = {"Codicioso-A", "Codicioso-B", "Codicioso-C", "Hambriento "};

        for (int i = 0; i < 4; i++) {
            final int id = i;
            hilos[i] = new Thread(() -> {
                while (System.currentTimeMillis() < fin) {
                    synchronized (lock) {
                        conteos[id]++;
                        // Los codiciosos hacen poco y sueltan rápido
                        // El hambriento "tarda más" en el lock
                        if (id == 3) {
                            // simular trabajo más costoso -> menos oportunidades
                            long espera = System.currentTimeMillis() + 2;
                            while (System.currentTimeMillis() < espera) { /* spin */ }
                        }
                    }
                    // Los codiciosos vuelven a pedir inmediatamente
                    // El hambriento tampoco descansa: sigue siendo RUNNABLE
                }
            }, nombres[i]);
            hilos[i].start();
        }

        for (Thread h : hilos) h.join();

        System.out.println("  Ejecuciones en " + DURACION_MS + "ms (mas = mas lucky):");
        for (int i = 0; i < 4; i++) {
            int barras = conteos[i] / 500;
            System.out.printf("  %-12s: %6d veces  %s%n",
                nombres[i], conteos[i], "|".repeat(Math.min(barras, 40)));
        }
        System.out.println();
        System.out.println("  El 'Hambriento' ejecuto MUCHO menos que los demas.");
        System.out.println("  No esta bloqueado, esta RUNNABLE, pero el SO nunca lo elige.");
        System.out.println("  -> INANICION (Starvation)");
    }

    // ================================================================
    // DEMO 2: Solución con ReentrantLock(fair = true)
    // ================================================================
    static void demo2_SolucionFairLock() throws InterruptedException {
        ConcurrencyRunner.section("SOLUCION: ReentrantLock(fair = true)");

        // fair = true: los hilos esperan en COLA FIFO.
        // El que lleva más tiempo esperando es el SIGUIENTE en entrar.
        // Esto elimina la starvation pero reduce el throughput
        // (más overhead por mantener la cola).

        final ReentrantLock fairLock = new ReentrantLock(true); // <-- fair=true
        int[] conteos = new int[4];
        final int DURACION_MS = 500;
        final long fin = System.currentTimeMillis() + DURACION_MS;

        Thread[] hilos = new Thread[4];
        String[] nombres = {"Hilo-A     ", "Hilo-B     ", "Hilo-C     ", "Hilo-D     "};

        for (int i = 0; i < 4; i++) {
            final int id = i;
            hilos[i] = new Thread(() -> {
                while (System.currentTimeMillis() < fin) {
                    fairLock.lock();
                    try {
                        conteos[id]++;
                        if (id == 3) {
                            long espera = System.currentTimeMillis() + 2;
                            while (System.currentTimeMillis() < espera) { /* spin */ }
                        }
                    } finally {
                        fairLock.unlock();
                    }
                }
            }, nombres[i]);
            hilos[i].start();
        }

        for (Thread h : hilos) h.join();

        System.out.println("  Ejecuciones en " + DURACION_MS + "ms con fair lock:");
        for (int i = 0; i < 4; i++) {
            int barras = conteos[i] / 200;
            System.out.printf("  %-12s: %6d veces  %s%n",
                nombres[i], conteos[i], "|".repeat(Math.min(barras, 40)));
        }
        System.out.println();
        System.out.println("  Con fair=true todos los hilos tienen oportunidades similares.");
        System.out.println("  El Hilo-D puede ser menor por su trabajo mas largo, pero");
        System.out.println("  no es IGNORADO - siempre entra en su turno de la cola FIFO.");
        System.out.println();
        System.out.println("  TRADE-OFF del fair lock:");
        System.out.println("  ✓ Previene starvation");
        System.out.println("  ✓ Orden predecible (FIFO)");
        System.out.println("  ✗ Menor throughput que unfair lock (~10-30% mas lento)");
        System.out.println("  ✗ Mas overhead por mantener la cola de espera");
    }

    // ================================================================
    // DEMO 3: Explicación visual con diagrama
    // ================================================================
    static void demo3_ExplicacionVisual() {
        ConcurrencyRunner.section("Explicacion visual de Starvation");

        System.out.println("  SIN fair lock (synchronized / ReentrantLock unfair):");
        System.out.println();
        System.out.println("  Tiempo ->  t1    t2    t3    t4    t5    t6    t7");
        System.out.println("  Hilo-A:    RUN  WAIT   RUN  WAIT   RUN  WAIT   RUN  <- siempre entra");
        System.out.println("  Hilo-B:    WAIT  RUN  WAIT   RUN  WAIT   RUN  WAIT  <- siempre entra");
        System.out.println("  Hambriento: WAIT WAIT  WAIT  WAIT  WAIT  WAIT  WAIT <- NUNCA entra!");
        System.out.println();
        System.out.println("  CON fair lock (ReentrantLock(true)):");
        System.out.println();
        System.out.println("  Cola de espera: [Hambriento, Hilo-A, Hilo-B, Hilo-A, Hambriento...]");
        System.out.println("  Tiempo ->  t1       t2       t3       t4       t5");
        System.out.println("  Hilo-A:    RUN    WAIT       RUN    WAIT       RUN");
        System.out.println("  Hilo-B:    WAIT    RUN     WAIT     RUN       WAIT");
        System.out.println("  Hambriento: WAIT   WAIT     RUN    WAIT        RUN  <- entra en su turno");
        System.out.println();
        System.out.println("  OTRAS SOLUCIONES para Starvation:");
        System.out.println("  1. ReentrantLock(true)     -> fair lock FIFO");
        System.out.println("  2. Semaphore(n, true)      -> fair semaphore");
        System.out.println("  3. Reducir contention      -> menos hilos por lock");
        System.out.println("  4. ThreadPoolExecutor      -> gestiona hilos equitativamente");
        System.out.println("  5. Redisenar: eliminar estado compartido (inmutabilidad)");
    }
}
