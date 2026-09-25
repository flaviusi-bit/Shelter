import {useEffect,useState} from 'react'
import {Animal,Treatment,Administration,getAnimals,getTreatments,getAdministrations,generateAdministrations,administer,setAdministrationStatus} from './api'

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
 const [activeTab,setActiveTab]=useState('Overview')
 return <div className="profile">
  <section className="profile-hero"><div className="big-avatar">{animal.name.charAt(0).toUpperCase()}</div><div><p className="eyebrow">{animal.animalType}</p><h2>{animal.name}</h2><p className="muted">{animal.status} · {animal.location||'No location'}</p></div></section>
  <nav className="tabs">{['Overview','Medical','Treatments','Vaccines','Deworming','Vet visits','Documents'].map(tab=><button key={tab} className={activeTab===tab?'active':''} onClick={()=>setActiveTab(tab)}>{tab}</button>)}</nav>
  {activeTab==='Overview'&&<section className="profile-grid">
   <article className="panel"><p className="eyebrow">IDENTITY</p><dl><dt>Sex</dt><dd>{animal.sex}</dd><dt>Weight</dt><dd>{animal.weightKg?animal.weightKg+' kg':'—'}</dd><dt>Microchip</dt><dd>{animal.microchipNumber||'—'}</dd><dt>Date of birth</dt><dd>{animal.dateOfBirth||'—'}</dd><dt>Intake</dt><dd>{animal.intakeDate}</dd><dt>Rescue source</dt><dd>{animal.rescueSource||'—'}</dd></dl></article>
   <article className="panel"><p className="eyebrow">ACTIVE TREATMENTS</p>{treatments.length===0?<p className="muted">No treatment plans recorded.</p>:<div className="treatment-list">{treatments.map(t=><TreatmentCard key={t.id} animalId={animal.id} treatment={t}/>)}</div>}</article>
  </section>}
  {activeTab==='Treatments'&&<section className="treatment-list full-width">{treatments.length===0?<div className="panel"><p className="muted">No treatment plans recorded.</p></div>:treatments.map(t=><TreatmentCard key={t.id} animalId={animal.id} treatment={t} expanded/>)}</section>}
  {activeTab!=='Overview'&&activeTab!=='Treatments'&&<section className="panel"><p className="eyebrow">{activeTab.toUpperCase()}</p><p className="muted">This module is planned for the next iteration.</p></section>}
  {animal.notes&&activeTab==='Overview'&&<section className="panel notes"><p className="eyebrow">NOTES</p><p>{animal.notes}</p></section>}
 </div>
}

function TreatmentCard({animalId,treatment,expanded=false}:{animalId:string;treatment:Treatment;expanded?:boolean}){
 const [administrations,setAdministrations]=useState<Administration[]>([]),[busy,setBusy]=useState(false),[error,setError]=useState('')
 async function load(){try{setAdministrations(await getAdministrations(animalId,treatment.id));setError('')}catch{setError('Could not load schedule')}}
 useEffect(()=>{load()},[animalId,treatment.id])
 async function generate(){setBusy(true);try{await generateAdministrations(animalId,treatment.id,14);await load()}catch(e){setError(e instanceof Error?e.message:'Could not generate schedule')}finally{setBusy(false)}}
 async function act(a:Administration){setBusy(true);try{await administer(animalId,treatment.id,a.id,'Current user');await load()}catch(e){setError(e instanceof Error?e.message:'Could not record administration')}finally{setBusy(false)}}
 async function mark(a:Administration,status:'MISSED'|'SKIPPED'){setBusy(true);try{await setAdministrationStatus(animalId,treatment.id,a.id,status);await load()}catch(e){setError(e instanceof Error?e.message:'Could not update administration')}finally{setBusy(false)}}
 return <div className="treatment">
  <div className="treatment-head"><div><strong>{treatment.medication}</strong><span>{treatment.dose} · {treatment.route} · {treatment.frequency}</span><small>{treatment.startDate}{treatment.endDate?' → '+treatment.endDate:''}</small></div><button className="secondary small" disabled={busy} onClick={generate}>{busy?'…':'Generate schedule'}</button></div>
  {error&&<div className="inline-error">{error}</div>}
  {expanded&&administrations.length>0&&<div className="administrations">{administrations.slice(0,30).map(a=><div className="administration" key={a.id}><div><strong>{new Date(a.scheduledAt).toLocaleString()}</strong><small>{a.status}{a.administeredAt?' · '+new Date(a.administeredAt).toLocaleTimeString()+' · '+a.administeredBy:''}</small></div>{a.status==='SCHEDULED'&&<div className="actions"><button className="primary small" disabled={busy} onClick={()=>act(a)}>ADMINISTER</button><button className="secondary small" disabled={busy} onClick={()=>mark(a,'SKIPPED')}>Skip</button></div>}</div>)}</div>}
  {!expanded&&administrations.length>0&&<div className="next-dose"><span>Next: {new Date(administrations.find(a=>a.status==='SCHEDULED')?.scheduledAt||administrations[0].scheduledAt).toLocaleString()}</span><span className="chip">{administrations.filter(a=>a.status==='ADMINISTERED').length} administered</span></div>}
 </div>
}
