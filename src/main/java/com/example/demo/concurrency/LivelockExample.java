package com.example.demo.concurrency;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ============================================================
 * EJEMPLO: LIVELOCK
 * ============================================================
 *
 * QUE ES UN LIVELOCK?
 * -------------------
 * Un livelock ocurre cuando dos o más hilos están ACTIVOS
 * (consumiendo CPU, estado RUNNABLE, no bloqueados), pero
 * REACCIONAN constantemente a las acciones del otro sin
 * hacer ningún progreso real.
 *
 * ANALOGIA DEL PASILLO:
 * ---------------------
 *   Persona A y Persona B caminan frente a frente.
 *   A se mueve a la derecha. B se mueve a su izquierda (mismo lado).
 *   Siguen frente a frente. A se mueve al otro lado. B también.
 *   Ambas se mueven continuamente (están "vivas"), pero nunca
 *   logran pasar una junto a la otra.
 *
 * ─────────────────────────────────────────────────────────────
 *  Deadlock   : hilos BLOQUEADOS (estado BLOCKED/WAITING)
 *               CPU = 0%, no hay progreso, nadie se mueve
 *
 *  Livelock   : hilos ACTIVOS (estado RUNNABLE)
 *               CPU > 0%, no hay progreso, se mueven en círculos
 *
 *  Starvation : un hilo activo, NUNCA elegido por el scheduler
 * ─────────────────────────────────────────────────────────────
 *
 * COMO OCURRE EN CÓDIGO:
 * ----------------------
 * Típicamente en algoritmos de "retroceder y reintentar":
 * Cuando un hilo no puede obtener el segundo lock, libera el
 * primero y reintenta. Si dos hilos hacen esto al mismo tiempo,
 * se bloquean y liberan en sincronía perpetua.
 *
 * SOLUCIÓN:
 * ---------
 * Introducir aleatoriedad en el tiempo de backoff.
 * Así los hilos se "desincronizan" y uno eventualmente avanza.
 */
public class LivelockExample {

    public static void main(String[] args) throws InterruptedException {
        run();
    }

    public static void run() throws InterruptedException {
        ConcurrencyRunner.header("LIVELOCK");

        demo1_LivelockReal();
        Thread.sleep(200);
        demo2_SolucionBackoffAleatorio();
        demo3_DiagramaVisual();
    }

    // ================================================================
    // DEMO 1: Livelock real - dos hilos reaccionando indefinidamente
    // ================================================================
    static void demo1_LivelockReal() throws InterruptedException {
        ConcurrencyRunner.section("PROBLEMA: Livelock real");

        // Escenario: dos hilos necesitan AMBOS locks para trabajar.
        // Cuando no pueden obtener el segundo, "son educados" y
        // sueltan el primero para que el otro pase.
        // El problema: ¡ambos hacen lo mismo al mismo tiempo!

        ReentrantLock lockA = new ReentrantLock();
        ReentrantLock lockB = new ReentrantLock();

        AtomicInteger intentosA = new AtomicInteger(0);
        AtomicInteger intentosB = new AtomicInteger(0);
        AtomicInteger[] exitos = {new AtomicInteger(0)};

        final int MAX_INTENTOS = 20; // límite para que la demo termine
        final long FIN = System.currentTimeMillis() + 2000; // max 2 segundos

        Thread hiloA = new Thread(() -> {
            while (intentosA.get() < MAX_INTENTOS && System.currentTimeMillis() < FIN) {
                // Hilo A: primero lockA, luego lockB
                lockA.lock();
                System.out.printf("  [Hilo-A] Tome lockA (intento %d), intento lockB...%n",
                    intentosA.incrementAndGet());

                if (lockB.tryLock()) {
                    try {
                        System.out.println("  [Hilo-A] ¡Tengo ambos locks! Trabajando...");
                        exitos[0].incrementAndGet();
                        return; // EXIT: progreso real
                    } finally {
                        lockB.unlock();
                    }
                } else {
                    // No pude obtener lockB -> "ser educado" y soltar lockA
                    System.out.println("  [Hilo-A] lockB ocupado. Suelto lockA para ceder paso.");
                    lockA.unlock();
                    // Sin pausa: vuelvo a intentar inmediatamente
                    // -> El problema: Hilo-B hace lo mismo al mismo tiempo!
                }
            }
            System.out.println("  [Hilo-A] *** Agote los intentos sin hacer progreso ***");
        }, "Hilo-A");

        Thread hiloB = new Thread(() -> {
            while (intentosB.get() < MAX_INTENTOS && System.currentTimeMillis() < FIN) {
                // Hilo B: primero lockB, luego lockA (orden opuesto)
                lockB.lock();
                System.out.printf("  [Hilo-B] Tome lockB (intento %d), intento lockA...%n",
                    intentosB.incrementAndGet());

                if (lockA.tryLock()) {
                    try {
                        System.out.println("  [Hilo-B] ¡Tengo ambos locks! Trabajando...");
                        exitos[0].incrementAndGet();
                        return; // EXIT: progreso real
                    } finally {
                        lockA.unlock();
                    }
                } else {
                    // No pude obtener lockA -> "ser educado" y soltar lockB
                    System.out.println("  [Hilo-B] lockA ocupado. Suelto lockB para ceder paso.");
                    lockB.unlock();
                    // Sin pausa: vuelvo a intentar inmediatamente -> LIVELOCK!
                }
            }
            System.out.println("  [Hilo-B] *** Agote los intentos sin hacer progreso ***");
        }, "Hilo-B");

        hiloA.start(); hiloB.start();
        hiloA.join(3000); hiloB.join(3000);

        System.out.println();
        System.out.println("  Intentos Hilo-A: " + intentosA.get());
        System.out.println("  Intentos Hilo-B: " + intentosB.get());
        System.out.println("  Exitos reales  : " + exitos[0].get()
            + " (deberia ser 2, pero con livelock puede ser 0)");
        System.out.println();
        System.out.println("  AMBOS HILOS ESTUVIERON ACTIVOS (RUNNABLE) todo el tiempo.");
        System.out.println("  Consumieron CPU. Pero no hicieron trabajo util.");
        System.out.println("  -> ESTO ES LIVELOCK");
    }

    // ================================================================
    // DEMO 2: Solución - Backoff aleatorio rompe la sincronía
    // ================================================================
    static void demo2_SolucionBackoffAleatorio() throws InterruptedException {
        ConcurrencyRunner.section("SOLUCION: Backoff aleatorio");

        // La clave: cuando falla, esperar un tiempo ALEATORIO antes de reintentar.
        // Si A espera 17ms y B espera 43ms, en el siguiente intento
        // A va primero, obtiene ambos locks, y sale. B espera brevemente.
        // La aleatoriedad rompe la sincronía del livelock.

        ReentrantLock lockA = new ReentrantLock();
        ReentrantLock lockB = new ReentrantLock();

        AtomicInteger intentosA = new AtomicInteger(0);
        AtomicInteger intentosB = new AtomicInteger(0);
        boolean[] exitoA = {false};
        boolean[] exitoB = {false};

        Thread hiloA = new Thread(() -> {
            while (!exitoA[0]) {
                lockA.lock();
                intentosA.incrementAndGet();

                if (lockB.tryLock()) {
                    try {
                        System.out.printf("  [Hilo-A] EXITO en intento %d! Realizando trabajo.%n",
                            intentosA.get());
                        exitoA[0] = true;
                        return;
                    } finally {
                        lockB.unlock();
                    }
                } else {
                    lockA.unlock();
                    // BACKOFF ALEATORIO: esperar un tiempo impredecible
                    long espera = (long)(Math.random() * 30) + 1; // 1-30ms aleatorio
                    System.out.printf("  [Hilo-A] lockB ocupado. Esperando %dms antes de reintentar.%n",
                        espera);
                    try { Thread.sleep(espera); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                }
            }
        }, "Hilo-A");

        Thread hiloB = new Thread(() -> {
            while (!exitoB[0]) {
                lockB.lock();
                intentosB.incrementAndGet();

                if (lockA.tryLock()) {
                    try {
                        System.out.printf("  [Hilo-B] EXITO en intento %d! Realizando trabajo.%n",
                            intentosB.get());
                        exitoB[0] = true;
                        return;
                    } finally {
                        lockA.unlock();
                    }
                } else {
                    lockB.unlock();
                    // BACKOFF ALEATORIO
                    long espera = (long)(Math.random() * 30) + 1;
                    System.out.printf("  [Hilo-B] lockA ocupado. Esperando %dms antes de reintentar.%n",
                        espera);
                    try { Thread.sleep(espera); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                }
            }
        }, "Hilo-B");

        long inicio = System.currentTimeMillis();
        hiloA.start(); hiloB.start();
        hiloA.join(5000); hiloB.join(5000);
        long tiempo = System.currentTimeMillis() - inicio;

        System.out.println();
        System.out.println("  Intentos Hilo-A: " + intentosA.get());
        System.out.println("  Intentos Hilo-B: " + intentosB.get());
        System.out.println("  Ambos completaron: " + (exitoA[0] && exitoB[0]));
        System.out.println("  Tiempo total: " + tiempo + "ms");
        System.out.println();
        System.out.println("  El backoff aleatorio desincroniiza los reintentos.");
        System.out.println("  Cuando uno espera mas que el otro, el otro avanza primero.");
        System.out.println();
        System.out.println("  NOTA: Este mismo patron es usado en:");
        System.out.println("  - Ethernet (CSMA/CD: colisiones con backoff aleatorio)");
        System.out.println("  - TCP (retransmision con exponential backoff)");
        System.out.println("  - HTTP clients (retry con jitter)");
    }

    // ================================================================
    // DEMO 3: Diagrama visual comparativo
    // ================================================================
    static void demo3_DiagramaVisual() {
        ConcurrencyRunner.section("Diagrama visual");

        System.out.println("  LIVELOCK (sin solucion):");
        System.out.println();
        System.out.println("  t=0  Hilo-A: toma lockA -> intenta lockB -> FALLA -> suelta lockA");
        System.out.println("  t=0  Hilo-B: toma lockB -> intenta lockA -> FALLA -> suelta lockB");
        System.out.println("  t=1  Hilo-A: toma lockA -> intenta lockB -> FALLA -> suelta lockA");
        System.out.println("  t=1  Hilo-B: toma lockB -> intenta lockA -> FALLA -> suelta lockB");
        System.out.println("  t=2  [igual que t=1... infinitamente]");
        System.out.println("       Ambos ACTIVOS, ambos usando CPU, CERO progreso.");
        System.out.println();
        System.out.println("  SOLUCION (backoff aleatorio):");
        System.out.println();
        System.out.println("  t=0  Hilo-A: FALLA -> espera 17ms (aleatorio)");
        System.out.println("  t=0  Hilo-B: FALLA -> espera 43ms (aleatorio)");
        System.out.println("  t=17 Hilo-A: toma lockA, luego lockB -> EXITO! libera ambos.");
        System.out.println("  t=43 Hilo-B: toma lockB, luego lockA -> EXITO! libera ambos.");
        System.out.println("       A y B están desincronizados -> no colisionan.");
        System.out.println();
        System.out.println("  ─────────────────────────────────────────────────────────────");
        System.out.println("  TABLA COMPARATIVA:");
        System.out.println("  ─────────────────────────────────────────────────────────────");
        System.out.println("  Problema    │ Estado del hilo │ Uso CPU │ Progreso");
        System.out.println("  ────────────┼─────────────────┼─────────┼─────────");
        System.out.println("  Deadlock    │ BLOCKED/WAITING │    0%   │ Ninguno");
        System.out.println("  Livelock    │ RUNNABLE        │   alto  │ Ninguno");
        System.out.println("  Starvation  │ RUNNABLE        │    0%   │ Ninguno para 1 hilo");
        System.out.println("  ─────────────────────────────────────────────────────────────");
        System.out.println();
        System.out.println("  SOLUCIONES:");
        System.out.println("  Deadlock    -> Orden consistente de locks / tryLock con timeout");
        System.out.println("  Livelock    -> Backoff aleatorio / ceder el turno con delay random");
        System.out.println("  Starvation  -> ReentrantLock(fair=true) / Semaphore(fair=true)");
    }
}
