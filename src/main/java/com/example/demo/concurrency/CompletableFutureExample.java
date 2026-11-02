package com.example.demo.concurrency;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * ============================================================
 * EJEMPLO 6: COMPLETABLE FUTURE (async/await de Java)
 * ============================================================
 *
 * QUE ES COMPLETABLEFUTURE?
 * -------------------------
 * CompletableFuture<T> es la forma moderna (Java 8+) de escribir
 * codigo asincronico en Java. Es como Promises en JavaScript o
 * async/await en otros lenguajes.
 *
 * Diferencia con Future:
 *   Future<T>            : solo puedes esperar (get()) o cancelar
 *   CompletableFuture<T> : puedes ENCADENAR, COMBINAR, TRANSFORMAR,
 *                          manejar ERRORES de forma fluida
 *
 * DIAGRAMA DE OPERACIONES:
 * ------------------------
 *   supplyAsync()  ──► thenApply()  ──► thenApply()  ──► thenAccept()
 *   (produce valor)   (transforma)    (transforma)    (consume)
 *
 *   future1 ─────────────────────────────┐
 *                                         ├──► thenCombine() ──► resultado
 *   future2 ─────────────────────────────┘
 *
 *   allOf(f1, f2, f3)  ──► espera todos
 *   anyOf(f1, f2, f3)  ──► el primero que termine
 */
public class CompletableFutureExample {

    static ExecutorService pool = Executors.newFixedThreadPool(4,
        r -> { Thread t = new Thread(r); t.setName("cf-pool-" + t.threadId()); return t; }
    );

    public static void main(String[] args) throws Exception { run(); }

    public static void run() throws InterruptedException, ExecutionException {
        ConcurrencyRunner.header("6. COMPLETABLE FUTURE");

        try {
            ejemplo_SupplyAsyncRunAsync();
            ejemplo_ThenApply();
            ejemplo_ThenAcceptThenRun();
            ejemplo_ThenCompose();
            ejemplo_ThenCombine();
            ejemplo_AllOfAnyOf();
            ejemplo_ManejoErrores();
            ejemplo_CasoRealPipeline();
        } finally {
            pool.shutdown();
        }
    }

    // ----------------------------------------------------------------
    // 1. supplyAsync y runAsync
    // ----------------------------------------------------------------
    static void ejemplo_SupplyAsyncRunAsync() throws InterruptedException, ExecutionException {
        ConcurrencyRunner.section("1. supplyAsync() y runAsync()");

        // supplyAsync: ejecuta en background y PRODUCE un valor
        CompletableFuture<String> cf = CompletableFuture.supplyAsync(() -> {
            System.out.printf("  [%s] supplyAsync corriendo%n", Thread.currentThread().getName());
            return "Hola desde async!";
        }); // usa ForkJoinPool.commonPool() por defecto

        // Con executor personalizado:
        CompletableFuture<Integer> cfConPool = CompletableFuture.supplyAsync(() -> {
            System.out.printf("  [%s] supplyAsync con pool personalizado%n",
                Thread.currentThread().getName());
            return 42;
        }, pool);

        // runAsync: ejecuta en background, NO produce valor (retorna CompletableFuture<Void>)
        CompletableFuture<Void> cfVoid = CompletableFuture.runAsync(() ->
            System.out.printf("  [%s] runAsync - sin valor de retorno%n",
                Thread.currentThread().getName())
        );

        System.out.println("  Resultado supplyAsync: " + cf.get());
        System.out.println("  Resultado con pool: " + cfConPool.get());
        cfVoid.get();

        // completedFuture: ya completado (util para testing o valores conocidos)
        CompletableFuture<String> yaListo = CompletableFuture.completedFuture("Listo!");
        System.out.println("  completedFuture: " + yaListo.get());
    }

    // ----------------------------------------------------------------
    // 2. thenApply - transformar el resultado (map)
    // ----------------------------------------------------------------
    static void ejemplo_ThenApply() throws InterruptedException, ExecutionException {
        ConcurrencyRunner.section("2. thenApply() - transformar resultado");

        // thenApply: como Stream.map() pero asincronico
        // Ejecuta en el MISMO hilo que completo el anterior (o main si ya termino)

        CompletableFuture<Integer> resultado = CompletableFuture
            .supplyAsync(() -> "42", pool)                    // String "42"
            .thenApply(s -> Integer.parseInt(s))              // Integer 42
            .thenApply(n -> n * 2)                            // Integer 84
            .thenApply(n -> n + 16);                          // Integer 100

        System.out.println("  thenApply chain: " + resultado.get()); // 100

        // thenApplyAsync: ejecuta en otro hilo (del pool)
        CompletableFuture<String> async = CompletableFuture
            .supplyAsync(() -> 10, pool)
            .thenApplyAsync(n -> {
                System.out.printf("  [%s] thenApplyAsync%n", Thread.currentThread().getName());
                return "Resultado: " + (n * 5);
            }, pool);

        System.out.println("  thenApplyAsync: " + async.get());
    }

    // ----------------------------------------------------------------
    // 3. thenAccept y thenRun
    // ----------------------------------------------------------------
    static void ejemplo_ThenAcceptThenRun() throws InterruptedException, ExecutionException {
        ConcurrencyRunner.section("3. thenAccept() y thenRun()");

        // thenAccept: consume el resultado, no produce uno nuevo (Consumer)
        CompletableFuture<Void> cf1 = CompletableFuture
            .supplyAsync(() -> "datos importantes", pool)
            .thenAccept(datos -> System.out.println("  thenAccept recibio: " + datos));

        cf1.get();

        // thenRun: no recibe ni produce valor, solo ejecuta algo al final (Runnable)
        CompletableFuture<Void> cf2 = CompletableFuture
            .supplyAsync(() -> {
                System.out.println("  Procesando...");
                return "ok";
            }, pool)
            .thenRun(() -> System.out.println("  thenRun: proceso terminado (sin acceder al resultado)"));

        cf2.get();
    }

    // ----------------------------------------------------------------
    // 4. thenCompose - encadenar futuros (flatMap)
    // ----------------------------------------------------------------
    static void ejemplo_ThenCompose() throws InterruptedException, ExecutionException {
        ConcurrencyRunner.section("4. thenCompose() - encadenar futuros");

        // thenCompose es como flatMap: cuando tu siguiente operacion
        // TAMBIEN es asincronica (retorna CompletableFuture)

        // thenApply() con CF anidado daría: CompletableFuture<CompletableFuture<String>>
        // thenCompose() aplana el resultado:  CompletableFuture<String>
        CompletableFuture<String> resultado = obtenerIdUsuario()
            .thenCompose(id -> obtenerNombreUsuario(id))  // encadena otro CF
            .thenCompose(nombre -> obtenerSaludo(nombre)); // encadena otro CF

        System.out.println("  thenCompose result: " + resultado.get());
    }

    static CompletableFuture<Integer> obtenerIdUsuario() {
        return CompletableFuture.supplyAsync(() -> {
            System.out.printf("  [%s] Obteniendo ID...%n", Thread.currentThread().getName());
            return 123;
        }, pool);
    }

    static CompletableFuture<String> obtenerNombreUsuario(int id) {
        return CompletableFuture.supplyAsync(() -> {
            System.out.printf("  [%s] Obteniendo nombre para ID %d...%n",
                Thread.currentThread().getName(), id);
            return "Juan";
        }, pool);
    }

    static CompletableFuture<String> obtenerSaludo(String nombre) {
        return CompletableFuture.supplyAsync(() -> "Hola, " + nombre + "!", pool);
    }

    // ----------------------------------------------------------------
    // 5. thenCombine - combinar dos futuros independientes
    // ----------------------------------------------------------------
    static void ejemplo_ThenCombine() throws InterruptedException, ExecutionException {
        ConcurrencyRunner.section("5. thenCombine() - combinar dos futuros");

        // thenCombine: cuando AMBOS terminan, combina sus resultados
        // Los dos futuros corren EN PARALELO

        long inicio = System.currentTimeMillis();

        CompletableFuture<String> nombre = CompletableFuture.supplyAsync(() -> {
            try { Thread.sleep(100); } catch (InterruptedException e) {}
            return "Maria";
        }, pool);

        CompletableFuture<Integer> edad = CompletableFuture.supplyAsync(() -> {
            try { Thread.sleep(80); } catch (InterruptedException e) {}
            return 30;
        }, pool);

        // Ambos corren en PARALELO (no esperan uno al otro)
        CompletableFuture<String> resultado = nombre.thenCombine(edad,
            (n, e) -> n + " tiene " + e + " anos");

        System.out.println("  thenCombine: " + resultado.get());
        System.out.println("  Tiempo (paralelo): " + (System.currentTimeMillis() - inicio) + "ms (~100ms, no 180ms)");

        // thenAcceptBoth: igual pero sin retornar valor
        nombre.thenAcceptBoth(edad, (n, e) ->
            System.out.printf("  thenAcceptBoth: %s, edad=%d%n", n, e)
        ).get();
    }

    // ----------------------------------------------------------------
    // 6. allOf y anyOf
    // ----------------------------------------------------------------
    static void ejemplo_AllOfAnyOf() throws InterruptedException, ExecutionException {
        ConcurrencyRunner.section("6. allOf() y anyOf()");

        CompletableFuture<String> f1 = CompletableFuture.supplyAsync(() -> {
            try { Thread.sleep(100); } catch (InterruptedException e) {}
            return "F1 (100ms)";
        }, pool);
        CompletableFuture<String> f2 = CompletableFuture.supplyAsync(() -> {
            try { Thread.sleep(50); } catch (InterruptedException e) {}
            return "F2 (50ms)";
        }, pool);
        CompletableFuture<String> f3 = CompletableFuture.supplyAsync(() -> {
            try { Thread.sleep(150); } catch (InterruptedException e) {}
            return "F3 (150ms)";
        }, pool);

        // allOf: espera que TODOS terminen
        // Retorna CompletableFuture<Void> (no da los resultados directamente)
        System.out.println("  allOf(): esperando todos...");
        CompletableFuture<Void> todos = CompletableFuture.allOf(f1, f2, f3);
        todos.get(); // esperar
        // Para obtener resultados: acceder a cada future individualmente
        System.out.println("  allOf resultados: " + f1.get() + ", " + f2.get() + ", " + f3.get());

        // Patron comun: allOf + stream para obtener todos los resultados
        List<CompletableFuture<String>> futures = Arrays.asList(
            CompletableFuture.supplyAsync(() -> "A", pool),
            CompletableFuture.supplyAsync(() -> "B", pool),
            CompletableFuture.supplyAsync(() -> "C", pool)
        );

        CompletableFuture<List<String>> todosResultados = CompletableFuture
            .allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join) // join() = get() sin checked exception
                .collect(Collectors.toList())
            );

        System.out.println("  allOf + stream: " + todosResultados.get());

        // anyOf: retorna el resultado del PRIMERO que termine
        CompletableFuture<Object> primero = CompletableFuture.anyOf(f1, f2, f3);
        System.out.println("  anyOf (primero en terminar): " + primero.get());
    }

    // ----------------------------------------------------------------
    // 7. Manejo de errores
    // ----------------------------------------------------------------
    static void ejemplo_ManejoErrores() throws InterruptedException, ExecutionException {
        ConcurrencyRunner.section("7. Manejo de errores");

        // exceptionally: manejar errores (como catch en async)
        CompletableFuture<String> conError = CompletableFuture
            .<String>supplyAsync(() -> {
                throw new RuntimeException("Fallo la operacion!");
            }, pool)
            .exceptionally(ex -> {
                System.out.println("  exceptionally capturo: " + ex.getMessage());
                return "valor por defecto"; // valor de recuperacion
            });

        System.out.println("  exceptionally resultado: " + conError.get());

        // handle: maneja tanto el exito como el error (siempre se llama)
        CompletableFuture<String> conHandle = CompletableFuture
            .supplyAsync(() -> {
                if (Math.random() > 0.5) throw new RuntimeException("Error aleatorio");
                return "Exito!";
            }, pool)
            .handle((resultado, error) -> {
                if (error != null) {
                    System.out.println("  handle - error: " + error.getMessage());
                    return "recuperado de error";
                } else {
                    System.out.println("  handle - exito: " + resultado);
                    return resultado;
                }
            });

        System.out.println("  handle resultado: " + conHandle.get());

        // whenComplete: observar el resultado sin cambiarlo
        CompletableFuture<Integer> observable = CompletableFuture
            .supplyAsync(() -> 100, pool)
            .whenComplete((val, ex) -> {
                if (ex == null) System.out.println("  whenComplete - valor: " + val);
                else            System.out.println("  whenComplete - error: " + ex.getMessage());
            });

        System.out.println("  whenComplete pasa el valor: " + observable.get());
    }

    // ----------------------------------------------------------------
    // 8. Caso Real: Pipeline async
    // ----------------------------------------------------------------
    static void ejemplo_CasoRealPipeline() throws InterruptedException, ExecutionException {
        ConcurrencyRunner.section("8. Caso real: Pipeline asincronico");

        // Simular: buscar usuario -> obtener pedidos -> calcular total -> enviar notificacion
        long inicio = System.currentTimeMillis();

        CompletableFuture<String> pipeline = CompletableFuture
            .supplyAsync(() -> {
                // 1. Buscar usuario en DB (simulado)
                sleep(50);
                return "usuario_123";
            }, pool)
            .thenCompose(userId ->
                // 2. Obtener pedidos del usuario (llamada async a otro servicio)
                CompletableFuture.supplyAsync(() -> {
                    sleep(80);
                    return List.of(100.0, 200.0, 50.0);
                }, pool)
            )
            .thenApply(pedidos -> {
                // 3. Calcular total (CPU, rapido)
                double total = pedidos.stream().mapToDouble(Double::doubleValue).sum();
                System.out.println("  Pipeline - total de pedidos: " + total);
                return total;
            })
            .thenCompose(total ->
                // 4. Enviar notificacion (async)
                CompletableFuture.supplyAsync(() -> {
                    sleep(30);
                    return "Notificacion enviada. Total: " + total;
                }, pool)
            )
            .exceptionally(ex -> "Pipeline fallo: " + ex.getMessage());

        System.out.println("  Pipeline resultado: " + pipeline.get());
        System.out.println("  Tiempo total: " + (System.currentTimeMillis() - inicio) + "ms (~160ms secuencial)");
    }

    static void sleep(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
