import { PageIntro } from '../components/common/PageIntro';

const DEFAULT_POWER_BI_URL =
  'https://app.powerbi.com/view?r=eyJrIjoiNDQ2Zjk2ZmQtYTVmNS00NDI5LWE1MjMtMjFiMzU4ZjlkMjk2IiwidCI6ImNjMjg2MzNmLTEyYjgtNDZjYi1iYzE1LTk1MWRhZTIzOWI0ZCIsImMiOjR9';

function validPowerBiUrl(value?: string): string | null {
  try {
    const url = new URL(value ?? '');
    return url.protocol === 'https:' && url.hostname === 'app.powerbi.com' ? url.href : null;
  } catch {
    return null;
  }
}

// Si existe VITE_POWER_BI_URL en el .env se usa esa; si no, el enlace por defecto.
const POWER_BI_URL =
  validPowerBiUrl(import.meta.env.VITE_POWER_BI_URL as string | undefined) ??
  validPowerBiUrl(DEFAULT_POWER_BI_URL);

export function PowerBiAccessPage() {
  if (!POWER_BI_URL) {
    return (
      <div className="space-y-6">
        <PageIntro
          eyebrow="Inteligencia de negocio"
          title="Power BI"
          description="Reportes corporativos con acceso de tu organización."
        />
        <section className="coke-panel p-6">
          <h2 className="text-xl font-bold">El reporte corporativo aún no está conectado</h2>
          <p className="mt-3 text-slate-600">
            Solicita al administrador la conexión con Power BI. Mientras tanto, puedes consultar los
            resultados en Reportes.
          </p>
        </section>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <PageIntro
        eyebrow="Inteligencia de negocio"
        title="Power BI"
        description="Dashboard corporativo con los indicadores consolidados de los eventos."
        actions={
          <a href={POWER_BI_URL} target="_blank" rel="noopener noreferrer" className="coke-button">
            Abrir en pestaña nueva
          </a>
        }
      />
      <section className="coke-panel overflow-hidden p-2 sm:p-4" aria-label="Dashboard de Power BI">
        <iframe
          title="CocaCola"
          src={POWER_BI_URL}
          className="h-[70vh] min-h-[420px] w-full rounded-xl border-0"
          allowFullScreen
        />
      </section>
    </div>
  );
}
