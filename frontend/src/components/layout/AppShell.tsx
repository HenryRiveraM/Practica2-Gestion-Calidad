import type { ReactNode } from 'react';
import { TopBar } from './TopBar';

type AppShellProps = {
  title: string;
  children: ReactNode;
};

export function AppShell({ title, children }: AppShellProps) {
  return (
    <div className="min-h-screen bg-white text-slate-950">
      <TopBar title={title} />
      <div
        className="coke-brand-strip"
        role="region"
        aria-label="Destapa la felicidad. Siente el sabor. Comparte una Coca-Cola. Momentos que saben mejor juntos."
      >
        <div className="coke-led-track" aria-hidden="true">
          {Array.from({ length: 8 }, (_, copy) => (
            <div className="coke-led-phrases" key={copy}>
              <span>Destapa la felicidad</span>
              <span>Siente el sabor</span>
              <span>Comparte una Coca-Cola</span>
              <span>Momentos que saben mejor juntos</span>
            </div>
          ))}
        </div>
      </div>
      <div className="coke-pattern">
        <main className="mx-auto w-full max-w-[1728px] px-5 pb-12 pt-9 sm:px-8 md:px-12 lg:px-[6.4%] lg:pt-12">
          <h1 className="sr-only">{title}</h1>
          {children}
        </main>
      </div>
    </div>
  );
}
