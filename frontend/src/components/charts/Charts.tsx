import { Bar, BarChart, CartesianGrid, Cell, LabelList, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';

type PeakAttendanceHour = { hour: number; attendees: number };

export function PeakAttendanceChart({ data }: { data: PeakAttendanceHour[] }) {
  if (data.length === 0) return null;

  const hours = [...data]
    .sort((left, right) => left.hour - right.hour)
    .map(({ hour, attendees }) => ({
      hour,
      label: `${String(hour).padStart(2, '0')}:00`,
      attendees,
    }));
  const peak = hours.reduce((highest, current) => (
    current.attendees > highest.attendees ? current : highest
  ), hours[0]);

  return (
    <section className="brand-chart-card" aria-labelledby="peak-attendance-title">
      <div className="brand-peak-header">
        <div>
          <p className="brand-chart-eyebrow">Ritmo del evento</p>
          <h3 id="peak-attendance-title">Horarios de mayor afluencia</h3>
          <p className="brand-peak-description">Personas que hicieron check-in en cada hora.</p>
        </div>
        <div className="brand-peak-highlight" aria-label={`Hora pico: ${peak.label}, ${peak.attendees} asistentes`}>
          <span>Hora pico</span>
          <strong>{peak.label}</strong>
          <small>{peak.attendees.toLocaleString('es-ES')} asistentes</small>
        </div>
      </div>

      <div className="brand-peak-visual" aria-hidden="true">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={hours} margin={{ top: 28, right: 10, left: -20, bottom: 0 }} barCategoryGap="28%">
            <CartesianGrid vertical={false} stroke="#e9e9ec" strokeDasharray="3 5" />
            <XAxis dataKey="label" tickLine={false} axisLine={false} tick={{ fill: '#33363b', fontSize: 12, fontWeight: 600 }} dy={12} minTickGap={16} />
            <YAxis allowDecimals={false} tickLine={false} axisLine={false} tick={{ fill: '#777b82', fontSize: 12 }} />
            <Tooltip
              cursor={{ fill: '#fff2f3' }}
              formatter={(value) => [`${Number(value).toLocaleString('es-ES')} asistentes`, 'Check-ins']}
              contentStyle={{ border: '1px solid #e5e5e8', borderRadius: 12, boxShadow: '0 10px 25px rgba(9,11,13,.1)', fontSize: 13 }}
            />
            <Bar dataKey="attendees" name="Check-ins" maxBarSize={88} radius={[10, 10, 0, 0]}>
              {hours.length <= 12 ? <LabelList dataKey="attendees" position="top" fill="#17191c" fontSize={13} fontWeight={700} /> : null}
              {hours.map((entry) => (
                <Cell key={entry.hour} fill={entry.hour === peak.hour ? '#f40009' : '#202225'} />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>
      <div className="brand-peak-footer" aria-hidden="true">
        <span><i className="brand-peak-key is-peak" /> Hora pico</span>
        <span><i className="brand-peak-key" /> Otros horarios</span>
      </div>
      <ol className="sr-only" aria-label="Check-ins por hora">
        {hours.map(({ hour, label, attendees }) => (
          <li key={hour}>{`${label}: ${attendees} ${attendees === 1 ? 'asistente' : 'asistentes'}`}</li>
        ))}
      </ol>
    </section>
  );
}

type LineChartCardProps = {
  data: Array<Record<string, string | number>>;
  title: string;
  dataKey: string;
  stroke?: string;
};

export function LineChartCard({ data, title, dataKey, stroke = '#f40009' }: LineChartCardProps) {
  return (
    <div className="coke-panel p-5 sm:p-6">
      <h3 className="mb-4 text-lg font-semibold text-slate-900">{title}</h3>
      <div className="h-64 w-full">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={data} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#ececef" />
            <XAxis dataKey="name" tickLine={false} axisLine={false} stroke="#6b7078" />
            <YAxis tickLine={false} axisLine={false} stroke="#6b7078" />
            <Tooltip />
            <Legend />
            <Line type="monotone" dataKey={dataKey} stroke={stroke} strokeWidth={3} dot={{ r: 4 }} />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
