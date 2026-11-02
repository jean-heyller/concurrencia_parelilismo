package com.example.demo.concurrency;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ============================================================
 * EJEMPLO 11: PATRON PRODUCTOR - CONSUMIDOR
 * ============================================================
 *
 * UNO DE LOS PATRONES MAS IMPORTANTES EN CONCURRENCIA.
 *
 * PROBLEMA:
 * ---------
 * Un hilo (productor) genera datos y otro hilo (consumidor) los procesa.
 * Si el productor es mas rapido que el consumidor, o viceversa,
 * necesitamos una forma de coordinarlos sin que se bloqueen entre si.
 *
 * SOLUCION: Cola intermedia (buffer)
 * ------------------------------------
 *   Productor ──► [Cola] ──► Consumidor
 *
 *   La cola desacopla la velocidad del productor de la del consumidor.
 *
 * IMPLEMENTACIONES:
 * -----------------
 *   1. wait/notify        : primitivo, propenso a errores
 *   2. BlockingQueue      : RECOMENDADO, facil y eficiente
 *   3. Disruptor          : ultra-alta performance (libreria externa)
 */
public class ProducerConsumerExample {

    public static void main(String[] args) throws InterruptedException { run(); }

    public static void run() throws InterruptedException {
        ConcurrencyRunner.header("11. PATRON PRODUCTOR - CONSUMIDOR");

        patron_WaitNotify();
        Thread.sleep(200);
        patron_BlockingQueue();
        Thread.sleep(200);
        patron_MultiplesProductoresConsumidores();
    }

    // ================================================================
    // PATRON 1: wait() / notify() - forma clasica (Java 1.0)
    // ================================================================
    static void patron_WaitNotify() throws InterruptedException {
        ConcurrencyRunner.section("1. wait() / notify() (clasico)");

        // wait() y notify() son metodos de Object.
        // Permiten comunicacion entre hilos usando el monitor del objeto.
        //
        // Reglas OBLIGATORIAS:
        //   1. Siempre llamar wait()/notify() dentro de un bloque synchronized
        //   2. wait() libera el lock y suspende el hilo
        //   3. notify() despierta UN hilo esperando en ese monitor
        //   4. notifyAll() despierta TODOS los hilos esperando
        //   5. Usar SIEMPRE while() en vez de if() para el chequeo (spurious wakeups)

        class Buffer {
            private final Object[] datos;
            private int cuenta = 0;
            private int putIndex = 0;
            private int takeIndex = 0;

            Buffer(int capacidad) { datos = new Object[capacidad]; }

            // Llamar con synchronized
            public synchronized void put(Object item) throws InterruptedException {
                // WHILE (no IF) - evitar spurious wakeups
                while (cuenta == datos.length) {
                    System.out.println("    [Buffer] Lleno, productor espera...");
                    wait(); // libera el lock y espera
                }
                datos[putIndex] = item;
                putIndex = (putIndex + 1) % datos.length;
                cuenta++;
                notifyAll(); // despertar hilos esperando para consumir
            }

            public synchronized Object take() throws InterruptedException {
                while (cuenta == 0) {
                    System.out.println("    [Buffer] Vacio, consumidor espera...");
                    wait();
                }
                Object item = datos[takeIndex];
                takeIndex = (takeIndex + 1) % datos.length;
                cuenta--;
                notifyAll(); // despertar hilos esperando para producir
                return item;
            }

            public synchronized int size() { return cuenta; }
        }

        Buffer buffer = new Buffer(3); // capacidad 3
        int ITEMS = 6;

        Thread productor = new Thread(() -> {
            try {
                for (int i = 1; i <= ITEMS; i++) {
                    buffer.put("item-" + i);
                    System.out.printf("    [Prod] put item-%d (buffer=%d)%n", i, buffer.size());
                    Thread.sleep(30);
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "Productor");

        Thread consumidor = new Thread(() -> {
            try {
                for (int i = 0; i < ITEMS; i++) {
                    Thread.sleep(80); // consumidor mas lento que productor
                    Object item = buffer.take();
                    System.out.printf("    [Cons] take %s (buffer=%d)%n", item, buffer.size());
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "Consumidor");

        productor.start(); consumidor.start();
        productor.join(); consumidor.join();
        System.out.println("    NOTA: wait/notify es tedioso y propenso a errores.");
        System.out.println("    PREFERIR BlockingQueue en codigo real.");
    }

    // ================================================================
    // PATRON 2: BlockingQueue - forma moderna (RECOMENDADA)
    // ================================================================
    static void patron_BlockingQueue() throws InterruptedException {
        ConcurrencyRunner.section("2. BlockingQueue (moderno - RECOMENDADO)");

        // BlockingQueue maneja toda la sincronizacion internamente.
        // put() bloquea si llena. take() bloquea si vacia.
        // Mucho mas simple y seguro que wait/notify.

        final int CAPACIDAD = 4;
        BlockingQueue<String> cola = new ArrayBlockingQueue<>(CAPACIDAD);
        final String VENENO = "FIN"; // señal de terminacion

        Thread productor = new Thread(() -> {
            try {
                for (int i = 1; i <= 8; i++) {
                    String item = "trabajo-" + i;
                    cola.put(item); // bloquea si llena
                    System.out.printf("    [Prod] producido: %s (cola=%d)%n", item, cola.size());
                    Thread.sleep(40);
                }
                cola.put(VENENO); // señal de fin al consumidor
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "Productor");

        Thread consumidor = new Thread(() -> {
            try {
                while (true) {
                    String item = cola.take(); // bloquea si vacia
                    if (VENENO.equals(item)) {
                        System.out.println("    [Cons] Recibio VENENO, terminando.");
                        break;
                    }
                    System.out.printf("    [Cons] procesado: %s (cola=%d)%n", item, cola.size());
                    Thread.sleep(100); // consumidor mas lento
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "Consumidor");

        productor.start(); consumidor.start();
        productor.join(); consumidor.join();
    }

    // ================================================================
    // PATRON 3: Multiples Productores + Multiples Consumidores
    // ================================================================
    static void patron_MultiplesProductoresConsumidores() throws InterruptedException {
        ConcurrencyRunner.section("3. Multiples Productores y Consumidores");

        final int N_PRODUCTORES = 3;
        final int N_CONSUMIDORES = 2;
        final int ITEMS_POR_PRODUCTOR = 3;
        final String VENENO = "__FIN__";

        BlockingQueue<String> cola = new LinkedBlockingQueue<>(10);
        AtomicInteger totalProducidos = new AtomicInteger(0);
        AtomicInteger totalConsumidos = new AtomicInteger(0);

        // Productores
        Thread[] productores = new Thread[N_PRODUCTORES];
        for (int p = 0; p < N_PRODUCTORES; p++) {
            final int prodId = p + 1;
            productores[p] = new Thread(() -> {
                try {
                    for (int i = 1; i <= ITEMS_POR_PRODUCTOR; i++) {
                        String item = "P" + prodId + "-item" + i;
                        cola.put(item);
                        totalProducidos.incrementAndGet();
                        System.out.printf("    [Prod-%d] Produjo: %s%n", prodId, item);
                        Thread.sleep((long)(Math.random() * 50));
                    }
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }, "Prod-" + prodId);
        }

        // Consumidores
        Thread[] consumidores = new Thread[N_CONSUMIDORES];
        for (int c = 0; c < N_CONSUMIDORES; c++) {
            final int consId = c + 1;
            consumidores[c] = new Thread(() -> {
                try {
                    while (true) {
                        String item = cola.take();
                        if (VENENO.equals(item)) {
                            // Reencolar el veneno para que el otro consumidor tambien lo vea
                            cola.put(VENENO);
                            break;
                        }
                        totalConsumidos.incrementAndGet();
                        System.out.printf("    [Cons-%d] Consumio: %s%n", consId, item);
                        Thread.sleep((long)(Math.random() * 80));
                    }
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }, "Cons-" + consId);
        }

        // Iniciar consumidores primero
        for (Thread c : consumidores) c.start();
        // Luego productores
        for (Thread p : productores) p.start();

        // Esperar que productores terminen
        for (Thread p : productores) p.join();

        // Señal de fin: UN veneno por consumidor
        cola.put(VENENO);

        // Esperar consumidores
        for (Thread c : consumidores) c.join();

        System.out.println("    Total producidos : " + totalProducidos.get()
            + " (esperado: " + (N_PRODUCTORES * ITEMS_POR_PRODUCTOR) + ")");
        System.out.println("    Total consumidos : " + totalConsumidos.get());
        System.out.println("    Diferencia       : " + (totalProducidos.get() - totalConsumidos.get())
            + " (debe ser 0)");
    }
}
