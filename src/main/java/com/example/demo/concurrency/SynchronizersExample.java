package com.example.demo.concurrency;

import java.util.concurrent.*;

/**
 * ============================================================
 * EJEMPLO 10: SINCRONIZADORES
 * ============================================================
 *
 * Los sincronizadores son utilidades de coordinacion entre hilos.
 * Permiten que hilos esperen ciertos eventos o condiciones
 * antes de continuar.
 *
 * SINCRONIZADORES DISPONIBLES:
 * ----------------------------
 *   CountDownLatch  : esperar que N eventos ocurran (uso unico)
 *   CyclicBarrier   : esperar que N hilos lleguen a un punto (reutilizable)
 *   Semaphore       : controlar acceso a N recursos
 *   Phaser          : coordinacion de fases (mas flexible que los anteriores)
 *   Exchanger       : intercambiar datos entre exactamente 2 hilos
 */
public class SynchronizersExample {

    public static void main(String[] args) throws Exception { run(); }

    public static void run() throws InterruptedException, BrokenBarrierException {
        ConcurrencyRunner.header("10. SINCRONIZADORES");

        ejemplo_CountDownLatch();
        ejemplo_CyclicBarrier();
        ejemplo_Semaphore();
        ejemplo_Phaser();
        ejemplo_Exchanger();
    }

    // ----------------------------------------------------------------
    // 1. CountDownLatch
    // ----------------------------------------------------------------
    static void ejemplo_CountDownLatch() throws InterruptedException {
        ConcurrencyRunner.section("1. CountDownLatch");

        // CountDownLatch(n): un contador que va de n hasta 0.
        // await()     : bloquea hasta que el contador llega a 0
        // countDown() : decrementa el contador en 1
        //
        // USO TIPICO 1: Esperar que todos los hilos esten listos antes de empezar
        // USO TIPICO 2: Esperar que todas las tareas terminen

        int N_SERVICIOS = 3;
        CountDownLatch listo = new CountDownLatch(N_SERVICIOS);

        System.out.println("  Simulando inicio de microservicios...");
        for (int i = 1; i <= N_SERVICIOS; i++) {
            final int id = i;
            new Thread(() -> {
                try {
                    int tiempo = id * 80;
                    Thread.sleep(tiempo);
                    System.out.printf("  [Servicio-%d] Listo! (%dms)%n", id, tiempo);
                    listo.countDown(); // reportar que este servicio esta listo
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }, "Servicio-" + i).start();
        }

        System.out.println("  [Main] Esperando que todos los servicios arranquen...");
        listo.await(); // bloquea hasta countDown() x3
        // listo.await(5, TimeUnit.SECONDS); // con timeout
        System.out.println("  [Main] Todos los servicios listos! Iniciando trafico.");

        System.out.println();
        System.out.println("  NOTA: CountDownLatch NO ES reutilizable.");
        System.out.println("  Una vez llega a 0, siempre retorna inmediatamente.");
        System.out.println("  Para reutilizable: usar CyclicBarrier o Phaser.");

        // --- Patron: pistola de salida ---
        System.out.println();
        CountDownLatch pistola = new CountDownLatch(1); // solo 1 evento
        CountDownLatch terminaron = new CountDownLatch(3);

        for (int i = 1; i <= 3; i++) {
            final int id = i;
            new Thread(() -> {
                try {
                    System.out.printf("  [Corredor-%d] En posicion, esperando pistola...%n", id);
                    pistola.await(); // TODOS esperan aqui
                    System.out.printf("  [Corredor-%d] Corriendo!%n", id);
                    Thread.sleep((long)(Math.random() * 100));
                    System.out.printf("  [Corredor-%d] Meta!%n", id);
                    terminaron.countDown();
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }, "Corredor-" + i).start();
        }

        Thread.sleep(50);
        System.out.println("  [Juez] DISPARO! (countDown a pistola)");
        pistola.countDown(); // liberar TODOS los corredores a la vez
        terminaron.await();
        System.out.println("  [Juez] Todos en la meta!");
    }

    // ----------------------------------------------------------------
    // 2. CyclicBarrier
    // ----------------------------------------------------------------
    static void ejemplo_CyclicBarrier() throws InterruptedException, BrokenBarrierException {
        ConcurrencyRunner.section("2. CyclicBarrier");

        // CyclicBarrier(n, accion): espera que n hilos lleguen al punto de barrera.
        // Cuando llegan todos, ejecuta la accion (opcional) y libera todos.
        // Es REUTILIZABLE (cyclic = ciclico).
        //
        // Util para: simulaciones por pasos, procesamiento por fases

        int N_HILOS = 3;
        CyclicBarrier barrera = new CyclicBarrier(N_HILOS,
            () -> System.out.println("  [Barrera] Todos llegaron! Iniciando siguiente fase...\n")
        );

        System.out.println("  Simulacion con 3 pasos y 3 hilos:");

        for (int i = 1; i <= N_HILOS; i++) {
            final int id = i;
            new Thread(() -> {
                try {
                    for (int paso = 1; paso <= 3; paso++) {
                        // Simular trabajo de este paso
                        Thread.sleep(id * 30L);
                        System.out.printf("  [Hilo-%d] Completo paso %d%n", id, paso);
                        barrera.await(); // esperar que los otros N-1 hilos lleguen
                        // Todos continuan al mismo tiempo al siguiente paso
                    }
                    System.out.printf("  [Hilo-%d] Termino todos los pasos!%n", id);
                } catch (InterruptedException | BrokenBarrierException e) {
                    Thread.currentThread().interrupt();
                }
            }, "Hilo-" + i).start();
        }

        Thread.sleep(800); // dar tiempo a que terminen

        System.out.println("  Diferencia con CountDownLatch:");
        System.out.println("  CountDownLatch: solo cuenta hacia abajo (uso unico)");
        System.out.println("  CyclicBarrier : se resetea automaticamente (reutilizable)");
        System.out.println("  CyclicBarrier.reset() : resetear manualmente si es necesario");
    }

    // ----------------------------------------------------------------
    // 3. Semaphore
    // ----------------------------------------------------------------
    static void ejemplo_Semaphore() throws InterruptedException {
        ConcurrencyRunner.section("3. Semaphore");

        // Semaphore(n): controla el acceso a hasta n recursos simultaneos.
        // acquire() : toma un permiso (bloquea si no hay disponibles)
        // release() : devuelve un permiso
        //
        // Semaphore(1) es similar a un mutex (solo 1 a la vez)
        // Semaphore(n) permite hasta n accesos simultaneos

        // Ejemplo: pool de conexiones de base de datos (max 3 conexiones)
        Semaphore conexiones = new Semaphore(3);
        System.out.println("  Pool de 3 conexiones DB, 7 hilos intentando conectar:");

        Thread[] hilos = new Thread[7];
        for (int i = 1; i <= 7; i++) {
            final int id = i;
            hilos[i-1] = new Thread(() -> {
                try {
                    System.out.printf("  [Request-%d] Esperando conexion... (disponibles: %d)%n",
                        id, conexiones.availablePermits());
                    conexiones.acquire(); // bloquea si no hay permisos
                    try {
                        System.out.printf("  [Request-%d] Tengo conexion! Usando DB...%n", id);
                        Thread.sleep(100); // simular uso de DB
                    } finally {
                        conexiones.release(); // SIEMPRE liberar en finally
                        System.out.printf("  [Request-%d] Libere conexion%n", id);
                    }
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }, "Request-" + id);
        }
        for (Thread h : hilos) h.start();
        for (Thread h : hilos) h.join();

        System.out.println("  Permisos disponibles al final: " + conexiones.availablePermits());

        // tryAcquire: no bloqueante
        System.out.println();
        Semaphore sem = new Semaphore(1);
        sem.acquire();
        boolean ok = sem.tryAcquire();  // false: ya ocupado
        System.out.println("  tryAcquire (ocupado): " + ok);
        sem.release();
        ok = sem.tryAcquire();          // true: libre
        System.out.println("  tryAcquire (libre): " + ok);
        sem.release();

        // Semaphore fair: hilos esperan en orden FIFO
        Semaphore fair = new Semaphore(1, true); // fair=true
        System.out.println("  Semaphore fair (FIFO): " + fair.isFair());
    }

    // ----------------------------------------------------------------
    // 4. Phaser
    // ----------------------------------------------------------------
    static void ejemplo_Phaser() throws InterruptedException {
        ConcurrencyRunner.section("4. Phaser (coordinacion avanzada de fases)");

        // Phaser combina CountDownLatch + CyclicBarrier + mas funcionalidad:
        // - Registro dinamico de participantes (pueden unirse/salir)
        // - Multiples fases numeradas
        // - Terminacion condicional

        Phaser phaser = new Phaser(1); // 1 participante inicial: el hilo main
        int N = 3;

        System.out.println("  Simulando procesamiento en 2 fases con " + N + " workers:");

        for (int i = 1; i <= N; i++) {
            final int id = i;
            phaser.register(); // registrar nuevo participante
            new Thread(() -> {
                // FASE 1
                System.out.printf("  [Worker-%d] Fase 1: procesando...%n", id);
                try { Thread.sleep(id * 40L); } catch (InterruptedException e) {}
                System.out.printf("  [Worker-%d] Fase 1: terminada%n", id);
                phaser.arriveAndAwaitAdvance(); // llegar y esperar que todos lleguen

                // FASE 2
                System.out.printf("  [Worker-%d] Fase 2: procesando...%n", id);
                try { Thread.sleep(id * 30L); } catch (InterruptedException e) {}
                System.out.printf("  [Worker-%d] Fase 2: terminada%n", id);
                phaser.arriveAndDeregister(); // llegar y desregistrarse (ya no participa mas)
            }, "Worker-" + i).start();
        }

        // Main espera fase 1
        phaser.arriveAndAwaitAdvance(); // main llega al final de fase 1
        System.out.println("  [Main] Todos completaron FASE 1. Iniciando FASE 2...");

        phaser.arriveAndDeregister(); // main se desregistra (no participa en fase 2)
        phaser.awaitAdvance(phaser.getPhase()); // esperar que fase 2 termine

        System.out.println("  [Main] Todas las fases completadas!");
        System.out.println("  Fase actual: " + phaser.getPhase());
        System.out.println("  Participantes registrados: " + phaser.getRegisteredParties());

        // Phaser con terminacion
        System.out.println();
        Phaser terminable = new Phaser(1) {
            @Override
            protected boolean onAdvance(int phase, int parties) {
                System.out.println("  [Phaser] Avanzando a fase " + (phase + 1)
                    + " con " + parties + " participantes");
                return phase >= 2; // terminar despues de 3 fases (0, 1, 2)
            }
        };

        for (int fase = 0; fase < 3; fase++) {
            System.out.println("  [Main] Ejecutando fase " + fase);
            if (terminable.isTerminated()) break;
            terminable.arriveAndAwaitAdvance();
        }
        System.out.println("  Phaser terminado: " + terminable.isTerminated());
    }

    // ----------------------------------------------------------------
    // 5. Exchanger
    // ----------------------------------------------------------------
    static void ejemplo_Exchanger() throws InterruptedException {
        ConcurrencyRunner.section("5. Exchanger (intercambio entre 2 hilos)");

        // Exchanger<T>: punto de intercambio entre EXACTAMENTE 2 hilos.
        // Cuando ambos llaman exchange(), intercambian sus valores y continuan.
        // Si solo uno llama exchange(), se bloquea esperando al otro.
        //
        // Util para: pipelines, doble buffer, algoritmos de reunion

        Exchanger<String> exchanger = new Exchanger<>();

        Thread productor = new Thread(() -> {
            try {
                String[] paquetes = {"datos-1", "datos-2", "datos-3"};
                for (String paquete : paquetes) {
                    System.out.println("  [Productor] Envio: " + paquete);
                    String respuesta = exchanger.exchange(paquete); // intercambia con consumidor
                    System.out.println("  [Productor] Recibi confirmacion: " + respuesta);
                }
                exchanger.exchange("FIN"); // señal de fin
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "Productor");

        Thread consumidor = new Thread(() -> {
            try {
                while (true) {
                    String recibido = exchanger.exchange("ACK"); // intercambia con productor
                    if ("FIN".equals(recibido)) {
                        System.out.println("  [Consumidor] Recibi FIN. Terminando.");
                        break;
                    }
                    System.out.println("  [Consumidor] Procese: " + recibido);
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "Consumidor");

        productor.start(); consumidor.start();
        productor.join(); consumidor.join();
    }
}
