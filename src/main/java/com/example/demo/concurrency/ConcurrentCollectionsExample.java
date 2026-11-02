package com.example.demo.concurrency;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ============================================================
 * EJEMPLO 8: COLECCIONES CONCURRENTES
 * ============================================================
 *
 * POR QUE NO USAR ArrayList/HashMap EN MULTI-HILO?
 * -------------------------------------------------
 * Las colecciones normales (ArrayList, HashMap, etc.) NO son
 * thread-safe. Usarlas desde multiples hilos puede causar:
 *   - ConcurrentModificationException
 *   - Datos corruptos
 *   - Bucles infinitos (HashMap pre-Java 8)
 *   - Perdida de datos
 *
 * OPCIONES THREAD-SAFE:
 * ---------------------
 *   1. Collections.synchronizedXxx()  - envuelve con synchronized (lento)
 *   2. ConcurrentHashMap              - alta concurrencia, segmentada
 *   3. CopyOnWriteArrayList           - para lectura frecuente, escritura rara
 *   4. BlockingQueue                  - para productor-consumidor
 *   5. ConcurrentLinkedQueue          - cola sin bloqueo (lock-free)
 *   6. ConcurrentSkipListMap          - mapa ordenado concurrente
 */
public class ConcurrentCollectionsExample {

    public static void main(String[] args) throws InterruptedException { run(); }

    public static void run() throws InterruptedException {
        ConcurrencyRunner.header("8. COLECCIONES CONCURRENTES");

        ejemplo_ConcurrentHashMap();
        ejemplo_ConcurrentHashMapOperaciones();
        ejemplo_CopyOnWriteArrayList();
        ejemplo_BlockingQueues();
        ejemplo_ConcurrentLinkedQueue();
        ejemplo_ConcurrentSkipListMap();
        ejemplo_DelayQueue();
    }

    // ----------------------------------------------------------------
    // 1. ConcurrentHashMap
    // ----------------------------------------------------------------
    static void ejemplo_ConcurrentHashMap() throws InterruptedException {
        ConcurrencyRunner.section("1. ConcurrentHashMap");

        // ConcurrentHashMap divide el mapa en SEGMENTOS (buckets).
        // Multiples hilos pueden leer/escribir en DIFERENTES segmentos a la vez.
        // Mucho mas rapido que HashMap sincronizado para alta concurrencia.

        ConcurrentHashMap<String, AtomicInteger> contadores = new ConcurrentHashMap<>();

        // 5 hilos incrementan contadores de categorias
        Thread[] hilos = new Thread[5];
        for (int i = 0; i < 5; i++) {
            final int id = i;
            hilos[i] = new Thread(() -> {
                String[] categorias = {"rojo", "verde", "azul"};
                for (int j = 0; j < 100; j++) {
                    String cat = categorias[j % 3];
                    // computeIfAbsent: crea el valor si no existe (atomicamente)
                    contadores.computeIfAbsent(cat, k -> new AtomicInteger(0))
                              .incrementAndGet();
                }
            }, "hilo-" + id);
        }

        for (Thread h : hilos) h.start();
        for (Thread h : hilos) h.join();

        System.out.println("  Contadores finales:");
        contadores.forEach((k, v) ->
            System.out.println("    " + k + " -> " + v.get())
        );
        System.out.println("  Total: " + contadores.values().stream().mapToInt(AtomicInteger::get).sum()
            + " (esperado: 500)");
    }

    // ----------------------------------------------------------------
    // 2. ConcurrentHashMap - operaciones atomicas
    // ----------------------------------------------------------------
    static void ejemplo_ConcurrentHashMapOperaciones() {
        ConcurrencyRunner.section("2. ConcurrentHashMap - operaciones atomicas");

        ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();

        map.put("a", 1);
        map.put("b", 2);

        // putIfAbsent: put solo si la clave NO existe (atomico)
        map.putIfAbsent("c", 3);
        map.putIfAbsent("a", 99); // NO sobreescribe porque "a" ya existe
        System.out.println("  putIfAbsent - a=" + map.get("a") + " (sigue siendo 1)");

        // compute: actualiza el valor con una funcion (atomico)
        map.compute("a", (k, v) -> v == null ? 1 : v + 10);
        System.out.println("  compute - a=" + map.get("a") + " (1 + 10 = 11)");

        // computeIfPresent: solo si la clave existe
        map.computeIfPresent("b", (k, v) -> v * 2);
        System.out.println("  computeIfPresent - b=" + map.get("b") + " (2 * 2 = 4)");

        // computeIfAbsent: solo si NO existe
        map.computeIfAbsent("d", k -> k.length());
        System.out.println("  computeIfAbsent - d=" + map.get("d") + " (longitud de 'd' = 1)");

        // merge: si existe combina, si no pone el valor nuevo
        map.merge("a", 5, Integer::sum); // a = 11 + 5 = 16
        System.out.println("  merge - a=" + map.get("a") + " (11 + 5 = 16)");

        // getOrDefault: valor por defecto si no existe
        System.out.println("  getOrDefault - z=" + map.getOrDefault("z", -1));

        // replace: reemplaza solo si el valor actual coincide (atomico)
        boolean reemplazado = map.replace("b", 4, 100);
        System.out.println("  replace(b, 4, 100) = " + reemplazado + ", b=" + map.get("b"));

        // forEach con paralelismo (Java 8+)
        System.out.print("  forEach: ");
        map.forEach(1, (k, v) -> System.out.print(k + "=" + v + " "));
        System.out.println();
    }

    // ----------------------------------------------------------------
    // 3. CopyOnWriteArrayList
    // ----------------------------------------------------------------
    static void ejemplo_CopyOnWriteArrayList() throws InterruptedException {
        ConcurrencyRunner.section("3. CopyOnWriteArrayList");

        // Cada ESCRITURA (add, set, remove) crea una COPIA del array subyacente.
        // Las LECTURAS nunca se sincronizan -> muy rapidas
        // IDEAL para: muchas lecturas, pocas escrituras (ej: listas de listeners)

        CopyOnWriteArrayList<String> lista = new CopyOnWriteArrayList<>();
        lista.addAll(Arrays.asList("A", "B", "C"));

        // Hilo lector puede iterar MIENTRAS otro hilo escribe
        // (el iterador opera sobre la copia en el momento de creacion del iterador)
        Thread lector = new Thread(() -> {
            for (String s : lista) { // itera sobre snapshot
                try { Thread.sleep(50); } catch (InterruptedException e) {}
                System.out.println("  [lector] lei: " + s);
            }
        }, "lector");

        Thread escritor = new Thread(() -> {
            try {
                Thread.sleep(75);
                lista.add("D"); // crea copia interna
                System.out.println("  [escritor] agregue D. Lista tiene: " + lista.size() + " elementos");
            } catch (InterruptedException e) {}
        }, "escritor");

        lector.start(); escritor.start();
        lector.join(); escritor.join();

        System.out.println("  Lista final: " + lista);
        System.out.println("  NOTA: CopyOnWrite es CARO para muchas escrituras (copia todo el array)");
    }

    // ----------------------------------------------------------------
    // 4. BlockingQueues
    // ----------------------------------------------------------------
    static void ejemplo_BlockingQueues() throws InterruptedException {
        ConcurrencyRunner.section("4. BlockingQueues");

        // BlockingQueue: cola que BLOQUEA al intentar poner en cola llena
        //               o sacar de cola vacia.
        // Ideal para patrones PRODUCTOR-CONSUMIDOR.

        // ArrayBlockingQueue: capacidad fija, FIFO, mas eficiente
        BlockingQueue<Integer> colaFija = new ArrayBlockingQueue<>(5);

        // LinkedBlockingQueue: capacidad opcional, FIFO
        BlockingQueue<Integer> colaLinked = new LinkedBlockingQueue<>(10);

        // PriorityBlockingQueue: sin capacidad maxima, ordena por prioridad
        BlockingQueue<Integer> colaPriority = new PriorityBlockingQueue<>();

        // OPERACIONES:
        // put(e)   : bloquea si la cola esta llena (espera que haya espacio)
        // take()   : bloquea si la cola esta vacia (espera que haya elemento)
        // offer(e) : inserta, retorna false si llena (no bloquea)
        // poll()   : saca, retorna null si vacia (no bloquea)
        // offer(e, tiempo, unidad): espera hasta X tiempo
        // poll(tiempo, unidad)    : espera hasta X tiempo

        System.out.println("  Demo ArrayBlockingQueue(capacidad=3):");
        BlockingQueue<String> cola = new ArrayBlockingQueue<>(3);

        cola.put("uno");
        cola.put("dos");
        cola.put("tres");
        System.out.println("  Cola llena: " + cola.size() + " elementos");

        boolean agrego = cola.offer("cuatro"); // no bloquea, retorna false
        System.out.println("  offer(cuatro) con cola llena: " + agrego);

        String sacado = cola.take();
        System.out.println("  take(): " + sacado);
        cola.put("cuatro"); // ahora cabe
        System.out.println("  Cola tras put(cuatro): " + cola);

        // PriorityBlockingQueue ordena por compareTo
        colaPriority.put(30); colaPriority.put(10); colaPriority.put(20);
        System.out.println("  PriorityBlockingQueue: " + colaPriority.take()
            + ", " + colaPriority.take() + ", " + colaPriority.take()
            + " (ordenado ascendente)");
    }

    // ----------------------------------------------------------------
    // 5. ConcurrentLinkedQueue - lock-free
    // ----------------------------------------------------------------
    static void ejemplo_ConcurrentLinkedQueue() throws InterruptedException {
        ConcurrencyRunner.section("5. ConcurrentLinkedQueue (lock-free)");

        // Implementacion lock-free usando CAS (Compare-And-Swap).
        // Alta performance cuando muchos hilos acceden a la vez.
        // NO bloquea (no tiene put()/take() bloqueantes).

        ConcurrentLinkedQueue<String> cola = new ConcurrentLinkedQueue<>();

        Thread productor = new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                cola.offer("item-" + i);
                System.out.printf("  [prod] offer: item-%d%n", i);
            }
        });

        Thread consumidor = new Thread(() -> {
            int consumidos = 0;
            while (consumidos < 5) {
                String item = cola.poll(); // null si vacia, no bloquea
                if (item != null) {
                    System.out.printf("  [cons] poll: %s%n", item);
                    consumidos++;
                } else {
                    Thread.yield(); // ceder CPU brevemente
                }
            }
        });

        productor.start(); consumidor.start();
        productor.join(); consumidor.join();
    }

    // ----------------------------------------------------------------
    // 6. ConcurrentSkipListMap
    // ----------------------------------------------------------------
    static void ejemplo_ConcurrentSkipListMap() throws InterruptedException {
        ConcurrencyRunner.section("6. ConcurrentSkipListMap (mapa ordenado concurrente)");

        // Skip List: estructura de datos probabilistica que mantiene orden.
        // Equivalente concurrente de TreeMap.
        // Permite operaciones: firstKey, lastKey, headMap, tailMap, etc.

        ConcurrentSkipListMap<Integer, String> mapa = new ConcurrentSkipListMap<>();

        Thread[] hilos = new Thread[4];
        for (int i = 0; i < 4; i++) {
            final int id = i;
            hilos[i] = new Thread(() -> {
                for (int j = 0; j < 5; j++) {
                    int key = id * 10 + j;
                    mapa.put(key, "val-" + key);
                }
            });
        }
        for (Thread h : hilos) h.start();
        for (Thread h : hilos) h.join();

        System.out.println("  Tamano: " + mapa.size() + " elementos (ordenados por clave)");
        System.out.println("  Primera clave: " + mapa.firstKey());
        System.out.println("  Ultima clave : " + mapa.lastKey());
        System.out.println("  Claves 10-20 : " + mapa.subMap(10, 21).keySet());

        // ConcurrentSkipListSet: Set ordenado concurrente (basado en SkipList)
        ConcurrentSkipListSet<String> set = new ConcurrentSkipListSet<>();
        set.addAll(Arrays.asList("banana", "apple", "cherry", "date"));
        System.out.println("  ConcurrentSkipListSet (ordenado): " + set);
    }

    // ----------------------------------------------------------------
    // 7. DelayQueue
    // ----------------------------------------------------------------
    static void ejemplo_DelayQueue() throws InterruptedException {
        ConcurrencyRunner.section("7. DelayQueue (elementos con retraso)");

        // DelayQueue: los elementos solo son accesibles despues de su retraso.
        // Util para: caché con expiracion, tareas programadas, timers.

        class TareaRetrasada implements Delayed {
            final String nombre;
            final long expiracion;

            TareaRetrasada(String nombre, long delayMs) {
                this.nombre = nombre;
                this.expiracion = System.currentTimeMillis() + delayMs;
            }

            @Override
            public long getDelay(TimeUnit unit) {
                long diff = expiracion - System.currentTimeMillis();
                return unit.convert(diff, TimeUnit.MILLISECONDS);
            }

            @Override
            public int compareTo(Delayed otro) {
                return Long.compare(this.getDelay(TimeUnit.MILLISECONDS),
                                   otro.getDelay(TimeUnit.MILLISECONDS));
            }
        }

        DelayQueue<TareaRetrasada> cola = new DelayQueue<>();
        cola.put(new TareaRetrasada("Tarea C", 300));
        cola.put(new TareaRetrasada("Tarea A", 100));
        cola.put(new TareaRetrasada("Tarea B", 200));

        System.out.println("  Elementos por orden de expiracion:");
        for (int i = 0; i < 3; i++) {
            TareaRetrasada t = cola.take(); // bloquea hasta que expire
            System.out.println("  -> " + t.nombre + " (expiro)");
        }
    }
}
