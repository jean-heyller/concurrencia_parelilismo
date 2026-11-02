package com.example.demo.concurrency;

/**
 * ============================================================
 * EJEMPLO 2: RACE CONDITION (Condicion de Carrera)
 * ============================================================
 *
 * QUE ES UNA RACE CONDITION?
 * --------------------------
 * Cuando multiples hilos acceden y modifican datos compartidos
 * al mismo tiempo, el resultado depende del orden en que el CPU
 * intercala las instrucciones. Esto se llama "condicion de carrera"
 * y produce resultados incorrectos e impredecibles.
 *
 * EJEMPLO CLASICO - contador++ NO es atomico:
 * -------------------------------------------
 * counter++ en bytecode son 3 instrucciones:
 *   1. LEER el valor de counter (ej: 5)
 *   2. SUMAR 1 (ej: 5+1 = 6)
 *   3. ESCRIBIR el resultado (ej: counter = 6)
 *
 * Con 2 hilos:
 *   Hilo A lee counter = 5
 *   Hilo B lee counter = 5   (antes de que A escriba!)
 *   Hilo A escribe counter = 6
 *   Hilo B escribe counter = 6  <-- ERROR: deberia ser 7!
 *
 * Perdimos un incremento. Esto es una race condition.
 */
public class RaceConditionExample {

    // Contador compartido SIN proteccion (inseguro)
    static int contadorInseguro = 0;

    // Contador compartido CON sincronizacion (seguro)
    static int contadorSeguro = 0;

    static final int HILOS = 10;
    static final int INCREMENTOS_POR_HILO = 1000;
    static final int ESPERADO = HILOS * INCREMENTOS_POR_HILO; // 10000

    public static void main(String[] args) throws InterruptedException { run(); }

    public static void run() throws InterruptedException {
        ConcurrencyRunner.header("2. RACE CONDITION (Condicion de Carrera)");

        demostrarRaceCondition();
        demostrarSolucionConSynchronized();
    }

    // ----------------------------------------------------------------
    // DEMO: Race condition - el contador da resultado incorrecto
    // ----------------------------------------------------------------
    static void demostrarRaceCondition() throws InterruptedException {
        ConcurrencyRunner.section("Race Condition (resultado incorrecto)");

        contadorInseguro = 0;
        Thread[] hilos = new Thread[HILOS];

        for (int i = 0; i < HILOS; i++) {
            hilos[i] = new Thread(() -> {
                for (int j = 0; j < INCREMENTOS_POR_HILO; j++) {
                    contadorInseguro++; // NO ATOMICO: leer-modificar-escribir
                }
            });
        }

        for (Thread h : hilos) h.start();
        for (Thread h : hilos) h.join();

        System.out.println("  Esperado  : " + ESPERADO);
        System.out.println("  Obtenido  : " + contadorInseguro);
        System.out.println("  Correcto  : " + (contadorInseguro == ESPERADO));

        if (contadorInseguro != ESPERADO) {
            System.out.println("  *** Race condition detectada! Perdimos "
                + (ESPERADO - contadorInseguro) + " incrementos ***");
        } else {
            System.out.println("  (Esta vez tuvo suerte, la race condition no siempre es visible)");
        }
    }

    // ----------------------------------------------------------------
    // SOLUCION: synchronized garantiza exclusion mutua
    // ----------------------------------------------------------------
    static synchronized void incrementarSeguro() {
        contadorSeguro++; // Solo UN hilo puede estar aqui a la vez
    }

    static void demostrarSolucionConSynchronized() throws InterruptedException {
        ConcurrencyRunner.section("Solucion con synchronized (resultado correcto)");

        contadorSeguro = 0;
        Thread[] hilos = new Thread[HILOS];

        for (int i = 0; i < HILOS; i++) {
            hilos[i] = new Thread(() -> {
                for (int j = 0; j < INCREMENTOS_POR_HILO; j++) {
                    incrementarSeguro(); // ahora es seguro
                }
            });
        }

        for (Thread h : hilos) h.start();
        for (Thread h : hilos) h.join();

        System.out.println("  Esperado  : " + ESPERADO);
        System.out.println("  Obtenido  : " + contadorSeguro);
        System.out.println("  Correcto  : " + (contadorSeguro == ESPERADO));

        System.out.println("\n  ALTERNATIVAS para evitar race conditions:");
        System.out.println("  1. synchronized (este ejemplo)");
        System.out.println("  2. ReentrantLock (ver SynchronizationExample)");
        System.out.println("  3. AtomicInteger (ver AtomicVariablesExample) <- mas rapido");
        System.out.println("  4. Variables locales por hilo (ThreadLocal)");
        System.out.println("  5. Objetos inmutables (no hay estado mutable compartido)");
    }
}
