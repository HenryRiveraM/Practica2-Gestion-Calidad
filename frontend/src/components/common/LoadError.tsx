type LoadErrorProps = {
  onRetry: () => void;
};

export function LoadError({ onRetry }: LoadErrorProps) {
  return (
    <div role="alert" className="rounded-xl border border-red-200 bg-red-50 p-5 text-red-700">
      <p>No se pudieron cargar los datos.</p>
      <button
        type="button"
        onClick={onRetry}
        className="mt-3 rounded-lg border border-red-300 px-3 py-1.5 text-sm font-semibold hover:bg-red-100"
      >
        Reintentar
      </button>
    </div>
  );
}
