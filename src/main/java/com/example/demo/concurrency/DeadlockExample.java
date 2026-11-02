package com.example.demo.concurrency;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ============================================================
 * EJEMPLO 4: DEADLOCK (Abrazo Mortal)
 * ============================================================
 *
 * QUE ES UN DEADLOCK?
 * -------------------
 * Un deadlock ocurre cuando dos o mas hilos se bloquean mutuamente
 * esperando recursos que el otro tiene. Ninguno puede avanzar.
 *
 * CONDICIONES DE COFFMAN (las 4 necesarias para deadlock):
 * --------------------------------------------------------
 * 1. Exclusion mutua     : El recurso no puede ser compartido
 * 2. Hold and wait       : Un hilo retiene un recurso y espera otro
 * 3. No preempcion       : No se puede quitar el recurso por la fuerza
 * 4. Espera circular     : Hilo A espera a B, B espera a A (ciclo)
 *
 * ESCENARIO CLASICO:
 * ------------------
 *   Hilo A: adquiere Lock1, intenta Lock2
 *   Hilo B: adquiere Lock2, intenta Lock1
 *
 *   Hilo A tiene Lock1, espera Lock2 (que tiene B)
 *   Hilo B tiene Lock2, espera Lock1 (que tiene A)
 *   -> DEADLOCK: ninguno puede avanzar
 */
public class DeadlockExample {

    static final ReentrantLock lockA = new ReentrantLock();
    static final ReentrantLock lockB = new ReentrantLock();

    public static void main(String[] args) throws InterruptedException { run(); }

    public static void run() throws InterruptedException {
        ConcurrencyRunner.header("4. DEADLOCK");

        mostrarPatronDeadlock();
        Thread.sleep(100);
        solucion1_OrdenConsistente();
        Thread.sleep(100);
        solucion2_TryLock();
        Thread.sleep(100);
        conceptoLivelock();
    }

    // ----------------------------------------------------------------
    // PATRON que CAUSA deadlock (usamos tryLock para no colgar la app)
    // ----------------------------------------------------------------
    static void mostrarPatronDeadlock() throws InterruptedException {
        ConcurrencyRunner.section("Patron que causa Deadlock (simulado con tryLock)");

        System.out.println("  El patron deadlock REAL seria:");
        System.out.println("  Hilo A: lock(lockA) -> lock(lockB)  [orden: A, B]");
        System.out.println("  Hilo B: lock(lockB) -> lock(lockA)  [orden: B, A]");
        System.out.println("  -> Orden opuesto = DEADLOCK!");
        System.out.println();
        System.out.println("  Simulando con tryLock para no colgar la app...");

        // Simulamos el patron con tryLock (version segura para demostracion)
        Thread hiloA = new Thread(() -> {
            try {
                if (lockA.tryLock(100, TimeUnit.MILLISECONDS)) {
                    try {
                        System.out.println("  [Hilo-A] Adquiri lockA, intento lockB...");
                        Thread.sleep(50); // dar tiempo al otro hilo
                        boolean ok = lockB.tryLock(100, TimeUnit.MILLISECONDS);
                        if (ok) {
                            try { System.out.println("  [Hilo-A] Adquiri lockB tambien (no hubo deadlock esta vez)");
                            } finally { lockB.unlock(); }
                        } else {
                            System.out.println("  [Hilo-A] No pude adquirir lockB -> DEADLOCK simulado!");
                        }
                    } finally { lockA.unlock(); }
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "Hilo-A");

        Thread hiloB = new Thread(() -> {
            try {
                if (lockB.tryLock(100, TimeUnit.MILLISECONDS)) {
                    try {
                        System.out.println("  [Hilo-B] Adquiri lockB, intento lockA...");
                        Thread.sleep(50);
                        boolean ok = lockA.tryLock(100, TimeUnit.MILLISECONDS);
                        if (ok) {
                            try { System.out.println("  [Hilo-B] Adquiri lockA tambien (no hubo deadlock esta vez)");
                            } finally { lockA.unlock(); }
                        } else {
                            System.out.println("  [Hilo-B] No pude adquirir lockA -> DEADLOCK simulado!");
                        }
                    } finally { lockB.unlock(); }
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "Hilo-B");

        hiloA.start(); hiloB.start();
        hiloA.join(); hiloB.join();
    }

    // ----------------------------------------------------------------
    // SOLUCION 1: Orden consistente de adquisicion de locks
    // ----------------------------------------------------------------
    static void solucion1_OrdenConsistente() throws InterruptedException {
        ConcurrencyRunner.section("Solucion 1: Orden consistente de locks");

        // REGLA: Siempre adquirir los locks en el MISMO orden en todos los hilos
        // Ambos hilos: lockA primero, luego lockB -> no hay ciclo -> no hay deadlock

        Thread hiloA = new Thread(() -> {
            lockA.lock(); // orden: A primero
            try {
                lockB.lock(); // orden: B segundo
                try {
                    System.out.println("  [Hilo-A] Tengo lockA y lockB (orden correcto)");
                } finally { lockB.unlock(); }
            } finally { lockA.unlock(); }
        }, "Hilo-A");

        Thread hiloB = new Thread(() -> {
            lockA.lock(); // MISMO orden: A primero (aunque B "necesite" B antes)
            try {
                lockB.lock(); // MISMO orden: B segundo
                try {
                    System.out.println("  [Hilo-B] Tengo lockA y lockB (orden correcto)");
                } finally { lockB.unlock(); }
            } finally { lockA.unlock(); }
        }, "Hilo-B");

        hiloA.start(); hiloB.start();
        hiloA.join(); hiloB.join();
        System.out.println("  Sin deadlock! Orden consistente = solucion mas simple.");
    }

    // ----------------------------------------------------------------
    // SOLUCION 2: tryLock con timeout - detectar y recuperarse
    // ----------------------------------------------------------------
    static void solucion2_TryLock() throws InterruptedException {
        ConcurrencyRunner.section("Solucion 2: tryLock con timeout y reintento");

        // Si no podemos adquirir todos los locks en tiempo,
        // liberamos los que tenemos y reintentamos.
        // Esto ROMPE la condicion "Hold and wait".

        Runnable tarea = () -> {
            int intentos = 0;
            while (intentos < 3) {
                boolean tieneA = false;
                boolean tieneB = false;
                try {
                    tieneA = lockA.tryLock(50, TimeUnit.MILLISECONDS);
                    if (tieneA) {
                        tieneB = lockB.tryLock(50, TimeUnit.MILLISECONDS);
                        if (tieneB) {
                            // Tenemos ambos locks!
                            System.out.printf("  [%s] Adquiri ambos locks en intento %d%n",
                                Thread.currentThread().getName(), intentos + 1);
                            Thread.sleep(10); // trabajo
                            return; // exito
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } finally {
                    // Liberar SIEMPRE lo que tenemos si no conseguimos ambos
                    if (tieneB) lockB.unlock();
                    if (tieneA) lockA.unlock();
                }
                intentos++;
                System.out.printf("  [%s] No consegui ambos locks, reintentando... (intento %d)%n",
                    Thread.currentThread().getName(), intentos);
                try { Thread.sleep(10 * intentos); } // backoff
                catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            }
        };

        Thread t1 = new Thread(tarea, "Worker-1");
        Thread t2 = new Thread(tarea, "Worker-2");
        t1.start(); t2.start();
        t1.join(); t2.join();
    }

    // ----------------------------------------------------------------
    // CONCEPTO: Livelock - hilos activos pero sin progreso
    // ----------------------------------------------------------------
    static void conceptoLivelock() {
        ConcurrencyRunner.section("Concepto: Livelock");

        // Un livelock es similar al deadlock, pero los hilos NO estan bloqueados.
        // Estan activos, pero continuamente se interfieren entre si sin avanzar.
        //
        // Ejemplo del pasillo:
        //   Persona A y Persona B se encuentran en un pasillo estrecho.
        //   A se mueve a la izquierda, B se mueve a la izquierda (mismo lado).
        //   A se mueve a la derecha, B se mueve a la derecha.
        //   Ambos siguen moviendose pero nunca se pasan.
        //
        // Diferencia:
        //   Deadlock  = hilos BLOQUEADOS (estado BLOCKED/WAITING)
        //   Livelock  = hilos ACTIVOS pero sin progreso util (estado RUNNABLE)
        //   Starvation = un hilo nunca obtiene el recurso (otros siempre ganan)
        //
        // Prevencion del Livelock:
        //   - Randomizar el tiempo de backoff (como hace TCP con colisiones)
        //   - Asignar prioridades o turnos

        System.out.println("  Livelock: hilos activos pero sin progreso");
        System.out.println("  Prevencion: backoff aleatorio, prioridades, turnos");
        System.out.println();
        System.out.println("  RESUMEN DEADLOCK:");
        System.out.println("  - Detectar: jstack, VisualVM, ThreadMXBean");
        System.out.println("  - Prevenir : orden consistente, tryLock timeout, design sin locks");
        System.out.println("  - Evitar   : adquirir un lock a la vez (si es posible)");
    }
}
