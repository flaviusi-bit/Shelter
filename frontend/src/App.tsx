const cards = [
  { label: 'Animals', value: '0', detail: 'registered' },
  { label: 'In treatment', value: '0', detail: 'active cases' },
  { label: 'Today', value: '0', detail: 'administrations' },
  { label: 'Due soon', value: '0', detail: 'vaccines & treatments' },
]

export default function App() {
  return (
    <main className="shell">
      <header className="topbar">
        <div>
          <p className="eyebrow">SHELTER MANAGEMENT</p>
          <h1>Good morning</h1>
          <p className="muted">Your shelter overview will appear here.</p>
        </div>
        <button className="primary">+ Add animal</button>
      </header>

      <section className="grid">
        {cards.map((card) => (
          <article className="card" key={card.label}>
            <span className="label">{card.label}</span>
            <strong>{card.value}</strong>
            <span className="muted">{card.detail}</span>
          </article>
        ))}
      </section>

      <section className="panel">
        <div>
          <p className="eyebrow">TODAY</p>
          <h2>Immediate tasks</h2>
        </div>
        <p className="empty">No tasks yet. Once animals and treatment schedules are added, the most urgent actions will appear here.</p>
      </section>
    </main>
  )
}
