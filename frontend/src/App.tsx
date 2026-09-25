import {useEffect,useState} from 'react'
import {Animal,Treatment,getAnimals,getTreatments} from './api'

export default function App(){
 const [animals,setAnimals]=useState<Animal[]>([]),[selected,setSelected]=useState<Animal|null>(null),[treatments,setTreatments]=useState<Treatment[]>([]),[query,setQuery]=useState(''),[error,setError]=useState('')
 async function load(){try{setAnimals(await getAnimals(query));setError('')}catch{setError('Could not load animals')}}
 useEffect(()=>{load()},[])
 useEffect(()=>{const t=setTimeout(load,250);return()=>clearTimeout(t)},[query])
 async function select(a:Animal){setSelected(a);try{setTreatments(await getTreatments(a.id))}catch{setTreatments([])}}
 return <main className="shell">
  <header className="topbar"><div><p className="eyebrow">SHELTER MANAGEMENT</p><h1>{selected?selected.name:'Animals'}</h1><p className="muted">{selected?'Animal profile':'Every animal, its history and care.'}</p></div>{selected&&<button className="secondary" onClick={()=>setSelected(null)}>← All animals</button>}</header>
  {!selected?<><section className="toolbar"><input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Search by name or microchip…" /><span className="muted">{animals.length} animals</span></section>{error&&<div className="error">{error}</div>}<section className="animal-list">{animals.map(a=><button className="animal-card clickable" key={a.id} onClick={()=>select(a)}><div className="avatar">{a.name.charAt(0).toUpperCase()}</div><div className="animal-main"><h2>{a.name}</h2><p>{a.animalType} · {a.sex} · {a.weightKg?a.weightKg+' kg':'weight not recorded'}</p><p className="muted">{a.location||'No location'} · {a.status}</p></div><span className="chip">{a.microchipNumber||'No chip'}</span></button>)}{animals.length===0&&<div className="empty">No animals registered yet.</div>}</section></>:<AnimalProfile animal={selected} treatments={treatments}/>}
 </main>
}

function AnimalProfile({animal,treatments}:{animal:Animal;treatments:Treatment[]}){
 return <div className="profile">
  <section className="profile-hero"><div className="big-avatar">{animal.name.charAt(0).toUpperCase()}</div><div><p className="eyebrow">{animal.animalType}</p><h2>{animal.name}</h2><p className="muted">{animal.status} · {animal.location||'No location'}</p></div></section>
  <nav className="tabs"><span className="active">Overview</span><span>Medical</span><span>Treatments</span><span>Vaccines</span><span>Deworming</span><span>Vet visits</span><span>Documents</span></nav>
  <section className="profile-grid">
   <article className="panel"><p className="eyebrow">IDENTITY</p><dl><dt>Sex</dt><dd>{animal.sex}</dd><dt>Weight</dt><dd>{animal.weightKg?animal.weightKg+' kg':'—'}</dd><dt>Microchip</dt><dd>{animal.microchipNumber||'—'}</dd><dt>Date of birth</dt><dd>{animal.dateOfBirth||'—'}</dd><dt>Intake</dt><dd>{animal.intakeDate}</dd><dt>Rescue source</dt><dd>{animal.rescueSource||'—'}</dd></dl></article>
   <article className="panel"><p className="eyebrow">ACTIVE TREATMENTS</p>{treatments.length===0?<p className="muted">No treatment plans recorded.</p>:<div className="treatment-list">{treatments.map(t=><div className="treatment" key={t.id}><strong>{t.medication}</strong><span>{t.dose} · {t.route} · {t.frequency}</span><small>{t.startDate}{t.endDate?' → '+t.endDate:''}</small></div>)}</div>}</article>
  </section>
  {animal.notes&&<section className="panel notes"><p className="eyebrow">NOTES</p><p>{animal.notes}</p></section>}
 </div>
}