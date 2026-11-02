package com.example.demo.concurrency;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeoutException;

/**
 * RUNNER PRINCIPAL - Ejecuta todos los ejemplos de concurrencia en orden.
 * Corre la app con: ./gradlew bootRun
 */
@Component
public class ConcurrencyRunner implements CommandLineRunner {

    @Override
    public void run(String... args) throws Exception {
        header("JAVA CONCURRENCIA Y PARALELISMO - GUIA COMPLETA");
        System.out.println("  Java " + System.getProperty("java.version"));
        System.out.println("  CPUs disponibles: " + Runtime.getRuntime().availableProcessors());

        // ============================================================
        // 1. HILOS BASICOS
        // ============================================================
        ThreadBasicsExample.run();

        // ============================================================
        // 2. RACE CONDITION (condicion de carrera)
        // ============================================================
        RaceConditionExample.run();

        // ============================================================
        // 3. SINCRONIZACION
        // ============================================================
        SynchronizationExample.run();

        // ============================================================
        // 4. DEADLOCK
        // ============================================================
        DeadlockExample.run();

        // ============================================================
        // 5. EXECUTOR SERVICE (pools de hilos)
        // ============================================================
        ExecutorServiceExample.run();

        // ============================================================
        // 6. COMPLETABLE FUTURE (async/await de Java)
        // ============================================================
        CompletableFutureExample.run();

        // ============================================================
        // 7. FORK / JOIN (divide y venceras paralelo)
        // ============================================================
        ForkJoinExample.run();

        // ============================================================
        // 8. COLECCIONES CONCURRENTES
        // ============================================================
        ConcurrentCollectionsExample.run();

        // ============================================================
        // 9. VARIABLES ATOMICAS
        // ============================================================
        AtomicVariablesExample.run();

        // ============================================================
        // 10. SINCRONIZADORES (Latch, Barrier, Semaphore, Phaser)
        // ============================================================
        SynchronizersExample.run();

        // ============================================================
        // 11. PRODUCTOR - CONSUMIDOR
        // ============================================================
        ProducerConsumerExample.run();

        // ============================================================
        // 12. STREAMS PARALELOS
        // ============================================================
        ParallelStreamsExample.run();

        // ============================================================
        // 13. VISIBILIDAD DE MEMORIA Y volatile
        // ============================================================
        MemoryVisibilityExample.run();

        // ============================================================
        // 14. INANICION (Starvation)
        // ============================================================
        StarvationExample.run();

        // ============================================================
        // 15. LIVELOCK
        // ============================================================
        LivelockExample.run();

        header("FIN - Todos los ejemplos ejecutados exitosamente");
    }

    // Utilidades de logging compartidas
    public static void header(String title) {
        System.out.println("\n" + "=".repeat(65));
        System.out.printf("  %s%n", title);
        System.out.println("=".repeat(65));
    }

    public static void section(String name) {
        System.out.println("\n  --- " + name + " ---");
    }

    public static void log(String msg) {
        System.out.printf("  [%s] %s%n", Thread.currentThread().getName(), msg);
    }
}
