import {FormEvent,useEffect,useState} from 'react'
import {Animal,createAnimal,getAnimals} from './api'

const emptyForm={name:'',animalType:'DOG',sex:'UNKNOWN',dateOfBirth:'',weightKg:'',microchipNumber:'',intakeDate:new Date().toISOString().slice(0,10),rescueSource:'',location:'',status:'ACTIVE',notes:''}

export default function App(){
 const [animals,setAnimals]=useState<Animal[]>([]),[query,setQuery]=useState(''),[showForm,setShowForm]=useState(false),[form,setForm]=useState(emptyForm),[error,setError]=useState('')
 async function load(q=query){try{setAnimals(await getAnimals(q));setError('')}catch{setError('Could not load animals')}}
 useEffect(()=>{load('')},[])
 useEffect(()=>{const t=setTimeout(()=>load(query),250);return()=>clearTimeout(t)},[query])
 async function submit(e:FormEvent){e.preventDefault();try{await createAnimal({...form,weightKg:form.weightKg?Number(form.weightKg):undefined,dateOfBirth:form.dateOfBirth||undefined});setForm(emptyForm);setShowForm(false);await load()}catch{setError('Could not save animal')}}
 const update=(key:string,value:string)=>setForm(f=>({...f,[key]:value}))
 return <main className="shell">
  <header className="topbar"><div><p className="eyebrow">SHELTER MANAGEMENT</p><h1>Animals</h1><p className="muted">Every animal, its history and care.</p></div><button className="primary" onClick={()=>setShowForm(true)}>+ Add animal</button></header>
  <section className="toolbar"><input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Search by name or microchip…" /><span className="muted">{animals.length} animals</span></section>
  {error&&<div className="error">{error}</div>}
  <section className="animal-list">{animals.map(a=><article className="animal-card" key={a.id}><div className="avatar">{a.name.charAt(0).toUpperCase()}</div><div className="animal-main"><h2>{a.name}</h2><p>{a.animalType} · {a.sex} · {a.weightKg?a.weightKg+' kg':'weight not recorded'}</p><p className="muted">{a.location||'No location'} · {a.status}</p></div><span className="chip">{a.microchipNumber||'No chip'}</span></article>)}{animals.length===0&&<div className="empty">No animals registered yet.</div>}</section>
  {showForm&&<div className="modal-backdrop"><form className="modal" onSubmit={submit}><div className="modal-head"><div><p className="eyebrow">NEW ANIMAL</p><h2>Add animal</h2></div><button type="button" className="close" onClick={()=>setShowForm(false)}>×</button></div>
   <div className="form-grid">
    <label>Name<input required value={form.name} onChange={e=>update('name',e.target.value)}/></label>
    <label>Type<select value={form.animalType} onChange={e=>update('animalType',e.target.value)}><option>DOG</option><option>CAT</option><option>OTHER</option></select></label>
    <label>Sex<select value={form.sex} onChange={e=>update('sex',e.target.value)}><option>UNKNOWN</option><option>MALE</option><option>FEMALE</option></select></label>
    <label>Weight (kg)<input type="number" step="0.001" min="0" value={form.weightKg} onChange={e=>update('weightKg',e.target.value)}/></label>
    <label>Date of birth<input type="date" value={form.dateOfBirth} onChange={e=>update('dateOfBirth',e.target.value)}/></label>
    <label>Intake date<input required type="date" value={form.intakeDate} onChange={e=>update('intakeDate',e.target.value)}/></label>
    <label>Microchip<input value={form.microchipNumber} onChange={e=>update('microchipNumber',e.target.value)}/></label>
    <label>Location<input value={form.location} onChange={e=>update('location',e.target.value)}/></label>
    <label>Rescue source<input value={form.rescueSource} onChange={e=>update('rescueSource',e.target.value)}/></label>
    <label>Status<select value={form.status} onChange={e=>update('status',e.target.value)}><option>ACTIVE</option><option>TREATMENT</option><option>QUARANTINE</option><option>FOSTER</option><option>ADOPTED</option></select></label>
    <label className="full">Notes<textarea value={form.notes} onChange={e=>update('notes',e.target.value)}/></label>
   </div><div className="actions"><button type="button" onClick={()=>setShowForm(false)}>Cancel</button><button className="primary">Save animal</button></div>
  </form></div>}
 </main>
}