export type Animal={id:string;animalCode:string;name:string;animalType:string;sex:string;dateOfBirth?:string;weightKg?:number;microchipNumber?:string;intakeDate:string;rescueSource?:string;location?:string;status:string;notes?:string;photoUrl?:string}
export type AnimalInput=Omit<Animal,'id'|'animalCode'>
export type Treatment={id:string;medication:string;dose:string;route:string;frequency:string;startDate:string;endDate?:string;status:string;instructions?:string;prescribedBy?:string}
export type Administration={id:string;scheduledAt:string;administeredAt?:string;administeredBy?:string;status:'SCHEDULED'|'ADMINISTERED'|'MISSED'|'SKIPPED';notes?:string}
async function json(r:Response){if(r.status===401){localStorage.removeItem('shelterAuth');window.dispatchEvent(new Event('shelter-auth-required'));throw new Error('Authentication required')}if(!r.ok)throw new Error(await r.text());return r.json()}
function headers(){const auth=localStorage.getItem('shelterAuth');return auth?{Authorization:'Basic '+auth}:{}}
export async function getAnimals(q=''):Promise<Animal[]>{return json(await fetch('/api/animals'+(q?'?q='+encodeURIComponent(q):''),{headers:headers()}))}
export async function createAnimal(a:AnimalInput):Promise<Animal>{return json(await fetch('/api/animals',{method:'POST',headers:{'Content-Type':'application/json',...headers()},body:JSON.stringify(a)}))}
export async function updateAnimal(id:string,a:AnimalInput):Promise<Animal>{return json(await fetch('/api/animals/'+id,{method:'PUT',headers:{'Content-Type':'application/json',...headers()},body:JSON.stringify(a)}))}
export async function getTreatments(animalId:string):Promise<Treatment[]>{return json(await fetch('/api/animals/'+animalId+'/treatments',{headers:headers()}))}
export async function getAdministrations(animalId:string,treatmentId:string):Promise<Administration[]>{return json(await fetch('/api/animals/'+animalId+'/treatments/'+treatmentId+'/administrations',{headers:headers()}))}
export async function generateAdministrations(animalId:string,treatmentId:string,days=14):Promise<Administration[]>{return json(await fetch('/api/animals/'+animalId+'/treatments/'+treatmentId+'/administrations/generate?days='+days,{method:'POST',headers:headers()}))}
export async function administer(animalId:string,treatmentId:string,administrationId:string,administeredBy:string,notes?:string):Promise<Administration>{return json(await fetch('/api/animals/'+animalId+'/treatments/'+treatmentId+'/administrations/'+administrationId+'/administer',{method:'POST',headers:{'Content-Type':'application/json',...headers()},body:JSON.stringify({notes})}))}
export async function setAdministrationStatus(animalId:string,treatmentId:string,administrationId:string,status:'MISSED'|'SKIPPED',notes?:string):Promise<Administration>{return json(await fetch('/api/animals/'+animalId+'/treatments/'+treatmentId+'/administrations/'+administrationId+'/status',{method:'POST',headers:{'Content-Type':'application/json',...headers()},body:JSON.stringify({status,notes})}))}

export type MedicalEvent={id:string;eventType:string;eventDate:string;title:string;diagnosis?:string;provider?:string;notes?:string}
export type Vaccination={id:string;vaccineName:string;vaccineType?:string;administeredDate:string;nextDueDate?:string;batchNumber?:string;veterinarian?:string;notes?:string}
export type Deworming={id:string;productName:string;treatmentType?:string;administeredDate:string;nextDueDate?:string;dose?:string;veterinarian?:string;notes?:string}
export async function getMedicalEvents(id:string):Promise<MedicalEvent[]>{return json(await fetch('/api/animals/'+id+'/medical-events',{headers:headers()}))}
export async function createMedicalEvent(id:string,a:Omit<MedicalEvent,'id'>):Promise<MedicalEvent>{return json(await fetch('/api/animals/'+id+'/medical-events',{method:'POST',headers:{'Content-Type':'application/json',...headers()},body:JSON.stringify(a)}))}
export async function getVaccinations(id:string):Promise<Vaccination[]>{return json(await fetch('/api/animals/'+id+'/vaccinations',{headers:headers()}))}
export async function createVaccination(id:string,a:Omit<Vaccination,'id'>):Promise<Vaccination>{return json(await fetch('/api/animals/'+id+'/vaccinations',{method:'POST',headers:{'Content-Type':'application/json',...headers()},body:JSON.stringify(a)}))}
export async function getDewormings(id:string):Promise<Deworming[]>{return json(await fetch('/api/animals/'+id+'/dewormings',{headers:headers()}))}
export async function createDeworming(id:string,a:Omit<Deworming,'id'>):Promise<Deworming>{return json(await fetch('/api/animals/'+id+'/dewormings',{method:'POST',headers:{'Content-Type':'application/json',...headers()},body:JSON.stringify(a)}))}
