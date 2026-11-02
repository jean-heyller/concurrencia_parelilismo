package com.example.demo.concurrency;

/**
 * ============================================================
 * EJEMPLO 1: HILOS BASICOS (Threads)
 * ============================================================
 *
 * QUE ES UN HILO (Thread)?
 * ------------------------
 * Un hilo es la unidad minima de ejecucion de un programa.
 * Un proceso puede tener multiples hilos corriendo en paralelo.
 * Todos los hilos de un proceso comparten la misma memoria (heap),
 * pero cada hilo tiene su propio stack de ejecucion.
 *
 *  Proceso
 *  ├── Hilo 1 (stack propio) ──► ejecuta codigo
 *  ├── Hilo 2 (stack propio) ──► ejecuta codigo
 *  └── Hilo 3 (stack propio) ──► ejecuta codigo
 *       └── Todos comparten el HEAP (objetos, variables)
 *
 * ESTADOS DE UN HILO:
 * -------------------
 *  NEW ──► RUNNABLE ──► RUNNING ──► TERMINATED
 *                  ↕               ↕
 *               BLOCKED        WAITING
 *                           TIMED_WAITING
 *
 *  NEW           : Thread creado pero no iniciado (new Thread())
 *  RUNNABLE      : Listo para correr, esperando CPU
 *  BLOCKED       : Esperando un lock (synchronized)
 *  WAITING       : Esperando indefinidamente (wait(), join())
 *  TIMED_WAITING : Esperando con timeout (sleep(), wait(ms))
 *  TERMINATED    : Finalizo su ejecucion
 */
public class ThreadBasicsExample {

    public static void main(String[] args) throws InterruptedException { run(); }

    public static void run() throws InterruptedException {
        ConcurrencyRunner.header("1. HILOS BASICOS (Threads)");

        forma1_ExtenderThread();
        forma2_ImplementarRunnable();
        forma3_LambdaRunnable();
        forma4_ThreadInfo();
        forma5_SleepYJoin();
        forma6_Interrupt();
        forma7_DaemonThread();
        forma8_ThreadPriority();
    }

    // ----------------------------------------------------------------
    // FORMA 1: Extender la clase Thread
    // ----------------------------------------------------------------
    static void forma1_ExtenderThread() throws InterruptedException {
        ConcurrencyRunner.section("Forma 1: Extender Thread");

        // Definimos una clase que extiende Thread
        class MiHilo extends Thread {
            private final String tarea;

            MiHilo(String nombre, String tarea) {
                super(nombre); // le damos nombre al hilo
                this.tarea = tarea;
            }

            @Override
            public void run() {
                // Este codigo corre en el hilo nuevo
                System.out.printf("  [%s] Ejecutando: %s%n", getName(), tarea);
            }
        }

        MiHilo h1 = new MiHilo("Hilo-Cocina", "Cocinar pasta");
        MiHilo h2 = new MiHilo("Hilo-Limpieza", "Limpiar cocina");

        System.out.println("  Estado antes de start(): " + h1.getState()); // NEW

        h1.start(); // <-- Crea el hilo del OS y llama run() en el nuevo hilo
        h2.start();

        h1.join(); // Espera a que h1 termine
        h2.join(); // Espera a que h2 termine

        System.out.println("  Estado despues de join(): " + h1.getState()); // TERMINATED

        // IMPORTANTE: NUNCA llames run() directamente, eso NO crea un hilo nuevo!
        // h1.run() ejecutaria el codigo en el hilo ACTUAL, no en uno nuevo.
    }

    // ----------------------------------------------------------------
    // FORMA 2: Implementar Runnable (preferida sobre extender Thread)
    // ----------------------------------------------------------------
    static void forma2_ImplementarRunnable() throws InterruptedException {
        ConcurrencyRunner.section("Forma 2: Implementar Runnable (recomendada)");

        // Runnable es una interfaz funcional con un unico metodo: run()
        // Ventaja: separas la TAREA del MECANISMO de ejecucion
        Runnable tarea = new Runnable() {
            @Override
            public void run() {
                System.out.printf("  [%s] Runnable ejecutandose%n", Thread.currentThread().getName());
            }
        };

        Thread t = new Thread(tarea, "Hilo-Runnable");
        t.start();
        t.join();
    }

    // ----------------------------------------------------------------
    // FORMA 3: Lambda (Java 8+) - la mas concisa
    // ----------------------------------------------------------------
    static void forma3_LambdaRunnable() throws InterruptedException {
        ConcurrencyRunner.section("Forma 3: Lambda Runnable (Java 8+)");

        // Runnable es una @FunctionalInterface, por eso acepta lambda
        Thread t1 = new Thread(() ->
            System.out.printf("  [%s] Lambda hilo 1%n", Thread.currentThread().getName()),
            "Lambda-1"
        );

        Thread t2 = new Thread(() ->
            System.out.printf("  [%s] Lambda hilo 2%n", Thread.currentThread().getName()),
            "Lambda-2"
        );

        t1.start();
        t2.start();
        t1.join();
        t2.join();
    }

    // ----------------------------------------------------------------
    // FORMA 4: Info del hilo actual
    // ----------------------------------------------------------------
    static void forma4_ThreadInfo() {
        ConcurrencyRunner.section("Forma 4: Informacion del hilo");

        Thread t = Thread.currentThread();

        System.out.println("  Nombre   : " + t.getName());
        System.out.println("  ID       : " + t.threadId());
        System.out.println("  Estado   : " + t.getState());
        System.out.println("  Prioridad: " + t.getPriority() + " (1=MIN, 5=NORM, 10=MAX)");
        System.out.println("  Daemon   : " + t.isDaemon());
        System.out.println("  Vivo     : " + t.isAlive());

        // Todos los hilos activos
        System.out.println("  Hilos activos en el grupo: " + Thread.activeCount());
    }

    // ----------------------------------------------------------------
    // FORMA 5: sleep() y join()
    // ----------------------------------------------------------------
    static void forma5_SleepYJoin() throws InterruptedException {
        ConcurrencyRunner.section("Forma 5: sleep() y join()");

        Thread trabajador = new Thread(() -> {
            System.out.println("  [trabajador] Empezando tarea larga...");
            try {
                Thread.sleep(300); // Pausa el hilo actual por 300ms
                // sleep() NO libera locks, solo pausa la ejecucion
            } catch (InterruptedException e) {
                System.out.println("  [trabajador] Fui interrumpido durante sleep!");
                Thread.currentThread().interrupt(); // Buena practica: restaurar el flag
                return;
            }
            System.out.println("  [trabajador] Tarea completada!");
        }, "trabajador");

        long inicio = System.currentTimeMillis();
        trabajador.start();

        System.out.println("  [main] Hilo lanzado, esperando con join()...");
        trabajador.join(); // Bloquea main hasta que 'trabajador' termine
        // join(ms) -> espera maximo 'ms' milisegundos

        long fin = System.currentTimeMillis();
        System.out.println("  [main] join() retorno. Tiempo: " + (fin - inicio) + "ms");
    }

    // ----------------------------------------------------------------
    // FORMA 6: interrupt() - cancelar un hilo
    // ----------------------------------------------------------------
    static void forma6_Interrupt() throws InterruptedException {
        ConcurrencyRunner.section("Forma 6: interrupt()");

        Thread hilo = new Thread(() -> {
            System.out.println("  [hilo] Empezando bucle largo...");
            for (int i = 0; i < 1_000_000; i++) {
                // isInterrupted() chequea si alguien llamo interrupt() en este hilo
                if (Thread.currentThread().isInterrupted()) {
                    System.out.println("  [hilo] Detecte interrupcion en iteracion " + i + ". Saliendo limpiamente.");
                    return; // salida limpia
                }
                // simular trabajo
                Math.sqrt(i);
            }
            System.out.println("  [hilo] Termine sin interrupcion");
        }, "hilo-largo");

        hilo.start();
        Thread.sleep(1); // dejar que corra un poco
        hilo.interrupt(); // señal de cancelacion (NO mata el hilo, solo pone un flag)
        hilo.join();

        // InterruptedException:
        // Cuando un hilo esta en sleep/wait/join y alguien llama interrupt(),
        // se lanza InterruptedException. Debes capturarla y decidir que hacer.
        System.out.println("  [main] Hilo terminado. Estado: " + hilo.getState());
    }

    // ----------------------------------------------------------------
    // FORMA 7: Daemon threads
    // ----------------------------------------------------------------
    static void forma7_DaemonThread() throws InterruptedException {
        ConcurrencyRunner.section("Forma 7: Daemon Threads");

        // Un hilo daemon es un hilo de fondo (background).
        // La JVM termina cuando TODOS los hilos no-daemon terminan,
        // sin importar si los daemon todavia corren.
        // Ejemplo: garbage collector es un hilo daemon.

        Thread daemon = new Thread(() -> {
            while (true) { // bucle infinito
                try {
                    System.out.println("  [daemon] Estoy vivo... (background task)");
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "hilo-daemon");

        daemon.setDaemon(true); // DEBE llamarse ANTES de start()
        daemon.start();

        Thread.sleep(500); // dejar que el daemon corra un poco
        System.out.println("  [main] Main termina. JVM matara al daemon automaticamente.");
        daemon.interrupt(); // para este ejemplo lo interrumpimos manualmente
        daemon.join(500);
    }

    // ----------------------------------------------------------------
    // FORMA 8: Prioridades
    // ----------------------------------------------------------------
    static void forma8_ThreadPriority() throws InterruptedException {
        ConcurrencyRunner.section("Forma 8: Prioridades de hilos");

        // Las prioridades son SUGERENCIAS al scheduler del OS, no garantias.
        // Valores: Thread.MIN_PRIORITY=1, NORM_PRIORITY=5, MAX_PRIORITY=10

        Thread bajo  = new Thread(() -> System.out.println("  [bajo-prio]  Ejecutandome"), "bajo");
        Thread normal = new Thread(() -> System.out.println("  [norm-prio]  Ejecutandome"), "normal");
        Thread alto  = new Thread(() -> System.out.println("  [alto-prio]  Ejecutandome"), "alto");

        bajo.setPriority(Thread.MIN_PRIORITY);   // 1
        normal.setPriority(Thread.NORM_PRIORITY); // 5
        alto.setPriority(Thread.MAX_PRIORITY);   // 10

        System.out.println("  Prioridades: bajo=" + bajo.getPriority()
            + ", normal=" + normal.getPriority()
            + ", alto=" + alto.getPriority());

        // El orden de ejecucion aun no esta garantizado por prioridad sola
        bajo.start(); normal.start(); alto.start();
        bajo.join(); normal.join(); alto.join();

        System.out.println("  NOTA: La prioridad no garantiza orden, solo una sugerencia al OS.");
    }
}
