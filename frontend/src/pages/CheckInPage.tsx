import { useEffect, useRef, useState, type FormEvent } from 'react';
import type { Html5Qrcode as Html5QrcodeScanner } from 'html5-qrcode';
import { PageIntro } from '../components/common/PageIntro';
import { ApiRequestError, checkInParticipant } from '../services/registrationService';
import type { CheckInResponse } from '../services/registrationService';
import { getOverview } from '../services/dashboardService';
import type { AnalyticsOverview } from '../types';
import { formatLocalDateTime } from '../utils/date';

const QR_READER_ID = 'check-in-qr-reader';
const UUID_REGEX = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i;

// Extrae el UUID aunque se haya pegado con prefijo ("Token QR: ...") o texto extra.
function extractToken(value: string): string {
  const match = value.match(UUID_REGEX);
  return match ? match[0].toLowerCase() : value.trim();
}

export function CheckInPage() {
  const [qrToken, setQrToken] = useState('');
  const [result, setResult] = useState<CheckInResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cameraError, setCameraError] = useState<string | null>(null);
  const [overviewError, setOverviewError] = useState<string | null>(null);
  const [overview, setOverview] = useState<AnalyticsOverview | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isCameraStarting, setIsCameraStarting] = useState(false);
  const [isCameraActive, setIsCameraActive] = useState(false);
  const scannerRef = useRef<Html5QrcodeScanner | null>(null);
  const hasScannedRef = useRef(false);
  const isMountedRef = useRef(false);

  useEffect(() => {
    isMountedRef.current = true;
    return () => {
      isMountedRef.current = false;
      const scanner = scannerRef.current;
      if (scanner?.isScanning) {
        void scanner.stop()
          .then(() => scanner.clear())
          .catch((stopError: unknown) => {
            console.error('No se pudo detener el lector QR al cerrar la pantalla.', stopError);
          });
      }
    };
  }, []);

  useEffect(() => {
    let active = true;
    void getOverview()
      .then((data) => {
        if (active) {
          setOverview(data);
          setOverviewError(null);
        }
      })
      .catch(() => {
        if (active) {
          setOverviewError('No se pudo cargar el resumen de asistencia.');
        }
      });
    return () => {
      active = false;
    };
  }, []);

  async function submitQrToken(value: string) {
    const normalizedToken = extractToken(value);
    setError(null);
    setResult(null);
    if (!normalizedToken) {
      setError('El token QR es obligatorio.');
      return;
    }

    setIsLoading(true);
    try {
      const checkIn = await checkInParticipant(normalizedToken);
      if (!isMountedRef.current) {
        return;
      }
      setResult(checkIn);
      try {
        const updatedOverview = await getOverview();
        if (isMountedRef.current) {
          setOverview(updatedOverview);
          setOverviewError(null);
        }
      } catch {
        if (isMountedRef.current) {
          setOverviewError('El check-in se registró, pero no se pudo actualizar el resumen.');
        }
      }
    } catch (requestError) {
      if (!isMountedRef.current) {
        return;
      }
      if (requestError instanceof ApiRequestError && requestError.status === 409) {
        setError(`Este QR ya fue utilizado: ${requestError.message}`);
      } else if (requestError instanceof ApiRequestError && requestError.status === 404) {
        setError(`No se encontró una inscripción para este QR: ${requestError.message}`);
      } else if (requestError instanceof Error) {
        setError(requestError.message);
      } else {
        setError('No fue posible validar el QR.');
      }
    } finally {
      if (isMountedRef.current) {
        setIsLoading(false);
      }
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await submitQrToken(qrToken);
  }

  async function stopCamera() {
    const scanner = scannerRef.current;
    if (!scanner?.isScanning) {
      setIsCameraActive(false);
      return;
    }

    try {
      await scanner.stop();
      scanner.clear();
      if (isMountedRef.current) {
        setIsCameraActive(false);
      }
    } catch (stopError) {
      if (isMountedRef.current) {
        setCameraError(stopError instanceof Error
          ? `No se pudo detener la cámara: ${stopError.message}`
          : 'No se pudo detener la cámara.');
      }
    }
  }

  async function startCamera() {
    if (!navigator.mediaDevices?.getUserMedia) {
      setCameraError('El escaneo requiere acceso a cámara desde localhost o una conexión HTTPS.');
      return;
    }

    setCameraError(null);
    setError(null);
    setResult(null);
    hasScannedRef.current = false;
    setIsCameraStarting(true);
    try {
      const { Html5Qrcode } = await import('html5-qrcode');
      if (!isMountedRef.current) {
        return;
      }
      const scanner = new Html5Qrcode(QR_READER_ID);
      scannerRef.current = scanner;
      await scanner.start(
        { facingMode: 'environment' },
        { fps: 10, qrbox: { width: 250, height: 250 } },
        (decodedText) => {
          if (hasScannedRef.current) {
            return;
          }
          hasScannedRef.current = true;
          const scannedToken = decodedText.trim();
          setQrToken(scannedToken);
          void stopCamera();
          void submitQrToken(scannedToken);
        },
        () => {},
      );
      if (isMountedRef.current) {
        setIsCameraActive(true);
      } else {
        await scanner.stop();
        scanner.clear();
      }
    } catch (startError) {
      scannerRef.current = null;
      if (isMountedRef.current) {
        setCameraError(startError instanceof Error
          ? `No se pudo iniciar la cámara: ${startError.message}`
          : 'No se pudo iniciar la cámara. Revisa los permisos del navegador.');
      }
    } finally {
      if (isMountedRef.current) {
        setIsCameraStarting(false);
      }
    }
  }

  const attendanceRate = overview?.attendance_rate;
  const attendanceRateWidth = attendanceRate == null
    ? 0
    : Math.min(100, Math.max(0, attendanceRate));

  return (
    <div className="space-y-6">
      <PageIntro
        eyebrow="Check-in"
        title="Registra una asistencia"
        description="Valida la inscripción al evento con el token QR real de la persona participante."
      />
      <div className="grid gap-5 lg:grid-cols-[minmax(0,1.5fr)_minmax(280px,.8fr)]">
        <section className="coke-panel p-6 sm:p-8" aria-labelledby="check-in-heading">
          <div className="mb-6 flex h-12 w-12 items-center justify-center rounded-2xl bg-red-50 text-coke-red" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" className="h-6 w-6" stroke="currentColor" strokeWidth="1.8">
              <path d="M4 8V5a1 1 0 0 1 1-1h3M16 4h3a1 1 0 0 1 1 1v3M20 16v3a1 1 0 0 1-1 1h-3M8 20H5a1 1 0 0 1-1-1v-3" strokeLinecap="round" />
              <path d="M8 8h3v3H8zM14 8h2v2h-2zM14 13h2v3h-2zM8 14h3v2H8z" strokeLinejoin="round" />
            </svg>
          </div>
          <h3 id="check-in-heading" className="text-xl font-bold text-slate-950">Validar código de acceso</h3>
          <p className="mt-2 max-w-xl text-sm leading-relaxed text-slate-600">
            Escanea el QR de inscripción o introduce el token recibido al completar el registro.
          </p>

          <div className="mt-5 space-y-3">
            <div id={QR_READER_ID} className="min-h-0 overflow-hidden rounded-xl" />
            <button
              type="button"
              onClick={() => void (isCameraActive ? stopCamera() : startCamera())}
              disabled={isLoading || isCameraStarting}
              className="w-full rounded-xl border border-slate-300 px-4 py-3 text-sm font-semibold text-slate-800 disabled:opacity-60"
            >
              {isCameraStarting ? 'Iniciando cámara…' : isCameraActive ? 'Detener cámara' : 'Activar cámara y escanear'}
            </button>
            {cameraError ? (
              <p role="alert" className="rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
                {cameraError}
              </p>
            ) : null}
          </div>

          <form className="mt-6 space-y-4 border-t border-slate-200 pt-5" onSubmit={handleSubmit}>
            <label className="block text-sm font-semibold text-slate-800" htmlFor="registration-qr-token">
              Token QR
            </label>
            <input
              id="registration-qr-token"
              name="qrToken"
              autoComplete="off"
              required
              value={qrToken}
              onChange={(event) => setQrToken(event.target.value)}
              placeholder="Pega o escribe el token del QR"
              className="coke-input w-full"
            />
            <button type="submit" disabled={isLoading || !qrToken.trim()} className="coke-button w-full sm:w-auto">
              {isLoading ? 'Validando QR…' : 'Confirmar asistencia'}
            </button>
          </form>

          {error ? (
            <p role="alert" className="mt-5 rounded-xl border border-red-200 bg-red-50 p-4 text-sm font-medium text-red-700">
              {error}
            </p>
          ) : null}

          {result ? (
            <div role="status" className="mt-5 rounded-2xl border border-emerald-200 bg-emerald-50 p-5 text-sm text-emerald-800">
              <p className="font-bold">Check-in exitoso</p>
              <p className="mt-1">Participante: {result.participantName}</p>
              <p>Evento: {result.eventName}</p>
              <p>Estado: {result.status === 'CHECKED_IN' ? 'Presente' : result.status}</p>
              <p>Hora del servidor: {formatLocalDateTime(result.checkInAt)}</p>
            </div>
          ) : null}
        </section>

        <aside className="coke-panel p-6" aria-label="Resumen de asistencia">
          <p className="text-sm font-medium text-slate-500">Asistentes</p>
          <p className="mt-2 text-4xl font-bold tracking-tight text-emerald-700">
            {overview?.attendees.toLocaleString('es-ES') ?? '—'}
          </p>
          <p className="mt-1 text-sm text-slate-500">
            de {overview?.registered.toLocaleString('es-ES') ?? '—'} inscritos
          </p>
          <div
            className="mt-5 h-2.5 overflow-hidden rounded-full bg-slate-100"
            role="img"
            aria-label={attendanceRate == null ? 'Tasa de asistencia no disponible' : `Tasa de asistencia ${attendanceRate}%`}
          >
            <div
              className="h-full rounded-full bg-emerald-600 transition-all"
              style={{ width: `${attendanceRateWidth}%` }}
            />
          </div>
          <p className="mt-3 text-sm font-semibold text-slate-800">
            {attendanceRate == null ? 'Sin tasa de asistencia' : `${attendanceRate.toFixed(1)}% de asistencia`}
          </p>
          {overviewError ? <p role="status" className="mt-3 text-sm text-amber-800">{overviewError}</p> : null}
          <div className="mt-7 border-t border-slate-100 pt-5">
            <h3 className="font-bold text-slate-900">Antes de confirmar</h3>
            <ol className="mt-3 space-y-3 text-sm text-slate-600">
              <li className="flex gap-3"><span className="step-index">1</span><span>Busca el QR o token de registro del asistente.</span></li>
              <li className="flex gap-3"><span className="step-index">2</span><span>Verifica que el token corresponda a una inscripción.</span></li>
              <li className="flex gap-3"><span className="step-index">3</span><span>Confirma la asistencia y revisa los datos devueltos por el servidor.</span></li>
            </ol>
          </div>
        </aside>
      </div>
    </div>
  );
}
