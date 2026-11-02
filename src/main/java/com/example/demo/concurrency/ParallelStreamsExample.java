package com.example.demo.concurrency;

import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

/**
 * ============================================================
 * EJEMPLO 12: STREAMS PARALELOS
 * ============================================================
 *
 * QUE SON LOS PARALLEL STREAMS?
 * ------------------------------
 * Los streams paralelos dividen los datos en partes y procesan
 * cada parte en un hilo diferente usando el ForkJoinPool comun.
 *
 * COMO FUNCIONA INTERNAMENTE:
 * ---------------------------
 *   datos ──► Spliterator (divide) ──► ForkJoin ──► combinar resultados
 *
 *   [1,2,3,4,5,6,7,8]
 *       ├── [1,2,3,4] ──► hilo 1 procesa
 *       └── [5,6,7,8] ──► hilo 2 procesa
 *           resultado: combinar
 *
 * CUANDO USAR PARALLEL STREAMS:
 * ------------------------------
 *   SI: muchos datos (miles/millones), operaciones CPU-intensivas
 *   NO: pocas datos, operaciones IO, orden importa, estado compartido
 *
 * OVERHEAD del paralelismo:
 *   Dividir datos + combinar resultados + cambio de contexto = costo fijo
 *   Solo vale la pena si el trabajo es suficientemente grande.
 */
public class ParallelStreamsExample {

    public static void main(String[] args) throws InterruptedException { run(); }

    public static void run() throws InterruptedException {
        ConcurrencyRunner.header("12. STREAMS PARALELOS");

        ejemplo_SecuencialVsParalelo();
        ejemplo_HillosUsados();
        ejemplo_ReduceParalelo();
        ejemplo_CollectParalelo();
        ejemplo_CuandoNoUsar();
        ejemplo_ForkJoinPoolPersonalizado();
        ejemplo_BenchmarkReal();
    }

    // ----------------------------------------------------------------
    // 1. Secuencial vs Paralelo
    // ----------------------------------------------------------------
    static void ejemplo_SecuencialVsParalelo() {
        ConcurrencyRunner.section("1. Secuencial vs Paralelo");

        long[] numeros = LongStream.rangeClosed(1, 1_000_000).toArray();

        // Secuencial
        long inicio = System.currentTimeMillis();
        long sumaSeq = Arrays.stream(numeros).sum();
        long tSeq = System.currentTimeMillis() - inicio;

        // Paralelo - solo agregar .parallel()
        inicio = System.currentTimeMillis();
        long sumaPar = Arrays.stream(numeros).parallel().sum();
        long tPar = System.currentTimeMillis() - inicio;

        System.out.println("  Suma secuencial : " + sumaSeq + " (" + tSeq + "ms)");
        System.out.println("  Suma paralela   : " + sumaPar + " (" + tPar + "ms)");
        System.out.println("  Iguales         : " + (sumaSeq == sumaPar));

        // Activar parallel en colecciones
        List<Integer> lista = IntStream.rangeClosed(1, 100).boxed().collect(Collectors.toList());

        // sequential() por defecto
        long sumSeq2 = lista.stream().mapToLong(i -> i).sum();

        // parallel() - procesar en paralelo
        long sumPar2 = lista.parallelStream().mapToLong(i -> i).sum();

        System.out.println("  lista.stream().sum()       = " + sumSeq2);
        System.out.println("  lista.parallelStream().sum()= " + sumPar2);
    }

    // ----------------------------------------------------------------
    // 2. Ver que hilos usa parallelStream
    // ----------------------------------------------------------------
    static void ejemplo_HillosUsados() {
        ConcurrencyRunner.section("2. Hilos usados por parallelStream");

        System.out.println("  Hilos que ejecutan el stream paralelo:");
        Set<String> hilosUsados = Collections.synchronizedSet(new HashSet<>());

        IntStream.range(0, 20)
            .parallel()
            .forEach(i -> {
                hilosUsados.add(Thread.currentThread().getName());
                // simular trabajo
                Math.sqrt(i * i + 1);
            });

        System.out.println("  Hilos usados: " + hilosUsados);
        System.out.println("  Total hilos : " + hilosUsados.size());
        System.out.println("  (usa ForkJoinPool.commonPool con " +
            ForkJoinPool.commonPool().getParallelism() + " hilos)");
    }

    // ----------------------------------------------------------------
    // 3. reduce() en paralelo
    // ----------------------------------------------------------------
    static void ejemplo_ReduceParalelo() {
        ConcurrencyRunner.section("3. reduce() - operaciones paralelas");

        // Para que reduce sea correcto en paralelo:
        // - La funcion debe ser ASOCIATIVA: (a op b) op c == a op (b op c)
        // - La funcion debe ser SIN EFECTOS SECUNDARIOS

        // Suma (asociativa) - correcto
        int suma = IntStream.rangeClosed(1, 100)
            .parallel()
            .reduce(0, Integer::sum); // identidad=0, operacion=suma
        System.out.println("  Suma 1..100 (paralelo): " + suma + " (esperado: 5050)");

        // Producto (asociativa)
        long producto = LongStream.rangeClosed(1, 10)
            .parallel()
            .reduce(1L, (a, b) -> a * b);
        System.out.println("  10! (paralelo): " + producto + " (esperado: 3628800)");

        // Maximo (asociativa)
        OptionalInt max = IntStream.of(3, 1, 4, 1, 5, 9, 2, 6)
            .parallel()
            .max();
        System.out.println("  Max (paralelo): " + max.getAsInt() + " (esperado: 9)");

        // collect con StringBuilder - INCORRECTO en paralelo (mutable container)
        // Usar collect con Collectors o StringJoiner en su lugar
        String correcto = IntStream.rangeClosed(1, 5)
            .parallel()
            .mapToObj(Integer::toString)
            .collect(Collectors.joining(", "));
        System.out.println("  Collectors.joining (correcto): " + correcto);
    }

    // ----------------------------------------------------------------
    // 4. collect en paralelo
    // ----------------------------------------------------------------
    static void ejemplo_CollectParalelo() {
        ConcurrencyRunner.section("4. collect() en paralelo");

        List<String> palabras = List.of("java", "stream", "parallel", "fork", "join",
            "hilo", "thread", "async", "lock", "sync");

        // groupingBy en paralelo
        Map<Integer, List<String>> porLongitud = palabras.parallelStream()
            .collect(Collectors.groupingByConcurrent(String::length));
        System.out.println("  groupingByConcurrent: " + porLongitud);

        // NOTA: usar groupingByConcurrent (no groupingBy) para paralelo
        // groupingBy crea un HashMap no thread-safe internamente
        // groupingByConcurrent crea un ConcurrentHashMap

        // toList, toSet funcionan igual en paralelo (el collector los combina)
        List<String> mayusculas = palabras.parallelStream()
            .map(String::toUpperCase)
            .collect(Collectors.toList());
        System.out.println("  toList en paralelo: " + mayusculas.size() + " elementos");

        // counting
        Map<Integer, Long> conteosPorLongitud = palabras.parallelStream()
            .collect(Collectors.groupingByConcurrent(String::length, Collectors.counting()));
        System.out.println("  Conteo por longitud: " + conteosPorLongitud);
    }

    // ----------------------------------------------------------------
    // 5. Cuando NO usar parallel
    // ----------------------------------------------------------------
    static void ejemplo_CuandoNoUsar() {
        ConcurrencyRunner.section("5. Cuando NO usar parallelStream");

        System.out.println("  CASO 1: Pocas operaciones (overhead > beneficio)");
        List<Integer> pocos = List.of(1, 2, 3, 4, 5);
        long inicio = System.currentTimeMillis();
        pocos.parallelStream().map(i -> i * 2).collect(Collectors.toList());
        System.out.println("    parallelStream con 5 elementos: "
            + (System.currentTimeMillis() - inicio) + "ms (lento por overhead!)");

        System.out.println();
        System.out.println("  CASO 2: Operaciones IO (NO paralelizar)");
        System.out.println("    parallelStream usa ForkJoinPool que es para CPU, no IO");
        System.out.println("    Para IO usar: CompletableFuture con executor de IO separado");

        System.out.println();
        System.out.println("  CASO 3: Estado compartido mutable (race condition)");
        List<Integer> resultadoMal = new ArrayList<>();
        List<Integer> numeros = IntStream.rangeClosed(1, 1000).boxed().collect(Collectors.toList());

        // INCORRECTO: ArrayList no es thread-safe
        numeros.parallelStream().forEach(resultadoMal::add); // puede fallar o dar resultados incorrectos
        System.out.println("    forEach en ArrayList mutable: " + resultadoMal.size()
            + " (esperado 1000, puede ser menor!)");

        // CORRECTO: usar collect
        List<Integer> resultadoBien = numeros.parallelStream()
            .collect(Collectors.toList()); // thread-safe
        System.out.println("    collect() en paralelo: " + resultadoBien.size() + " (correcto!)");

        System.out.println();
        System.out.println("  CASO 4: Operaciones con orden (findFirst, forEachOrdered)");
        System.out.println("    El paralelismo puede romper el orden esperado");
        List<Integer> desordenado = new ArrayList<>();
        IntStream.rangeClosed(1, 5).parallel().forEach(desordenado::add);
        System.out.println("    parallel forEach (sin orden): " + desordenado);

        List<Integer> ordenado = new ArrayList<>();
        IntStream.rangeClosed(1, 5).parallel().forEachOrdered(ordenado::add);
        System.out.println("    parallel forEachOrdered     : " + ordenado + " (ordenado, mas lento)");
    }

    // ----------------------------------------------------------------
    // 6. ForkJoinPool personalizado para parallelStream
    // ----------------------------------------------------------------
    static void ejemplo_ForkJoinPoolPersonalizado() throws InterruptedException {
        ConcurrencyRunner.section("6. ForkJoinPool personalizado para parallelStream");

        // Por defecto, parallelStream usa ForkJoinPool.commonPool()
        // que tiene (CPUs - 1) hilos.
        //
        // Si quieres controlar el numero de hilos:
        // Ejecutar el stream dentro de un ForkJoinPool personalizado.

        ForkJoinPool poolPersonalizado = new ForkJoinPool(2); // solo 2 hilos

        System.out.println("  commonPool parallelism: " + ForkJoinPool.commonPool().getParallelism());
        System.out.println("  poolPersonalizado parallelism: " + poolPersonalizado.getParallelism());

        try {
            Set<String> hilosUsados = Collections.synchronizedSet(new HashSet<>());

            // Ejecutar stream dentro del pool personalizado
            poolPersonalizado.submit(() -> {
                IntStream.rangeClosed(1, 16)
                    .parallel()
                    .forEach(i -> {
                        hilosUsados.add(Thread.currentThread().getName());
                        try { Thread.sleep(10); } catch (InterruptedException e) {}
                    });
            }).get(); // esperar que termine

            System.out.println("  Hilos usados (max 2): " + hilosUsados);
        } catch (Exception e) {
            System.out.println("  Error: " + e.getMessage());
        } finally {
            poolPersonalizado.shutdown();
        }
    }

    // ----------------------------------------------------------------
    // 7. Benchmark real: operacion CPU-intensiva
    // ----------------------------------------------------------------
    static void ejemplo_BenchmarkReal() {
        ConcurrencyRunner.section("7. Benchmark: operacion CPU-intensiva");

        int N = 500_000;
        List<Double> datos = new ArrayList<>(N);
        for (int i = 1; i <= N; i++) datos.add((double) i);

        // Operacion CPU-intensiva: calcular sqrt(log(x)) para muchos valores
        long inicio = System.currentTimeMillis();
        double resultadoSeq = datos.stream()
            .mapToDouble(x -> Math.sqrt(Math.log(x)))
            .sum();
        long tiempoSeq = System.currentTimeMillis() - inicio;

        inicio = System.currentTimeMillis();
        double resultadoPar = datos.parallelStream()
            .mapToDouble(x -> Math.sqrt(Math.log(x)))
            .sum();
        long tiempoPar = System.currentTimeMillis() - inicio;

        System.out.printf("  Secuencial : %.2f (%dms)%n", resultadoSeq, tiempoSeq);
        System.out.printf("  Paralelo   : %.2f (%dms)%n", resultadoPar, tiempoPar);
        if (tiempoPar > 0) {
            System.out.printf("  Speedup    : %.1fx%n", (double) tiempoSeq / tiempoPar);
        }
        System.out.println("  CPUs: " + Runtime.getRuntime().availableProcessors());

        System.out.println();
        System.out.println("  REGLA GENERAL:");
        System.out.println("  - N > 10,000 elementos Y operacion costosa -> parallelStream puede ayudar");
        System.out.println("  - Siempre medir! El overhead puede superar el beneficio.");
        System.out.println("  - En servidores: cuidado con ForkJoinPool.commonPool()");
        System.out.println("    (compartido con otros requests/tareas de la JVM)");
    }
}
