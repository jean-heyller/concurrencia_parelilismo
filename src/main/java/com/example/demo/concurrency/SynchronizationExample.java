package com.example.demo.concurrency;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.StampedLock;

/**
 * ============================================================
 * EJEMPLO 3: SINCRONIZACION
 * ============================================================
 *
 * COMO FUNCIONA LA SINCRONIZACION?
 * ---------------------------------
 * Java usa "locks" (candados) para garantizar que solo un hilo
 * pueda ejecutar una seccion critica a la vez.
 *
 * Cada objeto Java tiene un "monitor" (lock intrinseco).
 * Cuando un hilo entra a un bloque synchronized, adquiere ese lock.
 * Otros hilos que quieran el mismo lock quedan en estado BLOCKED.
 *
 * JERARQUIA DE HERRAMIENTAS:
 * --------------------------
 *   synchronized          <- mas simple, menos flexible
 *   ReentrantLock         <- mas flexible, manual lock/unlock
 *   ReentrantReadWriteLock <- multiple lectores O un escritor
 *   StampedLock           <- optimistic reads (Java 8+)
 */
public class SynchronizationExample {

    public static void main(String[] args) throws InterruptedException { run(); }

    public static void run() throws InterruptedException {
        ConcurrencyRunner.header("3. SINCRONIZACION");

        ejemplo_SynchronizedMethod();
        ejemplo_SynchronizedBlock();
        ejemplo_SynchronizedEstatico();
        ejemplo_ReentrantLock();
        ejemplo_ReentrantLockTryLock();
        ejemplo_ReadWriteLock();
        ejemplo_StampedLock();
        ejemplo_ThreadLocal();
    }

    // ----------------------------------------------------------------
    // 1. synchronized en metodo de instancia
    //    Lock: el objeto 'this'
    // ----------------------------------------------------------------
    static void ejemplo_SynchronizedMethod() throws InterruptedException {
        ConcurrencyRunner.section("1. synchronized method");

        class CuentaBancaria {
            private double saldo;

            CuentaBancaria(double saldoInicial) {
                this.saldo = saldoInicial;
            }

            // Solo un hilo puede ejecutar este metodo a la vez (por instancia)
            public synchronized void depositar(double monto) {
                double anterior = saldo;
                saldo += monto;
                System.out.printf("  [%s] deposito %.0f: %.0f -> %.0f%n",
                    Thread.currentThread().getName(), monto, anterior, saldo);
            }

            public synchronized double getSaldo() {
                return saldo;
            }
        }

        CuentaBancaria cuenta = new CuentaBancaria(1000);

        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 3; i++) cuenta.depositar(100);
        }, "Cajero-1");

        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 3; i++) cuenta.depositar(200);
        }, "Cajero-2");

        t1.start(); t2.start();
        t1.join(); t2.join();

        System.out.println("  Saldo final: " + cuenta.getSaldo() + " (esperado: 1900)");
    }

    // ----------------------------------------------------------------
    // 2. synchronized block
    //    Mas eficiente: solo sincronizas la seccion critica, no todo el metodo
    // ----------------------------------------------------------------
    static void ejemplo_SynchronizedBlock() throws InterruptedException {
        ConcurrencyRunner.section("2. synchronized block");

        class Registro {
            private int contador = 0;
            private final Object lockContador = new Object(); // lock dedicado
            private String ultimoMensaje = "";
            // NOTA: 'ultimoMensaje' tiene su propio lock implicito (this)

            public void procesar(int valor) {
                // Solo esta parte critica esta sincronizada
                // El resto del metodo puede correr en paralelo
                String procesado = "procesado-" + valor; // trabajo no critico

                synchronized (lockContador) {
                    contador++;
                    ultimoMensaje = procesado;
                }
            }

            public int getContador() {
                synchronized (lockContador) { return contador; }
            }
        }

        Registro reg = new Registro();
        Thread[] hilos = new Thread[5];
        for (int i = 0; i < 5; i++) {
            final int val = i;
            hilos[i] = new Thread(() -> reg.procesar(val));
        }
        for (Thread h : hilos) h.start();
        for (Thread h : hilos) h.join();

        System.out.println("  Contador final: " + reg.getContador() + " (esperado: 5)");
    }

    // ----------------------------------------------------------------
    // 3. synchronized en metodo ESTATICO
    //    Lock: el objeto Class (no una instancia)
    // ----------------------------------------------------------------
    static void ejemplo_SynchronizedEstatico() throws InterruptedException {
        ConcurrencyRunner.section("3. synchronized estatico");

        class Generador {
            private static int siguiente = 0;

            // Lock: Generador.class (compartido entre TODAS las instancias)
            public static synchronized int siguienteId() {
                return ++siguiente;
            }
        }

        Thread[] hilos = new Thread[4];
        int[] ids = new int[4];
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            hilos[i] = new Thread(() -> ids[idx] = Generador.siguienteId());
        }
        for (Thread h : hilos) h.start();
        for (Thread h : hilos) h.join();

        System.out.print("  IDs generados: ");
        for (int id : ids) System.out.print(id + " ");
        System.out.println("(todos unicos)");
    }

    // ----------------------------------------------------------------
    // 4. ReentrantLock - mas control que synchronized
    // ----------------------------------------------------------------
    static void ejemplo_ReentrantLock() throws InterruptedException {
        ConcurrencyRunner.section("4. ReentrantLock");

        // Ventajas sobre synchronized:
        // - tryLock() sin bloquear
        // - tryLock(tiempo) con timeout
        // - lockInterruptibly() (se puede interrumpir mientras espera)
        // - fairness (hilos esperan en orden FIFO)
        // - multiples Condition variables

        class Recurso {
            private final ReentrantLock lock = new ReentrantLock();
            private int valor = 0;

            public void incrementar() {
                lock.lock(); // adquiere el lock (bloquea si esta ocupado)
                try {
                    valor++;
                    // El lock es REENTRANTE: este mismo hilo puede llamar
                    // lock.lock() de nuevo sin bloquearse a si mismo
                } finally {
                    lock.unlock(); // SIEMPRE en finally para garantizar liberacion
                }
            }

            public int getValor() {
                lock.lock();
                try { return valor; }
                finally { lock.unlock(); }
            }
        }

        Recurso recurso = new Recurso();
        Thread[] hilos = new Thread[5];
        for (int i = 0; i < 5; i++) {
            hilos[i] = new Thread(() -> {
                for (int j = 0; j < 200; j++) recurso.incrementar();
            });
        }
        for (Thread h : hilos) h.start();
        for (Thread h : hilos) h.join();

        System.out.println("  Valor final: " + recurso.getValor() + " (esperado: 1000)");
    }

    // ----------------------------------------------------------------
    // 5. tryLock() - intento no bloqueante
    // ----------------------------------------------------------------
    static void ejemplo_ReentrantLockTryLock() throws InterruptedException {
        ConcurrencyRunner.section("5. tryLock() - intento sin bloqueo");

        ReentrantLock lock = new ReentrantLock();

        Thread hilo1 = new Thread(() -> {
            lock.lock();
            try {
                System.out.println("  [hilo1] Adquiri el lock, trabajando 200ms...");
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
                System.out.println("  [hilo1] Libere el lock");
            }
        }, "hilo1");

        Thread hilo2 = new Thread(() -> {
            try { Thread.sleep(50); } catch (InterruptedException e) { return; }

            // tryLock() intenta adquirir, retorna false inmediatamente si no puede
            boolean adquirido = lock.tryLock();
            if (adquirido) {
                try {
                    System.out.println("  [hilo2] Adquiri el lock a la primera!");
                } finally {
                    lock.unlock();
                }
            } else {
                System.out.println("  [hilo2] Lock ocupado, hago otra cosa (no me bloqueo)");
            }

            // tryLock(tiempo, unidad) - espera hasta X tiempo
            try {
                boolean adquirido2 = lock.tryLock(300, TimeUnit.MILLISECONDS);
                if (adquirido2) {
                    try {
                        System.out.println("  [hilo2] Adquiri el lock con timeout!");
                    } finally {
                        lock.unlock();
                    }
                } else {
                    System.out.println("  [hilo2] Timeout esperando el lock");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "hilo2");

        hilo1.start(); hilo2.start();
        hilo1.join(); hilo2.join();
    }

    // ----------------------------------------------------------------
    // 6. ReentrantReadWriteLock - multiples lectores, un escritor
    // ----------------------------------------------------------------
    static void ejemplo_ReadWriteLock() throws InterruptedException {
        ConcurrencyRunner.section("6. ReentrantReadWriteLock");

        // Ideal cuando: muchas lecturas, pocas escrituras
        // - Multiples hilos pueden LEER a la vez
        // - Solo UN hilo puede ESCRIBIR (y bloquea todos los lectores)

        class Cache {
            private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
            private final ReentrantReadWriteLock.ReadLock  readLock  = rwLock.readLock();
            private final ReentrantReadWriteLock.WriteLock writeLock = rwLock.writeLock();
            private String datos = "inicial";

            public String leer() {
                readLock.lock(); // multiples hilos pueden tener este lock a la vez
                try {
                    return datos;
                } finally {
                    readLock.unlock();
                }
            }

            public void escribir(String nuevosDatos) {
                writeLock.lock(); // exclusivo: bloquea escritores Y lectores
                try {
                    System.out.printf("  [%s] Escribiendo: %s%n",
                        Thread.currentThread().getName(), nuevosDatos);
                    datos = nuevosDatos;
                } finally {
                    writeLock.unlock();
                }
            }
        }

        Cache cache = new Cache();

        // 3 lectores concurrentes
        Thread[] lectores = new Thread[3];
        for (int i = 0; i < 3; i++) {
            final int id = i;
            lectores[i] = new Thread(() -> {
                String val = cache.leer();
                System.out.printf("  [lector-%d] Lei: %s%n", id, val);
            }, "lector-" + i);
        }

        Thread escritor = new Thread(() -> cache.escribir("datos-actualizados"), "escritor");

        for (Thread l : lectores) l.start();
        escritor.start();
        for (Thread l : lectores) l.join();
        escritor.join();
    }

    // ----------------------------------------------------------------
    // 7. StampedLock - lectura optimista (Java 8+)
    // ----------------------------------------------------------------
    static void ejemplo_StampedLock() throws InterruptedException {
        ConcurrencyRunner.section("7. StampedLock (lectura optimista)");

        // StampedLock es mas rapido que ReadWriteLock para lecturas frecuentes.
        // "Optimistic read": lees SIN adquirir lock, luego validas si hubo escritura.
        // Si hubo escritura, relees con lock real.

        class Punto {
            private final StampedLock lock = new StampedLock();
            private double x, y;

            public void mover(double dx, double dy) {
                long stamp = lock.writeLock(); // lock de escritura
                try {
                    x += dx;
                    y += dy;
                } finally {
                    lock.unlockWrite(stamp);
                }
            }

            public double distancia() {
                // LECTURA OPTIMISTA: no adquiere lock real
                long stamp = lock.tryOptimisticRead();
                double cx = x, cy = y; // lee los valores

                if (!lock.validate(stamp)) {
                    // Hubo una escritura mientras leiamos! Releer con lock real.
                    stamp = lock.readLock();
                    try {
                        cx = x; cy = y;
                    } finally {
                        lock.unlockRead(stamp);
                    }
                }
                return Math.sqrt(cx * cx + cy * cy);
            }
        }

        Punto p = new Punto();
        p.mover(3, 4);
        System.out.println("  Distancia: " + p.distancia() + " (esperado: 5.0)");
        System.out.println("  StampedLock: mas rapido que ReadWriteLock en lecturas");
    }

    // ----------------------------------------------------------------
    // 8. ThreadLocal - variables privadas por hilo
    // ----------------------------------------------------------------
    static void ejemplo_ThreadLocal() throws InterruptedException {
        ConcurrencyRunner.section("8. ThreadLocal");

        // ThreadLocal da a cada hilo su PROPIA copia de la variable.
        // No hay datos compartidos, no hay sincronizacion necesaria.
        // Uso comun: SimpleDateFormat, conexiones DB, contexto de usuario.

        ThreadLocal<Integer> idHilo = ThreadLocal.withInitial(() -> 0);

        Thread[] hilos = new Thread[3];
        for (int i = 0; i < 3; i++) {
            final int id = i * 10;
            hilos[i] = new Thread(() -> {
                idHilo.set(id); // cada hilo tiene SU copia
                try { Thread.sleep(50); } catch (InterruptedException e) {}
                // Cada hilo ve su propio valor, no el de los demas
                System.out.printf("  [%s] Mi ThreadLocal value: %d%n",
                    Thread.currentThread().getName(), idHilo.get());
                idHilo.remove(); // IMPORTANTE: limpiar para evitar memory leaks en thread pools
            }, "hilo-" + i);
        }
        for (Thread h : hilos) h.start();
        for (Thread h : hilos) h.join();
    }
}
