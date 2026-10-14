import { useCallback, useEffect, useState } from 'react';
import { LoadError } from '../components/common/LoadError';
import { PageIntro } from '../components/common/PageIntro';
import { getProducts } from '../services/dashboardService';
import type { Product } from '../types';

const productPhotos: Record<string, string> = {
  'coca-cola original': '/products/coca-cola-original.webp',
  'coca-cola zero': '/products/coca-cola-zero.webp',
  sprite: '/products/sprite.webp',
  'fanta naranja': '/products/fanta-naranja.webp',
  'del valle durazno': '/products/del-valle-durazno.webp',
};

function ProductPhoto({ name }: { name: string }) {
  const [failed, setFailed] = useState(false);
  const photo = productPhotos[name.trim().toLocaleLowerCase('es')];

  return (
    <figure className="product-photo">
      {photo && !failed ? (
        <>
          <img src={photo} alt={`Fotografía de ${name}, envase de referencia de 3 litros`} width={1000} height={1000} loading="lazy" decoding="async" onError={() => setFailed(true)} />
          <figcaption>Imagen referencial · El envase puede variar</figcaption>
        </>
      ) : (
        <div className="product-photo-placeholder">
          <svg viewBox="0 0 48 64" fill="none" aria-hidden="true"><path d="M19 4h10v9c0 6 7 9 7 17v25a5 5 0 0 1-5 5H17a5 5 0 0 1-5-5V30c0-8 7-11 7-17V4Z" stroke="currentColor" strokeWidth="2" /><path d="M19 9h10M12 31h24M12 48h24" stroke="currentColor" strokeWidth="2" /></svg>
          <span>Fotografía pendiente</span>
        </div>
      )}
    </figure>
  );
}

export function ProductsPage() {
  const [products, setProducts] = useState<Product[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(false);

  const loadProducts = useCallback(async () => {
    setIsLoading(true);
    setError(false);
    try {
      setProducts(await getProducts());
    } catch {
      setProducts([]);
      setError(true);
    } finally {
      setIsLoading(false);
    }
  }, []);
  useEffect(() => {
    void loadProducts();
  }, [loadProducts]);

  return (
    <div className="space-y-6">
      <PageIntro
        eyebrow="Portafolio Coca-Cola"
        title="Productos"
        description="Consulta las categorías, sabores y presentaciones asociados a las experiencias."
      />
      {isLoading ? <p role="status" className="rounded-xl bg-white p-5 text-slate-600">Cargando productos…</p> : null}
      {!isLoading && error ? <LoadError onRetry={() => void loadProducts()} /> : null}
      {!isLoading && !error && products.length === 0 ? <p className="rounded-xl border border-slate-200 bg-white p-5 text-slate-600">No hay productos disponibles.</p> : null}
      {!isLoading && !error && products.length > 0 ? (
      <div className="grid gap-5 sm:grid-cols-2 xl:grid-cols-3">
        {products.map((product) => (
          <article key={product.id} className="coke-panel product-card overflow-hidden p-5 sm:p-6">
            <div className="mb-5 flex items-center justify-between gap-3">
              <span className="inline-flex min-h-7 items-center rounded-full bg-slate-950 px-3 text-[10px] font-bold uppercase tracking-[0.12em] text-white">{product.category}</span>
              <span className={`inline-flex rounded-full px-3 py-1 text-xs font-semibold ${product.status === 'ACTIVE' ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-600'}`}>{product.status}</span>
            </div>
            <ProductPhoto name={product.name} />
            <h3 className="text-2xl font-bold leading-snug text-slate-950">{product.name}</h3>
            <div className="my-5 h-1 w-16 rounded-full bg-coke-red" />
            <dl className="space-y-3 text-sm">
              <div className="flex justify-between gap-4"><dt className="text-slate-500">Sabor</dt><dd className="text-right font-medium text-slate-800">{product.flavor || 'Sin especificar'}</dd></div>
              <div className="flex justify-between gap-4"><dt className="text-slate-500">Presentación</dt><dd className="text-right font-medium text-slate-800">{product.presentation || 'Sin especificar'}</dd></div>
            </dl>
          </article>
        ))}
      </div>
      ) : null}
    </div>
  );
}
