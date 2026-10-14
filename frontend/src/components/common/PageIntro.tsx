import type { ReactNode } from 'react';

type PageIntroProps = {
  eyebrow: string;
  title: string;
  description?: string;
  actions?: ReactNode;
};

export function PageIntro({ eyebrow, title, description, actions }: PageIntroProps) {
  return (
    <header className="page-intro">
      <div className="min-w-0">
        <p className="page-eyebrow">{eyebrow}</p>
        <h2 className="page-title">{title}</h2>
        {description ? <p className="page-description">{description}</p> : null}
      </div>
      {actions ? <div className="page-intro-actions">{actions}</div> : null}
    </header>
  );
}
