export function formatLocalDate(value?: string | null): string {
  if (!value) {
    return 'Sin datos';
  }

  const dateOnlyMatch = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (dateOnlyMatch) {
    const year = Number(dateOnlyMatch[1]);
    const month = Number(dateOnlyMatch[2]);
    const day = Number(dateOnlyMatch[3]);
    const localDate = new Date(year, month - 1, day);

    if (localDate.getFullYear() !== year || localDate.getMonth() !== month - 1 || localDate.getDate() !== day) {
      return 'Sin datos';
    }
    return localDate.toLocaleDateString('es-BO');
  }

  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? 'Sin datos' : parsed.toLocaleDateString('es-BO');
}

export function formatLocalDateTime(value?: string | null): string {
  if (!value) {
    return 'Sin datos';
  }

  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? 'Sin datos' : parsed.toLocaleString('es-BO');
}
