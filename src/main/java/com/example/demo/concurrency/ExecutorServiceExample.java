package com.example.demo.concurrency;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

/**
 * ============================================================
 * EJEMPLO 5: EXECUTOR SERVICE (Pools de Hilos)
 * ============================================================
 *
 * POR QUE USAR EXECUTOR SERVICE?
 * --------------------------------
 * Crear y destruir hilos es COSTOSO (recursos del OS).
 * Un pool de hilos REUTILIZA hilos ya creados.
 *
 *  Sin pool:  crear hilo -> tarea -> destruir hilo  (lento)
 *  Con pool:  [hilo libre] <- tarea                 (rapido)
 *
 * JERARQUIA:
 *   Executor
 *   └── ExecutorService
 *       ├── ThreadPoolExecutor  (implementacion base)
 *       └── ScheduledExecutorService
 *           └── ScheduledThreadPoolExecutor
 *
 * FABRICA: Executors.*
 *   newFixedThreadPool(n)      : n hilos fijos
 *   newCachedThreadPool()      : hilos dinamicos (crecen segun demanda)
 *   newSingleThreadExecutor()  : 1 solo hilo, tareas en cola
 *   newScheduledThreadPool(n)  : para tareas programadas/periodicas
 *   newVirtualThreadPerTaskExecutor() : virtual threads (Java 21+)
 */
public class ExecutorServiceExample {

    public static void main(String[] args) throws Exception { run(); }

    public static void run() throws InterruptedException, ExecutionException, TimeoutException {
        ConcurrencyRunner.header("5. EXECUTOR SERVICE");

        ejemplo_FixedThreadPool();
        ejemplo_CachedThreadPool();
        ejemplo_SingleThreadExecutor();
        ejemplo_ScheduledExecutor();
        ejemplo_CallableYFuture();
        ejemplo_InvokeAllYInvokeAny();
        ejemplo_ShutdownVsShutdownNow();
    }

    // ----------------------------------------------------------------
    // 1. FixedThreadPool - numero fijo de hilos
    // ----------------------------------------------------------------
    static void ejemplo_FixedThreadPool() throws InterruptedException {
        ConcurrencyRunner.section("1. newFixedThreadPool(3)");

        // Pool de 3 hilos. Si hay mas de 3 tareas, las extras esperan en cola.
        ExecutorService pool = Executors.newFixedThreadPool(3);

        System.out.println("  Enviando 6 tareas a un pool de 3 hilos:");
        for (int i = 1; i <= 6; i++) {
            final int tarea = i;
            pool.execute(() -> { // execute() para Runnable (sin resultado)
                System.out.printf("  [%s] Ejecutando tarea %d%n",
                    Thread.currentThread().getName(), tarea);
                try { Thread.sleep(50); } catch (InterruptedException e) {}
            });
        }

        pool.shutdown(); // No acepta mas tareas, pero termina las pendientes
        pool.awaitTermination(5, TimeUnit.SECONDS); // Espera que terminen
        System.out.println("  Todas las tareas completadas (3 hilos rotaron entre 6 tareas)");
    }

    // ----------------------------------------------------------------
    // 2. CachedThreadPool - hilos dinamicos
    // ----------------------------------------------------------------
    static void ejemplo_CachedThreadPool() throws InterruptedException {
        ConcurrencyRunner.section("2. newCachedThreadPool()");

        // Crea hilos nuevos segun necesidad, reutiliza hilos libres.
        // Hilos inactivos por 60s son terminados.
        // CUIDADO: puede crear demasiados hilos si hay muchas tareas simultaneas.

        ExecutorService pool = Executors.newCachedThreadPool();

        for (int i = 1; i <= 4; i++) {
            final int id = i;
            pool.execute(() ->
                System.out.printf("  [%s] Tarea %d%n", Thread.currentThread().getName(), id)
            );
        }

        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println("  CachedThreadPool: util para tareas cortas y en rafagas");
    }

    // ----------------------------------------------------------------
    // 3. SingleThreadExecutor - un hilo, tareas secuenciales
    // ----------------------------------------------------------------
    static void ejemplo_SingleThreadExecutor() throws InterruptedException {
        ConcurrencyRunner.section("3. newSingleThreadExecutor()");

        // 1 solo hilo. Las tareas se ejecutan en orden FIFO.
        // Si el hilo muere, se crea uno nuevo automaticamente.
        // Util para serializar acceso a un recurso.

        ExecutorService single = Executors.newSingleThreadExecutor();

        for (int i = 1; i <= 4; i++) {
            final int id = i;
            single.execute(() ->
                System.out.printf("  [%s] Tarea %d (siempre en orden)%n",
                    Thread.currentThread().getName(), id)
            );
        }

        single.shutdown();
        single.awaitTermination(5, TimeUnit.SECONDS);
    }

    // ----------------------------------------------------------------
    // 4. ScheduledExecutorService - tareas programadas
    // ----------------------------------------------------------------
    static void ejemplo_ScheduledExecutor() throws InterruptedException {
        ConcurrencyRunner.section("4. ScheduledExecutorService");

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

        // schedule(): ejecutar UNA VEZ despues de un delay
        scheduler.schedule(
            () -> System.out.println("  [scheduler] Ejecutado 1 vez despues de 100ms"),
            100, TimeUnit.MILLISECONDS
        );

        // scheduleAtFixedRate(): ejecutar periodicamente (cada N tiempo)
        // La siguiente ejecucion empieza N tiempo despues del INICIO de la anterior
        ScheduledFuture<?> tarea = scheduler.scheduleAtFixedRate(
            () -> System.out.printf("  [%s] tick (cada 150ms)%n", Thread.currentThread().getName()),
            0,    // delay inicial
            150,  // periodo
            TimeUnit.MILLISECONDS
        );

        Thread.sleep(500); // dejar correr 500ms
        tarea.cancel(false); // cancelar la tarea periodica

        // scheduleWithFixedDelay(): N tiempo despues del FIN de la anterior
        ScheduledFuture<?> tarea2 = scheduler.scheduleWithFixedDelay(
            () -> System.out.println("  [scheduler] delay fijo despues del fin"),
            0, 100, TimeUnit.MILLISECONDS
        );

        Thread.sleep(350);
        tarea2.cancel(false);

        scheduler.shutdown();
        scheduler.awaitTermination(2, TimeUnit.SECONDS);
    }

    // ----------------------------------------------------------------
    // 5. Callable y Future - tareas con resultado
    // ----------------------------------------------------------------
    static void ejemplo_CallableYFuture() throws InterruptedException, ExecutionException, TimeoutException {
        ConcurrencyRunner.section("5. Callable y Future");

        // Callable<T> es como Runnable pero RETORNA un resultado y puede lanzar Exception
        // submit() retorna un Future<T> que representa el resultado futuro

        ExecutorService pool = Executors.newFixedThreadPool(3);

        // Callable con lambda
        Callable<Integer> calculo = () -> {
            System.out.printf("  [%s] Calculando...%n", Thread.currentThread().getName());
            Thread.sleep(100);
            return 42;
        };

        // submit() retorna Future inmediatamente (el calculo sigue en background)
        Future<Integer> future1 = pool.submit(calculo);
        Future<Integer> future2 = pool.submit(() -> {
            Thread.sleep(50);
            return 100;
        });
        Future<Integer> future3 = pool.submit(() -> {
            Thread.sleep(80);
            return 200;
        });

        System.out.println("  Tareas enviadas. Haciendo otras cosas mientras calculan...");

        // isDone() : no bloqueante - chequea si termino
        System.out.println("  future1 terminado: " + future1.isDone());

        // get() : BLOQUEANTE - espera el resultado
        int r1 = future1.get();              // espera indefinidamente
        int r2 = future2.get(1, TimeUnit.SECONDS); // espera max 1 segundo
        int r3 = future3.get();

        System.out.println("  future1 terminado: " + future1.isDone());
        System.out.println("  Resultados: " + r1 + ", " + r2 + ", " + r3);
        System.out.println("  Suma: " + (r1 + r2 + r3));

        // Cancelar un Future
        Future<Integer> cancelable = pool.submit(() -> {
            Thread.sleep(5000);
            return 999;
        });
        boolean cancelado = cancelable.cancel(true); // true = interrumpir si esta corriendo
        System.out.println("  Future cancelado: " + cancelado);
        System.out.println("  isCancelled: " + cancelable.isCancelled());

        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
    }

    // ----------------------------------------------------------------
    // 6. invokeAll e invokeAny
    // ----------------------------------------------------------------
    static void ejemplo_InvokeAllYInvokeAny() throws InterruptedException, ExecutionException {
        ConcurrencyRunner.section("6. invokeAll() e invokeAny()");

        ExecutorService pool = Executors.newFixedThreadPool(3);

        List<Callable<String>> tareas = Arrays.asList(
            () -> { Thread.sleep(100); return "Resultado A (100ms)"; },
            () -> { Thread.sleep(50);  return "Resultado B (50ms)"; },
            () -> { Thread.sleep(150); return "Resultado C (150ms)"; }
        );

        // invokeAll(): espera que TODAS terminen, retorna lista de Futures
        System.out.println("  invokeAll(): esperando todas las tareas...");
        List<Future<String>> resultados = pool.invokeAll(tareas);
        for (Future<String> f : resultados) {
            System.out.println("  -> " + f.get());
        }

        // invokeAny(): retorna el resultado de la PRIMERA que termine (exitosa)
        // Cancela las demas.
        System.out.println("  invokeAny(): retorna la primera en terminar...");
        String primero = pool.invokeAny(tareas);
        System.out.println("  -> Primera en terminar: " + primero);

        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
    }

    // ----------------------------------------------------------------
    // 7. shutdown vs shutdownNow
    // ----------------------------------------------------------------
    static void ejemplo_ShutdownVsShutdownNow() throws InterruptedException {
        ConcurrencyRunner.section("7. shutdown() vs shutdownNow()");

        System.out.println("  shutdown():");
        System.out.println("    - No acepta tareas nuevas");
        System.out.println("    - Espera que las tareas en cola y corriendo terminen");
        System.out.println("    - Retorna void");
        System.out.println();
        System.out.println("  shutdownNow():");
        System.out.println("    - No acepta tareas nuevas");
        System.out.println("    - Intenta interrumpir tareas corriendo");
        System.out.println("    - Retorna lista de tareas pendientes (no iniciadas)");
        System.out.println();
        System.out.println("  PATRON RECOMENDADO para apagado limpio:");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        pool.submit(() -> System.out.println("  [pool] Tarea enviada"));

        // Patron de shutdown limpio
        pool.shutdown();
        try {
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                System.out.println("  Pool no termino en 5s, forzando shutdownNow...");
                List<Runnable> pendientes = pool.shutdownNow();
                System.out.println("  Tareas pendientes canceladas: " + pendientes.size());
                if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
                    System.out.println("  Pool no termino incluso con shutdownNow");
                }
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        System.out.println("  Pool terminado correctamente.");
    }
}
