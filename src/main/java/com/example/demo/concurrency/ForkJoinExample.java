package com.example.demo.concurrency;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.RecursiveTask;

/**
 * ============================================================
 * EJEMPLO 7: FORK / JOIN FRAMEWORK
 * ============================================================
 *
 * QUE ES FORK/JOIN?
 * -----------------
 * Framework de Java 7+ para paralelismo de tipo "divide y venceras".
 * Divide un problema grande en subproblemas mas pequenos,
 * los resuelve en paralelo, y combina los resultados.
 *
 * CONCEPTOS CLAVE:
 * ----------------
 *   ForkJoinPool      : pool de hilos especializado en fork/join
 *   ForkJoinTask<T>   : tarea abstracta (base de RecursiveTask y RecursiveAction)
 *   RecursiveTask<T>  : tarea que RETORNA un valor
 *   RecursiveAction   : tarea que NO retorna valor
 *
 * WORK STEALING (robo de trabajo):
 * ---------------------------------
 * Cada hilo tiene su propia cola de tareas (deque).
 * Cuando un hilo queda sin trabajo, "roba" tareas de la cola
 * de OTRO hilo (por el final de su deque).
 * Esto maximiza la utilizacion de CPU.
 *
 *   Hilo 1: [T1, T2, T3] <- procesa T1, T3 robada por Hilo 2
 *   Hilo 2: [T4, T5]     <- procesa T4, roba T3 de Hilo 1
 *
 * ALGORITMO:
 * ----------
 *   si (tarea pequena suficiente):
 *       resolver directamente (base case)
 *   sino:
 *       dividir en 2 mitades
 *       fork(mitad1)   <- enviar al pool para ejecucion paralela
 *       fork(mitad2)
 *       join(mitad1)   <- esperar resultado
 *       join(mitad2)
 *       combinar resultados
 */
public class ForkJoinExample {

    public static void main(String[] args) throws InterruptedException { run(); }

    public static void run() throws InterruptedException {
        ConcurrencyRunner.header("7. FORK / JOIN FRAMEWORK");

        ejemplo_RecursiveTaskSuma();
        ejemplo_RecursiveTaskBusqueda();
        ejemplo_RecursiveActionOrdenar();
        ejemplo_ForkJoinPoolInfo();
    }

    // ----------------------------------------------------------------
    // 1. RecursiveTask<Long> - Suma paralela de un array
    // ----------------------------------------------------------------
    static void ejemplo_RecursiveTaskSuma() {
        ConcurrencyRunner.section("1. RecursiveTask - Suma paralela de array");

        long[] numeros = new long[1_000_000];
        for (int i = 0; i < numeros.length; i++) numeros[i] = i + 1;

        // Suma secuencial para comparar
        long inicio = System.currentTimeMillis();
        long sumaSecuencial = 0;
        for (long n : numeros) sumaSecuencial += n;
        long tiempoSec = System.currentTimeMillis() - inicio;

        // Suma paralela con Fork/Join
        ForkJoinPool pool = ForkJoinPool.commonPool(); // usa el pool comun de la JVM
        inicio = System.currentTimeMillis();
        long sumaParalela = pool.invoke(new SumaTask(numeros, 0, numeros.length));
        long tiempoParalelo = System.currentTimeMillis() - inicio;

        System.out.println("  Suma secuencial : " + sumaSecuencial + " (" + tiempoSec + "ms)");
        System.out.println("  Suma paralela   : " + sumaParalela + " (" + tiempoParalelo + "ms)");
        System.out.println("  Resultados iguales: " + (sumaSecuencial == sumaParalela));
        System.out.println("  Pool parallelism: " + pool.getParallelism() + " (= num CPUs)");
    }

    /**
     * Tarea recursiva que suma un rango de un array.
     * Cuando el rango es pequeno (< UMBRAL), suma directamente.
     * Cuando es grande, se divide en 2 mitades y se ejecutan en paralelo.
     */
    static class SumaTask extends RecursiveTask<Long> {
        static final int UMBRAL = 10_000; // tamano minimo para dividir

        final long[] array;
        final int inicio, fin;

        SumaTask(long[] array, int inicio, int fin) {
            this.array = array;
            this.inicio = inicio;
            this.fin = fin;
        }

        @Override
        protected Long compute() {
            int tamano = fin - inicio;

            if (tamano <= UMBRAL) {
                // BASE CASE: sumar directamente
                long suma = 0;
                for (int i = inicio; i < fin; i++) suma += array[i];
                return suma;
            }

            // DIVIDE: partir en 2 mitades
            int medio = inicio + tamano / 2;
            SumaTask izquierda = new SumaTask(array, inicio, medio);
            SumaTask derecha   = new SumaTask(array, medio, fin);

            // FORK: lanzar la mitad derecha al pool (en paralelo)
            derecha.fork();

            // COMPUTE: calcular la mitad izquierda en el hilo actual
            long resultadoIzquierda = izquierda.compute();

            // JOIN: esperar el resultado de la mitad derecha
            long resultadoDerecha = derecha.join();

            // COMBINAR
            return resultadoIzquierda + resultadoDerecha;
        }
    }

    // ----------------------------------------------------------------
    // 2. RecursiveTask<Integer> - Busqueda paralela del maximo
    // ----------------------------------------------------------------
    static void ejemplo_RecursiveTaskBusqueda() {
        ConcurrencyRunner.section("2. RecursiveTask - Maximo paralelo");

        int[] datos = new int[100_000];
        for (int i = 0; i < datos.length; i++) datos[i] = (int)(Math.random() * 1_000_000);
        datos[42000] = 9_999_999; // poner maximo conocido

        ForkJoinPool pool = new ForkJoinPool(4); // pool con 4 hilos
        int maximo = pool.invoke(new MaximoTask(datos, 0, datos.length));

        System.out.println("  Maximo encontrado: " + maximo + " (esperado: 9999999)");
        pool.shutdown();
    }

    static class MaximoTask extends RecursiveTask<Integer> {
        static final int UMBRAL = 5_000;
        final int[] array;
        final int inicio, fin;

        MaximoTask(int[] array, int inicio, int fin) {
            this.array = array; this.inicio = inicio; this.fin = fin;
        }

        @Override
        protected Integer compute() {
            if (fin - inicio <= UMBRAL) {
                int max = array[inicio];
                for (int i = inicio + 1; i < fin; i++)
                    if (array[i] > max) max = array[i];
                return max;
            }
            int medio = inicio + (fin - inicio) / 2;
            MaximoTask izq = new MaximoTask(array, inicio, medio);
            MaximoTask der = new MaximoTask(array, medio, fin);

            // Forma alternativa con invokeAll (lanza ambas en paralelo)
            ForkJoinTask.invokeAll(izq, der);

            return Math.max(izq.join(), der.join());
        }
    }

    // ----------------------------------------------------------------
    // 3. RecursiveAction - Llenar array en paralelo (sin retorno)
    // ----------------------------------------------------------------
    static void ejemplo_RecursiveActionOrdenar() {
        ConcurrencyRunner.section("3. RecursiveAction - Inicializar array en paralelo");

        double[] resultado = new double[500_000];

        // Llenar con Math.sqrt en paralelo usando RecursiveAction
        ForkJoinPool pool = ForkJoinPool.commonPool();
        pool.invoke(new InicializarAction(resultado, 0, resultado.length));

        // Verificar algunos valores
        System.out.println("  resultado[0]   = " + String.format("%.4f", resultado[0]));     // sqrt(0) = 0
        System.out.println("  resultado[4]   = " + String.format("%.4f", resultado[4]));     // sqrt(4) = 2
        System.out.println("  resultado[9]   = " + String.format("%.4f", resultado[9]));     // sqrt(9) = 3
        System.out.println("  resultado[100] = " + String.format("%.4f", resultado[100]));   // sqrt(100) = 10
    }

    static class InicializarAction extends RecursiveAction {
        static final int UMBRAL = 10_000;
        final double[] array;
        final int inicio, fin;

        InicializarAction(double[] array, int inicio, int fin) {
            this.array = array; this.inicio = inicio; this.fin = fin;
        }

        @Override
        protected void compute() {
            if (fin - inicio <= UMBRAL) {
                for (int i = inicio; i < fin; i++)
                    array[i] = Math.sqrt(i); // operacion costosa
                return;
            }
            int medio = inicio + (fin - inicio) / 2;
            invokeAll(
                new InicializarAction(array, inicio, medio),
                new InicializarAction(array, medio, fin)
            );
        }
    }

    // ----------------------------------------------------------------
    // 4. Info del ForkJoinPool
    // ----------------------------------------------------------------
    static void ejemplo_ForkJoinPoolInfo() {
        ConcurrencyRunner.section("4. ForkJoinPool - Info y configuracion");

        ForkJoinPool commonPool = ForkJoinPool.commonPool();
        System.out.println("  commonPool.getParallelism()     : " + commonPool.getParallelism());
        System.out.println("  commonPool.getPoolSize()        : " + commonPool.getPoolSize());
        System.out.println("  commonPool.getActiveThreadCount(): " + commonPool.getActiveThreadCount());
        System.out.println("  commonPool.getQueuedTaskCount() : " + commonPool.getQueuedTaskCount());

        System.out.println();
        System.out.println("  Pool personalizado con 2 hilos:");
        ForkJoinPool custom = new ForkJoinPool(2);
        System.out.println("  custom.getParallelism(): " + custom.getParallelism());
        custom.shutdown();

        System.out.println();
        System.out.println("  CUANDO USAR FORK/JOIN:");
        System.out.println("  - Algoritmos divide y venceras (merge sort, quicksort)");
        System.out.println("  - Procesar arrays/listas grandes en paralelo");
        System.out.println("  - Arboles y grafos (recorrido paralelo)");
        System.out.println("  NOTA: Parallel Streams usan ForkJoinPool internamente!");
    }
}
