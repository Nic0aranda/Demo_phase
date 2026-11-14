package com.phase.api.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Serializa operaciones lectura-modificacion-escritura sobre una misma clave
 * de PhaseDB (cliente, factura, secuencia).
 *
 * <p>El lock vive en memoria: protege una sola instancia del API y es suficiente
 * para la demo. Si se escala horizontalmente hay que moverlo a un LOCK nativo de
 * Phase o a un coordinador externo.</p>
 */
@Service
public class PhaseLockService {

    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final long timeoutMs;

    public PhaseLockService(@Value("${erp.locks.timeout-ms:5000}") long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public <T> T conLock(String clave, Supplier<T> accion) {
        ReentrantLock lock = locks.computeIfAbsent(clave, key -> new ReentrantLock());
        boolean adquirido = false;
        try {
            adquirido = lock.tryLock(timeoutMs, TimeUnit.MILLISECONDS);
            if (!adquirido) {
                throw new ApiException(HttpStatus.CONFLICT, "OPERACION_EN_CURSO",
                        "Otra operacion sobre " + clave + " esta en curso. Intenta nuevamente.");
            }
            return accion.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "OPERACION_INTERRUMPIDA",
                    "La operacion fue interrumpida. Intenta nuevamente.");
        } finally {
            if (adquirido) {
                lock.unlock();
            }
        }
    }

    public void conLock(String clave, Runnable accion) {
        conLock(clave, () -> {
            accion.run();
            return null;
        });
    }
}
